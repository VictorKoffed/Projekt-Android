# ☕ Coffee Journal (Android Project)

A mobile app built in **Kotlin** using **Jetpack Compose**.  
The app lets coffee enthusiasts record and visualize their brews with a simple and modern interface.

## ✨ Features 

**Done ✅**
- Fetch random coffee images via the **Coffee API** using **Volley** 
- Display images with **Coil** in Jetpack Compose 
- Connect to the **Bookoo smart scale** via Bluetooth for real-time weight data
- Store brew history locally with **Room (SQLite)**
- See weight and time in a chart of the brew from the **Bookoo smart scale** via Bluetooth for real-time brew data
- Add Flow to the chart graf
- Create a working Homescreen that follows the mockup **Homescreen**
- Create a working Graphscreen that follows the mockup **Graphscreen**
- Clean and responsive UI design inspired by modern coffee apps
- Capture and save photos of brews using **CameraX**

**Planned**

- All done....

## 🧩 Technology Stack

- **Kotlin** – Main language
- **Jetpack Compose** – UI framework
- **CameraX** – Photo capture
- **Room (SQLite)** – Local data storage
- **Bluetooth Low Energy (BLE)** – Real-time scale connection
- **Volley** – HTTP requests
- **Coil** – Image loading

## 🚀 Wireframe/Mockups
https://www.figma.com/proto/LbyNuDuzUL5rzdC0vUEnVo/Systemutveckling-f%C3%B6r-mobila-applikationer-II-HT25-LP1-SUM200?node-id=1-2&p=f&t=pV9HWQnQhHwXVO8U-1&scaling=min-zoom&content-scaling=fixed&page-id=0%3A1&starting-point-node-id=1%3A2

## 🚀 Setup

1. Clone the repository
2. Open in **Android Studio**
3. Sync Gradle and run on an emulator or Android device
4. *(Optional)* Enable Bluetooth permissions to test smart scale features

##  Known issue (Bugfix)

1. The auto connect BLE dont work.

##  ToDo List

1. Rensa upp varningar och göra koden snyggare
2. Fix the buttons in brewdetailscreen vikt and flow (vikt should be wheight) and i think the button flow shuld be same blue as the graf and white text and wheight should be black as its graph and white text?
3. Fix imports
4. kolla över kommentarer
5. den huvudsakliga accentfärgen (0xFFDCC7AA) används ibland som en hårdkodad val i Compose-filer (t.ex. i HomeScreen.kt) i stället för att konsekvent hämtas från MaterialTheme.colorScheme.primary (som är definierad i temat)
6. Data migration to new device?

## 👤 Author

**Victor Koffed** – 2025  
Student project for **Course SUM200**

##    License

MIT License

Copyright (c) 2024 BooKoo

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

Link - https://github.com/BooKooCode/OpenSource/blob/main/bookoo_mini_scale/protocols.md
