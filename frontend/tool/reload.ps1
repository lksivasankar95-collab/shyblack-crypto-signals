param(
  [ValidateSet("r", "R")]
  [string] $Key = "r"
)

$cmdFile = Join-Path $PSScriptRoot ".reload_cmd"
Set-Content -Path $cmdFile -Value $Key -Encoding ascii
if ($Key -ceq "R") {
  Write-Host "Queued Flutter hot restart"
} else {
  Write-Host "Queued Flutter hot reload"
}
