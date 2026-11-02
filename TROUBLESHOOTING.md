# BatMon — Setup & Troubleshooting Guide

This guide covers initial setup, Home Assistant integration, and troubleshooting for common platform and network issues.

---

## 1. Initial Setup

### Permissions
1. **Notifications (Android 13+ / API 33+):**
   - BatMon requires the `POST_NOTIFICATIONS` runtime permission to show local alerts when battery thresholds are crossed.
   - If not granted during initial launch, tap **"Grant Permission"** on the Dashboard or enable it in **Android Settings > Apps > BatMon > Notifications**.
2. **Battery Optimization Exemption (Recommended for Low-Power Mode):**
   - To reduce background deferrals caused by Android Doze, exempt BatMon from battery optimization in **Android Settings > Apps > BatMon > Battery > Unrestricted**.

---

## 2. Monitoring Modes: Low-Power vs. Real-Time

| Feature | Low-Power Mode (Default) | Real-Time Mode (Optional) |
|---|---|---|
| **Mechanism** | Android battery broadcasts while open; WorkManager periodic sync (~15 min) in background | Android Foreground Service (`dataSync`) with persistent notification |
| **Battery Impact** | Extremely minimal | Low (~1–2% additional daily) |
| **Alert Timeliness** | Immediate while screen is on; delayed up to 15–30 min when backgrounded/Doze | Immediate under all conditions |
| **Notification** | Shows alerts only when thresholds cross | Ongoing status notification in notification shade |

**To switch modes:** Go to **Settings > Real-Time Monitoring** and toggle the switch.

---

## 3. Home Assistant MQTT Integration Setup

1. **Broker Configuration:**
   - In BatMon **Settings > MQTT / Home Assistant**:
     - **Broker Host:** Enter your MQTT broker IP or hostname (e.g., `192.168.1.15` or `core-mosquitto`).
     - **Port:** Default is `1883` for standard TCP.
     - **Username & Password:** Enter your MQTT credentials (if authentication is configured on the broker; leave blank for anonymous).
     - **Protocol:** BatMon uses standard MQTT 3.1.1 via Eclipse Paho v3 (`tcp://<host>:<port>`).
2. **Testing Connection:**
   - Tap **"Test Connection"**. BatMon executes a pre-flight TCP reachability check, connects with `MqttConnectOptions`, publishes an availability ping to `${mqttTopicPrefix}_${deviceShortId}/status`, and reports feedback:
     - `Connected to <host>:<port> (Authenticated as <username>)` or `(Anonymous)`
     - `Cannot reach <host>:<port> (check IP/port/Wi-Fi)` if TCP socket fails within 3 seconds
     - `Broker reached, but authentication failed (check username/password)` if broker returns reason codes 4 or 5
3. **Auto-Discovery Entities:**
   - Ensure **"Enable Home Assistant Discovery"** is toggled ON.
   - Tap **"Publish HA Discovery Now"**.
   - In Home Assistant, navigate to **Settings > Devices & Services > MQTT**. A new device `Batmon <model>` will appear with:
     - `sensor.<prefix>_<device_id>_battery_level` (Battery percentage)
     - `sensor.<prefix>_<device_id>_charging_status` (Charging status)
     - `binary_sensor.<prefix>_<device_id>_is_charging` (Charging binary sensor)
     - `sensor.<prefix>_<device_id>_temperature` (Battery temperature)
     - `sensor.<prefix>_<device_id>_health` (Battery health)
     - `sensor.<prefix>_<device_id>_last_update` (Timestamp of last sync)

---

## 4. HTTP Webhooks Setup

1. **Enable Webhooks:** Toggle the **Webhooks** master switch in **Settings > Webhooks**.
2. **Configure Event Destinations:**
   - Select the event tab (e.g., `Battery Low`, `Battery High`).
   - Enter your target URL (HTTPS enforced by default).
   - If testing on a local network over plain HTTP (e.g., `http://192.168.1.50:8123/...`), toggle **"Allow Insecure HTTP"** and acknowledge the warning.
   - Add any required headers (e.g., `Authorization: Bearer <token>`).
   - Tap **"Save Event Configuration"**.
3. **Test Delivery:**
   - Tap **"Test Delivery"**. BatMon will dispatch a live test event and record the result in **Delivery Queue & History**.

---

## 5. Smart-Plug Charging Automation Setup

1. Copy [`homeassistant/charging_automation.yaml`](homeassistant/charging_automation.yaml) into your Home Assistant `automations.yaml` (or create via UI).
2. Replace entity placeholders:
   - `switch.smart_plug_charger_placeholder` with your actual smart plug entity.
   - `sensor.batmon_my_device_battery` with your device's battery sensor entity.
3. Verify operation:
   - When battery falls to ≤ 45%, the automation turns the smart plug ON (if currently OFF).
   - When battery charges to ≥ 80%, the automation turns the smart plug OFF (if currently ON).

---

## 6. Troubleshooting Common Issues

### A. Notifications Not Appearing
- **Check Android 13+ Runtime Permission:** Verify notifications are permitted in Android Settings > Apps > BatMon > Notifications.
- **Notification Channels:** Ensure the `Low Battery Alerts` and `High Battery Alerts` channels are not set to silent or blocked.
- **Threshold State:** Notifications only fire when a threshold is newly crossed. If the battery is already below 45% on startup, it re-arms only after charging above 45%.

### B. Background Updates Delayed or Stopping
- **Doze Mode (Low-Power Mode):** In default low-power mode, Android Doze may delay background WorkManager tasks by up to 30–60 minutes. Switch to **Real-Time Mode** if instantaneous detection is required.
- **Force-Stop Limitation:** If BatMon is force-stopped (by user or OS task killer), Android terminates all background workers and broadcast receivers until the user opens BatMon again manually.
- **OEM Battery Killers (Samsung, Xiaomi, Huawei, OnePlus):**
  - Disable "Put unused apps to sleep".
  - Disable manufacturer "Battery Saver" / "Auto-start restrictions" for BatMon.
  - Visit [dontkillmyapp.com](https://dontkillmyapp.com) for device-specific instructions.

### C. MQTT Connection Fails
- **Authentication Failure (`NOT_AUTHORIZED` / code 4 or 5):**
  - **Password Visibility:** Tap the eye icon next to the Password field in Settings to visually confirm the typed password matches your intended password (no autocorrected or missing characters).
  - **Reason Code Details:** BatMon's connection test displays the broker return code and sent credentials summary:
    - **rc 4:** Bad username or password. The broker checked the password file and the password did not match.
    - **rc 5:** Not authorised. Mosquitto returns rc 5 when the username is missing from `password_file`, when the user is not permitted by `acl_file`, or when anonymous access is disallowed.
    - **Probe Verdict:** If BatMon receives rc 4/5, it tests whether the broker permits anonymous connections:
      - `Probe: broker permits anonymous`: The broker accepts connections without credentials. The configured username is missing from the broker's password file, or credentials are not required.
      - `Probe: anonymous also refused`: The broker enforces authentication (`allow_anonymous false`) and rejected the credentials.
  - **Mosquitto Docker Container Troubleshooting:**
    ```bash
    # 1. Inspect live broker logs to see why the client was refused:
    docker logs <mosquitto-container-name> --tail 50 | grep -i "authorised"

    # 2. Add or update user in Mosquitto's password file:
    docker exec -it <mosquitto-container-name> mosquitto_passwd -b /mosquitto/config/pwfile <username> <password>

    # 3. Reload Mosquitto to apply password file changes without downtime:
    docker kill -s HUP <mosquitto-container-name>

    # 4. Verify credentials from the command line:
    mosquitto_sub -h 192.168.1.15 -p 1883 -u <username> -P <password> -t "batmon_#" -d
    ```
  - **Whitespace:** BatMon uses `trimBlanks()` (mirroring Mosquitto's `misc__trimblanks`) to strip only ASCII space and tab, preserving any intended Unicode characters.
- **Unreachable Broker (`Cannot reach <host>:<port>`):**
  - Ensure you specify the broker's LAN IP (e.g., `192.168.1.15`) or reachable hostname. Do not use `localhost` (which refers to the Android device itself).
  - Verify your Android device is on the same local network / Wi-Fi subnet and that port 1883 is open in the broker host's firewall.
- **Client ID:** BatMon automatically generates a clean client ID `mqtt-<topicPrefix>-<deviceShortId>` to prevent broker client ID collisions.

### D. Smart Plug Not Switching
- **Stale Data Safety Rejection:** If the battery sensor has not updated in > 30 minutes, the Home Assistant automation deliberately refuses to switch the plug to prevent improper charging based on stale data.
- **Already in Target State:** If the plug is already ON, the automation intentionally avoids sending redundant ON commands.
- **Manual Override:** Check if `input_boolean.batmon_manual_override` is ON.

### E. Webhook Delivery Failures
- **Insecure HTTP Blocked:** BatMon rejects `http://` URLs unless **Allow Insecure HTTP** is explicitly enabled in Settings.
- **Server 4xx Errors:** Permanent client errors (400, 401, 404) are not retried indefinitely. Check your webhook endpoint URL and authentication headers.
- **Offline / Transient Errors (5xx, Timeouts):** BatMon places failed requests into the durable queue and automatically retries with exponential backoff once connectivity is restored.
- **Malformed Custom Header:** Header names cannot contain spaces (use `Content-Type`, not `Content Type`). An invalid header is reported as `Invalid request: ...` and is not retried.
- **Error Shows an Exception Name Instead of "null":** Some Android network errors carry no message. BatMon now reports the exception type (e.g. `UnknownHostException`) so the cause is identifiable.
