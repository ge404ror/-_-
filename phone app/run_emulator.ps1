param(
    [string]$AvdName = "",
    [string]$SdkRoot = ""
)

$ErrorActionPreference = "Stop"

if (-not $SdkRoot) {
    $SdkRoot = $env:ANDROID_SDK_ROOT
    if (-not $SdkRoot) { $SdkRoot = "E:\AndroidBuildTools\android-sdk" }
}

$adb = Join-Path $SdkRoot "platform-tools\adb.exe"
$emulator = Join-Path $SdkRoot "emulator\emulator.exe"
$apk = Join-Path $PSScriptRoot "Last-Frequency-x86_64.apk"

foreach ($required in @($adb, $emulator, $apk)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required file not found: $required"
    }
}

if (-not $env:ANDROID_AVD_HOME) {
    $customAvdHome = Join-Path (Split-Path $SdkRoot -Parent) "avd"
    if (Test-Path -LiteralPath $customAvdHome -PathType Container) { $env:ANDROID_AVD_HOME = $customAvdHome }
}

if (-not $AvdName) {
    $avds = @(& $emulator -list-avds | Where-Object { $_.Trim() })
    if ($avds.Count -eq 0) { throw "No Android Virtual Devices were found." }
    if ($avds.Count -eq 1) {
        $AvdName = $avds[0].Trim()
    } else {
        Write-Host "Available virtual devices:"
        for ($i = 0; $i -lt $avds.Count; $i++) { Write-Host "[$($i + 1)] $($avds[$i])" }
        $choice = Read-Host "Choose a device number"
        $index = 0
        if (-not [int]::TryParse($choice, [ref]$index) -or $index -lt 1 -or $index -gt $avds.Count) {
            throw "Invalid device number."
        }
        $AvdName = $avds[$index - 1].Trim()
    }
}

$devices = @(& $adb devices | Select-String '^emulator-\d+\s+device$')
if ($devices.Count -eq 0) {
    Write-Host "Starting Android Emulator: $AvdName"
    Start-Process -FilePath $emulator -ArgumentList @("-avd", $AvdName)
}

$deadline = (Get-Date).AddMinutes(3)
$serial = $null
$booted = $false
do {
    Start-Sleep -Seconds 2
    $device = & $adb devices | Select-String '^emulator-\d+\s+device$' | Select-Object -First 1
    if ($device) {
        $serial = ($device.ToString() -split '\s+')[0]
        $booted = ((& $adb -s $serial shell getprop sys.boot_completed 2>$null) -join '').Trim() -eq '1'
    }
} while (-not $booted -and (Get-Date) -lt $deadline)

if (-not $booted) { throw "Emulator did not finish booting within 3 minutes." }

# Copy to an ASCII-only path for ADB compatibility on some Windows installations.
$installApk = Join-Path $SdkRoot "Last-Frequency-x86_64.apk"
Copy-Item -LiteralPath $apk -Destination $installApk -Force
Write-Host "Installing on $serial"
& $adb -s $serial install -r $installApk
if ($LASTEXITCODE -ne 0) { throw "ADB could not install the APK." }

& $adb -s $serial shell monkey -p com.lastfrequency.player 1 | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not launch LAST FREQUENCY." }
Write-Host "LAST FREQUENCY is running in Android Emulator."


