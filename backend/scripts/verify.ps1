param([string]$Service = '', [string]$MavenRepository = '', [switch]$Offline)
$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $workspace
if (!$MavenRepository) { $MavenRepository = Join-Path $repositoryRoot '.m2/repository' }
$projects = @('infrastructure/config-server', 'infrastructure/discovery-server', 'services/api-gateway', 'services/user-service', 'services/product-service', 'services/order-service', 'services/payment-service', 'services/notification-service')
if ($Service) { $projects = $projects | Where-Object { (Split-Path -Leaf $_) -eq $Service }; if (!$projects) { throw "Unknown service: $Service" } }
foreach ($project in $projects) {
    Write-Host "Verifying $project"
    $arguments = @('-B', '-ntp', "-Dmaven.repo.local=$MavenRepository", '-f', (Join-Path $workspace "$project/pom.xml"), 'verify')
    if ($Offline) { $arguments += '-o' }
    & mvn @arguments
    if ($LASTEXITCODE -ne 0) { throw "Verification failed: $project" }
}
