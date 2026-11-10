# SmartCare — Medication Adherence and Emergency Response System

**An Android application that helps elderly users manage their medication schedules while enabling family members and caregivers to stay informed about medication-related activities and emergencies.**

SmartCare brings medication scheduling, reminders, missed-dose escalation, adherence tracking, AI-assisted blister-pack scanning, expiry-date recognition, SOS assistance, and nearby pharmacy discovery into one connected application.

> **Project Type:** Academic Major Project  
> **Platform:** Android  
> **Development Period:** 2026–2027  
> **Status:** Academic prototype

---

## Overview

Managing multiple medicines and remembering their schedules can be challenging for elderly individuals. Family members may also find it difficult to monitor medication-related activities when they are not physically present.

SmartCare addresses these challenges through two connected user roles:

- **Elder:** Views assigned medicines, receives reminders, acknowledges doses, and accesses assistance features.
- **Guardian:** Connects with an Elder, manages medication schedules, reviews adherence reports, and receives relevant alerts.

The application uses Android services, cloud backend integration, push notifications, and on-device AI components to support these workflows.

## Key Features

### 1. Elder–Guardian Account Pairing
- Guardian generates a pairing code.
- Elder uses the code to connect their account.
- The paired relationship supports medication management and caregiver monitoring.

### 2. Medication Management
- Guardian can add and manage medication details and schedules for the paired Elder.
- Elder can view assigned medicines and their scheduled timings.
- Medication information is stored and retrieved through the backend.

### 3. Scheduled Medication Reminders
- Uses Android `AlarmManager` to schedule medication reminders.
- Notifies the Elder when a dose is due.
- Elder can acknowledge a reminder using the **Taken** action.
- Acknowledgements are recorded for medication activity and adherence reporting.

### 4. Multi-Stage Missed-Dose Escalation
SmartCare uses a staged escalation workflow when a scheduled dose remains unacknowledged.

| Stage | Intended timing | Action |
|---|---:|---|
| Initial reminder | T+0 | Notify the Elder |
| First checkpoint | T+5 min | Check acknowledgement |
| Second checkpoint | T+10 min | Continue escalation if unacknowledged |
| Final checkpoint | T+15 min | Trigger the final configured stage |

The interval was temporarily reduced during development to test the workflow more quickly. The application uses scheduled Android events and a backend notification workflow to support guardian alerts.

### 5. Medication Adherence Reports
- Records medication acknowledgement activity.
- Provides reports for reviewing medication activity over selected dates.
- Helps guardians identify missed or unacknowledged doses.

**Note:** A Taken acknowledgement records the user's action; it does not independently verify that the medicine was consumed.

### 6. AI-Based Blister-Pack Scanning
SmartCare integrates an object-detection model that runs on the Android device using ONNX Runtime.

The model identifies three classes:

- `filled_tablet` — a pocket classified as containing a tablet.
- `empty_pocket` — an empty blister-pack pocket.
- `broken_tablet` — a region classified as a broken tablet.

The scanning workflow displays detection results and supports comparison between AI-generated counts and user-confirmed counts. Verification information can be recorded for later review.

**Model details:**

| Property | Value |
|---|---|
| Format | ONNX |
| Runtime | ONNX Runtime |
| Input tensor | `images` |
| Input shape | `1 × 3 × 640 × 640` |
| Output shape | `1 × 7 × 8400` |
| Input format | RGB |

Model files included in the repository:

- `app/src/main/assets/best.onnx`

### 7. Expiry-Date Recognition
The expiry-date scanning workflow combines image processing and text recognition to assist users in identifying medicine expiry dates.

- Detects the expiry-date region using an ONNX model.
- Uses Google ML Kit Text Recognition to read visible text.
- Extracts a possible expiry date.
- Allows the Elder to review and correct the recognised date.
- Updates the medication expiry information through the repository/backend integration.

Model file:

- `app/src/main/assets/expiry_date_detector.onnx`

### 8. SOS Emergency Assistance
- Provides an SOS feature for requesting assistance.
- Uses Google Fused Location Provider to obtain device location.
- Integrates location information into the emergency notification workflow.

### 9. Nearby Pharmacy Discovery
- Uses the device's location to search for nearby pharmacies.
- Integrates the Geoapify Places API.
- Displays matching pharmacy results to help users find nearby services.

### 10. Medication Supply Alerts
Includes medication-related low-stock checking and notification functionality to help users and guardians identify supplies that may need attention.

---

## Application Workflow

```text
             GUARDIAN
                 |
       Register / Generate Code
                 |
                 v
          ELDER–GUARDIAN
              PAIRING
                 |
                 v
       Manage Medication Schedule
                 |
                 v
         Backend Data Storage
                 |
                 v
         Schedule Reminders
                 |
                 v
          ELDER RECEIVES
             REMINDER
                 |
          +------+------+
          |             |
        TAKEN       NOT ACKNOWLEDGED
          |             |
          v             v
    Record Dose      Escalation
    Acknowledgement  Workflow
          |             |
          v             v
    Adherence       Guardian Alert
      Report
```

Additional features such as blister-pack scanning, expiry-date recognition, SOS assistance, and pharmacy discovery are available through the application.

---

## Technology Stack

| Category | Technologies |
|---|---|
| Android Development | Kotlin, Android Studio |
| UI Development | Jetpack Compose |
| Android Scheduling | AlarmManager, WorkManager |
| Background Services | Android Services, Broadcast Receivers |
| Backend | Supabase |
| Database | PostgreSQL |
| Authentication | Supabase Auth |
| Server-side Processing | Supabase Edge Functions |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| AI Model Format | ONNX |
| AI Runtime | ONNX Runtime |
| Object Detection | YOLO-based model |
| Text Recognition | Google ML Kit |
| Location Services | Google Fused Location Provider |
| Pharmacy Search | Geoapify Places API |
| API Testing | Postman |
| Version Control | Git and GitHub |

---

## Project Structure

The repository follows a standard Android application structure.

```text
SmartCare-MedicationApp/
│
├── app/
│   ├── build.gradle.kts
│   ├── google-services.json
│   │
│   └── src/
│       ├── main/
│       │   ├── assets/
│       │   │   ├── best.onnx
│       │   │   └── expiry_date_detector.onnx
│       │   │
│       │   ├── java/com/yourteam/smartcare/
│       │   │   ├── api/
│       │   │   ├── data/
│       │   │   ├── emergency/
│       │   │   ├── escalation/
│       │   │   ├── notifications/
│       │   │   ├── repositories/
│       │   │   ├── ui/
│       │   │   └── utils/
│       │   │
│       │   ├── res/
│       │   └── AndroidManifest.xml
│       │
│       ├── androidTest/
│       └── test/
│
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── test_yolo_images.py
└── README.md
```

**Important package responsibilities:**

| Package | Responsibility |
|---|---|
| `api/` | API clients, notification integration, and reminder-related components |
| `data/` | Data models, repositories, and backend access |
| `emergency/` | Emergency services, location handling, and related workers |
| `escalation/` | Dose tracking, scheduling, acknowledgement, and escalation logic |
| `notifications/` | Notification services, receivers, and notification utilities |
| `repositories/` | Medication, pharmacy, and verification data operations |
| `ui/` | Application screens, navigation, and user-interface logic |
| `utils/` | Shared application configuration and utilities |

---

## Getting Started

### Prerequisites

- Android Studio
- JDK 21
- Android SDK compatible with the project configuration
- Git
- An Android device or emulator
- Supabase project and required backend configuration
- Firebase project configuration for push notifications
- Geoapify API key for pharmacy discovery

### 1. Clone the repository

```bash
git clone https://github.com/PratikshaShetty-10/SmartCare-MedicationApp.git
```

Move into the project directory:

```bash
cd SmartCare-MedicationApp
```

### 2. Open the project

1. Open Android Studio.
2. Select **Open**.
3. Choose the cloned project folder.
4. Allow Gradle synchronisation to complete.
5. Install any required Android SDK components.

### 3. Configure backend services

Set up the Supabase project and configure the application with the required Supabase URL and client key using the configuration approach expected by the source code.

The corresponding database schema, authentication settings, access policies, and Edge Functions must also be configured separately.

### 4. Configure Firebase

Configure Firebase for the Android application and ensure Firebase Cloud Messaging is set up for the registered application.

The repository includes `app/google-services.json`, which contains Firebase Android client configuration. Use your own Firebase project configuration if setting up a separate environment.

**Never add Firebase Admin service-account private keys or other server credentials to the repository.** Server-side credentials should be configured securely in the backend environment.

### 5. Configure Geoapify

Obtain a Geoapify API key and provide it through the project's expected local secret configuration.

Do not commit private API keys or local secret files.

### 6. Build and run

On Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

On macOS/Linux:

```bash
./gradlew :app:assembleDebug
```

After a successful build, run the application on a compatible Android device or emulator through Android Studio.

Grant the permissions required for the features being used, such as notifications, camera, and location.

---

## Testing and Validation

Development testing covered the major application workflows and integrations, including:

- User authentication and Elder–Guardian pairing.
- Medication creation and schedule management.
- Scheduled medication reminders.
- Multi-stage missed-dose escalation.
- Dose acknowledgement and cancellation of subsequent escalation actions.
- Guardian notification integration.
- Medication adherence reporting.
- ONNX model integration and inference.
- Blister-pack detection and verification workflow.
- Expiry-date recognition and medication expiry updates.
- Geoapify pharmacy search and result retrieval.

The project was also built successfully using the Gradle debug build task during development.

Testing was performed as part of the academic prototype. It does not represent clinical validation or guarantee identical behaviour on every Android device.

---

## Limitations and Considerations

- **Medication acknowledgement:** The Taken action records user acknowledgement, not confirmed medicine consumption.
- **AI predictions:** Blister-pack detection can be affected by lighting, image quality, camera angle, and packaging variations.
- **Expiry-date recognition:** OCR may misread unclear or differently formatted dates. User verification is required.
- **Notifications:** Android background restrictions, battery optimisation, device restarts, permissions, and network availability can affect delivery.
- **Location:** SOS and pharmacy discovery depend on location permissions and location-service availability.
- **Backend dependency:** Authentication, data synchronisation, and push notification workflows depend on correctly configured backend services.

SmartCare is an academic prototype, not a certified medical device. It must not replace professional medical advice, medical supervision, or emergency services.

---

## Future Enhancements

Potential improvements include:

- More comprehensive testing across Android versions and devices.
- Stronger recovery mechanisms for scheduled reminders after device restarts.
- More configurable caregiver escalation preferences.
- Improved AI model accuracy using more diverse blister-pack images.
- Better expiry-date extraction and validation.
- Enhanced medication inventory tracking.
- More detailed adherence analytics.
- Additional accessibility improvements for elderly users.
- Further backend security and access-control hardening.

---

## Project Team and Acknowledgements

SmartCare was developed as a collaborative academic major project.

**Institution:** Canara Engineering College  
**Department:** Computer Science and Engineering  
**Academic Year:** 2026–2027

**Project Team**

- Maya Kamath
- Meghana Madev Naik
- N S Suhani
- Prathiksha Shetty

**Project Guide:** Ms. Saritha M  
**Project Mentor:** Mr. Praharsh T

We acknowledge the guidance and support provided by our project guide and mentor throughout the development of SmartCare, along with the collaborative contributions of all team members.

---

## Usage and Copyright

Copyright © 2026 SmartCare Project Team.

This repository contains an academic project developed collaboratively by its credited student contributors. No open-source licence is granted for the original project materials. Reuse, modification, redistribution, or commercial use of those materials requires permission from the respective copyright holders.

Third-party frameworks, libraries, APIs, and tools remain subject to their respective licences and terms.

---

**SmartCare — Making medication management more connected, organised, and accessible.**
