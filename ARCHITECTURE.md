# Architecture Documentation

## Application Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        User Interface                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌──────────────┐    ┌──────────────────┐    ┌──────────────┐
│  MainActivity │    │ Document Preview │    │   Settings   │
│              │    │     Activity     │    │   Activity   │
│  - Camera    │    │                  │    │              │
│  - Detection │    │  - Preview Doc   │    │  - Quality   │
│  - Capture   │    │  - Save to PDF   │    │  - Sens.     │
│  - Overlay   │    │  - Retake        │    │  - Auto Cap  │
└──────────────┘    └──────────────────┘    └──────────────┘
        │                     │
        │                     │
        ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                     Core Components                          │
├─────────────────────────────────────────────────────────────┤
│  DocumentScanner (OpenCV)     DocumentOverlayView           │
│  - detectDocument()           - Visual Feedback             │
│  - transformDocument()        - Rectangle Overlay           │
└─────────────────────────────────────────────────────────────┘
        │                     │
        │                     │
        ▼                     ▼
┌─────────────────────────────────────────────────────────────┐
│                    External Libraries                        │
├─────────────────────────────────────────────────────────────┤
│  CameraX          OpenCV 4.8.0        iText7               │
│  - Preview        - Edge Detection    - PDF Generation      │
│  - Capture        - Transform         - Layout              │
│  - Analysis       - Image Processing                        │
└─────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

### MainActivity
**Purpose:** Main entry point for the application

**Responsibilities:**
- Initialize OpenCV library
- Request runtime permissions (Camera, Storage)
- Set up CameraX for camera preview
- Configure ImageAnalysis for real-time document detection
- Manage UI state (capture button, status text)
- Handle navigation drawer menu
- Coordinate between camera, detection, and capture

**Key Methods:**
- `onCreate()` - Initialize components
- `startCamera()` - Set up CameraX
- `processImage()` - Analyze frames for document detection
- `captureDocument()` - Take picture and transform

### DocumentOverlayView
**Purpose:** Custom view to display detection feedback

**Responsibilities:**
- Draw rectangle overlay on detected document
- Show corner markers
- Change color based on detection state
- Scale coordinates from image space to screen space

**Key Methods:**
- `setDocumentCorners()` - Update detected corners
- `onDraw()` - Render overlay on canvas

### DocumentScanner
**Purpose:** Core document detection and transformation logic

**Responsibilities:**
- Detect document edges using OpenCV
- Find largest rectangular contour
- Order corners consistently
- Perform perspective transformation
- Crop document from image

**Key Methods:**
- `detectDocument()` - Find document corners
- `transformDocument()` - Apply perspective correction
- `orderPoints()` - Sort corners correctly

### DocumentPreviewActivity
**Purpose:** Show captured document and enable PDF export

**Responsibilities:**
- Display transformed document image
- Generate PDF from bitmap
- Save PDF to Documents folder
- Handle retake functionality

**Key Methods:**
- `onCreate()` - Load captured image
- `saveToPdf()` - Export document as PDF

### SettingsActivity
**Purpose:** Manage user preferences

**Responsibilities:**
- Provide UI for settings
- Save preferences to SharedPreferences
- Load existing settings

**Key Methods:**
- `onCreate()` - Initialize settings UI
- SeekBar and CheckBox listeners

## Data Flow

### Detection Flow
```
Camera Frame
    ↓
ImageProxy → toBitmap()
    ↓
DocumentScanner.detectDocument()
    ↓
OpenCV Processing:
    - Grayscale conversion
    - Gaussian blur
    - Canny edge detection
    - Contour finding
    - Polygon approximation
    ↓
Array<Point>? (4 corners or null)
    ↓
Scale to screen coordinates
    ↓
DocumentOverlayView.setDocumentCorners()
    ↓
Visual feedback on screen
```

### Capture Flow
```
User presses Capture button
    ↓
ImageCapture.takePicture()
    ↓
Full resolution ImageProxy
    ↓
Convert to Bitmap
    ↓
DocumentScanner.transformDocument(bitmap, corners)
    ↓
OpenCV Processing:
    - Calculate output dimensions
    - Create perspective matrix
    - Apply warpPerspective
    ↓
Transformed Bitmap
    ↓
Save to cache
    ↓
Start DocumentPreviewActivity
    ↓
Load from cache → Display
    ↓
User chooses to save
    ↓
iText7 PDF generation
    ↓
Save to Documents/CamScanner/
```

## Threading Model

- **Main Thread:** UI updates, user interactions
- **CameraExecutor:** Camera operations, image capture
- **Coroutines (Dispatchers.Default):** Document detection, transformation
- **Coroutines (Dispatchers.Main):** UI updates after background processing

## Error Handling

- OpenCV initialization failure → Toast message, app closes
- Camera permission denied → Toast message, app closes
- Document not detected → Capture button disabled
- Capture/save failure → Toast error message
- PDF generation error → Toast with error details

## Permissions

### Required Permissions
- `CAMERA` - Access device camera (runtime permission)
- `WRITE_EXTERNAL_STORAGE` - Save PDF files (runtime, API ≤ 28)
- `READ_EXTERNAL_STORAGE` - Read files (runtime, API ≤ 32)

### Permission Flow
```
App Launch
    ↓
Check permissions
    ↓
All granted? ──Yes→ Start camera
    │
    No
    ↓
Request permissions
    ↓
User grants? ──Yes→ Start camera
    │
    No
    ↓
Show error → Close app
```

## Configuration

### Build Configuration
- `minSdk: 24` (Android 7.0)
- `targetSdk: 34` (Android 14)
- `compileSdk: 34`

### Dependencies Version Management
- Kotlin: 1.9.0
- Gradle: 8.0
- AndroidX: Latest stable
- CameraX: 1.3.0
- OpenCV: 4.8.0
- iText7: 7.2.5

## Performance Considerations

1. **Image Analysis Throttling**
   - Uses `STRATEGY_KEEP_ONLY_LATEST` to prevent backlog
   - Processes only latest frame if previous still processing

2. **Memory Management**
   - Releases OpenCV Mat objects after use
   - Recycles bitmaps when done
   - Uses cache directory for temporary files

3. **Thread Safety**
   - Camera operations on dedicated executor
   - OpenCV processing on background coroutines
   - UI updates on main thread only

## Future Architecture Improvements

1. **MVVM Pattern**: Separate UI logic from business logic
2. **Repository Pattern**: Abstract data sources
3. **Dependency Injection**: Use Hilt or Koin
4. **Jetpack Compose**: Modern declarative UI
5. **Room Database**: Store scan history
6. **WorkManager**: Background PDF processing
