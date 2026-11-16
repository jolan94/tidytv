# TidyTV roadmap

Things deliberately left out of v1. v1 is: connect with no code, back up, turn bloat off, optional
tv-detox home screen, restore everything. Everything below comes later.

## Launcher choices
- Done in 0.2.0: cleanyourtv's Apple TV style launcher as a second choice (Android 8+, dock written
  to `config.json`), built by `tools/build-styled-home.sh`. Still to do: build it in CI, themes.
- Done in 0.5.0: Projectivy Launcher as a third choice, opened on the TV's Play Store page; you press
  Install with the remote and TidyTV sets it as Home. Restore leaves it installed. Never bundled.
- Done in 0.5.0: Check my TV and re-tidy (apps a TV update turned back on, plus new apps on the TV's list).
- Done in 0.5.0: the reboot test ("Restart the TV and check" waits about 2 minutes, then checks again).
- Done in 0.5.0: boot-loop rescue ("TV stuck after a change?" retries every 5 seconds for up to 10
  minutes, then turns everything it changed back on).
- Measure each launcher's RAM with `dumpsys meminfo` before showing numbers. Known so far: tv-detox
  launcher is a 37 KB APK using about 30 MB RAM; the cleanyourtv launcher is about 300 KB.

## Mascot and art
- Full mascot set: waving, searching, look-at-TV, backup, broom, confetti, worried, sleeping.
- A "press Allow" TV illustration, developer-options illustrations, a launcher preview, a README
  banner, adaptive-icon polish.

## More checks and safety
- **Check my TV**: re-apply after firmware updates (tv-detox `check.sh`).
- **Reboot test** at the end: wait for adbd to come back, then re-verify the disabled set and Home.
- **Boot-loop rescue**: keep retrying the connection and enable everything as soon as ADB answers.

## Extra tweaks
- Animation scale 0.5, screensaver off, sleep timer, ad-blocking Private DNS. Each one is backed up
  first and restored from the backup value, never a hardcoded one (the Restorer already does this
  for any setting recorded in the action log).
- Microphone permission revoke recipe (from the TCL list notes).

## Lists
- Fetch newer lists from raw.githubusercontent.com at runtime, validated by the same parser.
- **Share my working list**: open a pre-filled upstream `tv-list` issue.

## Icons
- Done in 0.2.0: real app icons read from the TV's APKs (up to 12 MB each). Still to do: read icons
  from bigger APKs without pulling the whole file.

## Before any Play Store listing
- Search for trademark conflicts with the name "TidyTV" (fine for GitHub).

## v2 features researched 2026-10-20 (not started)

### Remote control
- Done in 0.4.0: the ADB remote (D-pad, OK, Back, Home, Menu, volume, power, media keys, typing, an
  "Apps on your TV" launcher) through a kept-open legacy `shell:` stream, on every TV incl. Fire TV.
  Still to do: the Android TV Remote Protocol v2 below (no developer mode, faster, wake from standby).
- Hybrid. ADB baseline (already connected, the only open route on Fire TV), ideally scrcpy-server in
  control-only mode (Apache-2.0, millisecond key/text/touch injection). Plain `input keyevent` is a
  fallback: 50–150 ms on Android 12+, 300–1000 ms on older TVs and Fire OS.
- Optional Android TV Remote Protocol v2 (TLS, pairing on 6467 with a 6-hex code, session on 6466) on
  Google/Android TV: ~10–30 ms keys, no developer mode, wake from standby, proper IME text.
  Write it fresh in Kotlin from tronikos/androidtvremote2 (Apache-2.0, protos) and AOSP
  google-tv-pairing-protocol (Apache-2.0). Generate our own client cert.
- harimoradiya/androidtvremote (Apache-2.0, Compose): study and copy UI. NEVER copy its
  `ssl/KeyStoreManager.java` (decompiled from a commercial app), the Amazon jars or `WhisperPlay.jar.cache`.
  outofcontextapps/androidtvremote is the same code relabelled MIT; use only its `.proto` files.
- GPL, ideas only: ADBKeyBoard, HA-Firemote, erenseymen/android-tv-remote.
- Size: ADB D-pad/home/back/volume ~600–900 LOC; v2 transport +~1,500 LOC; keyboard and app grid ~2–3 days each.

### File transfer and sideloading
- Done in 0.4.0: send files from the phone to Downloads › TidyTV on the TV (streamed, progress,
  free-space check, never overwrites). Still to do: browse/pull TV files, APK and split sideloading,
  "send a phone app to the TV".
- Kadb 2.1.5 already has push/pull (Source/Sink), sync `listV2/statV2`, `install(Source,size)`,
  `installMultiple`, `uninstall`, `execCmd`, `abbExec`. Progress: wrap the Source in a counting okio
  ForwardingSource.
- Split APKs: `install-create -S` → `install-write -S <size> <id> <name> -` streamed → `install-commit`,
  `install-abandon` on failure. Fallback for hanging streamed installs (Android 14, Fire OS): push to
  /data/local/tmp then `pm install`. OBB to /sdcard/Android/obb/<pkg>/.
- Copy from sam1am/anyapk (README says Apache-2.0 but no LICENSE file; ask the author to add one):
  `AdbInstaller.installMultiple`, `PackageBundle.kt` (.apks/.xapk/.apkm). Pick splits for the TV's
  ABI/density (`ro.product.cpu.abilist`, `wm density`), not the phone's. Alternative:
  solrudev/Ackpine `ackpine-splits` (Apache-2.0).
- "Send a phone app to the TV": `ApplicationInfo.sourceDir` + `splitSourceDirs`. Warn on ABI mismatch,
  no leanback entry (launch via `monkey -p`), touchscreen-only apps.
- GPL, ideas only: aShellYou (good remote file-browser UX), App Manager, SAI.
- Size: send file ~450 LOC; browse TV files ~700; APK/split sideload ~1,000; send phone app ~400.
- Done in 0.6.0: looks for the Apple TV style home screen (Classic, Aurora, Glass, Scenes from upstream themes/) and your own photo as the wallpaper, shrunk to 1920x1080 JPEG on the phone first. Add-ons (Photo Prints, Weather) deferred.

## Requests from users (Play reviews and in-app feedback, 2026-10-20 to 2026-10-31)
Closed in Winkle HQ as "recorded here". Order is by how often it was asked, not a promise.
- **Custom DNS / DoH** besides the AdGuard Block ads switch (Play review, 0.9.0).
- **Home screen extras:** weather and live-score widgets, more themes, resizing and reordering apps by remote or phone (4 asks plus GitHub #4; the steps page shipped in 0.10.1).
- **Wallpaper:** done in 0.10.1 (send your own, 4K). Open: turn off the focus light effect.
- **Remote:** pairing without ADB (Bluetooth / Android TV Remote Protocol v2, GitHub #7).
- **Restore without the old phone:** a restore that works from a new phone or from the TV itself (asked 3 times).
- **Translate the launcher** into more languages (German "last opened" is untranslated).
- **TVs with no list yet** (asked through the app's "My TV isn't on a list" button): MECOOL KM6, Banoika FHD Streaming Stick (BKK), realme TV Stick (BOS), H96 Max V11 (rk3328_box), Airtel Xstream box. Check upstream cleanyourtv for each before writing anything.
