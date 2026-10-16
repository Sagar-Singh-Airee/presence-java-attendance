# Presence — Face Recognition Attendance System

> **Purpose:** This is the simple user and developer guide for the whole project.
> **Why it is used:** It explains how to run, use, understand, and troubleshoot Presence without reading every source file.

Presence is a small, local Java web application for an academic attendance demonstration. It detects faces with OpenCV’s Haar Cascade, identifies registered faces with OpenCV contrib’s real LBPH recognizer, and stores one attendance record per person per local calendar day.

It runs at **http://127.0.0.1:8080**. Face photos, the SQLite database, and the generated model never leave the computer.

> This is not a production biometric-security system. It has no login, liveness detection, anti-spoofing, or accuracy guarantee. Get appropriate permission from every person whose image is registered.

## What is included

- Live MJPEG webcam feed with face boxes and labels
- A shared camera capture service: browser viewers do not open separate physical cameras
- Multi-photo registration, image validation, Haar face detection, and 200×200 grayscale/equalized preprocessing
- Genuine OpenCV `LBPHFaceRecognizer`, retrained from saved original photos at startup and after additions/removals
- SQLite-backed people, face-sample, and attendance records
- Database-level and service-level protection against duplicate daily attendance
- Registered-face removal that deletes photographs/recognition data but retains historical attendance
- Searchable/filterable attendance history and UTF-8 CSV export
- Dark, responsive local web UI and JUnit tests that never use a real webcam

## Technology and dependency check

The project uses Java 21, Spring Boot 3.5.7, SQLite JDBC 3.50.3.0, and Bytedeco OpenCV 4.14.0-1.5.14. The Maven configuration deliberately declares the OpenCV Java bindings plus their explicit 64-bit Windows native artifacts. This supplies the contrib `cv::face` module used by `LBPHFaceRecognizer` without downloading native binaries for every operating system.

The included `nativeOpenCvAndContribLbphAreAvailable` test loads the native library and constructs `LBPHFaceRecognizer`. That is the project’s compile-and-run check for the required native/contrib API; run it with the commands below on the intended Windows computer.

The official `haarcascade_frontalface_default.xml` file is bundled in `src/main/resources/models/`. On first startup it is copied into `data/models/`, where OpenCV can open it from a normal filesystem path. No separate model download is needed.

## Windows 10/11 setup

1. Install a **64-bit JDK 21** and Apache **Maven 3.9+**. Ensure both `java` and `mvn` are on the PATH.

2. In PowerShell, verify the installations:

   ```powershell
   java -version
   mvn -version
   ```

   `java -version` should report version 21. Maven should report that it is using Java 21.

3. Open PowerShell in this project folder and download/build dependencies:

   ```powershell
   mvn clean test
   mvn clean package
   ```

   Maven obtains the OpenCV Java libraries and the Windows native binaries on this initial build. Once Maven’s local cache is populated, normal use of Presence does not need an internet connection.

4. Start the application:

   ```powershell
   mvn spring-boot:run
   ```

5. Browse to [http://127.0.0.1:8080](http://127.0.0.1:8080).

The database tables and data folders are created automatically; there is no SQL setup command.

### Packaged launch

After `mvn clean package`, start the packaged application with:

```powershell
java -jar target\presence-1.0.0.jar
```

For a simpler repeat launch after packaging, double-click `start-presence.cmd` or run this in Command Prompt:

```cmd
start-presence.cmd
```

Stop either launch with `Ctrl+C`. The camera is released during Spring Boot shutdown.

### Choose a different webcam

Camera index `0` is the default. To try camera index 1 for one Maven launch:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--presence.camera-index=1"
```

For the packaged application:

```powershell
java -jar target\presence-1.0.0.jar --presence.camera-index=1
```

Other useful properties are `--presence.recognition-threshold=95`, `--presence.frame-width=640`, and `--presence.frame-height=480`. The server deliberately binds only to `127.0.0.1` by default.

## Use it

1. Open **Register face**. Enter a full name and upload one or more well-lit, front-facing JPG/JPEG/PNG photos containing a clear face. The form accepts a maximum total of 16 MB.
2. Each file is decoded by OpenCV, checked with the Haar Cascade, and rejected if unreadable or no front-facing face is detected. The largest detected face is used for that photo’s LBPH training sample.
3. Return to the dashboard. Allow browser camera permission if asked. A recognized face gets a mint label; an unrecognized face gets `Unknown` and is never recorded.
4. Open **Attendance** to search records by a fragment of a name and/or a date. The CSV export always contains all database records, not merely the current filter result.
5. Open **Registered faces** to remove a person. Their local photos and training samples are deleted, and their historical attendance continues to show their saved name/date/time.

## Understanding the recognition threshold

LBPH returns a *distance-like score*, not a probability or percentage. Lower is a closer match. Presence calls a person recognized only when the result is at or below `presence.recognition-threshold` (default `95`). Lighting, camera quality, pose, and the small training set affect the useful threshold; test it with consented photos and adjust it conservatively. It does not imply perfect recognition.

## Local data

Runtime data is deliberately ignored by Git:

```text
data/
├── presence.db                 SQLite database
├── known_faces/
│   └── <person-id>/            original registered image files
└── models/
    ├── haarcascade_frontalface_default.xml
    └── lbph_model.yml          regenerated after registrations/deletions
```

The saved originals are the source of truth. The LBPH model is rebuilt at every startup so the application can recover after restarts. Do not manually edit database image paths or move files in `data/known_faces` while the app runs.

## Troubleshooting

| Symptom | What to try |
| --- | --- |
| `java` or `mvn` is not recognized | Install JDK 21/Maven and add their `bin` folders to the Windows PATH; reopen PowerShell. |
| Camera unavailable | Close Teams, Zoom, browser tabs, or other apps using the camera; confirm Windows privacy settings allow desktop apps to use it; then try `--presence.camera-index=1`. The rest of Presence remains usable without a camera. |
| OpenCV could not load | Run `mvn clean test` once with internet access so Maven can obtain the declared Windows OpenCV native artifacts. Confirm Maven is using 64-bit Java 21. Do not mix another OpenCV Java binding into this project. |
| No face found during registration | Use a closer, front-facing, sharp image with good, even lighting and one dominant face. Haar detection is deliberately conservative. |
| Face is often Unknown | Add several varied, clear photographs for the same exact name and cautiously tune `presence.recognition-threshold`; LBPH is sensitive to capture conditions. |
| Port 8080 is in use | Start with `mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"` and open that port locally. |

## Automated tests

`src/test/java/com/presence/PresenceApplicationTests.java` covers:

- schema creation and person persistence
- case-insensitive duplicate-name rejection
- duplicate same-day attendance and later-date attendance
- name/date filters
- CSV headers and escaped values
- deletion preserving historic attendance
- empty/invalid registration input
- OpenCV native initialization plus LBPH-contrib availability

The tests use `target/test-presence-data`, not the real `data` directory, and do not access a physical webcam. Manual camera verification is still required: register consented test photos, open the dashboard, confirm the feed/boxes, confirm a recognized person receives exactly one record for the day, and confirm an unknown face creates none.

## Project tree

```text
Presence/
├── pom.xml
├── README.md
├── .gitignore
├── src/main/java/com/presence/
│   ├── PresenceApplication.java
│   ├── config/                 application, database, paths, native OpenCV setup
│   ├── controller/             dashboard, registration, photos, people, attendance
│   ├── model/                  Person, FaceSample, AttendanceRecord
│   ├── repository/             parameterized SQLite access
│   └── service/                camera, Haar/LBPH, registration, records, CSV, storage
├── src/main/resources/
│   ├── application.properties
│   ├── models/haarcascade_frontalface_default.xml
│   ├── static/css/styles.css
│   └── templates/
└── src/test/java/com/presence/PresenceApplicationTests.java
```

## Known limitations

- A physical webcam is required for live recognition and has to be tested on the target computer.
- Haar + LBPH is a classical, lightweight academic approach. It is not robust enough for security or high-stakes identity decisions.
- There is no liveness check, account system, remote access, cloud backup, or support for video files/MJPEG URLs.
- Current recognition, native-library, and camera behavior must be verified with the `mvn clean test` and manual steps above on a computer where Java 21 and Maven are installed.
