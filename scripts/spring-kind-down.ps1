param(
    [string]$KindClusterName = "tiketi-local",
    [switch]$DeleteCluster
)

$ErrorActionPreference = "Stop"

if ($DeleteCluster) {
    kind delete cluster --name $KindClusterName
    Write-Host "Deleted kind cluster '$KindClusterName'."
    exit 0
}

kubectl delete namespace tiketi-spring --ignore-not-found=true
Write-Host "Deleted namespace 'tiketi-spring'."
