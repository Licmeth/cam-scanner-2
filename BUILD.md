# Build Instructions

## Prerequisites

1. Android Studio Arctic Fox or later
2. Android SDK API Level 34
3. Kotlin 1.9.0 or later
4. Gradle 8.0 or later

## Setup Steps

1. **Clone the repository:**
   ```bash
   git clone https://github.com/Licmeth/cam-scanner-2.git
   cd cam-scanner-2
   ```

2. **Open in Android Studio:**
   - Launch Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned repository
   - Wait for Gradle sync to complete

3. **Build the project:**
   - From the menu: Build > Make Project
   - Or use the command line:
     ```bash
     ./gradlew build
     ```

4. **Run on device/emulator:**
   - Connect an Android device via USB with USB debugging enabled
   - Or start an Android Virtual Device (AVD)
   - Click the "Run" button in Android Studio
   - Or use the command line:
     ```bash
     ./gradlew installDebug
     ```

## Project Structure

```
cam-scanner-2/
├── app/
│   ├── build.gradle.kts          # App-level Gradle configuration
│   ├── src/
│   │   └── main/
│   │       ├── AndroidManifest.xml
│   │       ├── java/com/licmeth/camscanner/
│   │       │   ├── MainActivity.kt              # Main camera activity
│   │       │   ├── DocumentOverlayView.kt       # Custom view for rectangle overlay
│   │       │   ├── DocumentScanner.kt           # OpenCV document detection logic
│   │       │   ├── DocumentPreviewActivity.kt   # Preview and save screen
│   │       │   └── SettingsActivity.kt          # Settings screen
│   │       └── res/                             # Resources (layouts, strings, etc.)
├── build.gradle.kts              # Project-level Gradle configuration
├── settings.gradle.kts           # Gradle settings
└── gradle/                       # Gradle wrapper files

```

## Key Features Implemented

1. **Camera Preview with Document Detection**
   - Real-time document edge detection using OpenCV
   - Visual feedback with colored rectangle overlay
   - Automatic corner detection

2. **Document Capture**
   - Capture button enabled when document is detected
   - Automatic perspective transformation
   - Image cropping to document bounds

3. **Document Preview**
   - Full-screen document view
   - Options to retake or save

4. **PDF Export**
   - Save scanned documents as PDF
   - Files saved to Documents/CamScanner directory

5. **Settings**
   - Image quality adjustment
   - Edge detection sensitivity
   - Auto-capture option

6. **Navigation Drawer**
   - Slide-out menu from the left
   - Access to Settings, About, and Help

## Dependencies

- **AndroidX Libraries:** Core, AppCompat, Material Design, ConstraintLayout
- **CameraX:** Camera preview and image capture
- **OpenCV 4.8.0:** Document detection and image processing
- **iText7:** PDF generation
- **Kotlin Coroutines:** Asynchronous operations

## Permissions

The app requires the following permissions:
- `CAMERA` - To access the device camera
- `WRITE_EXTERNAL_STORAGE` - To save PDF files (API level 28 and below)
- `READ_EXTERNAL_STORAGE` - To read files (API level 32 and below)

## Troubleshooting

### OpenCV Issues
- If OpenCV fails to initialize, ensure you're running on a physical device or a properly configured emulator
- Check logcat for OpenCV-related errors

### Camera Issues
- Ensure camera permissions are granted
- Check that the device has a working camera

### Build Issues
- Clean and rebuild: Build > Clean Project, then Build > Rebuild Project
- Invalidate caches: File > Invalidate Caches / Restart

## Testing

To test the app:
1. Launch the app
2. Grant camera and storage permissions
3. Point the camera at a document (e.g., a piece of paper with text)
4. Wait for the green rectangle to appear
5. Tap "Capture"
6. Review the cropped document
7. Tap "Save as PDF" to export

The PDF will be saved to: `/storage/emulated/0/Documents/CamScanner/scan_YYYYMMDD_HHMMSS.pdf`
