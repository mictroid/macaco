# insert-strings.template.ps1
#
# Inserts one or more <string> keys into every Macaco locale strings.xml, after $anchor.
#
# ENCODING RULES (see SKILL.md):
#   - Save THIS script as UTF-8 *WITH* BOM, or powershell.exe -File mangles non-ASCII literals.
#       From Git Bash:  printf '\xEF\xBB\xBF' > run.ps1 && cat this.ps1 >> run.ps1
#   - Output XML is written UTF-8 *WITHOUT* BOM (UTF8Encoding($false)) — Android requires no BOM.
#
# Run:  powershell.exe -ExecutionPolicy Bypass -File "<path>\run.ps1"

$base = "C:\Users\micke\android studio folder\wanderlog\app\src\main\res"

# Existing key to insert the new key(s) AFTER. Pick one in the same logical section.
$anchor = 'name="settings_backup_failed"'

# locale dir -> array of translated values, one per new key, SAME ORDER in every locale.
# The "values" (English) row is the source of truth. Fill in real translations for all 11.
$tr = [ordered]@{
  "values"        = @("ENGLISH_1", "ENGLISH_2")
  "values-fr"     = @("FR_1",      "FR_2")
  "values-es"     = @("ES_1",      "ES_2")
  "values-zh-rCN" = @("ZH_1",      "ZH_2")
  "values-nl"     = @("NL_1",      "NL_2")
  "values-de"     = @("DE_1",      "DE_2")
  "values-pt"     = @("PT_1",      "PT_2")
  "values-sv"     = @("SV_1",      "SV_2")
  "values-ja"     = @("JA_1",      "JA_2")
  "values-it"     = @("IT_1",      "IT_2")
  "values-pl"     = @("PL_1",      "PL_2")
}

# One <string> line per new key, in the SAME order as the $tr value arrays.
function Build-Block($v) {
  return @(
    "    <string name=`"NEW_KEY_1`">$($v[0])</string>",
    "    <string name=`"NEW_KEY_2`">$($v[1])</string>"
  )
}

foreach ($loc in $tr.Keys) {
  $file = Join-Path $base "$loc\strings.xml"
  if (-not (Test-Path $file)) { Write-Output "MISSING FILE: $file"; continue }
  $lines = [System.IO.File]::ReadAllLines($file)
  $block = Build-Block $tr[$loc]
  $out = New-Object System.Collections.Generic.List[string]
  $done = $false
  foreach ($line in $lines) {
    $out.Add($line)
    if (-not $done -and $line -match [regex]::Escape($anchor)) {
      foreach ($b in $block) { $out.Add($b) }
      $done = $true
    }
  }
  if (-not $done) { Write-Output "ANCHOR NOT FOUND: $file"; continue }
  $enc = New-Object System.Text.UTF8Encoding($false)   # $false = NO BOM (required for strings.xml)
  [System.IO.File]::WriteAllLines($file, $out, $enc)
  Write-Output "OK: $loc"
}
