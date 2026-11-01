# BatMon — Product & Technical Specification

**Application:** BatMon  
**Platform:** Android phones, tablets, and compatible Android devices  
**Minimum Android version:** Android 9 (API 28)  
**Primary integrations:** Home Assistant, MQTT, HTTP webhooks  
**Technology:** Kotlin, Jetpack Compose, Material 3

## 1. Product overview

Build a lightweight Android application that monitors battery state, generates configurable alerts when thresholds are crossed, and integrates with Home Assistant for automated charging control.

The app must support Android 9 (API 28) and newer, phones and tablets, portrait and landscape layouts, and standalone use without Home Assistant.

Core features:
- Circular battery percentage dial.
- Configurable low/high thresholds (defaults: 45% and 80%).
- Local notifications.
- Configurable HTTP webhooks per event.
- MQTT state publishing and Home Assistant MQTT discovery.
- Charging-state and availability sensors.
- Background monitoring designed to minimize battery use.
- Persistent settings and recovery after normal restarts/reboots.
- Per-device identity for multiple installations.
- Diagnostics, delivery history, and clear reporting of stale data.

## 2. Product principles

### Simplicity and UI
Use an Apple-inspired design approach without copying proprietary assets: generous spacing, clear typography, restrained colors, rounded surfaces, accessible controls, subtle animation, and consistent light/dark themes. Use Material 3 and Jetpack Compose.

The dashboard should immediately show battery percentage, charging state, low/high thresholds, monitoring state, last update, and integration status. Advanced configuration belongs in Settings.

### Battery efficiency
Do not run an unrestricted polling loop. Prefer system battery broadcasts while the process is active. Use WorkManager for deferred/retryable work. Make the trade-off between low-power and near-real-time monitoring explicit. Do not request unrelated permissions.

### Honest status
Distinguish monitoring enabled, monitoring active, monitoring delayed/restricted, integration disconnected, stale battery data, and notification permission denied.

## 3. Technology stack

- Kotlin
- Jetpack Compose and Material 3
- Minimum SDK API 28
- Gradle Kotlin DSL
- DataStore for settings
- Room only if needed for durable event history/queueing
- WorkManager for deferred work and retries
- Android battery broadcasts and BatteryManager
- NotificationManager and notification channels
- Maintained MQTT client compatible with API 28
- OkHttp or equivalent maintained HTTP client
- Kotlin serialization or another maintained JSON library
- JUnit, coroutine tests, Compose UI tests, and instrumented tests

Use stable compatible dependency versions. Document the required JDK and Android SDK. Do not claim the project builds until a real build succeeds.

## 4. Functional requirements

### FR-01 Battery dashboard
Display:
- Battery percentage (0–100%) and circular gauge.
- Charging, discharging, full, or unknown state.
- Low and high thresholds.
- Monitoring status and last update.
- MQTT status when enabled.
- Webhook delivery status when configured.

Animate only meaningful changes. Include accessibility labels.

### FR-02 Thresholds
Defaults:
- Low threshold: 45%.
- High threshold: 80%.
- Low and high notifications enabled.

Allow thresholds from 1% to 99%, with low < high. Apply changes immediately and persist them. Avoid duplicate events while the battery remains beyond a threshold.

Example: emit a low event at or below the low threshold and a high event at or above the high threshold. Re-arm the low event after rising above the low threshold; re-arm the high event after falling below the high threshold. On startup, evaluate current state without duplicating a previously processed event.

### FR-03 Notifications
Provide notification channels for low battery, high battery, and relevant monitoring/integration warnings. Include event, observed percentage, threshold, charging state, and timestamp. Tapping a notification opens the relevant app screen.

Handle runtime notification permission on Android 13+; do not request it on Android 9–12. If permission is denied, explain this in the UI while continuing other permitted functions.

### FR-04 Battery monitoring
Read battery percentage and charging status from Android APIs. Read health, temperature, and charging source only when available; never fabricate missing values.

**Low-power mode (default):**
- Use system battery broadcasts while the process is active.
- Use WorkManager for deferred synchronization/retries.
- Do not promise exact scheduling or immediate threshold detection when the process is stopped.
- Display when monitoring/reporting may be delayed.

**Real-time mode (optional):**
- Use a foreground service only when explicitly enabled.
- Show the required persistent notification.
- Implement the appropriate foreground-service permissions/types and version-specific restrictions.
- Prefer event-driven updates over polling.
- Explain power and notification implications before enabling.

WorkManager periodic work is inexact and can be deferred by Android. Document Doze, manufacturer restrictions, process death, and force-stop behaviour.

### FR-05 Recovery
Persist settings and last processed threshold state. On permitted startup/recovery:
- Restore settings and monitoring preferences.
- Reinitialize permitted work.
- Reconnect MQTT where allowed.
- Evaluate current battery state without duplicate events.

Respect restrictions on background service startup after reboot. A force-stopped app may remain stopped until manually launched; document this limitation.

## 5. Home Assistant and MQTT

MQTT and webhooks must be independently configurable and may both be enabled.

### MQTT settings
Provide broker hostname/IP, port, client ID, username/password, TLS settings, topic prefix, discovery prefix, availability topic, reconnection settings, and connection test.

Defaults:
- Discovery prefix: `homeassistant`
- Topic prefix: `batmon/<device_id>`
- Suggested topics: `batmon/<device_id>/state`, `batmon/<device_id>/availability`, `batmon/<device_id>/event`

Use a stable configurable device ID, not an IMEI or hardware serial number.

### State payload
Example only; values and timestamp must be generated from actual state:

```json
{
  "device_id": "android-tablet-office",
  "battery_percent": 67,
  "charging_status": "discharging",
  "is_charging": false,
  "low_threshold": 45,
  "high_threshold": 80,
  "monitoring_enabled": true,
  "timestamp": "2026-10-30T10:00:00Z"
}
```

Publish on meaningful state changes and configured synchronization intervals. Retained state may be used, but consumers must be able to identify stale data. A retained value does not prove a device is online.

### MQTT discovery
Create stable Home Assistant MQTT discovery entities where applicable:
- Battery percentage sensor.
- Charging-state binary sensor.
- Monitoring-enabled diagnostic sensor, if appropriate.
- Battery health sensor when available.
- Last-update diagnostic sensor.
- Availability indicator.

Use correct device classes, state classes, units, availability topics, JSON templates, and stable unique IDs. Publish discovery configuration after connection and refresh as needed. Support clean removal when practical without deleting unrelated entities.

### Availability
Use `online` / `offline` and an MQTT Last Will where supported. Mark devices stale/unavailable when updates stop beyond a configured timeout. Do not present retained values as live readings.

## 6. Webhooks

Webhooks are a first-class feature and must work without MQTT.

### Configuration
Allow separate configuration for:
- Low battery.
- High battery.
- Charging started.
- Charging stopped.
- Optional battery-state updates.
- Optional monitoring warnings.

Each event configuration supports enable/disable, URL, HTTP method (POST default), custom headers, optional authentication, JSON payload template, connection/request timeouts, retry policy, and test button.

Use HTTPS by default. Permit HTTP only after explicit configuration and show a security warning for insecure destinations. Never disable TLS certificate validation.

### Example payloads

```json
{
  "event": "battery_low",
  "event_id": "generated-unique-id",
  "device_id": "android-tablet-office",
  "battery_percent": 45,
  "threshold_percent": 45,
  "is_charging": false,
  "timestamp": "actual-event-timestamp"
}
```

```json
{
  "event": "battery_high",
  "event_id": "generated-unique-id",
  "device_id": "android-tablet-office",
  "battery_percent": 80,
  "threshold_percent": 80,
  "is_charging": true,
  "timestamp": "actual-event-timestamp"
}
```

Do not hard-code example values. Use actual observed state and timestamps.

### Delivery
When reliable delivery is enabled, use a durable queue:
- Queue while offline.
- Retry transient failures with exponential backoff.
- Apply maximum retry limits.
- Persist delivery status.
- Log HTTP status and sanitized errors.
- Use unique event IDs and optional idempotency headers.
- Do not retry permanent errors indefinitely.
- Explain that retries can result in duplicate delivery; receivers should deduplicate by event ID.

Do not send requests continuously when nothing changes.

### Security
Protect credentials using Android Keystore-backed encryption or an appropriate secure-storage approach. Redact secrets from logs. Validate URLs, warn about insecure HTTP, and avoid unnecessary identifiers/data.

## 7. Smart-plug charging control

Home Assistant is the authority for smart-plug switching. BatMon reports battery state and may emit threshold events; Home Assistant evaluates them and controls the selected plug.

Default policy:
- Turn charger ON at or below 45%.
- Turn charger OFF at or above 80%.

The Home Assistant automation must:
- Confirm battery data is fresh.
- Verify the correct device.
- Check that automation is enabled and the switch is available.
- Avoid repeated identical commands.
- Support manual override.
- Recover safely after Home Assistant restarts.
- Verify resulting switch state where possible.
- Never turn on a charger solely based on stale battery data.

Include example Home Assistant automation YAML with placeholder entity IDs clearly marked for replacement.

## 8. UI

### Main screen
- BatMon title and Settings.
- Large circular battery dial.
- Percentage and charging state.
- Low/high threshold controls.
- Monitoring toggle.
- Integration status and last update.

### Settings sections
**Battery:** thresholds, notifications, monitoring mode.  
**MQTT / Home Assistant:** broker, credentials, topics, discovery, connection test.  
**Webhooks:** per-event URL/headers/payload, test delivery, history.  
**Device:** device name/ID, Android version, diagnostics.  
**About:** app version, privacy, background limitations.

Use responsive Compose window-size classes or equivalent. Support compact phone and expanded tablet layouts, portrait/landscape, large fonts, accessibility, and light/dark themes. Do not merely stretch the phone layout on tablets.

Provide meaningful visual states for low/high battery, charging, disconnected integrations, webhook failure, restricted monitoring, and stale data. Never rely on color alone.

## 9. Persistence and data model

Use DataStore for normal settings and Room if durable event history/queueing requires it.

Suggested initial settings model:

```kotlin
data class BatMonSettings(
    val deviceId: String,
    val deviceName: String,
    val lowThreshold: Int = 45,
    val highThreshold: Int = 80,
    val notificationsEnabled: Boolean = true,
    val lowWebhookEnabled: Boolean = false,
    val highWebhookEnabled: Boolean = false,
    val mqttEnabled: Boolean = false,
    val webhooksEnabled: Boolean = false,
    val realTimeMonitoringEnabled: Boolean = false
)
```

Expand with typed MQTT, webhook, delivery, and monitoring settings. Validate before saving. Migrate settings safely. Do not store secrets in plain text. Keep application configuration separate from transient screen state.

## 10. Architecture

Suggested packages/modules:
- `app`: application setup and navigation.
- `core/model`: shared models.
- `core/data`: repositories and persistence.
- `battery`: battery acquisition and threshold engine.
- `notifications`: local notifications.
- `background`: WorkManager and permitted foreground-service logic.
- `mqtt`: MQTT connection and Home Assistant discovery.
- `webhooks`: HTTP configuration, queue, retry and delivery history.
- `ui/dashboard`: main screen.
- `ui/settings`: settings.
- `ui/components`: reusable UI.
- `diagnostics`: monitoring and integration health.

Suggested data flow:

`Android battery APIs → BatteryRepository → ThresholdEngine → NotificationService / MQTT Publisher / Webhook Queue`

The UI observes state through ViewModels/reactive streams. Composables must not perform network calls directly.

## 11. Background execution constraints

Android background execution is not an always-running daemon. Account for Doze, process termination, battery optimization, manufacturer restrictions, WorkManager delays, denied notifications, network disconnection, force-stop behaviour, reboot restrictions, and foreground-service restrictions.

Never promise real-time threshold detection in low-power mode. Provide diagnostics explaining the selected mode and restrictions. If near-real-time detection is needed, offer the optional foreground service with a persistent notification and documented limitations.

## 12. Security and privacy

- Request only necessary permissions.
- Use TLS for MQTT and HTTPS for webhooks where supported.
- Never disable certificate verification.
- Protect credentials at rest.
- Never log passwords, tokens, or sensitive headers.
- Do not collect location or unrelated personal data.
- Use configurable IDs rather than hardware serial numbers.
- Validate URLs and use network timeouts.
- Sanitize logs.
- Document transmitted fields and destinations.

## 13. Testing

### Unit tests
Cover threshold crossing/re-arming, duplicate prevention, invalid thresholds, startup evaluation, persistence, MQTT serialization, discovery payloads, webhook payloads, retry/backoff, stale-data detection, and device identity.

### Integration tests
Cover MQTT connection/reconnection, Home Assistant discovery, retained state/availability, webhook success/failure, offline queue/retry, settings restoration, notifications, and charging automation with simulated sensor states.

### UI tests
Cover phone/tablet layouts, portrait/landscape, thresholds, validation, themes, accessibility, setup errors, and webhook results.

### Compatibility
Test representative emulators/devices:
- Android 9 / API 28.
- Android 10–12.
- Android 13.
- Android 14.
- Android 15 and newer supported versions.

Test at least one phone and one tablet form factor. Verify notifications, background updates, reboot recovery, and platform restrictions.

## 14. Acceptance criteria

The app is complete only when:
1. It builds with documented tools.
2. It installs and runs on Android 9 (API 28) and newer supported versions.
3. It supports responsive phone and tablet layouts.
4. Battery percentage and charging state are correct.
5. Low/high thresholds are independently configurable.
6. Threshold events are not duplicated while beyond a threshold.
7. Notifications handle platform permissions correctly.
8. MQTT works independently of webhooks.
9. MQTT discovery creates expected Home Assistant entities.
10. Webhooks work independently of MQTT.
11. Webhook tests, error reporting, queueing, and retries work.
12. Secrets are not exposed in logs or plain-text settings.
13. Home Assistant can use state to control a configured smart plug.
14. Stale data and unavailable integrations are handled correctly.
15. No unrestricted polling loop is used.
16. Monitoring trade-offs are clearly communicated.
17. Settings survive normal restarts and reboot recovery where Android permits.
18. Automated tests pass.
19. Setup and troubleshooting documentation is included.
20. No screen contains non-functional placeholder controls.

## 15. Deliverables

- `SPECIFICATION.md`
- `README.md`
- `ARCHITECTURE.md`
- `IMPLEMENTATION_PLAN.md`
- `AGENTS.md`
- Android source and Gradle configuration.
- Unit, integration, and UI tests.
- MQTT/Home Assistant discovery documentation.
- Example charging automations.
- Webhook examples.
- Security/privacy documentation.
- Troubleshooting guide.
- Debug APK only after a successful real build.

## 16. OpenCode implementation plan

### Phase 1 — Foundation
Initialize Android project, API 28 minimum, Compose design system, navigation, dashboard skeleton, and build instructions.

### Phase 2 — Battery monitoring
Implement battery repository, threshold engine, notifications, DataStore, and unit tests.

### Phase 3 — Background reliability
Implement low-power mode, optional foreground service, recovery, diagnostics, and stale-data detection.

### Phase 4 — MQTT/Home Assistant
Implement broker settings, connection management, discovery, state publishing, availability, reconnection, and tests.

### Phase 5 — Webhooks
Implement per-event settings, secure HTTP, durable queue, retries, delivery history, payload preview, and test delivery.

### Phase 6 — Charging automation
Provide tested Home Assistant YAML, stale-data protection, manual override, and simulated ON/OFF tests.

### Phase 7 — Polish and release
Complete responsive UI, themes, accessibility, tests, debug build, and documentation.

After each phase, run relevant tests, fix regressions, update docs, record unresolved limitations, and avoid implementing later-phase features prematurely.

## 17. Non-goals

Unless requested later, do not implement cloud accounts, advertising, analytics tracking, location tracking, unrelated remote device management, mandatory paid cloud services, direct control of every smart-plug brand, or guaranteed threshold detection when Android has stopped or force-stopped the app.
