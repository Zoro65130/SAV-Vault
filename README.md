# SAV Vault

Native Android file manager starter project built with Kotlin and Jetpack Compose.

## Build using GitHub Actions

1. Upload the contents of this project to a GitHub repository.
2. Make sure the default branch is named `main`.
3. Open the **Actions** tab and enable workflows if GitHub asks.
4. Push a commit to `main`, or run **Build SAV Vault APK** manually.
5. Open the completed workflow run and download the `SAV-Vault-APK` artifact.

## Important limitations

- The app requests Android's special all-files-access setting on Android 11+; users must grant it manually.
- Android restricts access to some system-managed folders, including parts of `Android/data` and `Android/obb`.
- Copy, move, delete, rename, and open/share are basic implementations and should be tested carefully on the target device before storing important files.
- This starter does not yet implement ZIP archive tools, media playback, favorites, recent-file history, or a full APK manager.
