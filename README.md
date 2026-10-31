# SmartCare — Medication Adherence and Emergency Response System

**An Android-based healthcare assistance application designed to support elderly people with medication management and help family members stay informed about their well-being.**

---

## Table of Contents

1. [Introduction](#1-introduction)
2. [Problem Statement](#2-problem-statement)
3. [Project Objectives](#3-project-objectives)
4. [Understanding SmartCare](#4-understanding-smartcare)
5. [User Roles](#5-user-roles)
6. [Features and Functionalities](#6-features-and-functionalities)
7. [Medication Management Workflow](#7-medication-management-workflow)
8. [Missed-Dose Escalation System](#8-missed-dose-escalation-system)
9. [AI-Based Blister-Pack Detection](#9-ai-based-blister-pack-detection)
10. [Expiry-Date Recognition](#10-expiry-date-recognition)
11. [SOS and Location Assistance](#11-sos-and-location-assistance)
12. [Nearby Pharmacy Discovery](#12-nearby-pharmacy-discovery)
13. [Technology Stack](#13-technology-stack)
14. [Backend and Service Integration](#14-backend-and-service-integration)
15. [Testing and Validation](#15-testing-and-validation)
16. [Project Setup](#16-project-setup)
17. [Project Limitations](#17-project-limitations)
18. [Future Enhancements](#18-future-enhancements)
19. [Project Team and Acknowledgements](#19-project-team-and-acknowledgements)
20. [Copyright and Usage Rights](#20-copyright-and-usage-rights)
21. [Disclaimer](#21-disclaimer)

---

## 1. Introduction

Managing medication regularly can become challenging for elderly people, particularly when they need to follow multiple prescriptions, remember different dosage timings and keep track of their medication supplies.

Family members and caregivers may also find it difficult to monitor whether an elderly person is following their medication schedule, especially when they are not physically present.

**SmartCare — Medication Adherence and Emergency Response System** is an Android application developed to address these challenges by bringing medication management, caregiver coordination, reminders and emergency assistance together in one platform.

The application connects elderly users with their guardians or caregivers. It provides medication scheduling, scheduled reminders, missed-dose notifications, adherence monitoring, AI-assisted blister-pack detection, expiry-date recognition, SOS assistance and nearby pharmacy discovery.

The main objective is to make everyday medication management more organised while helping guardians remain informed about important medication-related events.

SmartCare combines Android application functionality with cloud-based backend services and AI-related integrations to provide a connected healthcare assistance experience.

## 2. Problem Statement

Elderly people who regularly take medication may face several challenges:

- Forgetting medication timings.
- Managing multiple medicines with different schedules.
- Difficulty keeping track of missed doses.
- Not knowing when medication supplies are running low.
- Difficulty identifying the condition of medicine blister packs.
- Forgetting or being unable to check medication expiry dates.
- Limited access to immediate assistance during emergencies.
- Difficulty finding nearby pharmacies when medication is required.

At the same time, family members and caregivers may not always be available to monitor these situations personally.

Traditional reminders alone may not be sufficient because they do not necessarily provide caregivers with information about missed doses or other important events.

SmartCare aims to address these challenges through a connected application that supports both elderly users and their guardians.

## 3. Project Objectives

The primary objectives of SmartCare are:

1. **Medication Management:** Provide a structured way for guardians to add and manage medication details and schedules.
2. **Timely Reminders:** Help elderly users remember their medication timings through scheduled notifications.
3. **Missed-Dose Monitoring:** Identify unacknowledged medication reminders and initiate an escalation workflow.
4. **Guardian Notifications:** Keep guardians informed about missed medication events through push notifications.
5. **Adherence Monitoring:** Provide medication activity information through adherence reports.
6. **AI-Assisted Detection:** Integrate an image-based blister-pack detection model to identify different blister-pack conditions.
7. **Expiry-Date Recognition:** Use text recognition to assist with identifying medicine expiry dates.
8. **Emergency Assistance:** Provide an SOS feature with location-related information.
9. **Pharmacy Discovery:** Help users find nearby pharmacies using location-based search.

## 4. Understanding SmartCare

SmartCare is designed around two connected user experiences:

- **Elder:** The person who follows the medication schedule and uses the application for reminders, dose acknowledgement and assistance.
- **Guardian:** The family member or caregiver who manages medication information and monitors relevant activities.

The application uses a pairing mechanism to connect an Elder account with a Guardian account.

Once connected, the Guardian can manage medication schedules, while the Elder can view the assigned medicines and acknowledge medication reminders.

The application also supports additional functionality such as adherence reports, blister-pack scanning, expiry-date recognition, SOS assistance and pharmacy discovery.

The central idea is to make medication-related information accessible to both users while keeping their responsibilities clear.

## 5. User Roles

### 5.1 Elder

The Elder interface focuses on making everyday medication activities simple and accessible.

The Elder can:

- View assigned medications.
- Check medication names and schedules.
- Receive scheduled medication reminders.
- Acknowledge a medication reminder using the **Taken** action.
- Access medication-related scanning functionality.
- Use the SOS feature when assistance is needed.
- Discover nearby pharmacies.

The Taken action records the user's acknowledgement of a medication reminder. It does not independently verify that the medicine was physically consumed.

### 5.2 Guardian

The Guardian interface focuses on medication management and monitoring.

The Guardian can:

- Register and access the application.
- Generate a pairing code to connect with an Elder.
- Manage medication information for the paired Elder.
- Add and maintain medication schedules.
- Monitor medication activity.
- View adherence reports.
- Receive notifications about missed medication events.
- Stay informed about relevant medication-related alerts.

### 5.3 Elder–Guardian Pairing

SmartCare uses a pairing-code mechanism to establish a connection between the Elder and Guardian.

The general workflow is:

1. The Guardian generates a pairing code.
2. The Elder enters the generated code.
3. The application validates the pairing request.
4. Once successfully paired, the accounts are connected.
5. The Guardian can manage medication information for the paired Elder.

This connection forms the basis for medication management and caregiver monitoring.

## 6. Features and Functionalities

### 6.1 User Authentication

SmartCare provides an authentication mechanism for users to access their respective application experiences.

Supabase is used as part of the backend authentication and data-management infrastructure.

The application distinguishes between Elder and Guardian functionality so that users can access the features associated with their roles.

### 6.2 Medication Management

Medication management is one of the core functionalities of SmartCare.

The Guardian can add medication information and configure schedules for the paired Elder.

Medication information can include:

- Medication name.
- Dosage information.
- Scheduled medication timings.
- Relevant medication details.
- Expiry-date information where available.

The Elder can view the assigned medication information and follow the configured schedule.

### 6.3 Scheduled Medication Reminders

SmartCare uses Android's **AlarmManager** to schedule medication reminders.

When a medication reminder is due, the application triggers a notification to inform the Elder that it is time to take the scheduled medicine.

The Elder can acknowledge the reminder using the Taken action.

This acknowledgement is recorded in the application's medication activity and can be used for adherence monitoring.

### 6.4 Medication Adherence Reports

SmartCare includes an adherence-reporting feature to help guardians understand the Elder's medication activity.

The report uses recorded medication information and acknowledgement history to present adherence-related details.

It helps guardians review medication activity over a selected period and identify patterns that may require attention.

An acknowledgement is treated as a recorded user action, not as independent proof of medicine consumption.

### 6.5 Guardian Notifications

The application integrates Supabase Edge Functions and Firebase Cloud Messaging (FCM) to support notification delivery to guardians.

When relevant medication-related events occur, the backend notification workflow can process the event and send a push notification.

This helps guardians remain informed without having to continuously open the application.

### 6.6 Medication Supply and Low-Stock Alerts

SmartCare includes medication-related low-stock notification functionality to help guardians remain aware of medication supply concerns.

The purpose is to make it easier to identify when a medicine may need attention or replenishment.

### 6.7 AI-Based Blister-Pack Scanning

SmartCare integrates an AI-based blister-pack detection model to assist with identifying the condition of medicine blister packs.

The integrated model is exported in ONNX format and runs through ONNX Runtime.

The model identifies three classes:

- `broken_tablet`
- `empty_pocket`
- `filled_tablet`

This functionality is intended to provide additional information during medication management.

The detection result is an AI-generated prediction and should not be treated as a medically verified assessment.

### 6.8 Expiry-Date Recognition

SmartCare integrates Google ML Kit Text Recognition to assist with reading text from medicine packaging.

The general process is:

1. The user accesses the expiry-date scanning functionality.
2. Text is recognised from the available medicine-packaging image.
3. The recognised text is processed to identify a possible expiry date.
4. The application uses the identified date in its medication-expiry workflow.
5. The expiry information can be updated through the application's backend integration.

OCR results may require user verification because packaging text, image quality and date formats can affect recognition accuracy.

### 6.9 SOS Emergency Assistance

SmartCare provides an SOS feature intended to help users request assistance during an emergency.

The application integrates location-related functionality so that relevant location information can be used as part of the emergency workflow.

Google Fused Location Provider is used to obtain device location information.

The purpose is to make location-related assistance more accessible when the user needs help.

### 6.10 Nearby Pharmacy Discovery

The application integrates the Geoapify Places API to support nearby pharmacy discovery.

Using location information, the application can retrieve nearby pharmacy results and display relevant information to the user.

The general workflow is:

1. The application obtains the user's location.
2. The location is used to search for nearby pharmacy places.
3. Geoapify returns matching place information.
4. The application processes and displays the pharmacy results.

This feature helps users identify pharmacies near their current location.

## 7. Medication Management Workflow

The medication workflow connects the Guardian's medication management activities with the Elder's daily medication routine.

**Step 1 — Guardian manages medication**

The Guardian adds medication details and configures the required schedules for the paired Elder.

**Step 2 — Medication information becomes available**

The application stores and retrieves the relevant medication information through its backend integration.

**Step 3 — Reminders are scheduled**

The application uses Android AlarmManager to schedule reminders according to the configured medication timings.

**Step 4 — Elder receives a reminder**

When the scheduled time arrives, the Elder receives a medication notification.

**Step 5 — Elder acknowledges the reminder**

The Elder can select Taken to record the acknowledgement.

**Step 6 — Medication activity is recorded**

The acknowledgement is reflected in the application's medication activity and can contribute to adherence reporting.

**Step 7 — Unacknowledged reminders enter the escalation workflow**

If a scheduled dose remains unacknowledged, the application's missed-dose escalation process can initiate the configured notification workflow.

## 8. Missed-Dose Escalation System

A key functionality of SmartCare is its multi-stage missed-dose escalation system.

A normal medication reminder informs the Elder that a medicine is due. However, if the reminder remains unacknowledged, the application is designed to initiate additional escalation stages.

The intended escalation timeline is:

| Stage | Time | Purpose |
|---|---|---|
| Initial reminder | T+0 | Notify the Elder that medication is due. |
| First checkpoint | T+5 minutes | Check whether the dose has been acknowledged. |
| Second checkpoint | T+10 minutes | Continue the escalation process if still unacknowledged. |
| Final checkpoint | T+15 minutes | Reach the final configured escalation stage. |

The timeline represents the intended configuration. During development and testing, the checkpoint interval was temporarily reduced to two minutes to make it easier to verify the workflow.

### How the escalation workflow works

1. A medication reminder is scheduled for the configured time.
2. The Elder receives the initial reminder.
3. The application checks the medication acknowledgement status.
4. If the dose remains unacknowledged, the escalation process continues through its configured checkpoints.
5. Relevant missed-dose events are processed through the backend notification workflow.
6. Supabase Edge Functions and Firebase Cloud Messaging support the guardian notification process.

The purpose of this system is to provide more than a single reminder and help guardians become aware of potentially missed medication events.

The workflow depends on device scheduling, application execution, network availability and notification permissions. It should not be interpreted as a guarantee of uninterrupted notification delivery.

## 9. AI-Based Blister-Pack Detection

SmartCare integrates a trained object-detection model to identify different conditions of medicine blister packs.

The model was developed as a separate AI component and integrated into the Android application.

### Model integration details

| Property | Details |
|---|---|
| Model format | ONNX |
| Runtime | ONNX Runtime |
| Input name | `images` |
| Input shape | `1 × 3 × 640 × 640` |
| Input format | RGB image tensor |
| Output shape | `1 × 7 × 8400` |
| ONNX IR version | 8 |
| ONNX opset | 18 |

### Detection classes

| Class | Description |
|---|---|
| `broken_tablet` | Identifies a blister-pack region classified as broken tablet. |
| `empty_pocket` | Identifies an empty blister-pack pocket. |
| `filled_tablet` | Identifies a blister-pack pocket classified as containing a tablet. |

The Android application integrates the exported model using ONNX Runtime so that the model can be used as part of the scanning workflow.

### Important note about AI predictions

The model provides image-based predictions. Lighting, image quality, camera angle, packaging condition and other factors may influence the result.

The detection output is intended as an assistance feature. It is not a replacement for manual verification or professional medical advice.

## 10. Expiry-Date Recognition

SmartCare includes an expiry-date recognition workflow using Google ML Kit Text Recognition.

The purpose is to reduce the effort required to manually enter expiry information from medicine packaging.

The application uses OCR to recognise text from an image and processes the recognised information as part of the expiry-date workflow.

The recognised date is used in the application's medication-expiry update process.

### Technology used

- Google ML Kit Text Recognition.
- Android application integration.
- Backend integration for updating medication expiry information.

### Limitations

OCR accuracy can depend on:

- Image clarity.
- Lighting conditions.
- Text size and printing quality.
- Packaging design.
- Expiry-date format.

Therefore, recognised expiry information should be checked before being relied upon.

## 11. SOS and Location Assistance

Emergency assistance is another important part of SmartCare.

The SOS feature is intended to help the Elder request assistance when required.

The application uses Google Fused Location Provider for device-location functionality.

Location information is used as part of the application's emergency-related workflow.

The feature is designed to make it easier to associate an assistance request with relevant location information.

Location availability depends on device permissions, location settings, connectivity and the device's ability to obtain a location fix.

## 12. Nearby Pharmacy Discovery

SmartCare provides nearby pharmacy discovery through the Geoapify Places API.

The feature uses location-based search to identify pharmacies around the user's current location.

### Technology used

- Google Fused Location Provider.
- Geoapify Places API.
- Android networking and application integration.

### Pharmacy discovery workflow

1. The application obtains the user's location.
2. The location coordinates are used to perform a nearby-place search.
3. Geoapify returns matching pharmacy information.
4. The application processes the response.
5. The pharmacy results are displayed to the user.

During development, the integration was tested successfully, and the application received pharmacy results from the API.

The feature helps users discover nearby pharmacies without having to search manually.

## 13. Technology Stack

SmartCare combines Android development, backend services, cloud messaging, AI model integration and location-based APIs.

### Android Application

| Technology | Purpose |
|---|---|
| Kotlin | Primary Android application programming language. |
| Android Studio | Android development environment. |
| Jetpack Compose | Android user-interface development. |
| Android AlarmManager | Scheduling medication reminders and timed events. |
| Google Fused Location Provider | Obtaining device location information. |

### Backend and Cloud Services

| Technology | Purpose |
|---|---|
| Supabase | Backend platform and application data services. |
| Supabase Auth | User authentication. |
| PostgreSQL | Relational database used through Supabase. |
| Supabase Edge Functions | Backend event processing and notification workflow. |
| Firebase Cloud Messaging | Push notification delivery. |

### AI and Text Recognition

| Technology | Purpose |
|---|---|
| ONNX | Model exchange and deployment format. |
| ONNX Runtime | Running the integrated blister-pack detection model. |
| YOLO-based object detection | Blister-pack condition detection. |
| Google ML Kit Text Recognition | Recognising expiry-related text from medicine packaging. |

### External APIs and Development Tools

| Technology | Purpose |
|---|---|
| Geoapify Places API | Nearby pharmacy discovery. |
| Postman | API testing and request validation. |
| Git | Version control. |
| GitHub | Source-code hosting and collaboration. |

## 14. Backend and Service Integration

SmartCare connects several application components and external services to support its core workflows.

### Supabase

Supabase provides backend services used by the application, including authentication and PostgreSQL-backed data access.

It supports the storage and retrieval of application information required for medication management and related features.

### Supabase Edge Functions

Supabase Edge Functions are used for backend-side processing associated with the notification workflow.

They help process relevant application events and support the missed-dose guardian notification process.

### Firebase Cloud Messaging

Firebase Cloud Messaging is integrated to support push notifications.

It is used as part of the process of delivering relevant notifications to guardians.

### Android AlarmManager

AlarmManager is used for scheduling medication reminders and timed application events on Android devices.

Its behaviour can be affected by Android background-execution policies, battery restrictions, device restarts and notification permissions.

### External API Integration

Geoapify is used for nearby pharmacy discovery, while Google Fused Location Provider supplies device-location information.

These services allow SmartCare to connect application functionality with location-based information.

## 15. Testing and Validation

During development, individual components and application workflows were tested to verify their expected behaviour.

Testing activities included:

- Verifying user authentication and account access.
- Checking Elder–Guardian pairing.
- Testing medication management workflows.
- Verifying scheduled medication reminders.
- Testing the missed-dose escalation workflow with a reduced checkpoint interval.
- Testing API requests and responses using Postman.
- Verifying the backend notification integration.
- Validating the exported ONNX model.
- Checking the ONNX model input and output specifications.
- Testing the pharmacy API integration.
- Verifying that pharmacy results were successfully received and displayed.
- Testing the expiry-date recognition and update workflow.

### ONNX Model Validation

The exported model was validated successfully during development.

The validation confirmed:

- ONNX IR version: 8.
- ONNX opset: 18.
- Input tensor: `images`.
- Input shape: `[1, 3, 640, 640]`.
- Output shape: `[1, 7, 8400]`.

### Pharmacy API Testing

The Geoapify integration was tested successfully during development. The application received 20 pharmacy results in a successful API response.

Testing was performed as part of the academic prototype development process. It does not constitute clinical validation or a guarantee of reliability under every real-world device and network condition.

## 16. Project Setup

SmartCare is an Android application developed using Kotlin and Android Studio.

The following are the general requirements for setting up the project.

### Prerequisites

- Android Studio.
- A compatible Android SDK.
- JDK compatible with the project's Gradle configuration.
- Git.
- A Supabase project.
- Firebase project configuration for FCM.
- Geoapify API key for pharmacy discovery.

### Step 1 — Clone the repository

```bash
git clone https://github.com/PratikshaShetty-10/SmartCare-MedicationApp
```

Move into the downloaded project directory:

```bash
cd SmartCare-MedicationApp
```

### Step 2 — Open the project

1. Launch Android Studio.
2. Select **Open**.
3. Choose the cloned SmartCare project directory.
4. Allow Android Studio to synchronise the Gradle project.
5. Resolve any SDK or dependency prompts required by the project.

### Step 3 — Configure backend services

Configure the required Supabase project details using the configuration approach already defined in the source code.

Make sure the application has the appropriate backend URL and public client key.

The Supabase database schema, authentication configuration, access policies and Edge Functions must be configured according to the project's implementation.

### Step 4 — Configure Firebase

Set up the Firebase configuration required by the application.

Ensure that the Android application is registered with the appropriate Firebase project and that Firebase Cloud Messaging is configured.

### Step 5 — Configure Geoapify

Create or obtain a Geoapify API key and configure it using the project's existing secret-management approach.

Do not commit private API keys or secret configuration files to a public repository.

### Step 6 — Build and run

1. Connect a compatible Android device or start an emulator.
2. Allow Android Studio to complete Gradle synchronisation.
3. Build the application.
4. Run SmartCare on the selected device.
5. Grant the permissions required by the features being tested, including notifications and location access where applicable.

**Note:** The exact configuration filenames, database setup instructions and Gradle commands should follow the files included in the repository. Never place private service credentials in publicly accessible source files.

## 17. Project Limitations

SmartCare is an academic software prototype. Although its major functionalities have been implemented and tested during development, certain real-world conditions require additional work.

### Device and Notification Reliability

Android may restrict background execution depending on the device, battery settings and operating-system version.

Device restarts, notification permissions and battery optimisation may affect scheduled reminders and notification delivery.

### Network Dependency

Some application features depend on backend services and internet connectivity.

Temporary network failures may affect data synchronisation, backend processing, push notifications and API responses.

### AI Detection Accuracy

The blister-pack detection model may produce incorrect predictions under certain image conditions.

Its output should be treated as assistance rather than a definitive assessment.

### OCR Accuracy

Expiry-date recognition may be affected by image quality, packaging design, text visibility and date formats.

Manual verification is important before relying on the recognised date.

### Location Availability

SOS and nearby pharmacy functionality depend on location permissions and the availability of device-location services.

### Medical Reliability

A recorded Taken acknowledgement does not independently establish that a medicine was consumed.

SmartCare is not a clinically validated medical device and should not replace professional medical advice or supervision.

## 18. Future Enhancements

SmartCare can be extended further to improve reliability, usability and the overall caregiver experience.

Potential future enhancements include:

1. **Improved Notification Reliability:** Strengthen reminder scheduling and recovery mechanisms for device restarts and background restrictions.
2. **Advanced Escalation Controls:** Provide more configurable escalation stages and caregiver notification preferences.
3. **Improved AI Accuracy:** Expand model training and validation using more diverse blister-pack images and real-world conditions.
4. **Enhanced OCR Processing:** Improve expiry-date extraction, format handling and user verification.
5. **Medication Inventory Management:** Provide more detailed medicine stock tracking and replenishment reminders.
6. **Improved Adherence Analytics:** Add more detailed reports and historical medication activity visualisations.
7. **Caregiver Coordination:** Support additional caregivers and more flexible family coordination.
8. **Security Improvements:** Continue strengthening access control, data protection and backend security policies.
9. **Usability Improvements:** Make the Elder interface more accessible through larger text, clearer navigation and simplified interactions.
10. **Comprehensive Real-World Testing:** Evaluate application behaviour across different Android versions, devices, network conditions and usage scenarios.

These enhancements would help move the academic prototype towards a more robust and dependable application.

## 19. Project Team and Acknowledgements

SmartCare was developed as a collaborative academic project.

The project involved multiple areas of development, including Android application development, backend integration, database setup, user-interface development and AI model integration.

We would like to acknowledge the guidance and support provided throughout the project.

**Project Details**

- **Project Title:** SmartCare — Medication Adherence and Emergency Response System
- **Project Type:** Academic Major Project
- **Academic Year:** 2026–2027
- **Institution:** Canara Engineering College
- **Department:** Computer Science and Engineering

**Project Team**

- Maya Kamath
- Meghana Madev Naik
- N S Suhani
- Prathiksha Shetty

**Project Guide:** Ms. Saritha M

**Project Mentor:** Mr. Praharsh T

We sincerely thank our project guide and mentor for their valuable guidance, suggestions and support throughout the development of SmartCare.

We also acknowledge the contributions of all team members who worked together to design, implement, integrate and test the different components of the application.

## 20. Copyright and Usage Rights

**Copyright © 2026 SmartCare Project Team. All Rights Reserved.**

SmartCare — Medication Adherence and Emergency Response System is an original academic project developed collaboratively by its credited student contributors.

The original source code, project documentation, application logic, original designs and other original materials in this repository are protected by applicable copyright laws.

**No open-source licence is granted for this project.**

Unless prior written permission is obtained from the respective copyright holder(s), no permission is granted to reproduce, copy, modify, redistribute, republish or commercially use the original materials contained in this repository.

Making this repository publicly accessible for viewing does not transfer ownership or grant a general licence to reuse its original materials.

All third-party libraries, frameworks, APIs, models and tools used by the project remain subject to their respective owners' licences, terms and conditions. This notice does not claim ownership over third-party materials.

The project guide and mentor are acknowledged for their academic guidance and supervision. This acknowledgement does not imply that they are software authors or copyright holders.

For permission to use the original project materials, contact the credited project contributors and obtain the necessary written authorisation.

## 21. Disclaimer

SmartCare is an academic project developed for educational and demonstration purposes.

It is intended to demonstrate how Android application development, backend services, notification systems, AI-based image processing, text recognition and location-based APIs can be combined to support medication management and caregiver assistance.

The application has not been certified as a medical device and has not undergone clinical validation.

AI detection and OCR outputs may contain errors. Medication acknowledgement does not guarantee actual medicine consumption. Notifications and location services may be affected by device settings, permissions, network availability and operating-system restrictions.

SmartCare must not be used as a replacement for professional medical advice, diagnosis, treatment or emergency services.

---

**SmartCare — Making Medication Management More Connected, Organised and Accessible.**
