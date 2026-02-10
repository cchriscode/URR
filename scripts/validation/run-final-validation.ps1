param(
    [string]$NewBaseUrl = "http://localhost:3001",
    [string]$LegacyBaseUrl = "",
    [switch]$SkipUnit,
    [switch]$SkipWeb,
    [switch]$SkipApi,
    [switch]$SkipParity,
    [string]$ReportDir = "reports/final-validation",
    [string]$K8sNamespace = "tiketi-spring",
    [string]$PostgresDeployment = "postgres-spring",
    [string]$PostgresUser = "tiketi_user",
    [string]$PostgresPassword = "tiketi_password"
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $repoRoot

$reportPath = Join-Path $repoRoot $ReportDir
New-Item -ItemType Directory -Force -Path $reportPath | Out-Null

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportFile = Join-Path $reportPath "validation-$timestamp.json"
$latestFile = Join-Path $reportPath "latest.json"

$report = [ordered]@{
    timestamp = (Get-Date).ToString("o")
    newBaseUrl = $NewBaseUrl
    legacyBaseUrl = $LegacyBaseUrl
    steps = @()
    apiChecks = @()
    parityChecks = @()
}

function Add-Step {
    param(
        [string]$Name,
        [string]$Status,
        [double]$DurationSec,
        [string]$Details
    )
    $report.steps += [ordered]@{
        name = $Name
        status = $Status
        durationSec = [Math]::Round($DurationSec, 2)
        details = $Details
    }
}

function Run-CommandStep {
    param(
        [string]$Name,
        [scriptblock]$Action
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        & $Action
        $sw.Stop()
        Add-Step -Name $Name -Status "passed" -DurationSec $sw.Elapsed.TotalSeconds -Details "ok"
    }
    catch {
        $sw.Stop()
        Add-Step -Name $Name -Status "failed" -DurationSec $sw.Elapsed.TotalSeconds -Details $_.Exception.Message
        throw
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        $Body
    )

    $params = @{
        Method = $Method
        Uri = $Url
        UseBasicParsing = $true
    }

    if ($Headers) {
        $params.Headers = $Headers
    }

    if ($null -ne $Body) {
        $params.ContentType = "application/json"
        $params.Body = ($Body | ConvertTo-Json -Depth 30)
    }

    try {
        $resp = Invoke-WebRequest @params
        return [ordered]@{
            status = [int]$resp.StatusCode
            body = $resp.Content
        }
    }
    catch {
        $status = -1
        $body = $_.Exception.Message
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
            if ($_.ErrorDetails.Message) {
                $body = $_.ErrorDetails.Message
            }
            else {
                try {
                    $stream = $_.Exception.Response.GetResponseStream()
                    if ($stream) {
                        $reader = New-Object System.IO.StreamReader($stream)
                        $rawError = $reader.ReadToEnd()
                        if (-not [string]::IsNullOrWhiteSpace($rawError)) {
                            $body = $rawError
                        }
                    }
                }
                catch {
                    # keep default exception text
                }
            }
        }
        return [ordered]@{
            status = $status
            body = $body
        }
    }
}

function Add-ApiCheck {
    param(
        [string]$Name,
        [int]$Expected,
        [hashtable]$Actual
    )

    $passed = ($Actual.status -eq $Expected)
    $report.apiChecks += [ordered]@{
        name = $Name
        expectedStatus = $Expected
        actualStatus = $Actual.status
        passed = $passed
    }

    if (-not $passed) {
        throw "API check failed: $Name expected=$Expected actual=$($Actual.status) body=$($Actual.body)"
    }
}

function Parse-JsonBody {
    param(
        [hashtable]$Response,
        [string]$Name
    )

    if ([string]::IsNullOrWhiteSpace($Response.body)) {
        throw "Empty response body: $Name"
    }

    $raw = [string]$Response.body
    $clean = ($raw -replace "[\x00-\x08\x0B\x0C\x0E-\x1F]", "")
    $compact = ($clean -replace "`r", "" -replace "`n", "")

    $candidates = @($raw, $clean, $compact)

    $objStart = $clean.IndexOf("{")
    $objEnd = $clean.LastIndexOf("}")
    if ($objStart -ge 0 -and $objEnd -gt $objStart) {
        $candidates += $clean.Substring($objStart, $objEnd - $objStart + 1)
    }

    $arrStart = $clean.IndexOf("[")
    $arrEnd = $clean.LastIndexOf("]")
    if ($arrStart -ge 0 -and $arrEnd -gt $arrStart) {
        $candidates += $clean.Substring($arrStart, $arrEnd - $arrStart + 1)
    }

    foreach ($candidate in ($candidates | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)) {
        try {
            return ($candidate | ConvertFrom-Json)
        }
        catch {
            continue
        }
    }

    throw "Invalid JSON response for $Name. body=$($Response.body)"
}

function Get-TopLevelKeys {
    param([string]$Body)

    if ([string]::IsNullOrWhiteSpace($Body)) {
        return @()
    }

    try {
        $obj = $Body | ConvertFrom-Json
        if ($obj -is [System.Collections.IEnumerable] -and -not ($obj -is [string])) {
            if ($obj.Count -gt 0 -and $obj[0] -is [pscustomobject]) {
                return @($obj[0].PSObject.Properties.Name | Sort-Object -Unique)
            }
            return @()
        }
        if ($obj -is [pscustomobject]) {
            return @($obj.PSObject.Properties.Name | Sort-Object -Unique)
        }
    }
    catch {
        return @()
    }

    return @()
}

function Ensure-KubectlReady {
    if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
        throw "kubectl is required for expanded API validation"
    }

    & kubectl get deployment $PostgresDeployment -n $K8sNamespace | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to access deployment/$PostgresDeployment in namespace $K8sNamespace"
    }
}

function Invoke-PsqlQuery {
    param(
        [string]$Database,
        [string]$Sql
    )

    $output = & kubectl exec -n $K8sNamespace "deployment/$PostgresDeployment" -- env "PGPASSWORD=$PostgresPassword" psql -U $PostgresUser -d $Database -At -c $Sql 2>&1
    if ($LASTEXITCODE -ne 0) {
        $joined = (@($output) -join "`n")
        throw "psql query failed for database '$Database': $joined"
    }

    $rows = @(
        @($output) |
            ForEach-Object { $_.ToString().Trim() } |
            Where-Object {
                $_ -ne "" -and
                $_ -notmatch "^Defaulted container" -and
                $_ -notmatch "^command terminated"
            }
    )
    return ,$rows
}

function Extract-Id {
    param(
        [object]$Object,
        [string]$PropertyPath,
        [string]$Name
    )

    $cursor = $Object
    foreach ($segment in $PropertyPath.Split('.')) {
        if ($null -eq $cursor) {
            throw "Missing value for $Name at '$PropertyPath'"
        }
        $cursor = $cursor.$segment
    }

    if ($null -eq $cursor -or [string]::IsNullOrWhiteSpace([string]$cursor)) {
        throw "Missing value for $Name at '$PropertyPath'"
    }

    return [string]$cursor
}

function Extract-JsonStringField {
    param(
        [string]$Body,
        [string]$FieldName,
        [string]$Context
    )

    $pattern = '"' + [regex]::Escape($FieldName) + '"\s*:\s*"([^"]*)"'
    $match = [regex]::Match($Body, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)
    if (-not $match.Success) {
        throw "Missing '$FieldName' in $Context response"
    }
    return [string]$match.Groups[1].Value
}

# 1) Unit tests
if (-not $SkipUnit) {
    Run-CommandStep -Name "unit:auth-service" -Action {
        $env:JAVA_HOME = "C:\Users\USER\project-ticketing-copy\.tools\jdk21\jdk-21.0.10+7"
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Set-Location "$repoRoot\services-spring\auth-service"
        ./gradlew.bat clean test
        if ($LASTEXITCODE -ne 0) { throw "auth-service test failed" }
    }

    Run-CommandStep -Name "unit:ticket-service" -Action {
        $env:JAVA_HOME = "C:\Users\USER\project-ticketing-copy\.tools\jdk21\jdk-21.0.10+7"
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Set-Location "$repoRoot\services-spring\ticket-service"
        ./gradlew.bat clean test
        if ($LASTEXITCODE -ne 0) { throw "ticket-service test failed" }
    }

    Run-CommandStep -Name "unit:payment-service" -Action {
        $env:JAVA_HOME = "C:\Users\USER\project-ticketing-copy\.tools\jdk21\jdk-21.0.10+7"
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Set-Location "$repoRoot\services-spring\payment-service"
        ./gradlew.bat clean test
        if ($LASTEXITCODE -ne 0) { throw "payment-service test failed" }
    }

    Run-CommandStep -Name "unit:stats-service" -Action {
        $env:JAVA_HOME = "C:\Users\USER\project-ticketing-copy\.tools\jdk21\jdk-21.0.10+7"
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Set-Location "$repoRoot\services-spring\stats-service"
        ./gradlew.bat clean test
        if ($LASTEXITCODE -ne 0) { throw "stats-service test failed" }
    }

    Run-CommandStep -Name "unit:gateway-service" -Action {
        $env:JAVA_HOME = "C:\Users\USER\project-ticketing-copy\.tools\jdk21\jdk-21.0.10+7"
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
        Set-Location "$repoRoot\services-spring\gateway-service"
        ./gradlew.bat clean test
        if ($LASTEXITCODE -ne 0) { throw "gateway-service test failed" }
    }
}

# 2) Frontend static checks
if (-not $SkipWeb) {
    Run-CommandStep -Name "web:lint" -Action {
        Set-Location $repoRoot
        npm run web:lint
        if ($LASTEXITCODE -ne 0) { throw "web lint failed" }
    }

    Run-CommandStep -Name "web:build" -Action {
        Set-Location $repoRoot
        npm run web:build
        if ($LASTEXITCODE -ne 0) { throw "web build failed" }
    }
}

# 3) API integration checks (expanded end-to-end)
if (-not $SkipApi) {
    Run-CommandStep -Name "api:integration:expanded" -Action {
        $health = Invoke-Api -Method "GET" -Url "$NewBaseUrl/health" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /health" -Expected 200 -Actual $health

        $suffix = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        $password = "Password123!"

        $userEmail = "validation_user_$suffix@tiketi.local"
        $adminEmail = "validation_admin_$suffix@tiketi.local"

        $registerUser = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/auth/register" -Headers $null -Body @{
            email = $userEmail
            password = $password
            name = "validation-user"
        }
        Add-ApiCheck -Name "POST /api/auth/register (user)" -Expected 201 -Actual $registerUser

        $loginUser = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/auth/login" -Headers $null -Body @{
            email = $userEmail
            password = $password
        }
        Add-ApiCheck -Name "POST /api/auth/login (user)" -Expected 200 -Actual $loginUser
        $userToken = (Extract-JsonStringField -Body $loginUser.body -FieldName "token" -Context "POST /api/auth/login (user)") -replace "\s", ""
        $userId = Extract-JsonStringField -Body $loginUser.body -FieldName "userId" -Context "POST /api/auth/login (user)"
        $userHeaders = @{ Authorization = "Bearer $userToken" }

        $registerAdmin = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/auth/register" -Headers $null -Body @{
            email = $adminEmail
            password = $password
            name = "validation-admin"
        }
        Add-ApiCheck -Name "POST /api/auth/register (admin candidate)" -Expected 201 -Actual $registerAdmin

        $loginAdminBefore = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/auth/login" -Headers $null -Body @{
            email = $adminEmail
            password = $password
        }
        Add-ApiCheck -Name "POST /api/auth/login (admin candidate)" -Expected 200 -Actual $loginAdminBefore

        Ensure-KubectlReady
        $safeAdminEmail = $adminEmail.Replace("'", "''")
        Invoke-PsqlQuery -Database "auth_db" -Sql "UPDATE users SET role = 'admin' WHERE email = '$safeAdminEmail';" | Out-Null

        $roleRows = Invoke-PsqlQuery -Database "auth_db" -Sql "SELECT role FROM users WHERE email = '$safeAdminEmail';"
        if ($roleRows.Count -eq 0 -or $roleRows[0] -ne "admin") {
            throw "Failed to promote admin user role in auth_db"
        }

        $loginAdminAfter = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/auth/login" -Headers $null -Body @{
            email = $adminEmail
            password = $password
        }
        Add-ApiCheck -Name "POST /api/auth/login (promoted admin)" -Expected 200 -Actual $loginAdminAfter
        $adminToken = (Extract-JsonStringField -Body $loginAdminAfter.body -FieldName "token" -Context "POST /api/auth/login (promoted admin)") -replace "\s", ""
        $adminRole = Extract-JsonStringField -Body $loginAdminAfter.body -FieldName "role" -Context "POST /api/auth/login (promoted admin)"
        if ($adminRole -ne "admin") { throw "Admin login role claim is not admin" }
        $adminHeaders = @{ Authorization = "Bearer $adminToken" }

        $meUser = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/auth/me" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/auth/me (user token)" -Expected 200 -Actual $meUser

        $meAdmin = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/auth/me" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/auth/me (admin token)" -Expected 200 -Actual $meAdmin

        $adminNoToken = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/admin/dashboard" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/admin/dashboard (no token)" -Expected 401 -Actual $adminNoToken

        $adminWithUser = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/admin/dashboard" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/admin/dashboard (user token)" -Expected 403 -Actual $adminWithUser

        $statsNoToken = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/stats/overview" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/stats/overview (no token)" -Expected 401 -Actual $statsNoToken

        $statsWithUser = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/stats/overview" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/stats/overview (user token)" -Expected 403 -Actual $statsWithUser

        $seatLayoutsResp = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/admin/seat-layouts" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/admin/seat-layouts" -Expected 200 -Actual $seatLayoutsResp
        $seatLayoutsJson = Parse-JsonBody -Response $seatLayoutsResp -Name "GET /api/admin/seat-layouts"

        $layoutName = "Validation Layout $suffix"
        $safeLayoutName = $layoutName.Replace("'", "''")
        $layoutInsertSql = "INSERT INTO seat_layouts (name, description, total_seats, layout_config) VALUES ('$safeLayoutName', 'Validation seat layout', 2, jsonb_build_object('sections', jsonb_build_array(jsonb_build_object('name','A','rows',1,'seatsPerRow',2,'price',120000)))) RETURNING id;"
        $layoutInsert = Invoke-PsqlQuery -Database "ticket_db" -Sql $layoutInsertSql
        if ($layoutInsert.Count -eq 0) {
            throw "Failed to insert seat_layouts test fixture"
        }
        $seatLayoutId = [string]$layoutInsert[0]

        $saleStart = (Get-Date).ToUniversalTime().AddDays(-1).ToString("o")
        $saleEnd = (Get-Date).ToUniversalTime().AddDays(7).ToString("o")
        $eventDate = (Get-Date).ToUniversalTime().AddDays(14).ToString("o")

        $createEventResp = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/admin/events" -Headers $adminHeaders -Body @{
            title = "Validation Event $suffix"
            description = "Validation migration parity event"
            venue = "Validation Hall"
            address = "Validation Street"
            eventDate = $eventDate
            saleStartDate = $saleStart
            saleEndDate = $saleEnd
            posterImageUrl = "https://example.com/poster.png"
            artistName = "Validation Artist"
            ticketTypes = @(
                @{
                    name = "General"
                    price = 110000
                    totalQuantity = 20
                    description = "General seat"
                }
            )
        }
        Add-ApiCheck -Name "POST /api/admin/events" -Expected 201 -Actual $createEventResp
        $createEventJson = Parse-JsonBody -Response $createEventResp -Name "POST /api/admin/events"
        $eventId = Extract-Id -Object $createEventJson -PropertyPath "event.id" -Name "eventId"

        $updateEventResp = Invoke-Api -Method "PUT" -Url "$NewBaseUrl/api/admin/events/$eventId" -Headers $adminHeaders -Body @{
            title = "Validation Event Updated $suffix"
            description = "Validation migration parity event updated"
            venue = "Validation Hall"
            address = "Validation Street"
            eventDate = $eventDate
            saleStartDate = $saleStart
            saleEndDate = $saleEnd
            posterImageUrl = "https://example.com/poster.png"
            artistName = "Validation Artist"
            ticketTypes = @(
                @{
                    name = "General"
                    price = 110000
                    totalQuantity = 20
                    description = "General seat"
                }
            )
        }
        Add-ApiCheck -Name "PUT /api/admin/events/{id}" -Expected 200 -Actual $updateEventResp

        $createTicketResp = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/admin/events/$eventId/tickets" -Headers $adminHeaders -Body @{
            name = "VIP"
            price = 180000
            totalQuantity = 5
            description = "VIP zone"
        }
        Add-ApiCheck -Name "POST /api/admin/events/{id}/tickets" -Expected 201 -Actual $createTicketResp
        $createTicketJson = Parse-JsonBody -Response $createTicketResp -Name "POST /api/admin/events/{id}/tickets"
        $adminTicketId = Extract-Id -Object $createTicketJson -PropertyPath "ticketType.id" -Name "adminTicketId"

        $updateTicketResp = Invoke-Api -Method "PUT" -Url "$NewBaseUrl/api/admin/tickets/$adminTicketId" -Headers $adminHeaders -Body @{
            name = "VIP-UPDATED"
            price = 185000
            totalQuantity = 6
            description = "VIP zone updated"
        }
        Add-ApiCheck -Name "PUT /api/admin/tickets/{id}" -Expected 200 -Actual $updateTicketResp

        $deleteSeatsResp = Invoke-Api -Method "DELETE" -Url "$NewBaseUrl/api/admin/events/$eventId/seats" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "DELETE /api/admin/events/{id}/seats" -Expected 200 -Actual $deleteSeatsResp

        $generateSeatsResp = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/admin/events/$eventId/generate-seats" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "POST /api/admin/events/{id}/generate-seats (without layout)" -Expected 400 -Actual $generateSeatsResp

        $eventsResp = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/events?page=1&limit=20" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/events" -Expected 200 -Actual $eventsResp

        $eventDetailResp = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/events/$eventId" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/events/{id}" -Expected 200 -Actual $eventDetailResp

        $ticketsResp = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/tickets/event/$eventId" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/tickets/event/{eventId}" -Expected 200 -Actual $ticketsResp
        $ticketsJson = Parse-JsonBody -Response $ticketsResp -Name "GET /api/tickets/event/{eventId}"
        $ticketTypes = @($ticketsJson.ticketTypes)
        if ($ticketTypes.Count -eq 0) {
            throw "No ticketTypes returned for validation event"
        }
        $ticketTypeId = [string]$ticketTypes[0].id

        $availabilityResp = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/tickets/availability/$ticketTypeId" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/tickets/availability/{ticketTypeId}" -Expected 200 -Actual $availabilityResp

        $seatLayoutsPublic = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/seats/layouts" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/seats/layouts" -Expected 200 -Actual $seatLayoutsPublic

        $safeSeatEventTitle = ("Validation Seat Event " + $suffix).Replace("'", "''")
        $seatEventInsertSql = "INSERT INTO events (title, description, venue, address, event_date, sale_start_date, sale_end_date, status, seat_layout_id, created_by) VALUES ('$safeSeatEventTitle', 'Validation seat event', 'Seat Hall', 'Seat Street', NOW() + INTERVAL '14 days', NOW() - INTERVAL '1 day', NOW() + INTERVAL '7 days', 'on_sale', CAST('$seatLayoutId' AS UUID), CAST('$userId' AS UUID)) RETURNING id;"
        $seatEventRows = Invoke-PsqlQuery -Database "ticket_db" -Sql $seatEventInsertSql
        if ($seatEventRows.Count -eq 0) {
            throw "Failed to insert seat event fixture"
        }
        $seatEventId = [string]$seatEventRows[0]

        $seatInsertSql = "INSERT INTO seats (event_id, section, row_number, seat_number, seat_label, price, status) VALUES (CAST('$seatEventId' AS UUID), 'A', 1, 1, 'A-1-1', 120000, 'available') RETURNING id;"
        $seatRows = Invoke-PsqlQuery -Database "ticket_db" -Sql $seatInsertSql
        if ($seatRows.Count -eq 0) {
            throw "Failed to insert seat fixture"
        }
        $seatId = [string]$seatRows[0]

        $seatsResp = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/seats/events/$seatEventId" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/seats/events/{eventId}" -Expected 200 -Actual $seatsResp

        $seatReserveResp = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/seats/reserve" -Headers $userHeaders -Body @{
            eventId = $seatEventId
            seatIds = @($seatId)
        }
        Add-ApiCheck -Name "POST /api/seats/reserve" -Expected 200 -Actual $seatReserveResp
        $seatReserveJson = Parse-JsonBody -Response $seatReserveResp -Name "POST /api/seats/reserve"
        $seatReservationId = Extract-Id -Object $seatReserveJson -PropertyPath "reservation.id" -Name "seatReservationId"

        $seatReservationDetail = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/seats/reservation/$seatReservationId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/seats/reservation/{reservationId}" -Expected 200 -Actual $seatReservationDetail

        $queueCheck = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/queue/check/$seatEventId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "POST /api/queue/check/{eventId}" -Expected 200 -Actual $queueCheck

        $queueStatus = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/queue/status/$seatEventId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/queue/status/{eventId}" -Expected 200 -Actual $queueStatus

        $queueHeartbeat = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/queue/heartbeat/$seatEventId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "POST /api/queue/heartbeat/{eventId}" -Expected 200 -Actual $queueHeartbeat

        $queueLeave = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/queue/leave/$seatEventId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "POST /api/queue/leave/{eventId}" -Expected 200 -Actual $queueLeave

        $reservationResp = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/reservations" -Headers $userHeaders -Body @{
            eventId = $eventId
            items = @(
                @{
                    ticketTypeId = $ticketTypeId
                    quantity = 1
                }
            )
        }
        Add-ApiCheck -Name "POST /api/reservations" -Expected 200 -Actual $reservationResp
        $reservationJson = Parse-JsonBody -Response $reservationResp -Name "POST /api/reservations"
        $reservationId = Extract-Id -Object $reservationJson -PropertyPath "reservation.id" -Name "reservationId"

        $myReservations = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/reservations/my" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/reservations/my" -Expected 200 -Actual $myReservations

        $reservationById = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/reservations/$reservationId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/reservations/{id}" -Expected 200 -Actual $reservationById

        $cancelReservation = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/reservations/$reservationId/cancel" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "POST /api/reservations/{id}/cancel" -Expected 200 -Actual $cancelReservation

        $paymentReservationResp = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/reservations" -Headers $userHeaders -Body @{
            eventId = $eventId
            items = @(
                @{
                    ticketTypeId = $ticketTypeId
                    quantity = 1
                }
            )
        }
        Add-ApiCheck -Name "POST /api/reservations (for payment)" -Expected 200 -Actual $paymentReservationResp
        $paymentReservationJson = Parse-JsonBody -Response $paymentReservationResp -Name "POST /api/reservations (for payment)"
        $paymentReservationId = Extract-Id -Object $paymentReservationJson -PropertyPath "reservation.id" -Name "paymentReservationId"
        $paymentAmount = [int]$paymentReservationJson.reservation.totalAmount

        $preparePayment = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/payments/prepare" -Headers $userHeaders -Body @{
            reservationId = $paymentReservationId
            amount = $paymentAmount
        }
        Add-ApiCheck -Name "POST /api/payments/prepare" -Expected 200 -Actual $preparePayment
        $preparePaymentJson = Parse-JsonBody -Response $preparePayment -Name "POST /api/payments/prepare"
        if (-not $preparePaymentJson.orderId) {
            throw "orderId missing in payment prepare response"
        }
        $orderId = [string]$preparePaymentJson.orderId

        $orderLookup = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/payments/order/$orderId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/payments/order/{orderId}" -Expected 200 -Actual $orderLookup

        $paymentKey = "pay_$suffix"
        $confirmPayment = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/payments/confirm" -Headers $userHeaders -Body @{
            paymentKey = $paymentKey
            orderId = $orderId
            amount = $paymentAmount
        }
        Add-ApiCheck -Name "POST /api/payments/confirm" -Expected 200 -Actual $confirmPayment

        $myPayments = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/payments/user/me?limit=20&offset=0" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "GET /api/payments/user/me" -Expected 200 -Actual $myPayments

        $cancelPayment = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/payments/$paymentKey/cancel" -Headers $userHeaders -Body @{
            cancelReason = "validation-cancel"
        }
        Add-ApiCheck -Name "POST /api/payments/{paymentKey}/cancel" -Expected 200 -Actual $cancelPayment

        $processReservationResp = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/reservations" -Headers $userHeaders -Body @{
            eventId = $eventId
            items = @(
                @{
                    ticketTypeId = $ticketTypeId
                    quantity = 1
                }
            )
        }
        Add-ApiCheck -Name "POST /api/reservations (for process payment)" -Expected 200 -Actual $processReservationResp
        $processReservationJson = Parse-JsonBody -Response $processReservationResp -Name "POST /api/reservations (for process payment)"
        $processReservationId = Extract-Id -Object $processReservationJson -PropertyPath "reservation.id" -Name "processReservationId"

        $processPayment = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/payments/process" -Headers $userHeaders -Body @{
            reservationId = $processReservationId
            paymentMethod = "card"
        }
        Add-ApiCheck -Name "POST /api/payments/process" -Expected 200 -Actual $processPayment
        $processPaymentJson = Parse-JsonBody -Response $processPayment -Name "POST /api/payments/process"
        if ($processPaymentJson.payment -and $processPaymentJson.payment.orderId) {
            $processOrderLookup = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/payments/order/$($processPaymentJson.payment.orderId)" -Headers $userHeaders -Body $null
            Add-ApiCheck -Name "GET /api/payments/order/{orderId} (process flow)" -Expected 200 -Actual $processOrderLookup
        }

        $newsList = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/news?page=1&limit=20" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/news" -Expected 200 -Actual $newsList

        $newsCreate = Invoke-Api -Method "POST" -Url "$NewBaseUrl/api/news" -Headers $userHeaders -Body @{
            title = "Validation News $suffix"
            content = "Validation content"
            author = "validation-user"
            author_id = $userId
            is_pinned = $false
        }
        Add-ApiCheck -Name "POST /api/news" -Expected 201 -Actual $newsCreate
        $newsCreateJson = Parse-JsonBody -Response $newsCreate -Name "POST /api/news"
        $newsId = Extract-Id -Object $newsCreateJson -PropertyPath "news.id" -Name "newsId"

        $newsDetail = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/news/$newsId" -Headers $null -Body $null
        Add-ApiCheck -Name "GET /api/news/{id}" -Expected 200 -Actual $newsDetail

        $newsUpdate = Invoke-Api -Method "PUT" -Url "$NewBaseUrl/api/news/$newsId" -Headers $userHeaders -Body @{
            title = "Validation News Updated $suffix"
            content = "Validation content updated"
            is_pinned = $true
        }
        Add-ApiCheck -Name "PUT /api/news/{id}" -Expected 200 -Actual $newsUpdate

        $newsDelete = Invoke-Api -Method "DELETE" -Url "$NewBaseUrl/api/news/$newsId" -Headers $userHeaders -Body $null
        Add-ApiCheck -Name "DELETE /api/news/{id}" -Expected 200 -Actual $newsDelete

        $adminDashboard = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/admin/dashboard" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/admin/dashboard (admin token)" -Expected 200 -Actual $adminDashboard

        $adminReservations = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/admin/reservations?page=1&limit=20" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/admin/reservations" -Expected 200 -Actual $adminReservations

        $updateReservationStatus = Invoke-Api -Method "PATCH" -Url "$NewBaseUrl/api/admin/reservations/$processReservationId/status" -Headers $adminHeaders -Body @{
            status = "confirmed"
            paymentStatus = "completed"
        }
        Add-ApiCheck -Name "PATCH /api/admin/reservations/{id}/status" -Expected 200 -Actual $updateReservationStatus

        $statsOverview = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/stats/overview" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/stats/overview (admin token)" -Expected 200 -Actual $statsOverview

        $statsDaily = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/stats/daily?days=7" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/stats/daily" -Expected 200 -Actual $statsDaily

        $statsEvents = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/stats/events?limit=5" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/stats/events" -Expected 200 -Actual $statsEvents

        $statsRealtime = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/stats/realtime" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/stats/realtime" -Expected 200 -Actual $statsRealtime

        $statsPerformance = Invoke-Api -Method "GET" -Url "$NewBaseUrl/api/stats/performance" -Headers $adminHeaders -Body $null
        Add-ApiCheck -Name "GET /api/stats/performance" -Expected 200 -Actual $statsPerformance
    }
}

# 4) Optional parity checks vs legacy
if (-not [string]::IsNullOrWhiteSpace($LegacyBaseUrl) -and -not $SkipParity) {
    Run-CommandStep -Name "parity:legacy-vs-spring" -Action {
        $targets = @(
            "/health",
            "/api/events?page=1&limit=5",
            "/api/news?page=1&limit=5",
            "/api/seats/layouts"
        )

        foreach ($path in $targets) {
            $legacy = Invoke-Api -Method "GET" -Url "$LegacyBaseUrl$path" -Headers $null -Body $null
            $spring = Invoke-Api -Method "GET" -Url "$NewBaseUrl$path" -Headers $null -Body $null

            $legacyKeys = Get-TopLevelKeys -Body $legacy.body
            $springKeys = Get-TopLevelKeys -Body $spring.body
            $sameStatus = ($legacy.status -eq $spring.status)
            $sameKeys = (@($legacyKeys) -join ",") -eq (@($springKeys) -join ",")

            $report.parityChecks += [ordered]@{
                path = $path
                legacyStatus = $legacy.status
                springStatus = $spring.status
                statusMatched = $sameStatus
                legacyKeys = $legacyKeys
                springKeys = $springKeys
                keyShapeMatched = $sameKeys
            }
        }
    }
}

$failedSteps = @($report.steps | Where-Object { $_.status -ne "passed" }).Count
$failedApi = @($report.apiChecks | Where-Object { -not $_.passed }).Count
$report.summary = [ordered]@{
    stepsTotal = @($report.steps).Count
    stepsFailed = $failedSteps
    apiChecksTotal = @($report.apiChecks).Count
    apiChecksFailed = $failedApi
    parityChecksTotal = @($report.parityChecks).Count
}

$report | ConvertTo-Json -Depth 30 | Set-Content -Encoding ascii $reportFile
$report | ConvertTo-Json -Depth 30 | Set-Content -Encoding ascii $latestFile

Write-Host "Validation report: $reportFile"
Write-Host "Steps: $($report.summary.stepsTotal), failed: $($report.summary.stepsFailed)"
Write-Host "API checks: $($report.summary.apiChecksTotal), failed: $($report.summary.apiChecksFailed)"

if ($report.summary.stepsFailed -gt 0 -or $report.summary.apiChecksFailed -gt 0) {
    throw "Final validation failed"
}

Write-Host "Final validation passed."
