param(
    [string]$KindClusterName = "urr-local",
    [switch]$DeleteCluster
)

$ErrorActionPreference = "Stop"

if ($DeleteCluster) {
    kind delete cluster --name $KindClusterName
    Write-Host "Deleted kind cluster '$KindClusterName'."
    exit 0
}

kubectl delete namespace urr-spring --ignore-not-found=true
Write-Host "Deleted namespace 'urr-spring'."
