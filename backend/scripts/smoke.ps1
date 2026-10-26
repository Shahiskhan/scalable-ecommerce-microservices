param([string]$BaseUrl = 'http://localhost:8080', [ValidateSet('CONFIRMED','CANCELLED')][string]$ExpectedStatus='CONFIRMED')
$ErrorActionPreference = 'Stop'
if (!$env:FIREBASE_ID_TOKEN -or !$env:ADMIN_FIREBASE_ID_TOKEN) { throw 'Set FIREBASE_ID_TOKEN and ADMIN_FIREBASE_ID_TOKEN to short-lived Firebase ID tokens.' }
$customer = @{ Authorization = "Bearer $env:FIREBASE_ID_TOKEN" }
$admin = @{ Authorization = "Bearer $env:ADMIN_FIREBASE_ID_TOKEN" }
$sku = 'SMOKE_' + ([Guid]::NewGuid().ToString('N').Substring(0,12).ToUpperInvariant())
$product = @{ sku=$sku; name='Smoke test product'; priceMinor=1999; currency='USD'; stock=2 } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/products" -Headers $admin -ContentType 'application/json' -Body $product | Out-Null
$cart = @{ items=@(@{sku=$sku;quantity=1}) } | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Put -Uri "$BaseUrl/api/cart" -Headers $customer -ContentType 'application/json' -Body $cart | Out-Null
$headers = @{ Authorization=$customer.Authorization; 'Idempotency-Key'=[Guid]::NewGuid().ToString() }
$order = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/orders" -Headers $headers -ContentType 'application/json' -Body $cart
$duplicate = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/orders" -Headers $headers -ContentType 'application/json' -Body $cart
if ($duplicate.orderId -ne $order.orderId) { throw 'Idempotent retry created another order' }
$until = [DateTime]::UtcNow.AddSeconds(60)
do {
    Start-Sleep -Seconds 1
    $state = Invoke-RestMethod -Uri "$BaseUrl/api/orders/$($order.orderId)" -Headers $customer
} until ($state.status -in @('CONFIRMED','CANCELLED') -or [DateTime]::UtcNow -gt $until)
if ($state.status -ne $ExpectedStatus) { throw "Expected $ExpectedStatus, got $($state.status) for order $($order.orderId)" }
if ($state.status -eq 'CONFIRMED' -and $state.totalMinor -ne 1999) { throw 'Authoritative order amount mismatch' }
$until = [DateTime]::UtcNow.AddSeconds(30)
do {
    Start-Sleep -Seconds 1
    $notices = @(Invoke-RestMethod -Uri "$BaseUrl/api/notifications" -Headers $customer)
    $matches = @($notices | Where-Object { $_.orderId -eq $order.orderId })
} until ($matches.Count -eq 1 -or [DateTime]::UtcNow -gt $until)
if ($matches.Count -ne 1) { throw 'Terminal in-app notification was not persisted' }
# Inventory is eventually consistent; cache TTL is30s. Check after the terminal event is applied.
Start-Sleep -Seconds 32
$after = Invoke-RestMethod -Uri "$BaseUrl/api/products/$sku" -Headers $customer
$expectedStock = if ($ExpectedStatus -eq 'CONFIRMED') { 1 } else { 2 }
if ($after.availableStock -ne $expectedStock) { throw "Expected stock $expectedStock; got $($after.availableStock)" }
Write-Host "Smoke passed: order=$($order.orderId) status=$($state.status); no duplicate checkout, stock reconciled, notification persisted."
