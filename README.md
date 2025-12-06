# cam-scanner-2

An Android app that allows you to scan documents using your camera, automatically transform and crop the picture, and convert it to PDF.

## Features

- **Real-time Document Detection**: Uses OpenCV to detect document edges in real-time through the camera preview
- **Visual Feedback**: Shows a green rectangle overlay around detected documents
- **Automatic Perspective Correction**: Transforms and crops the captured image to fit the document perfectly
- **PDF Export**: Save scanned documents as PDF files
- **Settings Menu**: Accessible via a slide-out drawer menu
- **Runtime Permissions**: Requests camera and storage permissions on startup

## Requirements

- Android 7.0 (API level 24) or higher
- Camera with autofocus
- Storage permission for saving PDFs

## How to Use

1. Launch the app and grant camera and storage permissions when prompted
2. Point the camera at a document
3. Wait for the green rectangle to appear around the document
4. Press the "Capture" button to take the picture
5. Review the automatically cropped and transformed document
6. Press "Save as PDF" to save the document, or "Retake" to capture again
7. Access settings via the menu icon (three horizontal lines) in the top-left corner

## Building the Project

1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Run on an Android device or emulator

## Technologies Used

- Kotlin
- AndroidX CameraX for camera functionality
- OpenCV for document detection and image processing
- iText7 for PDF generation
- Material Design Components

## License

See LICENSE file for details.

