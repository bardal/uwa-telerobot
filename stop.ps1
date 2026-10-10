# Stop everything start.ps1 launched.
$pids = Join-Path $PSScriptRoot "run\pids.txt"
if (-not (Test-Path $pids)) { "nothing to stop"; return }
Get-Content $pids | ForEach-Object {
  $id, $name = $_ -split " ", 2
  try { Stop-Process -Id ([int]$id) -Force -ErrorAction Stop; "stopped $name ($id)" }
  catch { "$name ($id) already gone" }
}
Remove-Item $pids
