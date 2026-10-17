# SmartVision — Face Recognition Attendance System

[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://adoptium.net)
[![OpenCV](https://img.shields.io/badge/OpenCV-4.14.0--1.5.14-5C3EE8?logo=opencv&logoColor=white)](https://bytedeco.org)
[![SQLite](https://img.shields.io/badge/SQLite-3.50.3-003B57?logo=sqlite&logoColor=white)](https://sqlite.org)
[![Privacy](https://img.shields.io/badge/100%25-Local_&_Private-155240)](#)

> **Welcome!** This guide is written so that **anyone with only VS Code installed** can clone and run this project in minutes, even if you are an absolute beginner and have **never logged into GitHub**.
>
> SmartVision runs entirely on your local machine. No cloud accounts, no external tracking, no internet connection required to recognize faces.

---

## 📑 Table of Contents
1. [Prerequisites Checklist](#-prerequisites-checklist)
2. [Step 0: How to Open the Terminal in VS Code](#-step-0-how-to-open-the-terminal-in-vs-code)
3. [Step 1: Clone the Project (No GitHub Login Needed)](#-step-1-clone-the-project-no-github-login-needed)
4. [🪟 WINDOWS Guide (Install, Run, & Auto-Launch)](#-windows-guide)
   - [Method A: One-Click Automated Start (Recommended)](#method-a-one-click-automated-start-recommended)
   - [Method B: Direct Terminal Command](#method-b-direct-terminal-command)
   - [How to Rebuild from Source on Windows](#how-to-rebuild-from-source-on-windows)
5. [🍎 macOS Guide (Apple Silicon & Intel)](#-macos-guide)
   - [Step 1: Install Tools via Homebrew](#step-1-install-tools-via-homebrew)
   - [Step 2: Build for Mac (OpenCV Native Libraries)](#step-2-build-for-mac-opencv-native-libraries)
   - [Method A: One-Click Automated Start](#method-a-one-click-automated-start)
   - [Method B: Direct Terminal Command](#method-b-direct-terminal-command-1)
6. [🎯 How to Use SmartVision](#-how-to-use-smartvision)
7. [🛠️ Troubleshooting & Common Questions](#️-troubleshooting--common-questions)

---

## 📋 Prerequisites Checklist

Before running, you only need **one thing**:
* **Java 21 JDK** (Java Development Kit) installed on your computer.

If you don't have it yet, don't worry! Follow the step-by-step commands below for your operating system.

---

## 💻 Step 0: How to Open the Terminal in VS Code

You will run all commands inside the VS Code integrated terminal:

* **Windows:** Press <kbd>Ctrl</kbd> + <kbd>`</kbd> (the backtick key next to number 1)  
  *Or from the top menu:* Click **Terminal** → **New Terminal**.
* **macOS:** Press <kbd>Cmd</kbd> + <kbd>`</kbd>  
  *Or from the top menu:* Click **Terminal** → **New Terminal**.

A terminal panel will appear at the bottom of your VS Code window.

---

## 📦 Step 1: Clone the Project (No GitHub Login Needed)

You do **NOT** need to sign in to GitHub. Run this command in your VS Code terminal to download the project:

```bash
git clone https://github.com/your-username/Presence.git
cd Presence
```

*(If you downloaded the project as a `.zip` file, simply extract it and open the folder in VS Code via **File → Open Folder...**)*

---

## 🪟 WINDOWS Guide

### Step 1.1: Verify or Install Java 21

In your VS Code terminal (PowerShell), check if Java is installed:

```powershell
java -version
```

* **If it says `openjdk version "21..."` or `java version "21..."`:** Great! Skip to Step 1.2.
* **If it says "java is not recognized":** Install Java 21 with one copy-paste command:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

*(After installation completes, close the VS Code terminal panel with the trash icon and open a new one with <kbd>Ctrl</kbd> + <kbd>`</kbd> to refresh PATH).*

Verify again:
```powershell
java -version
```

---

### Method A: One-Click Automated Start (Recommended)

Run this single command in your terminal. It checks Java, picks the camera-fixed build, starts the web server on port `8081`, and **automatically opens your browser** in 4 seconds:

```powershell
.\start-presence.cmd
```

> 💡 **What this does automatically:**
> 1. Detects your Java 21 environment.
> 2. Starts the backend on **`http://127.0.0.1:8081`**.
> 3. Pops open your default web browser automatically — no copy-pasting URLs required!
> 4. To stop the application safely at any time, click the terminal and press <kbd>Ctrl</kbd> + <kbd>C</kbd>.

---

### Method B: Direct Terminal Command

If you prefer to run the standalone command directly in PowerShell:

```powershell
java -jar ".\target\presence-camera-fix-v2.jar" --server.port=8081 --presence.camera-index=0
```

Wait until you see:
```text
Tomcat started on port 8081 (http) with context path '/'
Started PresenceApplication in 4.995 seconds
```

Then open your browser automatically by running in a second terminal tab (or pasting into your browser):

```powershell
start http://127.0.0.1:8081
```

---

### How to Rebuild from Source on Windows

*(Only needed if you modify Java, HTML, or CSS code)*

1. Install Maven:
   ```powershell
   winget install Apache.Maven
   ```
2. Re-open terminal and package the project:
   ```powershell
   mvn clean package -DskipTests
   ```
3. Run the newly packaged application:
   ```powershell
   java -jar ".\target\presence-camera-fix-v2.jar" --server.port=8081 --presence.camera-index=0
   ```

---

## 🍎 macOS Guide

On macOS, OpenCV requires native libraries matching your Mac's processor architecture (Apple Silicon M1/M2/M3/M4 or Intel).

### Step 1: Install Tools via Homebrew

In your VS Code terminal (zsh), install Java 21 and Maven using [Homebrew](https://brew.sh):

```bash
brew install openjdk@21 maven
```

Add Java 21 to your path:
```bash
echo 'export PATH="$(brew --prefix openjdk@21)/bin:$PATH"' >> ~/.zshrc
echo 'export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"' >> ~/.zshrc
source ~/.zshrc
```

Verify:
```bash
java -version
mvn -version
```

---

### Step 2: Build for Mac (OpenCV Native Libraries)

Run the build command for your Mac type:

* **For Apple Silicon Mac (M1, M2, M3, M4, Max, Ultra):**
  ```bash
  mvn -Djavacpp.platform=macosx-arm64 clean package -DskipTests
  ```

* **For Intel Mac:**
  ```bash
  mvn -Djavacpp.platform=macosx-x86_64 clean package -DskipTests
  ```

---

### Method A: One-Click Automated Start

We have included a dedicated Mac launcher script `start-presence.sh`:

```bash
chmod +x ./start-presence.sh
./start-presence.sh
```

> 💡 **What this does automatically:**
> 1. Verifies your Java environment.
> 2. Starts the server on port `8081`.
> 3. Automatically triggers `open http://127.0.0.1:8081` to launch Safari/Chrome.
> 4. To stop the server, press <kbd>Ctrl</kbd> + <kbd>C</kbd>.

---

### Method B: Direct Terminal Command

```bash
java -jar target/smartvision-1.0.0.jar --server.port=8081 --presence.camera-index=0
```

To open your browser from terminal:
```bash
open http://127.0.0.1:8081
```

---

## 🎯 How to Use SmartVision

Once open at **`http://127.0.0.1:8081`**:

### 1. Register a Person
* Click **Register** in the navigation bar (or visit `/register`).
* Enter the person's full name (e.g., `Ayesha Sharma`).
* Select **3 to 5 clear face photos** (`.jpg`, `.jpeg`, or `.png`).
* Click **Add to face library →**.
* *Tip:* Good photos are well-lit, front-facing, and show only one face. Re-registering the same name adds additional training photos to strengthen their profile.

### 2. Mark Daily Attendance
* Return to the **Live** dashboard (`/`).
* Look towards your webcam.
* When a face is detected, a bounding box appears:
  * 🟩 **Green Box + Name:** Face recognized! Attendance is recorded automatically once for today.
  * 🔴 **Red Box + "Unknown":** Face not yet registered (never recorded to attendance).

### 3. Review & Export Records
* Click **Attendance** (`/attendance`) to view everyone marked present today.
* Filter by person name or pick any date.
* Click **Download CSV ↓** to download a clean spreadsheet that opens directly in Microsoft Excel, Google Sheets, or Numbers.

### 4. Manage Face Library
* Click **People** (`/faces`) to view all registered persons and their profile preview.
* If someone leaves, click **Remove person** to delete their photos and model training data. (Their past attendance history remains preserved).

---

## 🛠️ Troubleshooting & Common Questions

### 1. Webcam says "Camera unavailable" or shows black/OBS screen
* **Camera Index:** Built-in webcams are usually index `0`. If you have virtual camera software (OBS, Zoom, Camo), your real webcam might be index `1` or `2`.
  Start with camera index 1:
  ```powershell
  java -jar ".\target\presence-camera-fix-v2.jar" --server.port=8081 --presence.camera-index=1
  ```
* **Privacy Permissions:**
  * **Windows:** Open **Settings → Privacy & security → Camera**, turn ON *Camera access* and *Let desktop apps access your camera*.
  * **macOS:** Open **System Settings → Privacy & Security → Camera**, allow VS Code and Terminal.
* **Camera In Use:** Close any app using the camera (Zoom, Teams, OBS, Discord) before starting SmartVision.

### 2. "Port 8081 is already in use"
If port 8081 is busy (e.g. by another program), simply start on port **8082**:

* **Windows:**
  ```powershell
  java -jar ".\target\presence-camera-fix-v2.jar" --server.port=8082 --presence.camera-index=0
  start http://127.0.0.1:8082
  ```
* **macOS:**
  ```bash
  java -jar target/smartvision-1.0.0.jar --server.port=8082 --presence.camera-index=0
  open http://127.0.0.1:8082
  ```

### 3. How to stop the server cleanly?
Click into the terminal window running the app and press:
<kbd>Ctrl</kbd> + <kbd>C</kbd>

### 4. Where is my data saved?
All data is stored right inside the project folder in the `data/` directory:
```text
Presence/data/
├── presence.db                   (SQLite database: names & attendance records)
├── known_faces/<id>/             (Original uploaded training photos)
└── models/
    ├── haarcascade_frontalface_default.xml (Frontal face detector)
    └── lbph_model.yml                      (Trained LBPH face model)
```
To back up all attendance and registered faces, simply copy the `data/` folder.

---

## 📄 License & Notes
* This project is designed for local academic and organizational attendance tracking.
* Uses OpenCV LBPH face recognition (Local Binary Patterns Histograms) running locally on your CPU with zero external API calls.
