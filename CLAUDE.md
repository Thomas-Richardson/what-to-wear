# Working on What to Wear

Read `README.md` for how the app works and `docs/why.md` for why it is built this way. When a change alters one of the reasons in `docs/why.md`, update that document in the same change.

## Rules that protect Richie's data

- **His note is the only store of logs.** Never copy rows from "How do I feel at different temperatures" into the code, tests, fixtures or anywhere else in this repository, which is public. Tests that need realistic rows use made-up ones (`NoteTableTest`). `RealNoteTest` reads his vault directly and skips itself when the vault isn't there.
- **Never save logs anywhere else when the note can't be read.** Show the problem and ask him to connect the note again.
- **Change the note one line at a time.** Adding a log inserts one line and deleting removes one. Everything else in the file must stay byte for byte the same. Test any change to `NoteTable.kt` against that.
- **Keep his four columns first, and never rename any column.** The app and his `note-temperature` skill both find the table by its Temperature heading and read columns by name.
- **Test on the emulator with a copy of the note.** Never use the note in his vault for testing.

## Building, testing and shipping

- Java 17: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`. Android SDK: `export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`.
- Tests: `./gradlew testDebugUnitTest`. Build: `./build-and-install.sh` or `./gradlew assembleDebug`.
- Raise `versionCode` and `versionName` in `app/build.gradle.kts` for every version that goes to his phone.
- Build only on Richie's Mac. Updates install over the existing app only if signed with `~/.android/debug.keystore` from that Mac. Building elsewhere would force an uninstall, which loses his own clothes and the connection to the note.
- New versions reach his phone as `What to Wear.apk` in his Google Drive. Replace the existing file in place. Don't upload a second copy.
- Emulator: `$ANDROID_HOME/emulator/emulator -avd Pixel8a_test`. Before starting or stopping it, check whether it's already running (`pgrep -fl qemu`). If Richie started it, install onto it with `adb install -r` and leave it running. It has no Wi-Fi location, so give it one with `adb emu geo fix -122.4194 37.7749`.

## How Richie wants changes explained

He reads in plain English and doesn't know much Git. Say what changed and why in everyday words, explain any technical term once, and show screenshots from the emulator for anything visible. Keep the app's wording British English, and use his own words for things he named, such as the 1 to 5 scale.
