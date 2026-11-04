#!/bin/zsh
# Builds the app and installs it on a phone plugged in by USB cable (with USB debugging turned on).
# Also leaves a copy called "What to Wear.apk" in this folder, which you can send to the phone instead.
# Reinstalling keeps your logs, as long as it's built on this Mac (the app is signed with this Mac's key).
set -e
cd "$(dirname "$0")"
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
# No keys are loaded here on purpose: the app file must hold no secrets, because each version is
# published as a GitHub release. The OpenAI key is pasted into the app on the phone instead.

./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk "What to Wear.apk"
echo "Built: $(pwd)/What to Wear.apk"

if "$ANDROID_HOME/platform-tools/adb" get-state >/dev/null 2>&1; then
    "$ANDROID_HOME/platform-tools/adb" install -r "What to Wear.apk"
    echo "Installed on the phone."
else
    echo "No phone plugged in (or USB debugging is off), so it isn't installed yet."
fi
