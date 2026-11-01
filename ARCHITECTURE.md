# BatMon Architecture

## Goals

- Native Android app, minimum API 28.
- Low-power battery monitoring with explicitly documented limitations.
- Independent MQTT and webhook integrations.
- Home Assistant owns smart-plug switching.
- Testable threshold and delivery logic.

## Data flow

`Android battery APIs → BatteryRepository → ThresholdEngine → NotificationService / MqttPublisher / WebhookQueue`

The dashboard and settings UI observe state through ViewModels and reactive streams. Composables must not perform network operations.

## Components

- **BatteryRepository:** reads battery level, charging status, and supported optional fields.
- **ThresholdEngine:** evaluates crossings, re-arming, initial state, and duplicate suppression.
- **SettingsRepository:** stores thresholds, device identity, monitoring mode, and integration configuration.
- **NotificationService:** creates channels and posts local notifications with correct API-version handling.
- **BackgroundCoordinator:** schedules deferred work and manages the optional permitted foreground-service mode.
- **MqttPublisher:** manages broker connection, state publication, availability, and Home Assistant discovery.
- **WebhookDispatcher:** validates requests, queues events, retries transient failures, and records delivery status.
- **DiagnosticsRepository:** reports permissions, monitoring mode, last update, and integration health.
- **Home Assistant automation:** consumes fresh battery state and controls the configured switch.

## Persistence

Use DataStore for normal settings. Use Room if a durable webhook queue or event history requires relational queries. Encrypt sensitive integration credentials using an appropriate Android Keystore-backed design.

## Key design decisions

1. Do not depend on a permanently running UI process.
2. Do not claim WorkManager jobs execute at exact intervals.
3. Keep MQTT and webhooks independently configurable.
4. Treat retained MQTT state as potentially stale.
5. Use unique event IDs because webhook retries may duplicate delivery.
6. Home Assistant is the authority for smart-plug control.
7. Never switch on a charger using stale sensor data.
