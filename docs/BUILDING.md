# Building Mirage

Requirements:

- JDK 17
- Android SDK 36
- NDK `27.0.12077973`
- CMake `3.31.6`
- Gradle `8.14.5` (installed separately; wrapper scripts are not included)

The AirPlay engine pulls UxPlay, libplist, the OpenSSL build helper, FFmpeg and
Oboe as pinned git submodules:

```sh
git clone --recursive https://github.com/HiteshRohira/Mirage.git
cd Mirage/glasslauncher
gradle --no-daemon :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

For an existing clone, initialize dependencies with
`git submodule update --init --recursive`. The first build compiles OpenSSL and
FFmpeg and Oboe for the selected ABIs. The size-focused build produces standalone APKs
for both ARM ABIs by default; `-PtargetAbis=armeabi-v7a` or
`-PtargetAbis=arm64-v8a` builds only that ABI. Its release APKs are under
`glasslauncher/.build/compact/app/outputs/apk/release/` with the default
native profile. Use fresh native build state when comparing variants.

## Signing

APK installation requires a signature. For a first installation, create and
secure your own Android signing keystore, then sign your APK with `apksigner`
using your key and alias. Never commit the keystore or its passwords. For an
update to an existing package, use the **same signing certificate** as that
installation; a different key cannot update it. JVM tests and lint do not execute
device tests.

## Native profiles

Native profiles have separate outputs: `baseline` keeps the previous native
settings, `exports` localizes internal receiver symbols, and `crypto-size`
also builds OpenSSL with `-Os`. The default `compact` profile links OpenSSL
3.5 LTS, Oboe and the C++ runtime into one receiver library with packed Android
relocations and dead-code removal. It keeps all existing AirPlay crypto and media
fallbacks; the new, unused OpenSSL 3.5 post-quantum algorithms are not built. Select with `-PnativeProfile=PROFILE`; `-PuniversalApk=true` adds a
universal APK. `-PtvAbiOnly` remains an ARMv7-only alias.

## Signed builds of both ABIs

From the repository root:

```sh
python3 scripts/build-sideload.py --gradle /path/to/gradle --sdk /path/to/android-sdk \
  --keystore /private/path/mirage.jks --key-alias mirage \
  --password-file /private/path/password.txt \
  --certificate-sha256 YOUR_VERIFIED_CERTIFICATE_SHA256 --output dist/signed
```

Obtain the lowercase SHA-256 certificate digest from your keystore using
`keytool -list -v -keystore /private/path/mirage.jks -alias mirage` (remove the
colons). For an update, `--baseline-apk /path/to/installed-signed.apk` can replace
`--certificate-sha256`. The helper verifies the expected package, version,
complete native payload, certificate and alignment, and refuses a nonempty output
directory. It builds both ABIs even if you will install only one. The legacy
local key-directory defaults are optional; other users supply their own key.

## Project layout

- `glasslauncher/app` — the launcher: Home, All apps, Control Center, Bluetooth, the phone remote server and its web app.
- `glasslauncher/airplay` — the AirPlay engine and player:
  - native UxPlay core, with patches in `src/main/cpp/patches`;
  - Kotlin service and renderers;
  - the full-screen AirPlay player.
