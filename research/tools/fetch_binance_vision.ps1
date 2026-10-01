param(
  [string]$DatasetVersion = "NFM_RESEARCH_2023_09_2026_09_V1",
  [string]$OutRoot = "research-import",
  [string[]]$Symbols = @("BTCUSDT", "ETHUSDT"),
  [string[]]$Timeframes = @("1m", "5m", "15m", "1h", "4h"),
  [string]$StartMonth = "2023-09",
  [string]$EndMonthExclusive = "2026-09",
  [string]$TailDailyFrom = "2026-09-01",
  [string]$TailDailyTo = "2026-09-30",
  [switch]$SkipFunding,
  [switch]$SkipMetrics,
  [int]$MaxFiles = 0,
  [int]$ParallelMax = 16
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$base = "https://data.binance.vision/data/futures/um"
$root = (Resolve-Path -LiteralPath $OutRoot).Path
$dsRoot = Join-Path $root $DatasetVersion
$rawRoot = Join-Path $dsRoot "_raw"

function MonthRange([string]$start, [string]$endExclusive) {
  $s = [datetime]::ParseExact($start + "-01", "yyyy-MM-dd", $null)
  $e = [datetime]::ParseExact($endExclusive + "-01", "yyyy-MM-dd", $null)
  $out = @()
  while ($s -lt $e) { $out += $s.ToString("yyyy-MM"); $s = $s.AddMonths(1) }
  return $out
}

function DateRange([string]$from, [string]$to) {
  $s = [datetime]::ParseExact($from, "yyyy-MM-dd", $null)
  $e = [datetime]::ParseExact($to, "yyyy-MM-dd", $null)
  $out = @()
  while ($s -le $e) { $out += $s.ToString("yyyy-MM-dd"); $s = $s.AddDays(1) }
  return $out
}

$items = New-Object System.Collections.Generic.List[object]

foreach ($sym in $Symbols) {
  foreach ($tf in $Timeframes) {
    foreach ($m in (MonthRange $StartMonth $EndMonthExclusive)) {
      $url = "$base/monthly/klines/$sym/$tf/$sym-$tf-$m.zip"
      $rel = "$DatasetVersion/CANDLE/$sym/$tf/$sym-$tf-$m.csv"
      $items.Add([pscustomobject]@{ Url=$url; Kind="CANDLE"; Symbol=$sym; Timeframe=$tf; Rel=$rel }) | Out-Null
    }
    foreach ($d in (DateRange $TailDailyFrom $TailDailyTo)) {
      $url = "$base/daily/klines/$sym/$tf/$sym-$tf-$d.zip"
      $rel = "$DatasetVersion/CANDLE/$sym/$tf/$sym-$tf-$d.csv"
      $items.Add([pscustomobject]@{ Url=$url; Kind="CANDLE"; Symbol=$sym; Timeframe=$tf; Rel=$rel }) | Out-Null
    }
  }
  if (-not $SkipFunding) {
    foreach ($m in (MonthRange $StartMonth $EndMonthExclusive)) {
      $url = "$base/monthly/fundingRate/$sym/$sym-fundingRate-$m.zip"
      $rel = "$DatasetVersion/FUNDING_RATE/$sym/$sym-fundingRate-$m.csv"
      $items.Add([pscustomobject]@{ Url=$url; Kind="FUNDING_RATE"; Symbol=$sym; Timeframe=$null; Rel=$rel }) | Out-Null
    }
    foreach ($d in (DateRange $TailDailyFrom $TailDailyTo)) {
      $url = "$base/daily/fundingRate/$sym/$sym-fundingRate-$d.zip"
      $rel = "$DatasetVersion/FUNDING_RATE/$sym/$sym-fundingRate-$d.csv"
      $items.Add([pscustomobject]@{ Url=$url; Kind="FUNDING_RATE"; Symbol=$sym; Timeframe=$null; Rel=$rel }) | Out-Null
    }
  }
  if (-not $SkipMetrics) {
    foreach ($d in (DateRange $TailDailyFrom $TailDailyTo)) {
      # metrics exist only daily; real range is start of window .. tail
    }
    $metricsFrom = ([datetime]::ParseExact($StartMonth + "-01", "yyyy-MM-dd", $null))
    $metricsTo = [datetime]::ParseExact($TailDailyTo, "yyyy-MM-dd", $null)
    $cur = $metricsFrom
    while ($cur -le $metricsTo) {
      $d = $cur.ToString("yyyy-MM-dd")
      $url = "$base/daily/metrics/$sym/$sym-metrics-$d.zip"
      $rel = "$DatasetVersion/OPEN_INTEREST/$sym/$sym-metrics-$d.csv"
      $items.Add([pscustomobject]@{ Url=$url; Kind="OPEN_INTEREST"; Symbol=$sym; Timeframe=$null; Rel=$rel }) | Out-Null
      $cur = $cur.AddDays(1)
    }
  }
}

if ($MaxFiles -gt 0) { $items = $items | Select-Object -First $MaxFiles }
Write-Output ("Planned files: " + $items.Count)

# Pre-create directories
foreach ($it in $items) {
  $outAbs = Join-Path $root $it.Rel
  $dir = Split-Path $outAbs -Parent
  if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
  $rawAbs = (Join-Path $rawRoot ($it.Rel -replace [regex]::Escape($DatasetVersion + "/"), "")) + ".zip"
  $dir2 = Split-Path $rawAbs -Parent
  if (-not (Test-Path -LiteralPath $dir2)) { New-Item -ItemType Directory -Force -Path $dir2 | Out-Null }
}

# Build curl config (zip + checksum), skipping already-extracted outputs
$cfg = Join-Path $dsRoot "download.cfg"
$lines = New-Object System.Collections.Generic.List[string]
$pending = New-Object System.Collections.Generic.List[object]
foreach ($it in $items) {
  $outAbs = Join-Path $root $it.Rel
  $rawAbs = (Join-Path $rawRoot ($it.Rel -replace [regex]::Escape($DatasetVersion + "/"), "")) + ".zip"
  $it | Add-Member -NotePropertyName OutAbs -NotePropertyValue $outAbs -Force
  $it | Add-Member -NotePropertyName RawAbs -NotePropertyValue $rawAbs -Force
  if (Test-Path -LiteralPath $outAbs) { continue }
  $pending.Add($it) | Out-Null
  $lines.Add(('url = "{0}"' -f $it.Url))
  $lines.Add(('output = "{0}"' -f ($rawAbs -replace '\\', '/')))
  $lines.Add(('url = "{0}.CHECKSUM"' -f $it.Url))
  $lines.Add(('output = "{0}.CHECKSUM"' -f ($rawAbs -replace '\\', '/')))
}
Set-Content -LiteralPath $cfg -Value $lines -Encoding ascii
Write-Output ("Pending downloads: " + $pending.Count)

if ($pending.Count -gt 0) {
  & curl.exe -sSL --parallel --parallel-max $ParallelMax --config $cfg
}

# Verify + extract
$manifest = New-Object System.Collections.Generic.List[object]
$fail = 0; $ok = 0; $extracted = 0
foreach ($it in $items) {
  $outAbs = Join-Path $root $it.Rel
  $rawAbs = (Join-Path $rawRoot ($it.Rel -replace [regex]::Escape($DatasetVersion + "/"), "")) + ".zip"
  if (-not (Test-Path -LiteralPath $rawAbs)) { $fail++; Write-Output ("MISSING " + $it.Url); continue }
  $chk = $rawAbs + ".CHECKSUM"
  $checksum = $null
  if (Test-Path -LiteralPath $chk) {
    $txt = (Get-Content -LiteralPath $chk -Raw).Trim()
    $expected = ($txt -split '\s+')[0].ToLower()
    $actual = (Get-FileHash -LiteralPath $rawAbs -Algorithm SHA256).Hash.ToLower()
    if ($expected -ne $actual) { $fail++; Write-Output ("CHECKSUM-MISMATCH " + $it.Url); continue }
    $checksum = $actual
  }
  if (-not (Test-Path -LiteralPath $outAbs)) {
    try {
      $zip = [System.IO.Compression.ZipFile]::OpenRead($rawAbs)
      try {
        $entry = $zip.Entries | Where-Object { $_.Name.ToLower().EndsWith(".csv") } | Select-Object -First 1
        if ($null -eq $entry) { $fail++; Write-Output ("NO-CSV " + $it.Url); continue }
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $outAbs, $true)
        $extracted++
      } finally { $zip.Dispose() }
    } catch { $fail++; Write-Output ("EXTRACT-FAIL " + $it.Url + " :: " + $_.Exception.Message); continue }
  }
  $ok++
  $manifest.Add([pscustomobject]@{ kind=$it.Kind; datasetVersion=$DatasetVersion; symbol=$it.Symbol; timeframe=$it.Timeframe; file=$it.Rel; checksum=$checksum }) | Out-Null
}

$manifestPath = Join-Path $dsRoot "manifest.json"
$json = $manifest | ConvertTo-Json -Depth 4
Set-Content -LiteralPath $manifestPath -Value $json -Encoding utf8
Write-Output ("OK=$ok extracted=$extracted failed=$fail manifest=$manifestPath")
