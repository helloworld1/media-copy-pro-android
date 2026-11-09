# Media Copy Pro

**Media Copy Pro** is an Android utility for copying, organizing, and backing up photos and videos between local storage (internal storage, SD cards, USB OTG drives) and SMB/CIFS network shares (NAS, Windows/Linux file servers).

---

## Key Features

### 1. Flexible Source & Destination Storage
- **Local Storage (SAF)**: Select any folder on internal storage, SD card, or attached USB drive via Android's Storage Access Framework (`DocumentFile`).
- **SMB / NAS Shares**: Connect to SMB2/SMB3 network shares (`smbj`) as either the source or destination. Supports custom host/IP, share name, optional domain, username/password authentication, or anonymous access, plus in-app folder browsing and directory creation.

### 2. Automatic Date Extraction & Custom Destination Organization
Automatically organizes files into folders or renames them using metadata timestamps:
- **Photos & RAW Images**: Extracts `DateTimeOriginal` / `DateTimeDigitized` / `DateTime` from EXIF metadata (`jpg`, `jpeg`, `heic`, `heif`, `png`, `arw`, `cr2`, `cr3`, `dng`).
- **Videos**: Extracts timezone-aware creation dates directly from MP4/MOV `moov` atoms, with fallback to `MediaMetadataRetriever` and last-modified timestamps (`mp4`, `mov`, `mkv`, `avi`).
- **Customizable Destination Format**: Supports dynamic path templates using `/` to create subdirectories automatically:
  - `{yyyy}` – 4-digit year (e.g., `2026`)
  - `{MM}` – 2-digit month (`01`–`12`)
  - `{dd}` – 2-digit day (`01`–`31`)
  - `{HH}` – 2-digit hour (`00`–`23`)
  - `{mm}` – 2-digit minute (`00`–`59`)
  - `{ss}` – 2-digit second (`00`–`59`)
  - `{file_name}` – Full original file name with extension (e.g., `IMG_0001.JPG`)
  - `{file_name_only}` – File name without extension (e.g., `IMG_0001`)
  - `{file_ext}` – File extension without dot (e.g., `JPG`)
  - `{file_relative_path}` – Relative subfolder path from the source root

### 3. Filtering & Scanning Options
- **Folder Scan Mode**:
  - **Recursive**: Scans all subdirectories under the selected source folder.
  - **Current Folder**: Scans only the top-level files in the selected source folder.
- **Extension Pattern**: Comma-separated list of extensions with wildcard (`*`) support (default: `arw,cr*,dng,mov,mp4,jpg,heic`).
- **File Name Pattern**: Comma-separated wildcard patterns (e.g., `*20260102*`, `*holiday*`, or `*` for all files).

### 4. Conflict Resolution Strategies
Choose how existing files in the destination are handled:
- **Smart** *(Default)*: Skips the file if a destination file with the same name and byte size already exists; overwrites otherwise.
- **Skip**: Leaves the existing destination file untouched.
- **Overwrite**: Replaces the existing destination file.
- **Rename**: Appends an incrementing suffix (`filename_1.ext`, `filename_2.ext`, etc.) so no files are overwritten.

### 5. Configuration Profiles
- Save, switch, and delete named option profiles (folder scan mode, conflict strategy, extension pattern, file name pattern, and destination format).
- Protects against accidental exit when the active profile has unsaved changes.

### 6. Real-Time Copy Progress, Retries & History
- **Live Progress Tracking**: Displays files copied, byte progress, current transfer speed (MB/s), average speed, elapsed time, and estimated time remaining while keeping the screen awake during active transfers.
- **Fault Tolerance**: Automatically retries failed file transfers up to 3 times before marking a file as failed, and aborts the job after 3 consecutive file failures to handle unmounted drives or dropped network connections gracefully.
- **Copy History & Audit Log**: Saves a detailed JSON history for each copy job (start time, source/destination URIs, and per-file status: `COPIED`, `SKIPPED`, `RENAMED`, or `FAILED` with error messages).

---

## License

This project is licensed under the [MIT License](LICENSE).
