# Why What to Wear is built this way

Quotes are Richie's own words, from the interview on 1 November 2026 or from his requests while the app was being built. The rest is the technical reasoning behind each choice.

## The goal

Richie's original request, on 31 October 2026, was for "an android app that suggests what to wear based on the weather. You contribute data: what you wore, where, when. and it learns over time." In the interview he put the core of it like this: "I just want an app to tell me what type of clothing to wear on a given day. I want an app to just say, 'Okay, wear shorts today,' or 'Don't wear shorts today.'"

The project is also practice. In his words: "It is just fun practise at building an app with AI and learning how that works. It's a cool thing to show off to people." So the code is written by Claude Code under his direction, and the documentation is written so that he, and future Claude sessions, can understand and change it.

His measure of success: "If I find it effortless to remember to log and I've logged maybe 20, 30 entries."

## One plain answer each morning

The Today tab shows a single outfit, a one-line reason and a few notes (rain, wind, a big swing in temperature). It doesn't show a forecast to interpret. Richie said he will look every morning "at what the app says and choose appropriately", so the answer has to be readable at a glance. Each suggestion says why it was made, for example which past day it copied, so he can judge whether to trust it. The home-screen widget shows the same answer, so he doesn't have to open the app to see it.

## Logging takes a few taps

Richie wants capture to be effortless: "I want capture of new data to be effortless, and I want retrieval of previous data to be effortless. With the note, it was quite clunky." He plans to "log at random moments when I'm out". Several choices follow from that:

- **Now is the default, and sits on the top row.** Most logs will be made in the moment, so "Now" is selected when the Log tab opens. Richie asked for it to sit on the top row with Today, Yesterday and Earlier. While Now is picked, the morning, afternoon, evening and all-day choices are hidden, because Now already says when.
- **A Log button on the home screen.** Richie asked to "literally just click a Log button from my home screen" instead of opening the app and then the Log tab. The widget's 1 to 5 buttons open a small window over the home screen, so a log takes two taps: the score, then Save. The first tap doesn't save on its own, because the widget can't know what he's wearing. The window shows the clothes it will log, already ticked, so a different outfit can be fixed before it teaches the app the wrong thing. If he has already logged that day, it starts from that outfit, since he's probably still wearing it.
- **"Log what I wore" pre-fills the suggestion.** Usually only a tick or two needs changing.
- **How it felt is five big buttons.** It takes one tap.
- **You can add your own clothes from the Log tab.** Richie asked for this so that anything missing from the list can be added on the spot, without leaving the log.
- **No accounts and no sign-in.** Richie wants as little friction as possible: no signing in to things and no clicking through screens. The weather service, Open-Meteo, needs no account or key.

## The logs live in Richie's Obsidian note

Before the app, Richie kept a table in his Obsidian note "How do I feel at different temperatures": the temperature, whether it was windy, what he wore and how he felt. He wants the data to stay there "because that way I can point AI to it in the Claude desktop app, and also I can just see it myself."

He asked for a design that lets him "see both without duplicating", so the note is the app's only store. Two copies of the same logs drift apart: one gets an edit the other misses, and then nobody can tell which is right. With one copy that can't happen. For the same reason, if the app can't read the note (because it was renamed or moved, say), it refuses to save anywhere else and asks to be connected again. Before the note is connected the app keeps logs in a private file, and connecting moves them into the note and retires the file.

On 1 November 2026 the note was changed on the Mac (to add the app's columns) moments before Richie connected it on his phone, which still had the old version. Obsidian Sync kept both: the phone's copy of the table, then the Mac's, with a garbled heading row. The app misread every row and reported 92 it couldn't use. The note was put back from the correct version with nothing lost. Since then the app checks the table before reading or changing it. If it finds two heading rows or the same column twice, it says the note looks damaged and won't write to it, because editing a damaged table would only make it worse.

The app reaches the note through Android's file picker. Richie picks the note once and the app keeps permission to read and write that one file. Obsidian Sync carries the changes to his Mac, and when he reopens the app it reads the note again, so edits made on the Mac show up.

## Richie's four columns stay first

The app adds its six columns (Score, Counted as, Feels like °C, Date, Place, Weather) after Richie's four, and reads every column by its heading, so the order of the columns doesn't matter. His columns and his wording are never changed. This keeps the note readable as his own note, and it keeps his `note-temperature` skill working: the skill finds the table by its Temperature heading and fills the first four columns, and it now fills the Score, Counted as and Date columns too.

When the app changes the note it adds or removes exactly one line. The rest of the file stays byte for byte as it was. New rows go in by temperature, after the other rows at the same temperature, which is the order the skill already used.

## A reminder at 5pm

Success means it is "effortless to remember to log", so the app reminds him. At 5pm, on days with no log yet, a notification asks "How does your outfit feel?" and opens the quick-log window. It stays quiet on days he has already logged, so it never nags about something already done. 5pm is a guess at a time when he has been out in the day's weather. It's one number in `reminder/Reminders.kt` and easy to change, and the reminder can be switched off on the History tab.

## It starts from Richie's existing note

The note already held 46 rows of Richie's own experience, kept since at least January 2026. The app starts from those. Without the note it would start from a generic guess. 43 rows were given a score and the app's names for the clothes ("Counted as"). The other 3 were left out: one had no outfit ("bare hands"), one was during manual work at an animal sanctuary, and one was both cold and very hot. Where a row didn't mention a top or trousers, the likely ones were assumed and marked "(+ …)", so the outfit's warmth isn't understated. Those readings are visible and editable in the note.

Rows from before the app have no date, so each counts as half a log and never fades. New logs count for more the more recent they are. Over time the app's own logs take over.

## It dresses for the feels-like temperature

The same air temperature feels very different in wind. Richie's note often marks windy days. The app uses Open-Meteo's "feels like" (apparent) temperature, which allows for wind and humidity, and dresses for a mix of the average and the coldest hour of the time you're out (60% average, 40% coldest), because the cold part is what you notice.

Richie's note records the real temperature, so it had to be converted. Instead of guessing, `analysis/calibrate_note_temperatures.py` takes a year of Manchester weather and measures the gap on calm and windy daytime hours. The results are two straight lines, which the app uses whenever a row has no feels-like value.

## A 1 to 5 scale

Richie chose the scale and its words: "1 way too cold, 2 too cold 3 just right 4 too warm 5 too hot". His note was full of in-between feelings such as "a little bit cold" and "slightly cold", which a three-point scale (too cold, just right, too hot) would have exaggerated. He isn't yet sure five is right: "I wonder if 5-scale is just the right level of coarseness: if it needs to be fine-grained enough to be useful, but not so fine-grained that it's like fake precision." Revisit it after 20 to 30 logs.

Each score moves the app's idea of how warm he likes to be: a 2 by about 1 warmth point, a 1 by about 2.5 (more than a jumper's worth), and 4 and 5 the same in the other direction.

## The learning is simple enough to explain

The app expects tens of logs. With that little data a complex model would mostly learn noise, and its suggestions couldn't be explained. Instead the app uses four plain rules: reuse an outfit from a similar day that felt just right, fit a straight line of how warm he likes to be, learn the temperatures at which he's happy in particular items, and prefer the clothes he actually wears. Each suggestion can then say why it was made.

Two rules came from Richie's note. Cold hands come up six times in it, so gloves and a scarf go on automatically once it's cold enough, even when the outfit is otherwise warm enough. And clothes that never appear in the note or his logs (a puffer jacket, a fleece) are almost never suggested, because early versions suggested them and he doesn't seem to wear them.

## A native Android app, installed from Google Drive

Richie asked for an Android app for his Pixel 8a, so it is a native app written in Kotlin with Jetpack Compose. It uses the phone's location and file picker directly. Apart from the weather service it needs nothing online.

It isn't on the Play Store, because it's "just for me" for now and that would mean a developer account and a review process. New versions go into his Google Drive, and he taps the file to update. That costs a few taps per update, and it ties updates to the Mac that built the first version, because Android only installs an update signed with the same key. The key is `~/.android/debug.keystore` on that Mac and must not be lost.

## Storage: a note and a small file

The logs are a table in a Markdown note, and the app's own clothes are a small JSON file. A year of daily logs adds roughly 100 KB to the note, so the whole note is read in a blink and a database would add complexity without any benefit.

## The clocks changing

Open-Meteo applies one fixed time offset to a whole response, so its local times are an hour wrong after the clocks change. The app asks for times as plain timestamps and converts them with the place's real time zone. A test checks the evening of 1 November 2026, the night US clocks went back.

## How it's tested

The tests never go online. Weather replies are real ones from Open-Meteo, saved once and checked against figures worked out separately in Python, so a test can't fail because the internet is down or the weather changed. The saved replies cover the weekend the UK clocks went back, because clock changes have caught the app out before.

Some tests check rules across every temperature from -8°C to 32°C, wet and dry, with learning on and off: one top and one pair of bottoms, never two jackets, rain gear exactly when it rains, colder never meaning less warmth. Rules like these catch mistakes that a handful of examples would miss.

The screens are tested on an imitation phone (Robolectric), so the tests run on Richie's Mac and on GitHub without an emulator. The one test that reads his real note skips itself anywhere else, to keep his data out of the repository.

Writing these tests in November 2026 found four real bugs: two logs on the same day could be put in the wrong order, a clear sky with weak winter sun was saved as sunny, Windows-style line endings weren't kept on new rows, and the 5pm reminder drifted an hour when the clocks changed. Each fix has a test that fails without it.

## Not built yet

These come from Richie's answers and haven't been built.

1. **Revisit the 1 to 5 scale** after 20 to 30 logs, by looking at how often each score is used.
2. **Sharing with other people.** "I might share it with some other people after some testing." That would need a proper signing key kept somewhere safe, a way to start without Richie's note, and probably Google Play's internal testing track, so that updates arrive automatically.
