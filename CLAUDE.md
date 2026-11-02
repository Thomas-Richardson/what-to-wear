# Working on What to Wear

Read `README.md` for how the app works and `docs/why.md` for why it is built this way. When a change alters one of the reasons in `docs/why.md`, update that document in the same change.

## Rules that protect Richie's data

- **His note is the only store of logs.** Never copy rows from "How do I feel at different temperatures" into the code, tests, fixtures or anywhere else in this repository, which is public. Tests that need realistic rows use made-up ones (`NoteTableTest`). `RealNoteTest` reads his vault directly and skips itself when the vault isn't there.
- **Never save logs anywhere else when the note can't be read.** Show the problem and ask him to connect the note again.
- **Change the note one line at a time.** Adding a log inserts one line and deleting removes one. Everything else in the file must stay byte for byte the same. Test any change to `NoteTable.kt` against that.
- **Keep his four columns first, and never rename any column.** The app and his `note-temperature` skill both find the table by its Temperature heading and read columns by name.
- **Test on the emulator with a copy of the note.** Never use the note in his vault for testing.
- **Don't edit the note on the Mac while the phone may be changing it.** On 1 Nov 2026 a Mac edit and a phone edit landed together and Obsidian Sync kept both copies of the table. `NoteTable.damage` now makes the app stop rather than read or write a doubled table. If it happens again, compare the two copies, keep any rows that exist in only one, and put back a single table.

## Building, testing and shipping

- Java 17: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. Android SDK: `export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`.
- Tests: `./gradlew testDebugUnitTest`. Build: `./build-and-install.sh` or `./gradlew assembleDebug`.
- Raise `versionCode` and `versionName` in `app/build.gradle.kts` for every version that goes to his phone.
- Build only on Richie's Mac. Updates install over the existing app only if signed with `~/.android/debug.keystore` from that Mac. Building elsewhere would force an uninstall, which loses his own clothes and the connection to the note.
- New versions reach his phone as `What to Wear.apk` in his Google Drive. Replace the existing file in place. Don't upload a second copy.
- Emulator: `$ANDROID_HOME/emulator/emulator -avd Pixel8a_test`. Before starting or stopping it, check whether it's already running (`pgrep -fl qemu`). If Richie started it, install onto it with `adb install -r` and leave it running. It has no Wi-Fi location, so give it one with `adb emu geo fix -122.4194 37.7749`.

## The AI fill-in

- Never put an API key in the repository. `app/build.gradle.kts` reads `OPENAI_API_KEY_WHATTOWEAR` or `OPENAI_API_KEY_MISC` from the environment at build time, and `build-and-install.sh` loads them from `~/.config/llm/env`. The model name is set there too.
- Check a build has its key without showing it: `grep -c 'OPENAI_KEY = "sk-' app/build/generated/source/buildConfig/debug/com/richie/whattowear/BuildConfig.java`.
- The AI only ticks clothes. It must never save a log by itself.

## Tests

- Run all of them (`./gradlew testDebugUnitTest`) before shipping a version. GitHub also runs them on every push (`.github/workflows/tests.yml`).
- Tests never go online. For weather or AI answers, save a real reply in `app/src/test/resources` and check against it.
- When fixing a bug, first write a test that fails because of it, and check it passes only with the fix.
- Screen tests use Robolectric (`robolectric.properties` sets the imitation phone to Android 14, which the Robolectric version supports). Scroll to anything below the bottom of the screen before tapping it, as a finger would.

## The widget and the reminder

- The widget draws from a saved copy of today's suggestion (`WidgetData`), so it never waits for the internet. A job refreshes it every two hours and straight after a log is saved.
- After an app update, Android shows the widget's loading spinner until something redraws it. `AppUpdatedReceiver` does that. Keep it, or every update leaves a spinner on his home screen.
- To test the 5pm reminder on the emulator: WorkManager won't start a daily job before its time, even when the job is forced. Run `adb root`, switch off automatic time (`adb shell settings put global auto_time 0`), set the clock past 5pm (`adb shell date MMDDhhmmYYYY.ss`), force the app's jobs (`adb shell cmd jobscheduler run -f com.richie.whattowear <id>`, with the ids from `dumpsys jobscheduler`), then switch automatic time back on.

## How Richie wants changes explained

He reads in plain English and doesn't know much Git. Say what changed and why in everyday words, explain any technical term once, and show screenshots from the emulator for anything visible. Keep the app's wording British English, and use his own words for things he named, such as the 1 to 5 scale.
