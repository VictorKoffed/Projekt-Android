# ☕ Coffee Journal

[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-007BFF)](https://developer.android.com/kotlin)
[![Android](https://img.shields.io/badge/Android-Native-3DDC84)](https://developer.android.com/about)
[![Hilt](https://img.shields.io/badge/DI-Hilt-5D10E5)](https://developer.android.com/training/dependency-injection/hilt-android)
[![Room](https://img.shields.io/badge/Database-Room%20(SQLite)-4285F4)](https://developer.android.com/topic/libraries/architecture/room)
[![BLE](https://img.shields.io/badge/Connection-BLE%20Flows-00BCD4)](https://developer.android.com/guide/topics/connectivity/bluetooth/le)

## 📝 Introduction

**Coffee Journal** is a native Android application developed with **Kotlin** and **Jetpack Compose** for documenting, analyzing, and tracking coffee brews.

The app can communicate with smart coffee scales via **Bluetooth Low Energy (BLE)** and displays weight and flow rate in real time during an active brew. Brews, coffee beans, equipment, and measurement data are stored locally in a **Room/SQLite database**.

The project combines mobile development, hardware integration, and local data storage. The focus is on creating a user-friendly tool that allows users to track their brews over time and gain a clearer understanding of how different parameters affect the result.

The app includes support for:

- Real-time data from a smart coffee scale via BLE.
- Visualization of weight and flow rate during brewing.
- Storage and management of coffee beans and brews.
- Statistics and history for previous brews.
- Taking photos and attaching them to brews.
- Automatic reconnection to the last used scale.
- Responsive UI built with Jetpack Compose and Material 3.

The project was developed as an individual university project with a focus on Android development, real-time data, and integration with external hardware.

---

## 📑 Contents

- [Project Structure](#-project-structure)
- [Directory Structure](#-directory-structure)
- [Getting Started](#-getting-started)
- [Features](#-features)
- [Architecture](#️-architecture)
- [Kotlin/Android Concepts](#-kotlinandroid-concepts)
- [Testing](#-testing)
- [Screenshots & Demo](#-screenshots--demo)
- [Key Files](#-key-files)
- [License](#-license)
- [AI Assistance](#-ai-assistance)
- [Project & Course Context](#-project--course-context)
- [Future Development](#-future-development)

---

## 📁 Project Structure

The project is organized as an Android application where the UI, presentation/state, data layer, and external integrations are kept separate.

| Part | Type | Purpose |
|:---|:---|:---|
| `ProjektAndroid` | Gradle Root | Project-level structure and Gradle configuration. |
| `app` | Android Application | Contains the app's UI, ViewModels, repositories, database handling, BLE communication, and dependency injection. |

The application is structured around clear responsibilities across different layers:

- **UI** – Jetpack Compose screens and user interaction.
- **ViewModels** – Manage UI state and coordinate user flows.
- **Repositories** – Abstract access to data and external data sources.
- **Data** – Handles the Room database, BLE communication, and external data sources.
- **Domain** – Contains domain models used by the application.
- **DI** – Hilt is used for dependency injection and to connect the application's components.

---

## 🧱 Directory Structure

The core application logic is located under:

`app/src/main/java/com/victorkoffed/projektandroid/`

~~~text
com.victorkoffed.projektandroid/
├─ di/                        # Hilt modules and dependency injection
├─ data/
│  ├─ ble/                    # BLE communication with smart coffee scales
│  ├─ db/                     # Room database, entities, DAOs, and converters
│  └─ repository/             # Repository interfaces and implementations
├─ domain/                    # Domain models and application logic
└─ ui/
   ├─ navigation/             # Navigation and routes
   ├─ screens/                # Jetpack Compose screens
   ├─ theme/                  # Material 3 theme and UI styling
   └─ viewmodel/              # ViewModels and UI state management
~~~

---

## 🚀 Getting Started

### Prerequisites

To build and run the project, you need:

- Android Studio (Giraffe 2022.3.1 or later)
- Kotlin
- Gradle
- Android SDK (API 36 recommended)
- An Android device or Android emulator

> **Note:** BLE features require a physical Android device with Bluetooth. An emulator can be used to test other parts of the application, but is not recommended for features that require physical Bluetooth communication.

### Installation and Setup

1. Clone the repository.
2. Open the project in Android Studio.
3. Let Android Studio sync the Gradle files.
4. Make sure the required Android SDK versions are installed.
5. Connect an Android device with USB debugging enabled or start an emulator.
6. Select `app` as the run configuration.
7. Start the application using **Run**.

Android Studio will then build the project and install the application on the selected device.

### Building from the Command Line

The debug version can also be built with Gradle:

~~~bash
./gradlew assembleDebug
~~~

The resulting APK file is created under:

~~~text
app/build/outputs/apk/debug/
~~~

### BLE Features

To test the features that communicate with a Bookoo coffee scale, you need:

- A compatible Bookoo scale.
- Bluetooth enabled on the Android device.
- The Bluetooth permissions required by the Android version in use.
- The scale to be available for connection.

BLE communication is handled by `BookooBleClient`.

---

## ⚙️ Features

| Feature | Description |
|:---|:---|
| **Live Brew & BLE** | Connects to a Bookoo smart scale via Bluetooth Low Energy and displays weight and flow rate in real time during brewing. |
| **Real-Time Visualization** | Displays weight and flow data in a graph during an active brew. |
| **Brew History** | Saves and displays previous brews along with their measurement data and details. |
| **Data Storage** | Stores coffee beans, brews, equipment, and measurement data locally using Room/SQLite. |
| **Bean Management** | Create, edit, and archive coffee beans used in brews. |
| **Edit Brews** | Saved brews can be edited afterwards. |
| **Real-Time Statistics** | Displays information such as the number of brews, available bean weight, and time since the last brew. |
| **Scale Memory** | Stores information about which coffee scale was used most recently. |
| **Auto-Connect** | Automatically attempts to reconnect to the last used scale. |
| **Robust BLE Handling** | Handles disconnections from the scale during an active brew and attempts to re-establish communication. |
| **Photo Management** | Uses CameraX to take photos and attach them to brews. |
| **Dark Mode** | Supports light and dark themes with Material 3. |
| **Responsive UI** | Uses Jetpack Compose to provide a flexible interface adapted to different screen sizes. |

---

## 🏗️ Architecture

The app uses an **MVVM/MVI-inspired architecture**, where Jetpack Compose is responsible for presentation and ViewModels manage UI state and user flows.

Data access is abstracted through repositories, while separate data sources handle local data storage, BLE communication, and external API calls.

~~~mermaid
graph TD
  UI["Compose Screens"] --> VM["ViewModels / StateFlow"]
  VM --> Repo["Repositories"]
  Repo --> Data["Data Sources"]

  Data --> Room["Room / SQLite"]
  Data --> BLE["BookooBleClient / BLE"]
  Data --> Network["Coffee API"]

  DI["Hilt Dependency Injection"] --- VM
  DI --- Repo
  DI --- Data
~~~

### UI and State

Jetpack Compose is used for the app's user interface. Screens observe state from ViewModels using mechanisms such as `StateFlow`.

When the state changes, Compose can automatically update the affected parts of the UI.

### ViewModels

ViewModels act as an intermediary between the UI and repositories.

They are responsible for:

- Managing UI state.
- Coordinating user flows.
- Communicating with repositories.
- Handling asynchronous operations with Kotlin Coroutines and Flow.
- Handling real-time data from BLE.

### Repositories

Repositories abstract access to the application's different data sources.

This means that ViewModels do not need to know the implementation details of, for example:

- The Room database.
- BLE communication.
- External API calls.

This creates a clearer separation between presentation and data handling.

### Data Layer

The data layer contains the concrete implementations of the application's data sources.

Room / SQLite is used for local storage of, among other things:

- Coffee beans.
- Brews.
- Equipment.
- Measurement data.

`BookooBleClient` is responsible for communicating with the coffee scale via Bluetooth Low Energy.

External data sources are handled separately from the local database and accessed through repositories.

### Dependency Injection

Hilt is used for dependency injection and to manage how the application's components are created and connected.

It is used, among other things, to provide:

- The database.
- DAOs.
- Repositories.
- The BLE client.
- ViewModels.

This reduces coupling between components and makes the structure easier to test and maintain.

---

## 🧩 Kotlin/Android Concepts

| Area | Example in the Project | Usage |
|:---|:---|:---|
| **Kotlin Flow** | `StateFlow`, `SharedFlow`, `combine`, `collectLatest` | Used for reactive state management and for propagating changes between the data layer, ViewModels, and UI. |
| **Kotlin Coroutines** | `viewModelScope`, `withTimeoutOrNull`, `Dispatchers.IO` | Used for asynchronous operations without blocking the main thread. |
| **BLE Communication** | `callbackFlow`, `BluetoothGatt` | Used to receive and process real-time data from the coffee scale via Bluetooth Low Energy. |
| **Room** | `@Database`, `@Dao`, `@Entity`, `@DatabaseView`, `ForeignKey.CASCADE` | Used for local storage of brews, beans, equipment, and measurement data. |
| **Hilt** | Dependency injection modules | Used to create and provide databases, repositories, BLE clients, and other dependencies. |
| **Jetpack Compose** | `@Composable`, Material 3, Compose State | Used to build the app's user interface and manage UI state. |
| **Jetpack Navigation** | `NavHost`, routes, `SavedStateHandle` | Used for navigation between the app's different screens and for preserving navigation-related state. |
| **CameraX** | `ImageCapture`, `ProcessCameraProvider` | Used to take photos with the device camera and attach them to brews. |
| **Network Communication** | `CoffeeImageRepositoryImpl`, `URL().readText()` | Used to retrieve external information and images on `Dispatchers.IO`. |

---

## 🧪 Testing

The project contains both **unit tests** and **instrumented tests**.

### Unit Tests

Unit tests are located under:

~~~text
app/src/test/
~~~

One example is `BookooDataParserTest`, which tests the parsing of raw data from Bluetooth communication.

The tests verify, among other things, that BLE data is correctly interpreted for:

- Weight.
- Flow rate.
- Time.

Unit tests can be run with Gradle:

~~~bash
./gradlew test
~~~

### Instrumented Tests

Instrumented tests are located under:

~~~text
app/src/androidTest/
~~~

These tests run on an Android device or emulator and can be used to test functionality that depends on the Android framework.

They can be run from Android Studio or with Gradle:

~~~bash
./gradlew connectedAndroidTest
~~~

---

## 🖼️ Screenshots & Demo

### 📱 Wireframe

The basic concept for the app's home screen.

<p>
  <img src="docs/images/wireframe_home.png" alt="Wireframe Home" width="1048"/>
</p>

### 📱 Mockup

A later version of the design.

<p>
  <img src="docs/images/mockup_home.png" alt="Mockup Home" width="1296"/>
</p>

### 📲 Actual App

The working version of the app.

<p>
  <img src="docs/images/real_home.png" alt="Coffee Journal App Screenshot" width="1299"/>
</p>

### ⚖️ Bookoo Smart Scale

The smart coffee scale used to send brew data to the app via Bluetooth Low Energy.

<p>
  <img src="docs/images/BookooTermisMini.png" alt="Bookoo Termis Mini Smart Scale" width="500"/>
</p>

### 🔄 App Flow

Overview of the app's main user flow.

<p>
  <img src="docs/images/Flowchart.png" alt="Coffee Journal Flowchart" width="955"/>
</p>

### 🎬 Demo

<div align="center">
  <video src="https://github.com/user-attachments/assets/51382519-2b58-4130-84e8-cf8f1e16e673" autoplay loop muted playsinline width="250"></video>
</div>

---

## 📚 Key Files

Here are some of the most important files and components in the project.

<details>
<summary><strong>Gradle and Configuration</strong></summary>

- `gradle/libs.versions.toml` – Centralized management of version numbers and project dependencies.
- `app/build.gradle.kts` – Configuration for the Android application, Compose, Hilt, and KSP.
- `AndroidManifest.xml` – App declarations and permissions, including those for BLE and camera access.

</details>

<details>
<summary><strong>Data and Architecture</strong></summary>

- `data/repository/interfaces/BrewRepository.kt` – Interface for managing brew data.
- `data/db/DatabaseEntities.kt` – Room entities and models for brew data and measurements.
- `data/ble/BookooBleClient.kt` – Responsible for communication with the Bookoo scale via Bluetooth Low Energy.
- `di/DatabaseModule.kt` – Hilt configuration for the database and repositories.

</details>

<details>
<summary><strong>UI and Navigation</strong></summary>

- `MainActivity.kt` – App entry point and configuration of navigation and the main UI.
- `ui/viewmodel/scale/ScaleViewModel.kt` – Handles BLE state and measurement data from the scale.
- `ui/screens/brew/LiveBrewScreen.kt` – Responsible for the UI during an active brew.

</details>

---

## 📜 License

MIT License

Copyright (c) 2025 Coffee Journal

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

**Bookoo BLE Protocol:**  
[https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md](https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md)

---

## 🤖 AI Assistance

AI tools were used as development support throughout the project.

### Tools Used

- **ChatGPT** – Ideation, debugging, algorithms, and documentation.
- **Gemini** – Code suggestions, debugging, and parts of the implementation.

AI was primarily used as a development aid. Suggestions and generated code were reviewed, adapted, and tested before being used in the project.

The final implementation, as well as architectural and functional decisions, were made by the developer.

---

## 👥 Project & Course Context

This project was developed as an individual project within the course:

**System Development for Mobile Applications II (7.5 credits)**  
(*System Development for Mobile Applications II, 7.5 credits*)

The project focused on developing a native Android application that communicates with external hardware via Bluetooth Low Energy (BLE), handles real-time data, and stores information locally.

### 🎯 Project Focus

The work included:

- Developing a native Android app with Kotlin and Jetpack Compose.
- Communicating with a Bookoo coffee scale via Bluetooth Low Energy.
- Parsing and handling data from the scale's BLE protocol.
- Handling real-time data with Kotlin Coroutines and Flow.
- Storing beans, brews, and measurement data using Room/SQLite.
- A repository-based structure for separating data handling from the UI and ViewModels.
- MVVM/MVI-inspired architecture.
- Dependency injection with Hilt.
- Integration with the device camera using CameraX.
- Handling connections, disconnections, and reconnections to external hardware.

### 🧠 What the Project Provided Experience With

The project provided hands-on experience with:

- Android development using Kotlin and Jetpack Compose.
- BLE communication and integration with external hardware.
- Kotlin Coroutines, Flow, and asynchronous programming.
- Real-time data and state management in Android.
- Local data storage using Room and SQLite.
- Dependency injection with Hilt.
- Architecture and separation of responsibilities in a larger Android application.
- Integrating hardware, data storage, and user interfaces within the same application.

---

## 🚧 Future Development

There are several things I would like to build on in the project:

1. Assisted brewing with target weight, timings, and potentially a coffee-to-water ratio.
2. Show in the graph when the brewer is lifted to swirl.
3. Add support for espresso mode with Bookoo EM.
   - [Bookoo Espresso Monitor Protocol](https://github.com/BooKooCode/OpenSource/blob/main/espresso_monitor/protocols.md)
4. Add ratings for brews and beans.
5. Add roasting information.
6. Search saved beans.
7. Add support for water chemistry.
8. Add more details for beans, such as purchase price, country of origin, region, and tasting notes.
9. Add recipes, both predefined and custom, and potentially a brew timer mode.
10. Add support for DiFluid R2.
    - [DiFluid Developer SDK](https://digitizefluid.com/en-se/pages/difluid-developer-sdk-partnership)
11. Allow beans and brew graphs to be exported to other devices or users.
12. Allow a previous brew graph to be used as a reference during a new brew.
13. Fix landscape mode throughout the app.
