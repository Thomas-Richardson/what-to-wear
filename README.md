# What to Wear

What to Wear is an Android app that tells you what to wear today from the weather, and learns from what you actually wore and how it felt. Richie built it for his own Pixel 8a, with Claude Code writing the code, as practice at building an app with AI. It keeps its logs in a note in his Obsidian vault, so he can read them on his Mac and point Claude at them.

The reasons behind each design choice are in [docs/why.md](docs/why.md).

## What the app does

The **Today** tab finds where you are (through Google's location service, which uses Wi-Fi, phone masts and GPS, or Android's own sources on phones without it), gets the hour-by-hour forecast and suggests an outfit for the rest of the day (until 10pm) or the next 3 hours. It dresses you for the "feels like" temperature, which allows for wind and humidity, and leans towards the coldest part of the time you're out. It adds a rain jacket or umbrella when rain is likely, sunglasses when it's sunny, and gloves and a scarf when it's cold. Under the suggestion it says why, for example "Last time it felt like 11°C (Sat 31 Oct in San Francisco) you wore this and it felt just right." If it can't find you, it says which step failed (no permission, location switched off, or no reading) and offers the fix, or you can pick a place.

The **Log** tab records what you wore and how it felt. "When" defaults to **Now**, which uses the weather for the last hour and this one. You can also pick today, yesterday or any day up to three months back, and a part of the day (morning, afternoon, evening or all day). You tick your clothes, which are pre-filled if you tapped "Log what I wore" on the Today tab, and rate how it felt on a 1 to 5 scale: 1 way too cold, 2 too cold, 3 just right, 4 too warm, 5 too hot. Each group of clothes has an **Add your own** button for anything that isn't listed. The app looks up the weather for that time and place itself.

The **home-screen widget** shows today's suggestion, or what you logged if you've already logged today, with five buttons for the 1 to 5 scale. Tapping a number opens a small window over the home screen with that score picked and the clothes already ticked. Tap **Save** and the log goes into your note without the app opening. Tapping the suggestion opens the app. To add the widget, hold your finger on an empty part of the home screen, tap **Widgets**, find What to Wear and tap **Add**.

The **reminder** is a notification at 5pm on days you haven't logged yet: "How does your outfit feel?" Tapping it opens the same window. Android asks once for permission to show notifications, and the reminder can be switched off on the History tab.

The **History** tab lists every log, newest first, then the rows from before the app existed. It shows what the app has learnt (the outfit it would pick on a dry day at 5°C, 12°C and 20°C), your own clothes, and which note the logs live in.

## Where the data lives

The logs live in one place only: the note "How do I feel at different temperatures" in Richie's Main Obsidian vault. Obsidian Sync keeps the note on both his phone and his Mac. The app reads the whole table, adds one line when you save a log and removes one line when you delete one. Everything else in the note stays exactly as it was. If the note can't be read, for example because it was renamed or moved, the app says so and refuses to save anywhere else until you connect it again. If the table looks damaged (two heading rows, or the same column twice), the app says so and won't change the note until it's fixed. That happened once, on 1 November 2026, when the phone and the Mac changed the note at nearly the same moment and Obsidian Sync kept both versions of the table.

Richie's own four columns come first, as they always have. The app's six columns follow them.

| Column | Who fills it | What it holds |
| --- | --- | --- |
| Temperature | Richie or the app | The real air temperature, in whole degrees |
| wind? | Richie or the app | Y or N |
| What I wore | Richie or the app | His own words, or the app's names for the clothes |
| How I felt | Richie or the app | His own words, or the app's label plus any note |
| Score | The app (or the note-temperature skill) | 1 to 5, as above |
| Counted as | The app (or the skill) | The app's names for the clothes, when "What I wore" uses other words. Clothes in "(+ …)" weren't written down and are assumed. |
| Feels like °C | The app | The feels-like temperature the app dresses for. If blank, the app works it out from Temperature and wind. |
| Date | The app (or the skill) | YYYY-MM-DD, then a time (14:35) or a part of the day |
| Place | The app | Where the log was made |
| Weather | The app | Words such as "Clear", "Rain" or "Partly cloudy, rain likely" |

A row teaches the app only if it has a Score and clothes the app recognises. The History tab says how many rows it can't use yet. The rows written before the app existed have no date, so each counts as half a log and never fades. Newer logs count for more the more recent they are.

Until the note is connected, the app keeps logs in its own private file. Connecting the note moves those logs into it and retires the file, so only one copy is ever in use. Your own clothes are kept in a small private file on the phone, not in the note.

## How it learns

The learning is deliberately simple, so every suggestion can be explained. The code is in `app/src/main/java/com/richie/whattowear/model/Recommender.kt`.

1. **Reusing a day that felt right.** If a dated log within 2°C of today's feels-like temperature (and the same rain or no rain) felt just right, the app suggests that outfit again and names the day.
2. **How warm you like to be.** Every item has a rough warmth score: a T-shirt is 1, a gilet 1.5, a blazer 2 and a warm coat 4. The app fits a straight line of "warmth you were comfortable in" against feels-like temperature. A score of 3 says that warmth suited that temperature. A 2 says you wanted a bit more (about 1 point) and a 1 a lot more (about 2.5). A 4 and a 5 work the same way in the other direction. The line starts from a generic guess that counts as three made-up logs, so a handful of real logs only nudge it.
3. **When you'll wear things.** Shorts are only suggested from a feels-like 19°C, a warm coat at 10°C and below, a puffer at 7°C and below, and hats, scarves and gloves at 6°C and below, until a "just right" log shows otherwise. Hats, scarves and gloves go on whenever it's cold enough for them, even if the outfit is warm enough without them.
4. **What you wear.** Items that appear in your logs are preferred, and items that never appear are almost never suggested. Rain gear costs extra on dry days unless you often wear it anyway.

For each suggestion the app tries every sensible combination (one top, an optional layer, an optional jacket, bottoms, optional leggings, the cold-weather extras and an umbrella if needed) and picks the one closest to the warmth you want.

Richie's note records the real temperature, but the app works in feels-like. `analysis/calibrate_note_temperatures.py` measures the gap from a year of Manchester weather: feels-like is about 2°C below the real temperature on a calm 10°C day and about 4°C below on a windy one. The graphs it draws are in `analysis/`.

## Installing and updating on the phone

The app isn't on the Play Store. It is installed from a file called `What to Wear.apk`.

1. On the phone, open the file (Richie keeps it in his Google Drive) and tap **Install**, or **Update** if an older version is installed.
2. The first time, Android blocks installs from Drive. Tap **Settings**, turn on **Allow from this source**, go back and tap **Install**.
3. If Google Play Protect warns about an unknown app, tap **More details**, then **Install anyway**.
4. Open the app, allow location **while using the app**, and tap **Connect my note**. Pick "How do I feel at different temperatures" from the vault folder.

Updating keeps your logs and your own clothes. Updates only install over the existing app if they're built on the same computer as the first install, because Android checks that each update is signed with the same key. The key is `~/.android/debug.keystore` on Richie's Mac. Uninstalling the app doesn't touch the note, but it deletes your own clothes and the app's connection to the note.

## Building and testing

The app is written in Kotlin with Jetpack Compose (Android's current way of building screens). It needs Java 17 and the Android SDK. On Richie's Mac these came from Homebrew: `brew install openjdk@17` and `brew install --cask android-commandlinetools`.

```bash
cd ~/Documents/"What to wear app" && ./build-and-install.sh
```

That builds `What to Wear.apk` in the project folder, and installs it if a phone is plugged in with USB debugging on. To run the tests:

```bash
cd ~/Documents/"What to wear app" && ./gradlew testDebugUnitTest
```

`RealNoteTest` reads Richie's actual note from `~/Documents/Main` and checks the app's suggestions against it. On any other computer it skips itself, so the repository never needs a copy of his data. The other tests use made-up rows.

For trying the app without a phone there is an emulator (a pretend phone on the Mac) called `Pixel8a_test`, set up as a Pixel 8a. Start it with `/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd Pixel8a_test`. The emulator has no Wi-Fi location, so give it one with `adb emu geo fix -122.4194 37.7749` while the app is looking.

| Where | What it does |
| --- | --- |
| `model/Recommender.kt` | Works out what to wear and learns from the logs |
| `model/NoteTable.kt` | Reads and edits the table in the note, one line at a time |
| `model/Clothing.kt` | The clothes, their warmth scores and your own clothes |
| `model/Weather.kt` | Hour-by-hour weather and the summary the app dresses for |
| `model/LogRow.kt` | Turns a new log into a row for the note |
| `data/NoteStore.kt` | Opens the note on the phone through Android's file picker |
| `data/WeatherClient.kt` | Gets forecasts and place names from Open-Meteo |
| `ui/` | The Today, Log and History tabs, and the quick-log window |
| `QuickLogActivity.kt` | Opens the quick-log window over the home screen, from the widget or the reminder |
| `widget/` | The home-screen widget, the job that refreshes its suggestion every two hours, and its redraw after an update |
| `reminder/Reminders.kt` | The 5pm reminder |
| `data/Logs.kt` | Loads the logs the same way for the app, the widget and the reminder |
| `analysis/` | The calibration script and its graphs |

## Privacy

There are no accounts and no server. The weather comes from [Open-Meteo](https://open-meteo.com), which needs no key. The only data that leaves the phone is your rough location (or a place you searched for), sent to Open-Meteo to get the weather. The app asks for approximate location only.

## Status

Version 0.8, November 2026. Richie's measure of success: it feels effortless to remember to log, and he has logged 20 to 30 entries. Ideas not yet built are listed at the end of [docs/why.md](docs/why.md).

## Licence and credits

MIT licence (see `LICENSE`). Weather data by [Open-Meteo.com](https://open-meteo.com), under the CC BY 4.0 licence. The code was written by Claude Code (Anthropic) under Richie's direction.
