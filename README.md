# RoomDraft — Phase 3 (Layouts, 3D, Export)

Offline room planner for Android. Kotlin, Jetpack Compose, Material 3, MVVM, Room, DataStore.
Package: `com.nhdeveloper.roomdraft` · minSdk 26 · compile/target SDK 34 · no internet permission.

## Included in Phase 1
- Animated splash (Get Started / Open Existing Project)
- Home: project cards with to-scale thumbnails, empty state, Open / Rename / Duplicate / Delete (with confirmation)
- Create Project: name, room type, presets, unit (m, cm, ft, in) with live conversion, validation (0.5 m to 50 m), live preview and area
- Project overview: to-scale room drawing with dimension labels, details, rename, duplicate, delete
- Templates: 5 starter rooms; using one pre-fills Create Project (the template itself is never modified)
- Settings: theme (system/light/dark), default unit, grid visibility, grid spacing, snap-to-grid, delete all data, about
- Room Database (dimensions stored in meters), DataStore preferences, unit tests for conversions and validation

## Added in Phase 2
- 2D room editor on a Compose Canvas: to-scale room, grid, pinch-zoom, pan, fit view, snap-to-grid (edges snap to grid lines)
- Drag to move, corner handles to resize, rotate in 90 degree steps, duplicate, edit (color, rotation, size), delete with confirmation
- Undo / redo (100 steps), auto-save to Room database after every change (database migrated 1 to 2, existing projects kept)
- Furniture catalog: 39 original items in 11 categories, drawn as top-down symbols (no downloaded assets), Add Furniture screen (color, rotation, size)
- Overlap warning, out-of-room warning, optional out-of-bounds mode
- Measure tool: two-point distance, room size and area, furniture size, distance from furniture to each wall
- Templates now include starter furniture (editable, original never modified); project thumbnails show furniture
- Home: tap a project to open the editor; menu has Details (the overview screen)
- Extra tests: furniture geometry, overlap, snapping, template validity

## Added in Phase 3
- Multiple layouts per project: create (empty or from 3 sample arrangements sized for the room), rename, duplicate, delete (confirmation, last one protected), switch, compare thumbnails. Each layout keeps its own furniture, rotations and colors; room size is shared. Database migrated 2 to 3 (existing furniture becomes "Layout 1").
- Real 3D preview: built-in perspective renderer (no external 3D library, so no version conflicts). Floor, walls (front walls cut away), furniture with approximate heights and simple shapes, lighting and soft shadows. Orbit, pinch-zoom, two-finger pan, auto-rotate, reset, 2D/3D switch. It shows the live design.
- Save & Export: floor plan PNG, PDF summary (plan + furniture table), JSON project file with all layouts, all through the system file picker (works offline).
- Import: folder icon on Home imports a RoomDraft JSON file as a NEW project (never overwrites). Handles invalid JSON, wrong format, newer versions, bad dimensions, invalid items, files over 5 MB.
- Toolbar scrolls sideways (Select, Furniture, Measure, Layouts, 3D, Fit view, Export, Settings).
- More tests: JSON round trip and rejection cases, sample layout validity, 3D camera and projection.

## Not yet included
Wall segments, doors and windows (editing, and therefore doors in the 3D view and the blocked-door warning).
Undo/redo history is per layout and is cleared when you switch layouts.

## Build
- CoGo / AndroidIDE: open this folder, sync, build. Or `./gradlew assembleDebug`
- Versions: AGP 8.5.2, Kotlin 1.9.24, Compose BOM 2024.06.00 (compiler 1.5.14), Material3, Navigation Compose 2.7.7,
  Room 2.6.1 (KSP 1.9.24-1.0.20), DataStore 1.1.1, Lifecycle 2.8.3, Activity Compose 1.9.0, Gradle 8.7, JDK 17
- Tests: `./gradlew testDebugUnitTest`
