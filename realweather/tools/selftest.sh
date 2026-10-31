#!/bin/bash
# Runs the mod for real: a dedicated server creates a world and runs the commands, then the
# client plays through every weather scene under a virtual display and takes screenshots.
set -u
export RW_SUITE="${RW_SUITE:-$(cat tools/selftest_suite 2>/dev/null || echo all)}"
echo "suite: $RW_SUITE"
chmod +x gradlew
mkdir -p run/server run/client/saves
echo "eula=true" > run/server/eula.txt
cat > run/server/server.properties <<'PROPS'
online-mode=false
level-seed=20261029
spawn-protection=0
view-distance=6
PROPS
cat > run/client/options.txt <<'OPTS'
version:3955
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
pauseOnLostFocus:false
renderDistance:8
simulationDistance:6
graphicsMode:1
particles:0
guiScale:2
narrator:0
realmsNotifications:false
OPTS

echo "::group::dedicated server"
timeout 900 ./gradlew runServer -Pselftest --no-daemon -q > /tmp/server.log 2>&1
echo "server exit $?"
grep -E "RW-SELFTEST|realweather|Exception|ERROR" /tmp/server.log | grep -v "^\s*at " | head -60
tail -15 /tmp/server.log
echo "::endgroup::"
if [ ! -d run/server/world ]; then echo "no world generated"; exit 1; fi
cp -r run/server/world run/client/saves/rwtest

echo "::group::client"
export LIBGL_ALWAYS_SOFTWARE=1
timeout 1500 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -Pselftest --no-daemon -q > /tmp/client.log 2>&1
echo "client exit $?"
echo "::endgroup::"
echo "=== SELFTEST LOG"
grep "RW-SELFTEST" /tmp/client.log | sed 's/^.*RW-SELFTEST[^:]*: //' | head -260
echo "=== VEHICLE EVENTS"
grep -E "removed \(|destroyed by" /tmp/client.log | head -20
echo "=== PROBLEMS"
grep -nE "Exception|Caused by|\[ERROR\]|ERROR\]|/ERROR|FATAL|Failed to|Couldn't|shader" /tmp/client.log | grep -v "^\s*at " | head -80
echo "=== OPENGL"
grep -iE "OpenGL|GL_|Backend library|LWJGL" /tmp/client.log | head -10
echo "=== SCREENSHOTS"
ls -la run/client/screenshots 2>/dev/null
pip install -q pillow >/dev/null 2>&1
python3 - <<'PY'
import base64, glob, io, os
from PIL import Image
for f in sorted(glob.glob('run/client/screenshots/*.png')):
    im = Image.open(f).convert('RGB')
    im.thumbnail((800, 450))
    buf = io.BytesIO()
    im.save(buf, 'JPEG', quality=72)
    print('IMG ' + os.path.basename(f) + ' ' + base64.b64encode(buf.getvalue()).decode())
PY
echo "=== END"
