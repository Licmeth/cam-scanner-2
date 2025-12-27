# Cam Scanner

An Android app that allows you to scan documents using your smartphone's camera, automatically transform and crop the picture, and convert it to PDF.

## Features

- **Real-time Document Detection**: Uses OpenCV to detect document edges in real-time through the camera preview
- **Visual Feedback**: Shows a green rectangle overlay around detected documents
- **Automatic Perspective Correction**: Transforms and crops the captured image to fit the document perfectly
- **PDF Export**: Save scanned documents as PDF files

## Todo

- [ ] Smooth edge detection, by averaging multiple frames
- [ ] Multi-page document scanning
- [ ] Image filters (color, grayscale, black & white)
- [ ] Allow user to manually adjust corners before saving
- [ ] Allow user to define formats for the detection
- [x] Add flash usage
- [ ] Auto-adjust brightness and contrast
- [ ] Allow image JPEG2000 compression for pdf
- [ ] Enable pinch to zoom in camera preview
- [ ] Use About Libraries gradle plugin

## Requirements

- Android 7.0 (API level 24) or higher
- Camera, ideally with autofocus
- Storage permission for saving PDFs

## How to Use

1. Launch the app and grant camera and storage permissions when prompted
2. Point the camera at a document
3. Wait for the green rectangle to appear around the document
4. Press the "Capture" button to take the picture
5. Review the automatically cropped and transformed document
6. Press "Save as PDF" to save the document, or "Retake" to capture again

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

