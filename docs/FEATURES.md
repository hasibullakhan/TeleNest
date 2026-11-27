# 🌟 TeleNest — Exhaustive Feature Handbook & Technical Specifications

> Complete guide to all features, architecture components, background services, and security workflows in **TeleNest**.

---

## Table of Contents
1. [Core Cloud Storage & TDLib Engine](#1-core-cloud-storage--tdlib-engine)
2. [File & Directory Management System](#2-file--directory-management-system)
3. [Zero-Knowledge Client-Side Encryption Vault](#3-zero-knowledge-client-side-encryption-vault)
4. [Transfer Engine & Auto-Backup Workers](#4-transfer-engine--auto-backup-workers)
5. [In-App Media & Document Ecosystem](#5-in-app-media--document-ecosystem)
6. [Intelligent Search, Filters & Sorting](#6-intelligent-search-filters--sorting)
7. [Recycle Bin (Trash & Grace Period)](#7-recycle-bin-trash--grace-period)
8. [Available Offline & Cache Analyzer](#8-available-offline--cache-analyzer)
9. [UI/UX Theming & Material Design 3](#9-uiux-theming--material-design-3)
10. [In-App Auto-Update Architecture](#10-in-app-auto-update-architecture)
11. [Multi-Language Internationalization](#11-multi-language-internationalization)
12. [Multi-Account & Session Slot Isolation](#12-multi-account--session-slot-isolation)

---

## 1. Core Cloud Storage & TDLib Engine

### 1.1 Direct Native MTProto 2.0 Connection
- **Embedded C++ TDLib:** TeleNest incorporates the Telegram Database Library (`libtdjni.so`) directly inside the APK.
- **Zero Third-Party Relays:** All data packets travel directly from your device to Telegram's distributed data centers over MTProto 2.0. No intermediate servers or web proxies can intercept your session.
- **Session Durability:** TDLib handles network switches (Wi-Fi to Mobile Data), temporary disconnections, and cryptographic key rotations automatically.

### 1.2 Truly Unlimited Cloud Capacity
- **No Account Quota Limits:** Stores terabytes of data across Telegram's unlimited cloud infrastructure.
- **Original Uncompressed Quality:** Media is uploaded using `TdApi.InputMessageDocument`, ensuring no downscaling, compression artifacts, or metadata stripping.

### 1.3 Automatic Large File Splitting & Merging (>2GB)
- **Automatic Chunking:** Files exceeding Telegram's single-message limit (2 GB for standard, 4 GB for Premium) are split into sequential binary chunks (`.part001`, `.part002`, etc.) during upload.
- **Transparent Reassembly:** On download, chunks are automatically verified with checksums and seamlessly concatenated back into the original intact file.

---

## 2. File & Directory Management System

### 2.1 Hybrid Folder Architecture
TeleNest uses a dual-folder model:
1. **Virtual Folders:** High-performance, lightweight subdirectories indexed in a synchronized cloud manifest JSON (`telenest_manifest.json`) stored in your Telegram `Saved Messages`. Each message uploaded to a virtual folder is tagged with `#TELEDRIVE_VF:<folder_id>`.
2. **Channel Folders:** Private Telegram channels/supergroups mapped to top-level workspaces, tagged with `[TeleNest]` in their description.

### 2.2 24-Color Custom Folder Palette
- Choose from 24 Material Design 3 inspired colors:
  `Red`, `Pink`, `Purple`, `Deep Purple`, `Indigo`, `Blue`, `Light Blue`, `Cyan`, `Teal`, `Green`, `Light Green`, `Lime`, `Yellow`, `Amber`, `Orange`, `Deep Orange`, `Brown`, `Grey`, `Blue Grey`, `Pure Black`, `Sapphire`, `Emerald`, `Ruby`, `Gold`.
- Colors persist in the local database and synchronize across sessions via the cloud manifest.

### 2.3 Dual Layout Modes
- **Grid View:** Responsive cards featuring folder accent colors, live recursive item counts, cumulative folder sizes (`X files • Y MB`), and high-definition photo/video thumbnail previews.
- **List View:** Compact, high-density rows designed for rapid scanning, displaying exact byte sizes, dates, and quick action menus.

### 2.4 Navigation State & Scroll Position Memory
- Preserves exact scroll offsets when navigating through nested folders and returning back.
- No jarring jumps or disorientation when managing large directory structures.

### 2.5 Android Home Screen Pinning
- Deep integration with Android's `ShortcutManager`.
- Pin frequently used directories directly onto your device launcher. Tapping the shortcut immediately opens TeleNest inside that specific folder.

### 2.6 Recursive Move & Copy Engine
- Interactive modal bottom sheet with full directory tree navigation.
- Allows on-the-fly subfolder creation at any level before confirming transfer.
- Supports batch relocation of files, entire subdirectories, and cross-channel migrations.

---

## 3. Zero-Knowledge Client-Side Encryption Vault

### 3.1 Military-Grade E2EE
- **AES-256-GCM (`javax.crypto`):** Vault files are encrypted with authenticated AES-256-GCM before upload.
- **PBKDF2-HMAC-SHA256 key derivation:** 250,000 iterations with a random per-file salt. The file header (format version and iteration count) is authenticated. Files from older versions (10,000 iterations, no header) remain readable.
- **Argon2id master-password hash:** The master password itself is only verified against an Argon2id hash (64 MB, 2 passes).
- **Zero-knowledge towards Telegram:** Telegram only ever receives the encrypted blob.

### 3.2 Encrypted Settings Storage
- Preferences, API credentials and master-password data live in `EncryptedSharedPreferences` (Android Keystore-backed AES-256).
- The Room cache (file and folder metadata) is not encrypted. SQLCipher support is on the roadmap.

### 3.3 Biometric & Master PIN Security
- Integrated with `AndroidX BiometricPrompt` using a **CryptoObject**: the prompt is bound to an AES-GCM key in the Android Keystore that needs a fresh biometric authentication for every use.
- A biometric success only unlocks the app when that Keystore cipher actually decrypts the stored credential; a prompt without a valid cipher is treated as an error.
- Enrolling a new fingerprint or face invalidates the key. The master password is then required once, after which biometrics can be enabled again.
- Unlocks with fingerprint or face (device strong biometrics), with the master password as fallback.
- The app locks after it has been in the background for more than 30 seconds.

---

## 4. Transfer Engine & Auto-Backup Workers

### 4.1 Foreground Transfer Service
- Robust multi-threaded queue for concurrent uploads and downloads.
- Real-time status bar notification featuring live transfer speeds (KB/s, MB/s), remaining time estimates, and byte-level progress.
- Controls to **Pause**, **Resume**, and **Cancel** active transfers.

### 4.2 Centralized Transfer Hub
- Dedicated drawer destination showing:
  - **Active:** Currently transferring items with progress bars and speed gauges.
  - **Completed:** History of completed transfers with one-tap file open.
  - **Failed:** Failed jobs with error logs and one-tap retry buttons.

### 4.3 Background Auto-Backup Engine
- Powered by Android **WorkManager**.
- Monitors device directories (`DCIM/Camera`, `Pictures`, `Movies`, `Download`) for newly added media.
- Constrained execution options:
  - **Wi-Fi Only:** Ensures mobile data is never consumed unexpectedly.
  - **Charging Only:** Executes backups while the device is connected to power to conserve battery.

---

## 5. In-App Media & Document Ecosystem

### 5.1 Video Streaming (AndroidX Media3 / ExoPlayer)
- Instant cloud streaming without downloading the entire file first.
- Standard Media3 player controls (play/pause, seek bar, fullscreen layout).
- "Open with" hands the file to another installed player.

### 5.2 Audio Playback
- Audio files (MP3, FLAC, AAC, WAV, OGG, M4A) play in the same Media3 player.

### 5.3 High-Resolution Photo Gallery
- Built with **Coil** for smooth image decoding and caching.
- Multi-touch pinch-to-zoom and double-tap zoom.

### 5.4 Native PDF & Document Viewer
- High-fidelity PDF rendering with smooth scroll and zoom.
- Plain-text viewer for source code, logs, JSON and Markdown files.

### 5.5 APK Inspector & Installer
- Reads `AndroidManifest.xml` from APK files directly from cloud/cache.
- Shows package label, app icon, version name, version code, and minimum SDK.
- Launches system package installer for direct updates and installs.

---

## 6. Intelligent Search, Filters & Sorting

### 6.1 Real-Time Cloud Search
- Instant keyword filtering across filenames, folders, and extensions.

### 6.2 Smart Type Filter Chips
- **All:** Displays all contents.
- **Photos:** Filters JPEG, PNG, WEBP, GIF, SVG, RAW files.
- **Videos:** Filters MP4, MKV, AVI, MOV, WEBM files.
- **Audio:** Filters MP3, FLAC, WAV, AAC, OGG files.
- **Documents:** Filters PDF, DOCX, XLSX, PPTX, TXT, EPUB files.
- **APKs:** Filters Android application packages.

### 6.3 Dual Sorting Controls
- Sort criteria: **Name**, **Date**, or **Size**.
- Sort order: **Ascending (A→Z / Oldest)** or **Descending (Z→A / Newest)**.
- Enhanced with tactile, elevated surface buttons for rapid access.

---

## 7. Recycle Bin (Trash & Grace Period)

### 7.1 Soft-Delete Protection
- Prevents accidental loss by redirecting deletion actions to a 30-day Recycle Bin.

### 7.2 30-Day Automated Grace Period
- Retains deleted items with a live countdown timer.
- Items are permanently purged after 30 days.

### 7.3 One-Tap Exact Path Restore
- Restores files and complete directory hierarchies back to their original parent channel or virtual folder.

---

## 8. Available Offline & Cache Analyzer

### 8.1 Protected Offline Vault
- Saves designated files to internal app storage (`filesDir/offline_files`).
- Immune to system cache clearing and accessible without an internet connection.

### 8.2 Granular Cache & Storage Analyzer
- Visual breakdown of cloud vs local storage consumption.
- Category-specific cleaners for thumbnails, media caches, and temp files.

---

## 9. UI/UX Theming & Material Design 3

### 9.1 Pure Black AMOLED Mode (`#000000`)
- True black background optimized for battery conservation and contrast on OLED/AMOLED screens.

### 9.2 14+ Material 3 Accent Themes
- Includes Ocean Blue, Emerald Green, Indigo, Amber, Sunset Orange, Ruby Red, and more.

### 9.3 3-Way Theme Mode
- Switch between **Light**, **Dark**, and **Use System Default**.

---

## 10. In-App Auto-Update Architecture

- Connected directly to the GitHub Releases API (`/repos/hasibullakhan/TeleNest/releases/latest`).
- Automated startup checks and manual checks under **Settings → About**.
- In-app download with native package installation.

---

## 11. Multi-Language Internationalization

Full support for 11 languages with on-the-fly switching:
- 🇺🇸 English
- 🇧🇩 বাংলা (Bengali)
- 🇮🇳 हिन्दी (Hindi)
- 🇪🇸 Español (Spanish)
- 🇫🇷 Français (French)
- 🇸🇦 العربية (Arabic)
- 🇧🇷 Português (Portuguese)
- 🇷🇺 Русский (Russian)
- 🇵🇰 اردو (Urdu)
- 🇩🇪 Deutsch (German)
- 🇨🇳 中文 (Chinese)

---

## 12. Multi-Account & Session Slot Isolation

### 12.1 Google Drive-Style Multi-Account Switcher
- Add and stay signed in to multiple Telegram accounts simultaneously.
- Instant 1-tap switching between accounts directly from the **Profile** screen (`Switch` button or `+ Add Another Account`), positioned right above the `Logout` button in a compact single-screen layout.
- Automatic pre-filling of previously configured Telegram `API ID` and `API Hash` when adding additional accounts.

### 12.2 Strict Per-Account Data & Database Isolation
- **TDLib Session Isolation:** Each account slot uses its own isolated native TDLib database and file cache directory (`tdlib` for the primary slot and `tdlib_slot_<slotId>` for additional accounts).
- **Dynamic Room Database Switching (`AccountDatabaseManager`):** Each account maintains its own Room database (`teledrive.db` / `teledrive_slot_<slotId>.db`). `SwitchableDriveDao` uses `activeSlotId.flatMapLatest` so all UI Flows reactively switch to the selected account's database with zero data leakage.
- **Per-Account Cloud Manifests:** Virtual folders, folder color palettes, and cloud sync states are scoped per account (`teledrive_manifest_prefs_<slotId>`).

