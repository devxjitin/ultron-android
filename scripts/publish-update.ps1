<#
Builds the Ultron app and publishes it to the AWS update server, so the phone can update itself from anywhere.
Run from the laptop:   .\scripts\publish-update.ps1 -Notes "What changed"
The phone then shows "Update available" the next time Ultron is opened (or via About > Check for updates).
Needs: Docker running, and `aws login` done on this laptop.
#>
param(
    [string]$Notes = "",
    [string]$VersionName = "1.0.0",
    [string]$Bucket = "ultron-app-updates-143920986615"
)
$ErrorActionPreference = "Stop"
$android = Join-Path (Split-Path $PSScriptRoot -Parent) "android"

# Same number goes into the app (as its version code) and into latest.json, so the phone can compare builds.
$versionCode = [int]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() / 60)

Write-Host "Building version $VersionName (code $versionCode)..."
docker run --rm -v "${android}:/workspace" -v assistant-gradle-cache:/root/.gradle -v assistant-android-home:/root/.android `
    -w /workspace assistant-android-build ./gradlew assembleOta "-PappVersionCode=$versionCode" "-PappVersionName=$VersionName" --console=plain -q
if ($LASTEXITCODE -ne 0) { throw "The build failed; nothing was published." }

$apk = Join-Path $android ".docker-build\app\outputs\apk\ota\app-ota.apk"
$key = "apk/ultron-$versionCode.apk"
$info = [ordered]@{
    versionCode = $versionCode
    versionName = $VersionName
    notes       = $Notes
    sha256      = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
    sizeBytes   = (Get-Item $apk).Length
    apkKey      = $key
    publishedAt = (Get-Date).ToString("s")
}

Write-Host "Uploading to s3://$Bucket/$key ..."
aws s3 cp $apk "s3://$Bucket/$key" --only-show-errors
if ($LASTEXITCODE -ne 0) { throw "Upload failed (is 'aws login' still valid?)." }

# latest.json goes last: the phone only sees the new build once its file is fully uploaded.
$tmp = New-TemporaryFile
[IO.File]::WriteAllText($tmp, ($info | ConvertTo-Json), (New-Object Text.UTF8Encoding $false))
aws s3 cp $tmp "s3://$Bucket/latest.json" --content-type application/json --only-show-errors
Remove-Item $tmp
if ($LASTEXITCODE -ne 0) { throw "Could not write latest.json." }

# Keep only the three newest builds.
$old = aws s3api list-objects-v2 --bucket $Bucket --prefix apk/ --query "sort_by(Contents,&LastModified)[:-3].Key" --output json | ConvertFrom-Json
foreach ($k in $old) { aws s3 rm "s3://$Bucket/$k" --only-show-errors }

Write-Host "Published version $VersionName (code $versionCode). Open Ultron on the phone to update."
