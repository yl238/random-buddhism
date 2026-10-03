# Random Dhamma

An Android app (built for the Pixel 7) that sends one notification a day, at a random time between 07:00 and 22:00. It offers either:

- a random sutta from [dhammatalks.org/suttas](https://www.dhammatalks.org/suttas/), which opens in an in-app reader, or
- a random short morning talk from [dhammatalks.org/audio/morning](https://www.dhammatalks.org/audio/morning/), which plays at **0.75×** by default with background playback and lock-screen controls. A talk notification has a ▶ button, so you can start listening from the lock screen without unlocking.

The app never shows when the next teaching is due, so each one is a surprise.

It also has a "Give me one now" button, a history of past picks and a settings screen. Settings cover the time window, the sutta/talk mix, the default talk speed (0.5×–1.5×) and an optional exact-time alarm.

## Build and install

1. Open this folder in Android Studio (2026.1 or newer) and let Gradle sync.
   - **Requirements:** AGP 9.4, Gradle 9.8, compileSdk 37 and build-tools 36.0.0. These match the SDK installed on this machine.
2. On the Pixel 7, turn on **Developer options → USB debugging** (or Wireless debugging) and connect the phone.
3. Press **Run ▶** to install the debug build. From a terminal you can use `./gradlew installDebug` instead. Gradle needs a JDK; if `java` isn't on your PATH, use the one bundled with Android Studio:
   ```sh
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ```
4. On first launch, allow notifications.

## Tests and lint

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

The unit tests cover:
- HTML parsing, using real pages saved in `app/src/test/resources/`;
- random-time scheduling, including the slack that keeps late inexact alarms inside the window;
- when a stored alarm time is re-armed, left alone or replaced (`rearmAction`);
- when a year's cached talk list needs refreshing;
- the weighted coin flip.

Lint currently reports no issues.

## Manual checks on the phone

- **On-demand picks:** Settings → **Send test notification**, or Today → **Give me one now**.
- **Real alarm (debug builds only):** arm the daily alarm 20 seconds from now. This runs the full path: AlarmManager, the receiver, the worker, the notification and scheduling tomorrow.
  ```sh
  adb shell am broadcast -a org.randomdhamma.debug.FIRE_IN --ei seconds 20 \
      -n org.randomdhamma/.debug.DebugTriggerReceiver
  ```
- **Test notification from adb (debug builds only):** works with the screen locked and doesn't change today's time. Add `--es type TALK` (or `SUTTA`) to choose the kind.
  ```sh
  adb shell am broadcast -a org.randomdhamma.debug.TEST_NOTIFY \
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
  - A weekly background job refreshes the cache. A year's list is refreshed if it was fetched before that year ended (plus a week's grace), so talks added in late December are still picked up after New Year.
- **Lock-screen playback:** the ▶ button on a talk notification starts the playback service directly instead of opening the app, because opening an app requires unlocking.
  - The service becomes a foreground service before it calls `play()`, because Android 16+ ignores audio requests from background apps.
  - The media session is registered with the service when it starts, so Media3 shows its lock-screen player (⏸/▶ and progress) even though no app screen is connected.
- **Scheduling:** one alarm a day, using `AlarmManager.setAndAllowWhileIdle`. If "Exact time" is on and permitted, it uses `setExactAndAllowWhileIdle` instead.
  - When the alarm fires, it first queues today's pick with WorkManager, then schedules tomorrow. The worker retries, and falls back to a "Tap for today's random teaching" notification when offline.
  - AlarmManager can't report whether an alarm is still pending, so on app start or update the stored time is kept. A time that passed less than two hours ago is left alone, since an inexact alarm may still be on its way. Only after a reboot, which clears all alarms, is a passed time treated as missed.
  - Changing the time window, the clock or the time zone picks a new random time.
