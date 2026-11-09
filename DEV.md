# Development Guide (`DEV.md`)

This document describes the build setup, architecture, and code structure of **Media Copy Pro**.

---

## Tech Stack & Requirements

- **Language**: Kotlin (`2.3.20`), JVM Toolchain 17
- **Android SDK**: `minSdk = 24` (Android 7.0), `targetSdk = 36`, `compileSdk = 36`
- **Android Gradle Plugin**: `9.0.1` (Gradle `9.x`)
- **UI Framework**: Jetpack Compose (Material 3, Compose BOM `2026.03.01`)
- **Navigation**: AndroidX Navigation 3 (`androidx.navigation3:navigation3-runtime` & `navigation3-ui`)
- **Serialization**: `kotlinx-serialization-json`
- **File & Metadata Libraries**:
  - `androidx.documentfile:documentfile` – Storage Access Framework (SAF) local/USB directory access
  - `androidx.exifinterface:exifinterface` – Image EXIF timestamp parsing
  - `com.hierynomus:smbj` (`0.14.0`) – SMB2/SMB3 network share client

---

## Build Variants

Configured in `app/build.gradle.kts` and `app/src/debug/res/values/strings.xml`:

| Variant | Application ID | App Name | Notes |
| :--- | :--- | :--- | :--- |
| **`debug`** | `org.liberty.android.mediacopyprodebug` | `MCP Debug` | Can be installed side-by-side with the release build |
| **`release`** | `org.liberty.android.mediacopypro` | `Media Copy Pro` | Production package ID and app label |

### Common Gradle Commands

```bash
# Build debug APK (app/build/outputs/apk/debug/app-debug.apk)
./gradlew assembleDebug

# Build release APK (app/build/outputs/apk/release/app-release-unsigned.apk)
./gradlew assembleRelease

# Build both debug and release APKs
./gradlew assembleDebug assembleRelease

# Run unit tests
./gradlew testDebugUnitTest
```

---

## Project Structure

```text
app/src/
├── debug/res/values/
│   └── strings.xml                  # Overrides app_name -> "MCP Debug" for debug builds
└── main/
    ├── AndroidManifest.xml          # Permissions (INTERNET, ACCESS_NETWORK_STATE) & backup rules
    ├── java/org/liberty/android/mediacopypro/
    │   ├── MainActivity.kt          # Single-activity Compose host
    │   ├── NavigationKeys.kt        # @Serializable NavKey route definitions
    │   ├── Navigation.kt            # NavDisplay & entryProvider navigation graph
    │   ├── MainViewModel.kt         # Core copy engine, state management, and profile persistence
    │   ├── Mp4DateExtractor.kt      # Binary MP4/MOV moov box parser for timezone-aware dates
    │   ├── data/
    │   │   ├── AppFile.kt           # Unified file system abstraction (local SAF & remote SMB)
    │   │   ├── LocalAppFile.kt      # DocumentFile-backed AppFile implementation
    │   │   ├── SmbAppFile.kt        # DiskShare-backed (SMBJ) AppFile implementation
    │   │   ├── SmbManager.kt        # Connection/Session/DiskShare pool for SMB servers
    │   │   ├── SmbServer.kt         # @Serializable SMB server configuration model
    │   │   ├── SmbServerRepository.kt # SharedPreferences persistence for SMB servers
    │   │   ├── CopyProfile.kt       # @Serializable option profile model
    │   │   ├── CopyHistory.kt       # @Serializable job history summary model
    │   │   ├── CopiedFileDetail.kt  # @Serializable per-file copy result model
    │   │   └── HistoryManager.kt    # JSON file persistence in context.filesDir/history/
    │   ├── theme/
    │   │   ├── Color.kt
    │   │   ├── Theme.kt
    │   │   └── Type.kt
    │   └── ui/
    │       ├── main/
    │       │   ├── MainScreen.kt            # Primary configuration screen (source, dest, options, profiles)
    │       │   ├── CopyProgressScreen.kt    # Live progress, speed metrics, warnings, and cancel action
    │       │   ├── HistoryListScreen.kt     # List of past copy jobs with clear-history action
    │       │   └── CopiedFilesListScreen.kt # Per-file details for a specific copy job
    │       └── smb/
    │           ├── ServerListScreen.kt      # Saved SMB server list
    │           ├── ServerEditScreen.kt      # Add/edit/delete SMB server configuration
    │           └── SmbDirectoryScreen.kt    # Browse SMB directories and create remote folders
    └── res/
        ├── drawable/                # Vector icons and adaptive launcher drawables
        ├── values/                  # Default strings ("Media Copy Pro") and themes
        └── xml/                     # backup_rules.xml & data_extraction_rules.xml
```

---

## Architecture & Core Components

### 1. Unified Storage Abstraction (`AppFile`)
To support copying between any combination of local SAF folders and remote SMB shares (Local $\rightarrow$ Local, Local $\rightarrow$ SMB, SMB $\rightarrow$ Local, SMB $\rightarrow$ SMB), the copy engine in `MainViewModel` operates exclusively on the `AppFile` interface:
- **`LocalAppFile`**: Wraps `androidx.documentfile.provider.DocumentFile` and opens streams via `ContentResolver`.
- **`SmbAppFile`**: Wraps `com.hierynomus.smbj.share.DiskShare` and translates `/` paths to Windows-style `\` SMB paths. Uses custom URI strings of the form `smb://<server-uuid>/<relative-path>`.

### 2. Copy Engine (`MainViewModel`)
`MainViewModel.performCopy` runs on `Dispatchers.IO` and executes in two phases:
1. **Scan Phase**: Traverses `srcDir` (recursively or flat), compiles wildcard patterns into case-insensitive `Regex` instances for extensions and filenames, and accumulates matching `(AppFile, relativePath)` pairs along with total byte size.
2. **Transfer Phase**:
   - Extracts creation date per file (`ExifInterface` for images, `Mp4DateExtractor` + `MediaMetadataRetriever` for videos, fallback to `lastModified()`).
   - Evaluates the `destFormat` template variables (`{yyyy}`, `{MM}`, `{dd}`, `{HH}`, `{mm}`, `{ss}`, `{file_name}`, `{file_name_only}`, `{file_ext}`, `{file_relative_path}`) and creates intermediate destination directories.
   - Applies the selected `ConflictStrategy` (`SKIP`, `OVERWRITE`, `SMART`, `RENAME`).
   - Streams file bytes in 8 KB buffers while updating `CopyState` every 500 ms with instantaneous speed, overall average speed, elapsed time, and ETA.
   - Retries transient failures up to 3 times per file (rolling back partial byte counts) and aborts if 3 consecutive files fail.
   - Writes a `CopyHistory` JSON record via `HistoryManager` on completion or cancellation.

### 3. Timezone-Aware Video Date Extraction (`Mp4DateExtractor`)
Android's `MediaMetadataRetriever` often returns MP4 creation timestamps in UTC without local timezone offsets. `Mp4DateExtractor` inspects MP4/MOV streams directly to locate the `moov` atom (up to 20 MB) and scans for ISO-8601/QuickTime date strings containing explicit timezone offsets (e.g., `2026-07-12T12:02:28-0700` or `2026:07:16 17:09:52-07:00`), ensuring destination date folders match the local time where the video was recorded.

### 4. Data Persistence & Backup Privacy
- **App Preferences & Profiles**: Stored in `SharedPreferences("media_copy_prefs")` (`srcUri`, `destUri`, `currentProfileName`, `profiles_json`).
- **SMB Server Credentials**: Stored in `SharedPreferences("smb_servers_prefs")`. Excluded from Android Auto Backup and device-to-device transfer in `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.
- **Copy Job History**: Stored as individual `<timestamp>.json` files inside `context.filesDir/history/`.
