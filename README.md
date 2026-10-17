# SmartVision — Face Recognition Attendance System

> **Purpose:** This file is the complete beginner guide for running, using, explaining, and troubleshooting SmartVision.
> **Why it is used:** Someone can use this project on Windows or macOS without reading the Java source code first.

SmartVision is a local face-recognition attendance website. Register people with clear face photos, open the live webcam view, and SmartVision records each recognized person once per day.

Everything stays on the same computer. The local database, original face photos, and face-recognition model are kept in the **data** folder. No cloud account or internet connection is needed after the application has been built.

> **Important:** Use this only with every person’s knowledge and permission. This is an academic project, not a high-security biometric system. It has no liveness detection, anti-spoofing, login accounts, or perfect-accuracy guarantee.

## 1. Understand what starts first

SmartVision is one Spring Boot application.

- The **backend** is the Java command that starts the local web server.
- The **frontend** is already included in the same application as HTML, CSS, and JavaScript pages.
- After starting the backend command, open the browser address. There is **no separate frontend command**.

The normal local address is:

```text
http://127.0.0.1:8081
```

## 2. Windows — instant use of the ready-made application

### Step 1: Check Java

Open PowerShell inside the SmartVision folder and run:

```powershell
java -version
```

It must show Java version **21**.

If Java is not installed and Windows Package Manager is available, run:

```powershell
winget install EclipseAdoptium.Temurin.21.JDK
```

Close PowerShell, open a new PowerShell window, and run:

```powershell
java -version
```

### Step 2: Start the backend

This is the known working command for this Windows computer:

```powershell
java -jar ".\target\smartvision-ui-v4.jar" --server.port=8081 --presence.camera-index=0
```

Wait until the terminal shows:

```text
Tomcat started on port 8081
```

### Step 3: Open the frontend

Open a second PowerShell window and run:

```powershell
start http://127.0.0.1:8081
```

Or paste this address into Chrome, Edge, or Firefox:

```text
http://127.0.0.1:8081
```

Keep the first PowerShell window open. To stop SmartVision safely, click that first window and press:

```text
Ctrl+C
```

### Easier repeat start on Windows

This launcher selects the current camera-fixed package and uses port 8081:

```powershell
.\start-presence.cmd --presence.camera-index=0
```

## 3. Windows — download dependencies and rebuild the project

You need this section only if you change Java, HTML, CSS, or configuration files and need a new application package.

### Step 1: Install Maven

```powershell
winget install Apache.Maven
```

Close PowerShell, open it again, then check both tools:

```powershell
java -version
mvn -version
```

### Step 2: Build and download project dependencies

Inside the SmartVision folder, run:

```powershell
mvn clean test
mvn clean package
```

On the first run, Maven automatically downloads all required dependencies: Spring Boot, SQLite, OpenCV, and the Windows OpenCV native libraries. This can take several minutes. Later builds use the local Maven cache and are faster.

### Step 3: Run the newly built package

```powershell
java -jar ".\target\smartvision-1.0.0.jar" --server.port=8081 --presence.camera-index=0
```

Then open:

```powershell
start http://127.0.0.1:8081
```

## 4. macOS — install, build, and run

The ready-made **smartvision-ui-v4.jar** in this folder is packaged for Windows. On a Mac, build a Mac package from the source code so Maven downloads the matching Mac OpenCV libraries.

### Step 1: Install Homebrew if needed

Check whether Homebrew is already installed:

```bash
brew --version
```

If that command says it cannot find **brew**, install Homebrew with its official command:

```bash
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

### Step 2: Install Java 21 and Maven

```bash
brew install openjdk@21 maven
```

Make Java 21 available in the current Terminal window:

```bash
export PATH="$(brew --prefix openjdk@21)/bin:$PATH"
export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"
java -version
mvn -version
```

To keep those Java settings after opening a new Terminal window, run:

```bash
echo 'export PATH="$(brew --prefix openjdk@21)/bin:$PATH"' >> ~/.zshrc
echo 'export JAVA_HOME="$(brew --prefix openjdk@21)/libexec/openjdk.jdk/Contents/Home"' >> ~/.zshrc
source ~/.zshrc
```

### Step 3: Build and automatically download Mac dependencies

Open Terminal in the SmartVision project folder.

For an Apple Silicon Mac (M1, M2, M3, M4), run:

```bash
mvn -Djavacpp.platform=macosx-arm64 clean test
mvn -Djavacpp.platform=macosx-arm64 clean package
```

For an Intel Mac, run:

```bash
mvn -Djavacpp.platform=macosx-x86_64 clean test
mvn -Djavacpp.platform=macosx-x86_64 clean package
```

Maven downloads Spring Boot, SQLite, OpenCV, and the correct native Mac OpenCV libraries during the first build.

### Step 4: Start the backend and open the frontend

```bash
java -jar target/smartvision-1.0.0.jar --server.port=8081 --presence.camera-index=0
```

Open a second Terminal window and run:

```bash
open http://127.0.0.1:8081
```

To stop the app, return to the first Terminal window and press **Ctrl+C**.

## 5. Use SmartVision

### Register a person

1. Open the dashboard at **http://127.0.0.1:8081**.
2. Click **Register a face**.
3. Enter the person’s full name.
4. Select 3 to 5 clear JPG, JPEG, or PNG photos of that same person.
5. Click **Register Face**.

Best photos are well lit, sharp, front facing, and show only one person. Registering the same name later adds more training photos for that person.

### Mark attendance

1. Return to the dashboard.
2. Confirm that the live camera image is visible.
3. Ask the registered person to look at the webcam.
4. When the person’s name appears in green, attendance is automatically saved.

One person is recorded only once per calendar day. Unknown faces are labeled **Unknown** and are never recorded.

### View or download attendance

1. Open **Attendance** from the menu.
2. Search by name or choose a date.
3. Click **Download CSV** to download a file that opens in Excel.

### Manage registered people

Open **Registered Faces** to review photos or delete a person. Deleting a person removes their local training photos and future recognition data, but keeps their earlier attendance records.

## 6. Camera and port troubleshooting

### Choose the correct camera

Camera index **0** is the real webcam that worked on this Windows computer.

Camera index **1** was the OBS virtual camera. If you see an OBS image, stop the app using **Ctrl+C** and start it with camera index 0:

```powershell
java -jar ".\target\smartvision-ui-v4.jar" --server.port=8081 --presence.camera-index=0
```

Before starting the application, close OBS, Zoom, Teams, Discord, or other apps that may be using the webcam.

On Windows, also check **Settings → Privacy & security → Camera**, then enable **Camera access** and **Let desktop apps access your camera**.

### Change the port

A port is the local number after the colon in the browser address.

- Default in these instructions: **8081**
- If 8081 is busy, use **8082**

Windows:

```powershell
java -jar ".\target\smartvision-ui-v4.jar" --server.port=8082 --presence.camera-index=0
start http://127.0.0.1:8082
```

macOS:

```bash
java -jar target/smartvision-1.0.0.jar --server.port=8082 --presence.camera-index=0
open http://127.0.0.1:8082
```

Always open the same port in the browser that was used in the Java command.

## 7. Where your local data is stored

When SmartVision starts for the first time, it creates:

```text
data/
├── presence.db                         names and attendance records
├── known_faces/<person-id>/            original registered face photos
└── models/
    ├── haarcascade_frontalface_default.xml   face detector
    └── lbph_model.yml                        trained recognition model
```

To back up the project, stop SmartVision with **Ctrl+C**, then copy the entire **data** folder to a safe location.

Do not move or edit files inside **data/known_faces** while the application is running.

## 8. How SmartVision works

```text
Register photos → find the face → train the local recognition model
                                              ↓
Webcam image → find face → compare with model → known name / Unknown
                                              ↓
                              known name → mark attendance once that day
```

The project uses:

- **Haar Cascade:** finds face-shaped areas in an image.
- **LBPH (Local Binary Patterns Histograms):** compares a detected face against the registered faces.

LBPH gives a distance score, not a percentage. Lower means a closer match. Better lighting, a front-facing pose, a good webcam, and more varied training photos improve results.

## 9. Project file guide

Every editable source and configuration file begins with two simple comments: **Purpose** and **Why it is used**.

| File or folder | Simple purpose |
| --- | --- |
| **pom.xml** | Maven build instructions and platform-native OpenCV dependency selection. |
| **start-presence.cmd** | Simple Windows launcher for the packaged application. |
| **README.md** | This setup, usage, troubleshooting, and explanation guide. |
| **src/main/java/com/presence/PresenceApplication.java** | Starts the Spring Boot application. |
| **config/** | Application settings, local folders, database setup, and OpenCV loading. |
| **model/** | Person, face-photo, and attendance data structures. |
| **repository/** | SQLite database read and write code. |
| **service/** | Camera, face recognition, registration, attendance, CSV, and file-storage logic. |
| **controller/** | Connects browser pages and web requests to the application services. |
| **resources/application.properties** | Default ports, camera index, upload limits, and recognition settings. |
| **resources/models/** | Pre-trained OpenCV frontal-face detector. |
| **resources/templates/** | Browser HTML pages. |
| **resources/static/css/styles.css** | Website visual design and mobile layout. |
| **src/test/** | Automatic tests for core database, recognition, and attendance rules. |

## 10. Important limitations

- A physical webcam is required for live recognition.
- Accuracy depends on lighting, face angle, camera quality, and training photos.
- Haar Cascade + LBPH is lightweight and suitable for an academic demonstration, not security-grade identification.
- There is no liveness detection, so do not use this for high-stakes decisions.
- The app is intentionally local-only: no login accounts, cloud backup, remote access, or mobile app.

