<#
Builds the Ultron app and publishes it to the AWS update server, so the phone can update itself from anywhere.
Run from the laptop:   .\scripts\publish-update.ps1 -Notes "What changed"
The phone then shows "Update available" the next time Ultron is opened (or via About > Check for updates).
Needs: Android SDK 35 + JDK (ANDROID_HOME / JAVA_HOME), and `aws login` done on this laptop.
#>
param(
    [string]$Notes = "",
    [string]$VersionName = "1.0.0",
    # Raise the build number when a phone already runs a higher one (a side-loaded build); Android refuses downgrades.
    [int]$MinVersionCode = 0,
    [switch]$AllowStale,
    [string]$Bucket = "ultron-app-updates-143920986615",
    # Android SDK (the one Android CLI manages) and the system JDK 17+; JAVA_HOME must be set.
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:USERPROFILE\Android\Sdk" }),
    [string]$Jdk = $env:JAVA_HOME
)
$ErrorActionPreference = "Stop"
$android = Join-Path (Split-Path $PSScriptRoot -Parent) "android"

# Same number goes into the app (as its version code) and into latest.json, so the phone can compare builds.
$versionCode = [int]([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() / 60)

# Never publish from a checkout that is missing what is already on GitHub's main: that silently ships an older app
# (an old theme, missing connectors). -AllowStale skips the check on purpose.
if (-not $AllowStale) {
    $repo = Split-Path $PSScriptRoot -Parent
    git -C $repo fetch origin main --quiet
    if ($LASTEXITCODE -ne 0) { throw "Could not fetch origin/main to check this checkout is up to date." }
    git -C $repo merge-base --is-ancestor origin/main HEAD
    if ($LASTEXITCODE -ne 0) { throw "This checkout does not contain the latest origin/main, so the build would be older than what is live. Rebase or merge origin/main first (or pass -AllowStale)." }
    if (git -C $repo status --porcelain --untracked-files=no) { throw "There are uncommitted changes; commit them so the published build matches a commit." }
}

# Fail before the slow build if publishing could not work anyway.
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { throw "The AWS CLI ('aws') was not found; install it and run 'aws login' first." }
# Two publishes in the same minute (or a clock that went backwards) must not reuse a number: always go above what is live.
$liveCode = $null
try { $liveCode = [int]((aws s3 cp "s3://$Bucket/latest.json" - --only-show-errors | ConvertFrom-Json).versionCode) } catch { }
if ($liveCode -and $versionCode -le $liveCode) { $versionCode = $liveCode + 1 }
if ($versionCode -lt $MinVersionCode) { $versionCode = $MinVersionCode }

Write-Host "Building version $VersionName (code $versionCode)..."
if (-not ((Test-Path "$Sdk\platforms") -and (Test-Path "$Jdk\bin\java.exe"))) {
    throw "Android SDK not found at $Sdk or JDK not found at $Jdk. Set ANDROID_HOME / JAVA_HOME (SDK: android sdk install platforms/android-36 build-tools/36.0.0)."
}
$env:JAVA_HOME = $Jdk; $env:ANDROID_HOME = $Sdk; $env:ANDROID_SDK_ROOT = $Sdk
# The phone's installed app is signed with the debug key from the earlier builds. A different key makes Android refuse
# the update ("conflicts with an existing package"), so builds sign with a copy of that same key.
$keyHome = Join-Path $env:USERPROFILE ".ultron-android"
if (Test-Path (Join-Path $keyHome "debug.keystore")) { $env:ANDROID_USER_HOME = $keyHome }
else { throw "Missing $keyHome\debug.keystore; a different key would break updates on phones that already have the app." }
Push-Location $android
try { .\gradlew.bat assembleOta "-PappVersionCode=$versionCode" "-PappVersionName=$VersionName" --console=plain -q; $built = $LASTEXITCODE } finally { Pop-Location }
$apk = Join-Path $android "app\build\outputs\apk\ota\app-ota.apk"
if ($built -ne 0) { throw "The build failed; nothing was published." }

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
