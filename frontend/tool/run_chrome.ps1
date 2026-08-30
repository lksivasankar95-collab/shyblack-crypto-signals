param(
  [int] $WebPort = 5555
)

$ErrorActionPreference = "Stop"
$toolDir = $PSScriptRoot
$frontendDir = Split-Path $toolDir -Parent
$cmdFile = Join-Path $toolDir ".reload_cmd"
$flutterRoot = "C:\flutter"
$dart = Join-Path $flutterRoot "bin\cache\dart-sdk\bin\dart.exe"
$snapshot = Join-Path $flutterRoot "bin\cache\flutter_tools.snapshot"
$packages = Join-Path $flutterRoot "packages\flutter_tools\.dart_tool\package_config.json"

Set-Content -Path $cmdFile -Value "" -Encoding ascii

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = $dart
$psi.Arguments = "--packages=`"$packages`" `"$snapshot`" run -d web-server --web-hostname localhost --web-port $WebPort --hot"
$psi.WorkingDirectory = $frontendDir
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $false
$psi.RedirectStandardError = $false
$psi.CreateNoWindow = $false
$psi.EnvironmentVariables["FLUTTER_ROOT"] = $flutterRoot

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $psi
[void]$proc.Start()

Write-Host "Managed Flutter pid=$($proc.Id) web-server=http://localhost:$WebPort"
Write-Host "Keep ONE browser tab on that URL. Hot reload: powershell -File tool\reload.ps1 -Key r"

$lastWrite = (Get-Item $cmdFile).LastWriteTimeUtc
try {
  while (-not $proc.HasExited) {
    Start-Sleep -Milliseconds 350
    if (-not (Test-Path $cmdFile)) {
      continue
    }
    $stamp = (Get-Item $cmdFile).LastWriteTimeUtc
    if ($stamp -le $lastWrite) {
      continue
    }
    $lastWrite = $stamp
    $raw = Get-Content -Path $cmdFile -Raw -ErrorAction SilentlyContinue
    if ($null -eq $raw) { $raw = "" }
    $cmd = $raw.Trim()
    if ($cmd -eq "r" -or $cmd -eq "R") {
      Write-Host "Sending '$cmd' to flutter run..."
      $proc.StandardInput.WriteLine($cmd)
      $proc.StandardInput.Flush()
    }
  }
  Write-Host "Flutter exited with code $($proc.ExitCode)"
  exit $proc.ExitCode
} finally {
  if (-not $proc.HasExited) {
    $proc.Kill()
  }
}
