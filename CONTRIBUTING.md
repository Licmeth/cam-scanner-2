# Contributing to Cam Scanner

## Architecture Overview

The app follows a simple architecture with the following components:

### Main Components

1. **MainActivity** - The main camera screen
   - Manages camera preview using CameraX
   - Handles document detection in real-time
   - Shows visual feedback with rectangle overlay
   - Manages navigation drawer

2. **DocumentOverlayView** - Custom view for visual feedback
   - Draws the rectangle around detected document
   - Changes color based on detection state (green = detected, red = not detected)
   - Shows corner markers

3. **DocumentScanner** - OpenCV-based document detection
   - Detects document edges using contour detection
   - Orders corners properly (top-left, top-right, bottom-right, bottom-left)
   - Performs perspective transformation to straighten the document

4. **DocumentPreviewActivity** - Preview and save screen
   - Shows the transformed document
   - Allows user to retake or save to PDF
   - Handles PDF generation using iText7

5. **SettingsActivity** - Settings screen
   - Allows adjustment of image quality
   - Edge detection sensitivity control
   - Auto-capture toggle

## Code Flow

### Document Scanning Flow

1. User launches app → MainActivity
2. App requests camera and storage permissions
3. Camera preview starts with CameraX
4. ImageAnalysis use case processes frames in background
5. Each frame is analyzed with OpenCV to detect document edges
6. If document detected:
   - Corners are calculated and scaled to screen coordinates
   - DocumentOverlayView draws green rectangle
   - Capture button is enabled
7. User presses Capture button
8. Image is captured at full resolution
9. Document is transformed using perspective transformation
10. User is taken to DocumentPreviewActivity
11. User can save to PDF or retake

### Document Detection Algorithm

The `DocumentScanner.detectDocument()` method:
1. Converts bitmap to grayscale
2. Applies Gaussian blur to reduce noise
3. Uses Canny edge detection
4. Finds contours in the edge-detected image
5. Selects the largest contour
6. Approximates contour to a polygon
7. If polygon has 4 corners, it's considered a document
8. Orders corners in consistent order

### Document Transformation

The `DocumentScanner.transformDocument()` method:
1. Takes the captured image and detected corners
2. Calculates the output dimensions based on corner distances
3. Creates a perspective transformation matrix
4. Applies the transformation using OpenCV's warpPerspective
5. Returns the straightened and cropped document

## Key Technologies

- **Kotlin** - Primary language
- **CameraX** - Modern camera API
- **OpenCV** - Computer vision and image processing
- **iText7** - PDF generation
- **Material Design** - UI components
- **Coroutines** - Asynchronous programming

## Testing

Currently, the app requires a physical Android device or emulator for testing due to camera and OpenCV requirements.

### Manual Testing Steps

1. Launch app and grant permissions
2. Test document detection with various documents
3. Test capture and transformation
4. Verify PDF is saved correctly
5. Test settings changes
6. Test navigation drawer

## Future Enhancements

Potential improvements:
- Add image filters (grayscale, black & white, etc.)
- Implement batch scanning (multiple pages)
- Add OCR (text recognition)
- Cloud storage integration
- Manual corner adjustment
- Multi-page PDF support
- Undo/redo functionality
- Image rotation and cropping tools

## Code Style

- Follow Kotlin coding conventions
- Use descriptive variable names
- Add comments for complex logic
- Keep functions small and focused
- Handle errors gracefully
