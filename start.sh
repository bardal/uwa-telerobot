#!/usr/bin/env bash
# Start the whole telerobot locally: MOM server, virtual lab (simulated robot
# and cameras), RobotProxy, web server and the control applet.
# Build first with: mvn package    Stop with: ./stop.sh
# Set NO_APPLET=1 to skip launching appletviewer.
set -e
root=$(cd "$(dirname "$0")" && pwd)
jar="$root/target/telerobot.jar"
[ -f "$jar" ] || { echo "target/telerobot.jar not found - run 'mvn package' first"; exit 1; }
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"
appletviewer="${JAVA_HOME:+$JAVA_HOME/bin/}appletviewer"
web_port=${WEB_PORT:-18080}
run="$root/run"
[ -f "$run/pids.txt" ] && { echo "run/pids.txt exists - run ./stop.sh first"; exit 1; }
mkdir -p "$run/logs" "$run/web"
cp "$root"/web/* "$run/web/"
cp "$jar" "$run/web/telerobot.jar"
cd "$run"

wait_port() {
  i=0
  until (echo > "/dev/tcp/127.0.0.1/$1") 2>/dev/null || nc -z 127.0.0.1 "$1" 2>/dev/null; do
    i=$((i + 1)); [ $i -gt 120 ] && { echo "nothing listening on port $1 - see run/*.log"; exit 1; }
    sleep 0.25
  done
}

start_part() {
  name=$1; shift
  "$@" > "$name.log" 2> "$name.err" &
  echo "$! $name" >> pids.txt
  echo "started $name (pid $!)"
}

start_part mom "$java" -cp "$jar" au.edu.uwa.mech.mom.server.MOMServer ../config/MOM.xml
wait_port 4444
start_part lab "$java" -cp "$jar" au.edu.uwa.mech.sim.VirtualLab ../config/lab.xml
wait_port 30562
start_part proxy "$java" -cp "$jar" au.edu.uwa.mech.devices.robot.Server ../config/RobotProxy.properties
start_part web "$java" -cp "$jar" au.edu.uwa.mech.applications.StaticWebServer "$web_port" web
wait_port "$web_port"
if [ -z "$NO_APPLET" ]; then
  sleep 3
  start_part applet "$appletviewer" -J-Dsun.java2d.d3d=false "http://localhost:$web_port/index.html"
fi
