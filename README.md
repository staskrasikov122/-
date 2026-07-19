# GsGit Admin

Private Android control plane for `api.gsgit.org`. The app is intentionally limited to
server administration and contains no end-user GsGit functionality.

## Features

- server-verified `X-Admin-Key` login;
- Android Keystore-backed encrypted key storage;
- live dashboard from `/admin/stats`;
- kill-switch and update-gate configuration;
- confirmed global push announcements;
- searchable, expandable device inventory;
- explicit GlassFiles placeholder pending its real API contract;
- loading, empty, retry and human-readable error states.

## Build

```bash
./gradlew :app:assembleDebug
```

The installable APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The repository workflow also builds and uploads that APK as a private Actions artifact.

## Security

- The admin key is never embedded in source code or logged.
- The key is sent only to `https://api.gsgit.org` as `X-Admin-Key`.
- App backup and device-transfer backup are disabled for encrypted preferences.
- Authentication is authoritative on the server; the UI remains locked until
  `GET /admin/stats` returns HTTP 200.
- HTTP 401 clears the local key immediately.

Release signing is intentionally not configured in source. Before producing a stable
release build, add a private keystore and pass its path/password/alias through Gradle
properties (`ADMIN_RELEASE_*`). Never commit signing material.

## Artwork

The original generated launcher artwork is stored at `artwork/gsgit-admin-icon.png`.
JetBrains Mono is used under the SIL Open Font License 1.1.
