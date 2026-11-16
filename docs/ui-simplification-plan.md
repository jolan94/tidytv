# UI simplification plan (draft, 2026-10-23)

Goal: say less, show more. The layout, icons and cards explain the app; text is a label, not a lecture.

## Rules every screen follows
1. **One idea per screen**, one primary button.
2. **Max one short line under a title.** Anything longer moves behind a small (i) that opens a bottom sheet.
3. **Cards carry the choice**: big visual, short name, one tag. No paragraphs inside cards.
4. **Status is a chip or icon**, not a sentence ("Backup saved ✓", not "Backup saved. You can undo these changes anytime.").
5. **Warnings only when they change what the user does.** Reassurance lives in one place (a "Safe" chip), not on every screen.

## Shared building blocks (build first)
- `ChoiceCard`: preview image or icon, title, optional tag ("Recommended"), selected check, one tap target.
- `InfoChip`: icon + 2-3 words ("Free", "Backup first", "Never deletes").
- `InfoSheet`: bottom sheet opened by an (i) button for the long explanations that stay available.
- Consistent vector icons for the hub and groups instead of emoji (emoji look different per phone).

## Screen by screen

| Screen | Too much today | Proposed |
|---|---|---|
| Welcome | Footer paragraph ("Free and open source. TidyTV turns apps off, never deletes them..."); brand list under Android TV ("Sony, TCL, Philips, Hisense, Xiaomi, Chromecast...") | Three chips: Free, Never deletes, Backup first. TV cards show a logo/icon and name only; "Clean-up only" becomes a small badge on Fire TV. Mascot smaller. |
| Get your TV ready | 3 long steps, amber USB-power note, Wireless-debugging paragraph | 3 numbered steps with one short line each ("Tap Build 7 times", "Turn on USB debugging", "Allow on the TV"), settings path as small grey text. Amber note and wireless note go behind "Need help?". |
| Connect | Several explanation paragraphs, "Same Wi-Fi?" card | Scanning list of TV cards. Errors get one line plus one button. "Same Wi-Fi?" becomes an (i). |
| My TV hub | Subtitle under every tile; "Not tidied yet"; "What would you like to do?" | Tiles show icon + title only. Status moves onto the Tidy tile as a badge ("6 off" or a dot). Drop the question title, shrink the mascot so tiles sit higher. |
| Review (pick apps) | Banner + "You can undo..." sentence; per-group warning sentences; "N of M picked"; truncated labels ("Pushed message...") | Single "Backup saved ✓" chip. Group warnings become a short chip on the group ("Voice button stops"). App tiles show the real icon with the name on two lines; long names move to a tap-to-read tooltip. Not-exact list note becomes a yellow "Similar model" chip. |
| Tidying (progress) | "Android saves these changes a few seconds later. Please don't turn the TV off yet." | Progress bar + "Saving, keep the TV on". |
| Home screen offer (plan selection) | Long paragraph per option (size, Android version, how to move apps); heading + paragraph above | Large preview card per option: image, name, tag (Recommended / Popular), check when selected. One-line benefit max ("Calm, just your apps"). Size, Android version, instructions go in an (i) sheet. Sticky button names the choice ("Use Simple"). "No thanks" stays a quiet link. |
| Look (theme selection) | Explainer sentence; photo instructions | Grid of theme cards (preview + name). A "Your photo" card with a + replaces the instructions. Sending state is a progress line only. |
| Done | "Freed about N MB", "How did it go?" card, "Changed your mind about one? Turn it back on by itself." | Big result ("6 apps off") with mascot, then Back to My TV. Feedback moves to a small link or shows later. The apps-turned-off list stays, minus the instruction sentence. |
| Check my TV | Intro paragraph, long reboot-test explanation | Result card first ("All tidy: 6 still off"). Reboot test is a secondary button with a one-line caption. |
| Remote | Little text already | Keep. Check button labels and the typing field spacing. |
| Send files | Paragraph about Downloads > TidyTV and file types | Icon row (Photos, Videos, Music, Docs) and one caption ("Saved in Downloads > TidyTV"). Free space as a small chip. |
| Restore / Rescue | Step-by-step text | One step per card with an icon; the rest behind (i). |

## Why this also helps the store listing
The Play screenshots currently show these text-heavy screens. Redo the UI first, then retake the screenshots (same pipeline as before, about an hour).

## Google TV problem: what I think is happening
You saw only one app to turn off and no simplification (clean home screen) option on the new Google TV. Reading the code, there are two likely causes, and tomorrow's check will tell which:

1. **Only one app on the list.** The list comes from the nearest tested model (`ModelMatcher`). For a model with no exact list it uses a similar one, then keeps only the apps actually installed on the TV. A similar list from another brand can leave just one match. The screen then looks "done" when it isn't.
2. **No clean home screen offer.** `launcherPossible()` offers the clean home screen only if the TV's current home app is one of four known stock launchers (`com.google.android.tvlauncher`, LauncherX, `leanbacklauncher`, `com.mitv.tvhome`). A newer Google TV, or a maker launcher not in that list, gets no offer after tidying.

### UI plan for it (no data change yet)
- If the list finds fewer than about 5 apps, show a clear card instead of silence: "We found 1 app we're sure about", with a "Show all apps" button.
- "Show all apps": every removable app grouped as today, everything off by default, protected apps hidden, a short (i) on the risks. User picks; backup still comes first.
- Home screen: always reachable from the hub. If the TV's launcher is unknown, show a card ("Your TV uses a launcher we haven't tested") with a Try anyway option behind a confirmation, instead of hiding the option.
- Share logs button right on that screen, so we get the model and launcher name quickly.

### To check tomorrow (needs the TV)
- TV model name and which list it matched (the "list ... score ..." line in the trace).
- The TV's current home app package (the RESOLVE_HOME result).
- Whether the hub's Home screen tile also fails, or only the offer after tidying.
- Any apps you would expect to see that were missing.

## Proposed order
1. Shared components (`ChoiceCard`, `InfoChip`, `InfoSheet`, icons).
2. Hub, Welcome and Get-ready (first impression).
3. Review and the Google TV fallback ("Show all apps").
4. Home screen offer and Look cards.
5. Done, Check, Send files, Restore.
6. Update the screenshot tests, then retake the Play Store screenshots.

## Decisions for you
- Replace emoji with drawn icons everywhere (my suggestion), or keep emoji?
- On Done, keep the feedback card but lower on the page, or remove it from this screen?
- For unknown launchers: allow "Try anyway" with a confirmation, or never offer a home change on untested launchers?
