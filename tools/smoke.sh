#!/usr/bin/env bash
# Opens the app on the CI emulator, proves a live heading reaches the display,
# sets a mark and checks the display follows. The screenshot it takes is the
# one the README shows.
#
# A file rather than inline workflow script: the emulator action feeds inline
# scripts to `sh -c` one line at a time, which breaks every multi-line `if`.
set -x

PKG=no.mwmai.compass.debug
ACTIVITY=no.mwmai.compass.MainActivity
OUT=smoke
mkdir -p "$OUT"

chmod +x ./gradlew
./gradlew installDebug --no-daemon || { echo "::error::install failed"; exit 1; }

adb logcat -c || true
# No -W: it waits for a launch to settle, which never happens if the app dies.
adb shell am start -n "$PKG/$ACTIVITY" || echo "am start returned $?"
sleep 15

dump() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
    adb pull /sdcard/ui.xml "$OUT/$1" >/dev/null 2>&1 || true
}
crashed() {
    adb logcat -d > "$OUT/logcat.txt" 2>&1 || true
    if grep -qE "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"; then
        echo "::error::App crashed"
        grep -B 2 -A 45 -m 1 -E "FATAL EXCEPTION|AndroidRuntime: .*(Exception|Error)" "$OUT/logcat.txt"
        return 0
    fi
    return 1
}

dump ui_launch.xml
crashed && exit 1

MISSING=0
for TEXT in "MWM COMPASS" "MARK" "TRUE NORTH" "MAGNETIC" "Compass dial"; do
    if ! grep -qF "$TEXT" "$OUT/ui_launch.xml"; then
        echo "::error::The compass screen is missing the text: $TEXT"
        MISSING=1
    fi
done
# "---°" means no sensor sample ever arrived; a number means the sensor
# pipeline reached the display.
if ! grep -qE 'text="[0-9]{1,3}°"' "$OUT/ui_launch.xml"; then
    echo "::error::No live heading on the display"
    MISSING=1
fi
if [ "$MISSING" -ne 0 ]; then
    head -c 6000 "$OUT/ui_launch.xml"
    exit 1
fi

tap() {
    python3 tools/ui_center.py "$OUT/$1" "$2" > "$OUT/tap.txt" || { echo "::error::nothing to tap for: $2"; exit 1; }
    # shellcheck disable=SC2046
    adb shell input tap $(cat "$OUT/tap.txt")
}
tap ui_launch.xml "desc=MARK"
sleep 4
dump ui_marked.xml
adb exec-out screencap -p > "$OUT/compass.png" 2>/dev/null || true
crashed && exit 1

if ! grep -qF 'CLEAR MARK' "$OUT/ui_marked.xml"; then
    echo "::error::Tapped MARK but the button never switched to CLEAR MARK"
    head -c 6000 "$OUT/ui_marked.xml"
    exit 1
fi
if ! grep -qE 'text="MARK [0-9]{1,3}°' "$OUT/ui_marked.xml"; then
    echo "::error::The mark bearing is not on the display"
    exit 1
fi
echo "Live heading on the display, mark set and shown."
