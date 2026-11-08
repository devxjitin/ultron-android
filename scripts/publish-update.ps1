<#
Build and publish the OTA APK, or run the two stages separately in CI.
Build produces ota-manifest.json next to app-ota.apk; Publish verifies its commit, hash, size and version.
Requires SDK 36, JDK 17, AWS CLI authentication and the existing OTA signing key.
#>
param(
    [ValidateSet("BuildAndPublish", "Build", "Publish")][string]$Mode = "BuildAndPublish",
    [string]$Notes = "",
    [string]$VersionName = "",
    [int]$MinVersionCode = 0,
    [switch]$AllowStale,
    [string]$Bucket = "ultron-app-updates-143920986615",
    [string]$Sdk = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:USERPROFILE\Android\Sdk" }),
    [string]$Jdk = $env:JAVA_HOME,
    [string]$OutputDirectory = ""
)
$ErrorActionPreference = "Stop"
$repo = Split-Path $PSScriptRoot -Parent
$android = Join-Path $repo "android"
$onWindows = $env:OS -eq "Windows_NT"
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $android "app/build/outputs/apk/ota" }
$apk = Join-Path $OutputDirectory "app-ota.apk"
$manifest = Join-Path $OutputDirectory "ota-manifest.json"

function Assert-FreshCheckout {
    if (-not $AllowStale) {
        git -C $repo fetch origin main --quiet
        if ($LASTEXITCODE -ne 0) { throw "Could not fetch origin/main." }
        git -C $repo merge-base --is-ancestor origin/main HEAD
        if ($LASTEXITCODE -ne 0) { throw "This checkout is behind origin/main; nothing will be published." }
        if (git -C $repo status --porcelain --untracked-files=no) { throw "Commit tracked changes before publishing." }
    }
}

function Get-LiveVersion {
    # Do not silently turn network/auth errors into permission to reuse a version number.
    $liveText = aws s3 cp "s3://$Bucket/latest.json" - --only-show-errors
    if ($LASTEXITCODE -ne 0) { throw "Could not read latest.json; refusing to guess the live version." }
    $live = $liveText | ConvertFrom-Json
    if (-not $live.versionCode -or [long]$live.versionCode -le 0) { throw "The live release manifest has an invalid versionCode." }
    return [long]$live.versionCode
}

# The version and its notes come from the changelog shipped in the app, so what the phone shows always matches.
$changelog = Join-Path $android "app/src/main/assets/changelog.md"
function Get-ChangelogEntry {
    $lines = Get-Content -LiteralPath $changelog -Encoding UTF8
    $version = $null; $section = ""; $points = @()
    foreach ($line in $lines) {
        if ($line -match '^##\s+v?(\d+\.\d+\.\d+)') {
            if ($version) { break }
            $version = $Matches[1]; continue
        }
        if (-not $version) { continue }
        if ($line -match '^###\s+(.+)$') { $section = $Matches[1].Trim() }
        elseif ($line -match '^-\s+(.+)$' -and $section) { $points += "${section}: $($Matches[1].Trim())" }
    }
    if (-not $version) { throw "changelog.md has no '## x.y.z' version heading." }
    return [pscustomobject]@{ Version = $version; Notes = ($points -join "`n") }
}
if ($Mode -ne "Publish") {
    $entry = Get-ChangelogEntry
    if (-not $VersionName) { $VersionName = $entry.Version }
    if (-not $Notes) { $Notes = $entry.Notes }
}

Assert-FreshCheckout
if (-not (Get-Command aws -ErrorAction SilentlyContinue)) { throw "AWS CLI is missing." }
$sourceCommit = (git -C $repo rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceCommit -notmatch '^[a-f0-9]{40}$') { throw "Could not determine the source commit." }

if ($Mode -ne "Publish") {
    $versionCode = [long][Math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() / 60)
    $versionCode = [Math]::Max([Math]::Max($versionCode, (Get-LiveVersion) + 1), $MinVersionCode)
    if ($versionCode -gt [int]::MaxValue) { throw "Version code exceeds Android's limit." }
    $java = if ($Jdk) { Join-Path $Jdk $(if ($onWindows) { "bin/java.exe" } else { "bin/java" }) } else { "" }
    if (-not ((Test-Path (Join-Path $Sdk "platforms/android-36")) -and $java -and (Test-Path $java))) {
        throw "SDK platform 36 or JDK not found. Set ANDROID_HOME and JAVA_HOME."
    }
    $env:JAVA_HOME = $Jdk
    $env:ANDROID_HOME = $Sdk
    $keyHome = Join-Path $HOME ".ultron-android"
    if (-not (Test-Path (Join-Path $keyHome "debug.keystore"))) { throw "The existing OTA signing key is missing." }
    $env:ANDROID_USER_HOME = $keyHome
    $gradlew = Join-Path $android $(if ($onWindows) { "gradlew.bat" } else { "gradlew" })
    Write-Host "Building version $VersionName ($versionCode), commit $sourceCommit..."
    Push-Location $android
    try {
        # lintVital repeats the lint the checks already ran and took ~2.5 min of the build.
        & $gradlew assembleOta -x lintVitalOta "-PappVersionCode=$versionCode" "-PappVersionName=$VersionName" --build-cache --profile --console=plain
        $built = $LASTEXITCODE
    } finally { Pop-Location }
    if ($built -ne 0) { throw "Build failed; nothing was published." }
    $builtApk = Join-Path $android "app/build/outputs/apk/ota/app-ota.apk"
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    if ([IO.Path]::GetFullPath($builtApk) -ne [IO.Path]::GetFullPath($apk)) { Copy-Item -LiteralPath $builtApk -Destination $apk }
    $info = [ordered]@{
        versionCode = [long]$versionCode
        versionName = $VersionName
        notes = $Notes
        sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        sizeBytes = (Get-Item -LiteralPath $apk).Length
        apkKey = "apk/ultron-$versionCode.apk"
        sourceCommit = $sourceCommit
        publishedAt = [DateTimeOffset]::UtcNow.ToString("o")
    }
    [IO.File]::WriteAllText($manifest, ($info | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    if ($Mode -eq "Build") { Write-Host "Built and recorded $manifest; no upload performed."; return }
}

if (-not ((Test-Path -LiteralPath $manifest) -and (Test-Path -LiteralPath $apk))) { throw "Built APK or ota-manifest.json is missing." }
$info = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
if ($info.sourceCommit -ne $sourceCommit) { throw "The APK was built from a different commit." }
if ($info.sha256 -cne (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() -or
    [long]$info.sizeBytes -ne (Get-Item -LiteralPath $apk).Length) { throw "APK checksum or size does not match its build manifest." }
if ([long]$info.versionCode -le 0 -or [long]$info.versionCode -gt [int]::MaxValue -or
    $info.apkKey -cne "apk/ultron-$($info.versionCode).apk") { throw "Invalid release version or APK key." }
if ([long]$info.versionCode -le (Get-LiveVersion)) { throw "A release with this version or a newer one is already live." }
# Recheck after the long build and immediately before changing the live release pointer.
Assert-FreshCheckout
Write-Host "Uploading verified APK $($info.apkKey)..."
aws s3 cp $apk "s3://$Bucket/$($info.apkKey)" --only-show-errors
if ($LASTEXITCODE -ne 0) { throw "APK upload failed; latest.json was not changed." }
aws s3 cp $manifest "s3://$Bucket/latest.json" --content-type application/json --only-show-errors
if ($LASTEXITCODE -ne 0) { throw "Could not publish latest.json." }

# Preserve the established retention policy; a cleanup failure must not disguise a successful publication.
$oldText = aws s3api list-objects-v2 --bucket $Bucket --prefix apk/ --query "sort_by(Contents,&LastModified)[:-3].Key" --output json
if ($LASTEXITCODE -eq 0) {
    foreach ($key in ($oldText | ConvertFrom-Json)) {
        aws s3 rm "s3://$Bucket/$key" --only-show-errors
        if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove an old APK." }
    }
} else { Write-Warning "Could not list old APKs; release is published, cleanup skipped." }
Write-Host "Published version $($info.versionName) ($($info.versionCode)), commit $sourceCommit."
