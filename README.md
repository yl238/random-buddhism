# Random Dhamma

An Android app (built for the Pixel 7) that sends one notification a day, at a random time between 07:00 and 22:00. It offers either:

- a random sutta from [dhammatalks.org/suttas](https://www.dhammatalks.org/suttas/), which opens in an in-app reader, or
- a random short morning talk from [dhammatalks.org/audio/morning](https://www.dhammatalks.org/audio/morning/), which plays at **0.75×** by default with background playback and lock-screen controls.

The app never shows when the next teaching is due, so each one is a surprise.

It also has a "Give me one now" button, a history of past picks and a settings screen. Settings cover the time window, the sutta/talk mix, the default speed and an optional exact-time alarm.

## Build and install

1. Open this folder in Android Studio (2026.1 or newer) and let Gradle sync.
   - **Requirements:** AGP 9.4, Gradle 9.8, compileSdk 37 and build-tools 36.0.0. These match the SDK installed on this machine.
2. On the Pixel 7, turn on **Developer options → USB debugging** (or Wireless debugging) and connect the phone.
3. Press **Run ▶** to install the debug build. From a terminal you can use `./gradlew installDebug` instead.
4. On first launch, allow notifications.

## Tests

```sh
./gradlew :app:testDebugUnitTest
```

The unit tests cover:
- HTML parsing, using real pages saved in `app/src/test/resources/`;
- random-time scheduling;
- the weighted coin flip.

## Manual checks on the phone

- **On-demand picks:** Settings → **Send test notification**, or Today → **Give me one now**.
- **Real alarm (debug builds only):** arm the daily alarm 20 seconds from now. This runs the full path: AlarmManager, the receiver, the worker, the notification and scheduling tomorrow.
  ```sh
  adb shell am broadcast -a org.randomdhamma.debug.FIRE_IN --ei seconds 20 \
      -n org.randomdhamma/.debug.DebugTriggerReceiver
  ```
- **Re-roll today's time (debug builds only):** pick a fresh random time without showing it.
  ```sh
  adb shell am broadcast -a org.randomdhamma.debug.REROLL \
      -n org.randomdhamma/.debug.DebugTriggerReceiver
  ```
- **Check the alarm:** note this prints the scheduled time, which spoils the surprise.
  ```sh
  adb shell dumpsys alarm | grep randomdhamma
  ```
  An inexact alarm shows `window=+1h`, so times are picked at least an hour before the window ends.
- **Doze:** force the phone into Doze, then confirm the alarm still fires:
  ```sh
  adb shell dumpsys deviceidle force-idle
  ```

## How it works

- **Random sutta:** `https://www.dhammatalks.org/suttas/random` returns a 302 redirect to a random sutta. The app reads the `Location` header.
- **Random talk:** each year page (`/audio/morning/<year>/`) lists that year's MP3s. The current year is listed on `/audio/morning/` itself.
  - The app caches every year's list in Room so it can pick uniformly across all talks.
  - Until the cache is complete, it picks a random year and then a random talk from that year.
- **Scheduling:** `AlarmManager.setAndAllowWhileIdle` fires once a day. The alarm hands the network work to WorkManager, which retries and falls back to a "tap for today's teaching" notification when offline. Alarms are re-armed after a reboot or a time zone change.
