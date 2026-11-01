# BatMon

BatMon is a lightweight Android battery monitor for phones and tablets, with Home Assistant integration through MQTT and configurable HTTP webhooks.

## Features

- **Minimum Android version:** Android 9 (API 28). Target: Android 15 (API 35).
- **Modern UI:** Jetpack Compose, Material 3, light/dark themes, phone & tablet responsive layouts.
- **Battery Gauge:** Visual circular percentage gauge, charging status, and threshold markers.
- **Alert Thresholds:** Configurable low/high thresholds (defaults: 45% and 80%).
- **MQTT & Home Assistant Integration:** Broker connectivity (TCP/TLS), authentication, LWT availability (`online`/`offline`), state publishing, and Home Assistant MQTT auto-discovery for battery, charging binary sensor, diagnostics, and last-update timestamp.
- **HTTP Webhooks:** Event-triggered HTTP/HTTPS webhooks with durable queue, exponential backoff retries, delivery history, custom headers, payload templates, and HTTPS enforcement.
- **Smart-Plug Charging Automation:** Example Home Assistant automations for MQTT and Webhooks with stale-data protection, duplicate command suppression, manual override, and restart recovery.

## Build Requirements

- **JDK:** OpenJDK 17 or compatible.
- **Android SDK:** Compile SDK 35, Command-line tools, and platform `android-35`.
- **Gradle:** Uses included Gradle wrapper (`8.9`).

## Building & Testing

### Run Unit Tests
```bash
./gradlew test
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The resulting APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## MQTT & Home Assistant Integration

BatMon publishes battery metrics using MQTT 5.0 through Eclipse Paho (including Mosquitto / Home Assistant OS MQTT Add-on):

- **Connection:** Uses one MQTT 5.0 client with username/password authentication and an `offline` Last Will.
- **State Topic:** `batmon/<device_id>/state` (configurable prefix, displayed in Settings)
- **Event Topic:** `batmon/<device_id>/event` (threshold crossings and state events)
- **Availability Topic:** `batmon/<device_id>/availability` (`online` with LWT `offline`)
- **Home Assistant Auto-Discovery:** Publishes discovery definitions under `homeassistant/<component>/batmon_<device_id>/...`:
  - `sensor.batmon_<device_id>_battery`: Battery percentage (device class: `battery`)
  - `binary_sensor.batmon_<device_id>_charging`: Charging state (device class: `battery_charging`)
  - `binary_sensor.batmon_<device_id>_monitoring`: Monitoring status diagnostic sensor
  - `sensor.batmon_<device_id>_health`: Battery health diagnostic sensor
  - `sensor.batmon_<device_id>_last_update`: ISO-8601 timestamp sensor
- **Security:** Credentials are protected in app private storage, never logged in plaintext, and TLS with certificate verification is supported.

## Webhooks

BatMon supports first-class HTTP webhooks independent of MQTT:

- **Supported Events:**
  - `battery_low`: Emitted when percentage falls to or below the low threshold.
  - `battery_high`: Emitted when percentage rises to or above the high threshold.
  - `charging_started`: Emitted when charger is connected.
  - `charging_stopped`: Emitted when charger is disconnected.
  - `state_update`: Emitted on periodic state sync.
  - `monitoring_warning`: Emitted when warnings or stale states are detected.
- **Durable Queue & Retries:** Failed webhook deliveries (due to offline state, network timeouts, or 5xx server errors) are placed in a durable queue backed by DataStore Preferences and retried with exponential backoff up to the configured retry limit. Permanent 4xx client errors are not retried indefinitely.
- **Security & Privacy:** HTTPS is enforced by default. Insecure HTTP requires explicit user opt-in with a visible security warning. All sensitive headers (`Authorization`, API keys) and tokens are redacted from logs and error messages. TLS certificate verification is never disabled.
- **Custom Payloads:** Supports JSON payload templating with dynamic variables (`{event}`, `{event_id}`, `{device_id}`, `{battery_percent}`, `{threshold_percent}`, `{is_charging}`, `{timestamp}`).

## Smart-Plug Charging Automation (Home Assistant)

Home Assistant is the designated authority for smart-plug switching. BatMon provides state telemetry via MQTT or Webhooks, and Home Assistant automations evaluate thresholds and command the charger switch.

### Key Policies & Safety Rules
1. **Default Thresholds:**
   - Turn charger **ON** when battery level drops to or below **45%** (if switch is currently OFF).
   - Turn charger **OFF** when battery level rises to or above **80%** (if switch is currently ON).
2. **Stale Data Protection:** Readings older than 30 minutes (or when availability is offline) are discarded to prevent unwanted charging switches based on outdated data.
3. **Duplicate Command Suppression:** Automations explicitly verify the current state of the switch prior to commanding it, eliminating redundant turn-on/turn-off calls.
4. **Manual Override Support:** Supports an `input_boolean` helper to temporarily suspend automated charging without disabling the automation.
5. **Reboot Recovery:** Automations trigger on `homeassistant.start` to safely re-evaluate fresh battery state after Home Assistant restarts.
6. **Switch Verification:** Verifies resulting switch state after switching and alerts if the smart plug failed to toggle.

### Example Automations
- **MQTT Automation:** See [`homeassistant/charging_automation.yaml`](homeassistant/charging_automation.yaml). Integrates with MQTT discovery sensors (`sensor.batmon_<device_id>_battery` and `binary_sensor.batmon_<device_id>_availability`).
- **Webhook Automation:** See [`homeassistant/webhook_charging_automation.yaml`](homeassistant/webhook_charging_automation.yaml). Integrates directly with BatMon webhook events (`battery_low` and `battery_high`).

## Platform & Background Execution Limitations

Android background execution is constrained by system policies and battery optimization:

1. **Low-Power Mode (Default):**
   - Uses `Intent.ACTION_BATTERY_CHANGED` while active in foreground or active process.
   - Leverages `WorkManager` periodic tasks for background sync.
   - **Constraint:** By Android design, WorkManager periodic work has a minimum 15-minute interval and is subject to Doze deferrals. Exact schedule execution cannot be guaranteed when the app is in the background.

2. **Real-Time Mode (Optional Foreground Service):**
   - Uses an Android Foreground Service with an ongoing notification (`android:foregroundServiceType="dataSync"`).
   - Keeps battery event listening active for instantaneous threshold crossing alerts.
   - **Trade-off:** Requires displaying a persistent notification and draws slightly more power.

3. **Stale Data Protection:**
   - Battery readings older than 30 minutes in low-power mode (or 3 minutes in real-time mode) are flagged as **stale**.
   - Per safety guidelines, stale readings are marked unsafe for smart-plug automated switching.

4. **Device & Manufacturer Restrictions:**
   - **Force-Stop:** If the user or OS force-stops BatMon, all background workers and broadcast receivers cease execution until the app is opened manually.
   - **OEM Battery Killers:** Devices from Samsung, Xiaomi, Huawei, etc., may kill background tasks unless BatMon is explicitly added to the device's battery optimization whitelist.
   - **Reboot Recovery:** A `BootCompletedReceiver` restores periodic WorkManager sync or restarts the real-time service after device reboot.

## Setup & Troubleshooting

For complete first-time configuration instructions, MQTT broker setup, Home Assistant discovery, and solutions for common issues, see [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Author

**Sunil Sankar** ([@sunilsankar](https://github.com/sunilsankar))

## License

BatMon is open source software licensed under the [Apache License, Version 2.0](LICENSE).
