$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path -Parent $PSScriptRoot
$repositoryRoot = Split-Path -Parent $backendRoot
$target = Join-Path $backendRoot '.env'
if (Test-Path -LiteralPath $target) { throw '.env already exists. Preserve existing credentials.' }
function New-Secret { $bytes = [byte[]]::new(32); $random = [System.Security.Cryptography.RandomNumberGenerator]::Create(); try { $random.GetBytes($bytes) } finally { $random.Dispose() }; return ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant() }
$lines = @('FIREBASE_PROJECT_ID=replace-with-your-project-id', "INTERNAL_HMAC_SECRET=$(New-Secret)", 'CONFIG_USERNAME=config', "CONFIG_PASSWORD=$(New-Secret)", 'EUREKA_USERNAME=discovery', "EUREKA_PASSWORD=$(New-Secret)", "MYSQL_ROOT_PASSWORD=$(New-Secret)", "USER_DB_PASSWORD=$(New-Secret)", "PRODUCT_DB_PASSWORD=$(New-Secret)", "ORDER_DB_PASSWORD=$(New-Secret)", "PAYMENT_DB_PASSWORD=$(New-Secret)", "NOTIFICATION_DB_PASSWORD=$(New-Secret)", "REDIS_PASSWORD=$(New-Secret)")
[System.IO.File]::WriteAllLines($target, $lines)
$frontendEnv = Join-Path $repositoryRoot 'frontend/.env.local'
if (!(Test-Path -LiteralPath $frontendEnv)) { Copy-Item (Join-Path $repositoryRoot 'frontend/.env.example') $frontendEnv }
Write-Host 'Created backend/.env with random local credentials and frontend/.env.local from example. Set Firebase project/config values before starting.'
