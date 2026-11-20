# Quest Modder 0.3.1 (source repair)

Native Android project targeting Quest 3S / Android API 29+.

## New in 0.3
- Multi-file mod import.
- LemonLoader `copyToBase` packaging convention:
  - `.dll` → `assets/copyToBase/Mods/`
  - `.so` → `assets/copyToBase/Plugins/` (staging only; native loading remains game/loader-specific)
  - other files including `.dil` → `assets/copyToBase/QuestModder/`
- APK archive extraction and individual file export.
- APK repackage, removal of stale META-INF signature metadata, and local APK signing using a generated app-private RSA key and apksig.
- Android PackageInstaller session flow to request installation of the signed output.

## Not universal / still not implemented
- The app does not itself inject the LemonLoader runtime into arbitrary games. Use the official LemonLoader installer/patcher for a supported target game.
- It does not generate game-specific IL2CPP Interop assemblies or patch native `libmain.so`.
- `.so` staging does not guarantee the game loads that library. A compatible bootstrap/loader and expected ABI/entry points are required.
- `.dll` files must be built for a compatible managed runtime and target game. Arbitrary desktop .NET DLLs will not work.
- `.dil` has no universal Quest mod format; it is treated as a generic file.
- Re-signing creates a different signing identity. Android generally will not allow this output to update the developer-signed original. Replacing an installed game may require uninstalling it, which can erase local saves. Back up first.
- The app does not handle split APK sets, protected APKs, OBB migration, or game-specific patch profiles.
- APK alignment is not performed in this prototype. Alignment is recommended for production, particularly for uncompressed native libraries. Verify before distribution.

## Build
This archive is repaired source code, not a compiled APK. The build has not been verified in this environment.

Open with Android Studio (Gradle 8.9+ / AGP 8.7.3 / JDK 17), sync dependencies, then Build > Build APK(s). The debug APK should appear at `app/build/outputs/apk/debug/app-debug.apk` if Gradle sync and compilation succeed.
Expected output: `app/build/outputs/apk/debug/app-debug.apk`.

The signing key is generated once and stored in app-private storage. Losing app data loses this key, so APKs signed by a new key will not match previous installs signed by the old key.

## Important
Only modify software you own or are authorized to modify. LemonLoader is an independent open-source project; this app is not affiliated with or endorsed by it.

## Cloud build without installing Android Studio locally
This project includes `.github/workflows/build-android.yml`. Upload the contents of this project to a GitHub repository, then open the repository's **Actions** tab and run **Build Quest Modder APK** (or push to `main`/`master`). If the build succeeds, download the `QuestModder-debug-apk` artifact from that workflow run. This cloud workflow has not been executed from this environment, so the APK build is not yet verified.
