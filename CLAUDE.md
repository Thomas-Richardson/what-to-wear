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
- Each version is a GitHub release, which the app's **Check for updates** button reads: tag `v<versionName>`, with the app file built on his Mac attached as `what-to-wear-<versionName>.apk`. The app only downloads files attached to this repository's releases.
- Before publishing, check the app file holds no key: `unzip -p "What to Wear.apk" 'classes*.dex' | LC_ALL=C grep -a -c -E "sk-(proj-)?[A-Za-z0-9_-]{20,}"` must print 0.
- Also replace `What to Wear.apk` in his Google Drive in place, as a backup for fresh installs. Don't upload a second copy.
- Install builds onto the emulator with `adb -s emulator-5554 install -r`, not `build-and-install.sh`, unless Richie asks for his phone: the script installs on whatever is plugged in, which could be his phone.
- Emulator: `$ANDROID_HOME/emulator/emulator -avd Pixel8a_test`. Before starting or stopping it, check whether it's already running (`pgrep -fl qemu`). If Richie started it, install onto it with `adb install -r` and leave it running. It has no Wi-Fi location, so give it one with `adb emu geo fix -122.4194 37.7749`.

## Merging and releasing

Since 0.12 (9 November 2026), changes reach `main` through a pull request, so GitHub runs the tests before anything is merged. Earlier versions were pushed straight to `main`.

1. Commit on a branch, push it and open a pull request. Commit as Thomas Manandhar-Richardson <thomas.m.richardson@protonmail.com>, and push with `GH_TOKEN="$(gh auth token --user Thomas-Richardson)"`.
2. Wait for the tests: `gh pr checks <number> --watch`. Merge only if they pass.
3. Merge with `gh pr merge <number> --rebase --delete-branch` **as a command on its own**. Don't join it to the next step with `;`, and don't pipe its output into `tail` or `head`: the pipe hides a failure.
4. Before switching branch or deleting anything, check that it really merged: `gh pr view <number> --json state` must say `MERGED`. Then `git checkout main && git pull --ff-only`.
5. Publish the release (tag, app file name and key check are under "Building, testing and shipping" above).

Why step 3 matters: on 9 November 2026 GitHub answered the 0.12 merge with "502 Bad Gateway". The merge command's failure was hidden by `| tail`, and the next steps ran after a `;`, so the clean-up switched to `main` and deleted the local branch while the change was still unmerged. Nothing was lost, because the commit was still on GitHub. Use `&&` between steps that depend on each other, and never delete a branch until `main` on GitHub contains its commit.

If GitHub says **"Merge already in progress"** after an error like that, its side is stuck. Don't push again or open a new pull request. Retry every 20 seconds or so with `gh api -X PUT repos/Thomas-Richardson/what-to-wear/pulls/<number>/merge -f merge_method=rebase -f sha=<the branch's commit>`. On 9 November it cleared after about five minutes.

**Releases need Richie's say-so in the chat.** In Claude Code's auto mode, `gh release create` is blocked as making something public, even after he has asked for a merge. Ask him before trying, or give him the one-line command to paste into Terminal. Until a release is published, the app's **Check for updates** button still offers the old version.

## The AI fill-in

- Never put an API key in the repository or the app file: the app file is published. Since 0.11 Richie pastes his OpenAI key on the History tab and it's kept in the app's private storage on the phone (`Settings.aiKey`). The model name is in `app/build.gradle.kts`.
- To try the AI on the emulator, paste a key into the emulator's History tab yourself; never build one in.
- The AI only ticks clothes. It must never save a log by itself.

## Look

- The app uses Richie's own branding. The rules are in the `richie-branding` skill on his Mac (`~/.claude/skills/richie-branding/SKILL.md`), and the colours, font and shapes are in `ui/Theme.kt`. Use those, not Material's defaults or the wallpaper colours.
- Gold is for lines and borders only, never for text on ivory (it's too pale to read).

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
