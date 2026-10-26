$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $root "ffmpeg\bin"
$ffmpeg = Join-Path $target "ffmpeg.exe"
$ffprobe = Join-Path $target "ffprobe.exe"
$ready = Join-Path (Join-Path $root "ffmpeg") ".ready"
if ((Test-Path $ffmpeg) -and (Test-Path $ffprobe) -and (Test-Path $ready)) { exit 0 }
New-Item -ItemType Directory -Force -Path $target | Out-Null
$tmp = Join-Path $env:TEMP ("soundlarp-ffmpeg-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null
try {
    $zip = Join-Path $tmp "ffmpeg.zip"
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if (-not $curl) { throw "curl.exe is required to download FFmpeg." }
    & $curl.Source --fail --location --retry 3 --connect-timeout 30 --output $zip "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
    if ($LASTEXITCODE -ne 0) { throw "FFmpeg download failed with curl exit code $LASTEXITCODE." }
    if ((Get-Item -LiteralPath $zip).Length -lt 10MB) { throw "Downloaded FFmpeg archive is unexpectedly small." }
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $tmp "unpacked") -Force
    $ffmpegSource = Get-ChildItem (Join-Path $tmp "unpacked") -Filter "ffmpeg.exe" -Recurse | Select-Object -First 1
    $ffprobeSource = Get-ChildItem (Join-Path $tmp "unpacked") -Filter "ffprobe.exe" -Recurse | Select-Object -First 1
    if (-not $ffmpegSource -or -not $ffprobeSource) { throw "FFmpeg archive did not contain ffmpeg.exe and ffprobe.exe" }
    Copy-Item $ffmpegSource.FullName $ffmpeg -Force
    Copy-Item $ffprobeSource.FullName $ffprobe -Force
    Get-ChildItem $ffmpegSource.DirectoryName -Filter "*.dll" | Copy-Item -Destination $target -Force
    Set-Content -LiteralPath $ready -Value "bundled" -NoNewline
} finally {
    Remove-Item $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
Write-Host "FFmpeg and ffprobe are ready."
