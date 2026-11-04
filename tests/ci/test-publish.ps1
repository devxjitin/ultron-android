# No AWS calls or Android builds: exercise the real Publish path with in-memory command fakes.
$ErrorActionPreference = "Stop"
$scriptPath = Join-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) "scripts/publish-update.ps1"
$parseErrors = $null
$tokens = $null
[Management.Automation.Language.Parser]::ParseFile($scriptPath, [ref]$tokens, [ref]$parseErrors) | Out-Null
if ($parseErrors) { throw ($parseErrors | Out-String) }
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("ultron-publish-test-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$global:otaTestState = @{ Commit = "a" * 40; Uploads = [Collections.Generic.List[string]]::new(); Live = 100; ReadFailed = $false; ApkUploadFailed = $false }

function git {
    $global:LASTEXITCODE = 0
    if ($args -contains "rev-parse") { return $global:otaTestState.Commit }
}
function aws {
    $global:LASTEXITCODE = 0
    if ($args[0] -eq "s3" -and $args[1] -eq "cp") {
        if ($args[3] -eq "-") {
            if ($global:otaTestState.ReadFailed) { $global:LASTEXITCODE = 1; return }
            return (@{versionCode=$global:otaTestState.Live} | ConvertTo-Json -Compress)
        }
        $global:otaTestState.Uploads.Add([string]$args[3])
        if ($global:otaTestState.ApkUploadFailed -and ([string]$args[3]).EndsWith(".apk")) { $global:LASTEXITCODE = 1 }
    } elseif ($args[0] -eq "s3api") { return "[]" }
}

function Reset-Artifact {
    $global:otaTestState.Uploads.Clear()
    $global:otaTestState.Live = 100
    $global:otaTestState.ReadFailed = $false
    $global:otaTestState.ApkUploadFailed = $false
    $apk = Join-Path $testRoot "app-ota.apk"
    [IO.File]::WriteAllText($apk, "fake APK for isolated publication tests")
    $info = @{
        versionCode=101; versionName="1.0.0"; apkKey="apk/ultron-101.apk"; sourceCommit=$global:otaTestState.Commit
        sha256=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        sizeBytes=(Get-Item -LiteralPath $apk).Length
    }
    $info | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $testRoot "ota-manifest.json")
}
function Expect-Failure([string]$message) {
    $caught = $false
    try { & $scriptPath -Mode Publish -OutputDirectory $testRoot } catch {
        $caught = $true
        if ($_.Exception.Message -notlike "*$message*") { throw }
    }
    if (-not $caught) { throw "Expected failure containing: $message" }
}

try {
    Reset-Artifact
    & $scriptPath -Mode Publish -OutputDirectory $testRoot
    if ($global:otaTestState.Uploads.Count -ne 2 -or -not $global:otaTestState.Uploads[0].EndsWith(".apk") -or -not $global:otaTestState.Uploads[1].EndsWith("latest.json")) {
        throw "Release pointer must be uploaded only after its APK."
    }
    Reset-Artifact
    Add-Content -LiteralPath (Join-Path $testRoot "app-ota.apk") -Value "tampered"
    Expect-Failure "checksum"
    if ($global:otaTestState.Uploads.Count) { throw "Tampered APK must never upload." }
    Reset-Artifact
    $global:otaTestState.Live = 101
    Expect-Failure "already live"
    if ($global:otaTestState.Uploads.Count) { throw "Old version must never upload." }
    Reset-Artifact
    $global:otaTestState.ReadFailed = $true
    Expect-Failure "Could not read latest.json"
    if ($global:otaTestState.Uploads.Count) { throw "Failed live-version read must not upload." }
    Reset-Artifact
    $global:otaTestState.ApkUploadFailed = $true
    Expect-Failure "APK upload failed"
    if ($global:otaTestState.Uploads.Count -ne 1) { throw "Failed APK upload must not update latest.json." }
    Reset-Artifact
    $manifest = Join-Path $testRoot "ota-manifest.json"
    $info = Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
    $info.sourceCommit = "b" * 40
    $info | ConvertTo-Json | Set-Content -LiteralPath $manifest
    Expect-Failure "different commit"
    if ($global:otaTestState.Uploads.Count) { throw "Wrong-commit APK must never upload." }
    Write-Host "6 isolated OTA publication regression tests passed."
} finally {
    # The target is a verified, uniquely named child of the OS temporary directory.
    $resolved = [IO.Path]::GetFullPath($testRoot)
    $parent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ([IO.Path]::GetDirectoryName($resolved) -ne $parent -or -not ([IO.Path]::GetFileName($resolved)).StartsWith("ultron-publish-test-")) {
        throw "Refusing cleanup outside the test directory."
    }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
