# Start the whole telerobot locally: MOM server, virtual lab (simulated robot
# and cameras), RobotProxy, web server and the control applet.
# Build first with: mvn package    Stop with: .\stop.ps1
param(
  [string]$JavaHome = $env:JAVA_HOME,
  [int]$WebPort = 18080,
  [switch]$NoApplet
)
$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
$jar = Join-Path $root "lab\target\telerobot.jar"
if (-not (Test-Path $jar)) { throw "lab\target\telerobot.jar not found - run 'mvn package' first" }
$java = if ($JavaHome) { Join-Path $JavaHome "bin\java.exe" } else { (Get-Command java).Source }
$bin = Split-Path $java
$run = Join-Path $root "run"
$pids = Join-Path $run "pids.txt"
if (Test-Path $pids) { throw "run\pids.txt exists - run .\stop.ps1 first" }
New-Item -ItemType Directory -Force (Join-Path $run "logs"), (Join-Path $run "web") | Out-Null
Copy-Item (Join-Path $root "web\*") (Join-Path $run "web") -Force
Copy-Item $jar (Join-Path $run "web\telerobot.jar") -Force

function Wait-Port([int]$port, [int]$seconds = 30) {
  for ($i = 0; $i -lt $seconds * 4; $i++) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) { return }
    Start-Sleep -Milliseconds 250
  }
  throw "nothing listening on port $port after $seconds s - see run\*.log"
}

function Start-Part($name, $exe, $argList) {
  $p = Start-Process -FilePath $exe -ArgumentList $argList -WorkingDirectory $run -WindowStyle Minimized `
         -RedirectStandardOutput (Join-Path $run "$name.log") -RedirectStandardError (Join-Path $run "$name.err") -PassThru
  Add-Content $pids "$($p.Id) $name"
  "started $name (pid $($p.Id))"
}

$cp = "`"$jar`""
Start-Part "mom" $java @("-cp", $cp, "au.edu.uwa.mech.mom.server.MOMServer", "../config/MOM.xml")
Wait-Port 4444
Start-Part "lab" $java @("-cp", $cp, "au.edu.uwa.mech.sim.VirtualLab", "../config/lab.xml")
Wait-Port 30562
Start-Part "proxy" $java @("-cp", $cp, "au.edu.uwa.mech.devices.robot.Server", "../config/RobotProxy.properties")
Start-Part "web" $java @("-cp", $cp, "au.edu.uwa.mech.applications.StaticWebServer", "$WebPort", "web")
Wait-Port $WebPort
if (-not $NoApplet) {
  Start-Sleep -Seconds 3   # let the proxy and cameras register with MOM
  # the Direct3D pipeline leaves AWT canvases (the camera images) unpainted on some Windows setups
  Start-Part "applet" (Join-Path $bin "appletviewer.exe") @("-J-Dsun.java2d.d3d=false", "http://localhost:$WebPort/index.html")
}
