# ☕ Coffee Journal (Android Project)

[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-007BFF)](https://developer.android.com/kotlin)
[![Android](https://img.shields.io/badge/Android-Native-3DDC84)](https://developer.android.com/about)
[![Hilt](https://img.shields.io/badge/DI-Hilt-5D10E5)](https://developer.android.com/training/dependency-injection/hilt-android)
[![Room](https://img.shields.io/badge/Database-Room%20(SQLite)-4285F4)](https://developer.android.com/topic/libraries/architecture/room)
[![BLE](https://img.shields.io/badge/Connection-BLE%20Flows-00BCD4)](https://developer.android.com/guide/topics/connectivity/bluetooth/le)

Ett modernt kaffejournal byggt i **Kotlin** med **Jetpack Compose**. Appen är designad för kaffeentusiaster och erbjuder integration med smarta vågar via **Bluetooth Low Energy (BLE)** för realtidsdata, samt robust lokal datalagring för bryggningshistorik.

---

## Innehåll
- [Projektstruktur](#-projektstruktur)
- [Mappstruktur](#-mappstruktur)
- [Kom igång (Build & Run)](#-kom-igång-build--run)
- [Funktioner](#-funktioner)
- [Arkitektur](#-arkitektur)
- [Avancerade Kotlin/Android-koncept som används](#-avancerade-kotlinandroid-koncept-som-används)
- [Testning](#-testning)
- [Skärmbilder](#-skärmbilder)
- [Katalog över viktiga filer](#-katalog-över-viktiga-filer)
- [License](#-license)

---

## 📁 Projektstruktur

Projektet är organiserat enligt moderna Android-standarder (Clean/MVVM-inspirerat) med fokus på Separation of Concerns:

| Projektstruktur  | Namn                | Beskrivning                                             |
|:-----------------|:--------------------|:--------------------------------------------------------|
| `ProjektAndroid` | Gradle Root         | Huvudapplikationen.                                     |
| `app`            | Android Application | Innehåller UI, ViewModels, Repositories och Datakällor. |

---

## 🧱 Mappstruktur

Kärnlogiken för appen finns under `app/src/main/java/com/victorkoffed/projektandroid/`:

```text
com.victorkoffed.projektandroid/
├─ di/                        # Hilt Modules för DI (DatabaseModule)
├─ data/
│  ├─ ble/                    # BookooBleClient (Hantera BLE-protokollet)
│  ├─ db/                     # Room (Entities, DAO, Database, Converters)
│  └─ repository/              # Repository-interfaces och implementations
├─ domain/                     # Domänmodeller (BleConnectionState, ScaleMeasurement)
└─ ui/
   ├─ navigation/              # Navigeringsvägar (Screen.kt)
   ├─ screens/                 # Compose-skärmar (Home, Brew, Scale, etc.)
   ├─ theme/                   # Material 3-tema (Color, Type, Theme)
   └─ viewmodel/               # Hilt ViewModels (Logik, StateFlows)
```

---

## 🚀 Kom igång (Build & Run)

### Förutsättningar
- Android Studio (Giraffe 2022.3.1 eller nyare)
- Kotlin SDK (jvmToolchain(11))
- Android SDK (API 36 rekommenderas)
- Fysisk Android-enhet eller Emulator (krävs för BLE/CameraX)

### Steg
1. Klona repot.
2. Öppna i Android Studio.
3. Synkronisera Gradle (Gradle 8.14.3).
4. Välj målenhet och tryck **Run (Ctrl+F5)**.

---

## ⚙️ Funktioner

| Funktion                    | Beskrivning                                                                                             |
|:----------------------------|:--------------------------------------------------------------------------------------------------------|
| **Live Brew & BLE**         | Ansluter till Bookoo smart scale via Bluetooth och strömmar realtidsdata (vikt & flödeshastighet).      |
| **Visualisering**           | Visar vikt och flödesdata i en interaktiv graf (BrewSamplesGraph).                                      |
| **Datalagring**             | Robust lagring av alla data (Bönor, Bryggningar, Utrustning, Mätdata) i Room (SQLite).                  |
| **Realtidsstatistik**       | Visar översikt: totala bryggningar, tillgänglig bönvikt och tid sedan senaste kaffe (inkl. arkiverade). |
| **Bönarkivering**           | Möjlighet att arkivera bönor när lagersaldot når noll.                                                  |
| **Redigera Bryggdetaljer**  | Möjlighet att redigera sparade brygginställningar och anteckningar.                                     |
| **Vågminne & Auto-connect** | Stöder "Kom ihåg våg" och automatisk återanslutning.                                                    |
| **Mörkt Läge**              | Manuell växling för Ljust/Mörkt tema.                                                                   |
| **Fotohantering**           | CameraX används för att spara URI till bryggningsbild med stöd för fullskärmsvisning.                   |
| **Robust Live Brew**        | Förbättrad hantering av frånkoppling under pågående inspelning.                                         |

---

## 🧱 Arkitektur

```mermaid
graph TD
  UI["Compose Screens"] --> VM["Hilt ViewModels / StateFlow"]
  VM --> Repo["Repositories (Brew, Bean, Scale, Image, etc.)"]
  Repo --> Data["Data Sources"]
  Data --> Room["Room/SQLite DB"]
  Data --> BLE["BookooBleClient / BLE"]
  Data --> Network["Kotlin Coroutines / Coffee API"]
  DI["Hilt DI"] --- VM
  DI --- Repo
  DI --- Data
```

- **MVVM/MVI-inspirerad:** Compose Views observerar reaktiva `StateFlow` från ViewModels.
- **Repository Pattern:** Abstraherar datakällor genom `BrewRepository` och `ScaleRepository`.
- **Hilt/DI:** Automatisk beroendeinjektion av ViewModels, Repositories, Databas och BLE-klienter.

---

## 🧩 Avancerade Kotlin/Android-koncept som används

| Område                | Exempel i koden                                       | Förklaring                                                                                                                         |
|:----------------------|:------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------|
| Kotlin Flows          | `StateFlow`, `SharedFlow`, `combine`, `collectLatest` | Reaktivt dataflöde mellan DB, BLE och UI.                                                                                          |
| BLE-kommunikation     | `callbackFlow`, `BluetoothGatt`                       | Coroutines & Flows för asynkrona BLE-händelser.                                                                                    |
| Room Data             | `@DatabaseView`, `ForeignKey.CASCADE`                 | Avancerad databasmodellering med vyer och constraints.                                                                             |
| Coroutines            | `viewModelScope`, `withTimeoutOrNull`                 | Hanterar asynkrona operationer säkert.                                                                                             |
| CameraX               | `ImageCapture`, `ProcessCameraProvider`               | Enkel integration av foto i bryggningsflödet.                                                                                      |
| Nätverkskommunikation | `CoffeeImageRepositoryImpl`, `URL().readText()`       | Block-safe I/O utförd på Dispatchers.IO inuti en suspend-funktion.                                                                 |
| Jetpack Navigation    | `SavedStateHandle`                                    | Hanterar komplext tillstånd (som captured_image_uri från kameran) och bevarar navigeringsargument mellan processer och rotationer. |
                                                                                

---

## 🧪 Testning

- **Enhetstester:** `app/src/test` – Inkluderar logik för att validera parsning av råa BLE-data.
- BookooDataParserTest.kt: Validerar parsning av vikt, flöde och tid från råa Bluetooth-paket.
- **Instrumenterade tester:** `app/src/androidTest` – platshållare (ExampleInstrumentedTest.kt)

Kör tester:
```bash
./gradlew test
```

---

## 🖼️ Skärmbilder
📱 Wireframe (Grundide)
<p > <img src="docs/images/wireframe_home.png" alt="Mockup Home" width="1048"/> </p>
📱 Mockup (Designidé)
<p > <img src="docs/images/mockup_home.png" alt="Mockup Home" width="1296"/> </p>
📲 Faktisk app (Live version)
<p > <img src="docs/images/real_home.png" alt="Coffee Journal App Screenshot" width="1299"/> </p>
📲 Flowchart (Live version)
<p > <img src="docs/images/Flowchart.png" alt="Coffee Journal App Screenshot" width="955"/> </p>

### 🎬 Demo
<div align="center">
  <video src="https://github.com/user-attachments/assets/51382519-2b58-4130-84e8-cf8f1e16e673" autoplay loop muted playsinline width="250"></video>
</div>

---

## 📚 Katalog över viktiga filer

<details><summary><strong>Gradle/Konfiguration</strong></summary>

- `gradle/libs.versions.toml` – Central hantering av beroenden
- `app/build..kts` – Konfigurerar Android/Compose/Hilt/KSP
- `AndroidManifest.xml` – BLE- och kameratillstånd

</details>

<details><summary><strong>Data & Arkitektur</strong></summary>

- `data/repository/interfaces/BrewRepository.kt` – Huvudkontrakt för brygg-data
- `data/db/DatabaseEntities.kt` – Room-entiteter & BrewMetrics (View)
- `data/ble/BookooBleClient.kt` – BLE-hantering
- `di/DatabaseModule.kt` – Hilt-modul för databas & repository

</details>

<details><summary><strong>UI & Navigation</strong></summary>

- `MainActivity.kt` – NavHost, Drawer, Hilt ViewModel-hämtning
- `ui/viewmodel/scale/ScaleViewModel.kt` – Hanterar BLE-logik & state
- `ui/screens/brew/LiveBrewScreen.kt` – Compose-skärm för realtidsbryggning

</details>

---

## 📜 License

MIT License

Copyright (c) 2025 BooKoo

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

**Link:** [Bookoo BLE Protocol](https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md)

---

## AI-ASSISTANS OCH KODGENERERING

Delar av denna kodbas har skapats, refaktorerats eller assisterats med hjälp av stora språkmodeller (LLM) och AI-verktyg för att effektivisera utvecklingsprocessen och förbättra kodkvaliteten.

### Verktyg som använts

* **ChatGPT** (för utformning av komplexa algoritmer och dokumentation).
* **Gemini** (för autokomplettering, boilerplate och tester).

## 👥 Projekt & Kurskontext

Detta projekt utvecklades som ett individuellt projekt inom kursen:

**Systemutveckling för mobila applikationer II (7,5 hp)**  
(*System Development for Mobile Applications II, 7.5 credits*)

Projektet fokuserar på utveckling av en modern Android-applikation med integration mot extern hårdvara och lokal datalagring.

### 🎯 Fokus i projektet

Arbetet omfattade:

- Utveckling av en native Android-app med Jetpack Compose  
- Integration med Bluetooth Low Energy (BLE) för realtidsdata från extern enhet  
- Lokal datalagring med Room (SQLite) och repository-arkitektur  
- Implementering av MVVM/MVI-inspirerad arkitektur  
- Användning av dependency injection med Hilt  
- Hantering av mobil hårdvara (kamera och BLE)  
- Design av responsivt och modernt mobilgränssnitt  

### 🧠 Lärandeperspektiv

Projektet gav praktisk erfarenhet inom:

- Systemdesign för mobila applikationer  
- Integration av hårdvarunära funktioner i Android  
- Arkitekturval i moderna mobilappar  
- Realtidsdatahantering med Kotlin Coroutines och Flow  

### Omfattning av AI-assistans

AI har huvudsakligen använts för:
1.  **Boilerplate-kod:** Generering av standardstruktur och klassdefinitioner.
2.  **Algoritmiska lösningar:** Förslag på effektiva implementeringar för standardproblem (t.ex. sortering, databasinteraktioner).
3.  **Dokumentation:** Förbättring och generering av kommentarer och docstrings.

### Mänsklig granskning

All AI-genererad kod har granskats, testats och validerats manuellt av en mänsklig utvecklare.

---

## Framtida utveckling (Develovment branch)

1. Assisted brew, målvikt, tider att sikta på mm? kanske visa ratio mellan kaffe och vatten live i livebrew?(Nästan klart)
2. Visa i grafen med den markering där man lyft på bryggaren för att swirla.
3. Integrea espresso läge med Bookoo EM. https://github.com/BooKooCode/OpenSource/blob/main/espresso_monitor/protocols.md
4. Lägga till rating till brews och bönor.
5. Lägga till en del om rostning av bönor.
6. Sökfunktion på bönor.
7. lägga till en del om vattenkemi.
8. lägga till inköpspris, ursprungsland, region och smaknotiringar av bönan.
9. Lägga till recept, färdiga samt möjlighet att göra egna, inspration, Brew timer https://play.google.com/store/apps/details?id=com.apptivity.brewtimer
10. Lägga till defluid R2 https://digitizefluid.com/en-se/pages/difluid-developer-sdk-partnership
11. kunna exportera bönor, brew graph till andra enheter och användare?
12. kunna återanvända en gammal brewgrapf för att kunna följa den i en ny brew
13. Fixa landskapsläge i hela appen.
