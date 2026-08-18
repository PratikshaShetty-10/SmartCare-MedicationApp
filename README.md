# 🚨 SmartCare: Alerts, Notifications, and Emergency SOS Module

Welcome to the **Alerts, Notifications, and Emergency SOS** module of the SmartCare project. This document provides a complete technical and functional overview of the module, explaining how every component is built, why it was introduced, and how it ensures 100% reliability for elderly users and their guardians.

---

## 🏥 1. Real-World Scenario: How It Works in Action

To understand the core design of this module, consider a real-world health and safety scenario:
* **The Elder:** Grandpa Ramesh, who needs to take his heart medication at 1:00 PM.
* **The Guardian:** His daughter, Priya, who monitors his safety remotely.

When 1:00 PM arrives, Grandpa Ramesh is resting and leaves his phone on the table. Instead of a missed dose turning into a medical crisis, the app executes a fail-safe lifecycle:
1. **Proactive Reminder:** The app detects the scheduled medication time and rings a high-priority alert.
2. **Sensory Escalation:** Because he doesn’t respond, the app escalates automatically—speaking out loud using a text-to-speech engine and vibrating the device.
3. **Emergency Alarm & Tracking:** If ignored for a prolonged period, it forces an emergency screen open while continuously tracking his precise location via GPS.
4. **Dual-Channel Guardian Alert:** The app automatically notifies Priya over the internet (via Supabase Push Notifications) *and* over the mobile carrier network (via direct Cellular SMS) with a clickable Google Maps link, ensuring she can find him immediately even if data connectivity fails.

---

## 🛠️ 2. Technical Specifications & Feature Breakdown

Every feature in this module was designed to solve specific Android operating system limitations and healthcare safety requirements:

| Feature / Component | Technical Implementation & Files Used | How It Works Technically | Why It Was Introduced (The "Why") |
| :--- | :--- | :--- | :--- |
| **Medication Reminders** | `ReminderWorker.kt`<br>`NotificationHelper.kt` | Powered by Android **WorkManager** and `CoroutineWorker`. It periodically queries Supabase database tables to check medication schedules and fire high-priority notification channels. | Ensures background reliability; tasks execute and survive even if the app is closed or the phone is rebooted. |
| **Sensory Escalation** | `EscalationManager.kt`<br>`VoiceHelper.kt`<br>`VibrationHelper.kt` | Uses delayed coroutines (`delay(300_000)`) to tier alerts. It triggers Android's **Text-to-Speech (TTS)** engine with safe queue handling and modern API-safe haptic patterns (`VibratorManager`). | Accommodates elderly users who may have impaired vision or hearing by adding auditory and physical sensory feedback. |
| **Emergency SOS & Full-Screen Alarm** | `NotificationHelper.kt`<br>`EscalationWorker.kt` | Builds a maximum-priority notification (`NotificationCompat.PRIORITY_MAX`) using a **Full-Screen Intent** (`PendingIntent`) targeting `MainActivity`. | Guarantees that a critical medical alert breaks through background layers and forces the screen awake. |
| **Real-Time GPS Geolocation** | `GpsTrackingService.kt` | Utilizes Google Play Services **`FusedLocationProviderClient`** inside a persistent foreground service to capture high-accuracy latitude and longitude coordinates every 5 seconds, upserting them directly to Supabase (`user_locations`). | Delivers sub-meter accuracy without excessive battery drain, keeping live coordinates ready instantly for emergencies. |
| **Dual-Channel Fail-Safe Delivery** | `GuardianNotifier.kt`<br>`NotificationClient.kt` | Combines cloud delivery via **Supabase Edge Functions** (`send-alert`) with an Android native **`SmsManager`** fallback that dynamically queries guardian phone numbers from database profiles. | Eliminates single points of failure. If internet/data fails during a crisis, the cellular text message fallback guarantees alert delivery. |

---

## 📁 3. Architecture & File Structure

The module is structured cleanly into logical packages separating business logic, utilities, and API communications:

```text
com.yourteam.smartcare/
│
├── api/
│   └── GuardianNotifier.kt      # Handles Supabase Push Functions & Cellular SMS Fallback
│
├── emergency/
│   └── services/
│       ├── EscalationManager.kt  # Orchestrates multi-tier reminder & emergency sequences
│       └── GpsTrackingService.kt # Foreground service for real-time Fused Location tracking
│
├── notifications/
│   ├── services/
│       ├── FcmService.kt         # Firebase Cloud Messaging receiver for remote alerts
│       └── NotificationService.kt # Core notification state and batch utility service
│   └── workers/
│       └── StockCheckerWorker.kt # Background worker for 7-day/3-day medication refill warnings
│
└── utils/
    ├── NotificationHelper.kt     # Single-source-of-truth for channels, reminders & alarms
    ├── VibrationHelper.kt        # Cross-version haptic/vibration controller
    └── VoiceHelper.kt            # Safe Text-to-Speech manager with asynchronous queueing