# App User Interface Flow

## Screen 1: Main Camera Screen (MainActivity)

```
┌─────────────────────────────────────────┐
│ ☰  Cam Scanner                          │  <- Action bar with menu
├─────────────────────────────────────────┤
│                                         │
│     [Camera Preview with Live Feed]     │
│                                         │
│        ╔════════════════╗              │
│        ║                ║              │  <- Green rectangle overlay
│        ║   DOCUMENT     ║              │     when document detected
│        ║                ║              │
│        ╚════════════════╝              │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│  Status: "Document detected!"           │  <- Status text
│                                         │
│      [    CAPTURE    ]                  │  <- Capture button
│                                         │
└─────────────────────────────────────────┘

Features:
- Real-time document detection with OpenCV
- Visual feedback (green rectangle when detected)
- Status text updates
- Capture button (enabled only when document detected)
```

## Screen 2: Navigation Drawer (Slide from left)

```
┌───────────────────────┐
│                       │
│   Cam Scanner         │  <- Header
│                       │
├───────────────────────┤
│ ⚙️  Settings          │  <- Navigate to settings
│                       │
│ ℹ️  About             │  <- Show about info
│                       │
│ ❓  Help              │  <- Show help
│                       │
│                       │
│                       │
│                       │
└───────────────────────┘

Features:
- Slide-out from left edge
- Access to app settings
- About and help information
```

## Screen 3: Document Preview (DocumentPreviewActivity)

```
┌─────────────────────────────────────────┐
│ ←  Document Preview                     │  <- Back to camera
├─────────────────────────────────────────┤
│                                         │
│                                         │
│    [Transformed Document Image]         │  <- Full-screen view of
│                                         │     cropped document
│                                         │
│                                         │
│                                         │
│                                         │
├─────────────────────────────────────────┤
│  [  RETAKE  ]      [  SAVE AS PDF  ]   │  <- Action buttons
└─────────────────────────────────────────┘

Features:
- Full-screen preview of transformed document
- Retake option (goes back to camera)
- Save as PDF (exports to Documents folder)
```

## Screen 4: Settings (SettingsActivity)

```
┌─────────────────────────────────────────┐
│ ←  Settings                             │
├─────────────────────────────────────────┤
│                                         │
│  Image Quality                          │
│  [━━━━━━━●━━━━━━━━━━] 85%             │  <- Quality slider
│                                         │
│  Edge Detection Sensitivity             │
│  [━━━━━━━━━━●━━━━━━━] 50%             │  <- Sensitivity slider
│                                         │
│  ☐ Auto Capture                        │  <- Auto-capture toggle
│                                         │
│                                         │
│                                         │
└─────────────────────────────────────────┘

Features:
- Adjust image quality (0-100%)
- Adjust edge detection sensitivity
- Toggle auto-capture mode
- Settings saved automatically
```

## User Flow Diagram

```
     ┌─────────────┐
     │  App Launch │
     └──────┬──────┘
            │
            ▼
   ┌────────────────┐
   │ Request Perms  │ (Camera, Storage)
   └────────┬───────┘
            │
            ▼
     ┌──────────────┐
     │ Main Camera  │◄─────────┐
     │   Screen     │          │
     └──────┬───────┘          │
            │                  │
    Document Detected?         │
            │                  │
            ▼                  │
    [Capture Button]           │
            │                  │
            ▼                  │
   ┌─────────────────┐         │
   │   Transform &   │         │
   │   Crop Image    │         │
   └────────┬────────┘         │
            │                  │
            ▼                  │
   ┌─────────────────┐         │
   │    Preview      │         │
   │    Screen       │         │
   └────────┬────────┘         │
            │                  │
     User Choice?              │
            │                  │
       ┌────┴────┐            │
       │         │            │
    Retake?   Save?           │
       │         │            │
       └─────────┘            │
            │                 │
            ▼                 │
      Save as PDF             │
            │                 │
            ▼                 │
      ┌─────────┐            │
      │ Success │            │
      └─────────┘            │
            │                 │
            └─────────────────┘
```

## Key UI Elements

### 1. Camera Preview
- Full-screen camera preview using CameraX PreviewView
- Overlaid with custom DocumentOverlayView

### 2. Document Overlay
- Draws green rectangle when document detected
- Shows corner markers
- Changes to red if no document detected

### 3. Status Text
- "Detecting document..." - Initial state
- "Document detected!" - When document found
- "No document detected" - No document in view

### 4. Capture Button
- Disabled (grayed out) when no document
- Enabled (active) when document detected
- Material Design raised button

### 5. Navigation Drawer
- Material Design NavigationView
- Slide animation from left
- Material icons for menu items

### 6. Preview Screen
- ScrollView for zooming
- Two action buttons at bottom
- Dark background for better document visibility

### 7. Settings Screen
- SeekBars for numeric settings
- CheckBox for boolean settings
- Settings persist using SharedPreferences

## Color Scheme

- **Primary Color**: Purple (#6200EE)
- **Success/Detection**: Green (#4CAF50)
- **Error/No Detection**: Red (#F44336)
- **Background**: White/Black (depending on screen)
- **Text**: Black/White (for contrast)

## Accessibility

- All interactive elements are properly sized (min 48dp touch target)
- Content descriptions on ImageViews
- Clear visual feedback for all actions
- Status messages for screen readers

## Material Design Components

- ActionBar with navigation drawer toggle
- NavigationView for drawer menu
- Buttons with Material styling
- SeekBars with Material styling
- Proper elevation and shadows
- Ripple effects on touch
