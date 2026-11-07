# PC setup audit — 4 November 2026

## Firebase CMD installation repair — 4 November 2026

The user reported that CMD could not find the per-user npm Firebase command, including the full path. That failure could not be reproduced in the agent's process. Installed Firebase CLI 15.34.2 with Administrator approval into the existing machine Node.js prefix, `C:\Program Files\nodejs`. Verified from `C:\Users\devxj` in CMD with **only machine PATH**: `where firebase` resolves `C:\Program Files\nodejs\firebase.cmd`, `firebase --version` returns 15.34.2, and `firebase login:list --non-interactive` recognizes the user's signed-in account. The original CMD session itself was not directly inspected, so its exact failure cause remains unconfirmed.

## Python replaced with 3.14 — 4 November 2026

Installed Python **3.14.10**, recreated the backend environment at `X:\tmp\kira-py314`, and installed boto3, trafilatura, and their dependencies. Dependency validation, package imports, HTML extraction, and all **63 backend tests passed** under 3.14. Removed Python 3.15.2 through Python Install Manager and deleted the obsolete project environment `X:\tmp\kira-py315` after checking its path and interpreter configuration.

`python`, `py`, and `pip` now resolve to Python 3.14. Activate the current project environment with `X:\tmp\kira-py314\Scripts\Activate.ps1`. Earlier sections below are historical records of the setup process. AWS infrastructure, CI, and Lambda packaging still target Python 3.12; changing the PC interpreter did not migrate the deployed backend.

## Remaining essentials installed — 4 November 2026

Installed Android SDK platform 36, platform-tools 37.0.1, and build-tools 35.0.0 and 36.0.0 at `C:\Users\devxj\AppData\Local\Android\Sdk`. Set user `ANDROID_HOME` and added platform-tools to user PATH. ADB starts successfully. Reopen existing terminals to pick up environment changes.

Python 3.12 was explicitly skipped at the user's request. Created a Python 3.15 virtual environment at `X:\tmp\kira-py315` and installed `boto3` 1.43.123, `trafilatura` 2.3.1, and their dependencies. `pip check` and both package imports passed. Activate it in PowerShell with `X:\tmp\kira-py315\Scripts\Activate.ps1`, or invoke its `Scripts\python.exe` directly.

Verification completed: **231 Android unit tests passed**, **63 backend tests passed**, and `testDebugUnitTest lintDebug assembleDebug` finished with **BUILD SUCCESSFUL**. The APK is `X:\KIRA\android\app\build\outputs\apk\debug\app-debug.apk`. Compilation produced existing deprecation warnings and an SDK XML compatibility warning, but all requested checks succeeded. Phone installation and live cloud authentication were not tested. Optional Android Studio, emulator/system images, and phone-specific USB drivers were not installed.

## Installation follow-up — 4 November 2026

At the user's request after this audit, installed AWS CLI v2 **2.39.3** through Windows Package Manager and Firebase CLI **15.34.2** through global npm. Verified AWS version output, Firebase version/help output, and both commands on the refreshed system/user PATH. Reopen existing terminals to pick up PATH changes. Account login was not performed. npm warned that the `superstatic` dependency lists Node 20/22/24 support while this PC uses Node 26; Firebase version and help commands completed successfully, but deployment/emulator behavior was not tested. The original audit below records the state before these installations.

The main blocker for Android development is the missing Android SDK. Java, Git, Android CLI, Node.js, Python, and the project's local configuration and signing files are already present. No software was installed or settings changed during this audit.

## Requirements from the actual project

The build files and CI are more current than the root README:

| Component | Project requirement | Evidence |
|---|---|---|
| Android Java runtime | JDK 17 | `android/app/build.gradle.kts`, `.github/workflows/checks.yml` |
| Android platform | API 36; targets API 35; runs on API 26+ | `android/app/build.gradle.kts` |
| Android Gradle plugin | 8.13.2 | `android/build.gradle.kts` |
| Gradle | 8.14.6 through the included wrapper | `android/gradle/wrapper/gradle-wrapper.properties` |
| Kotlin / Compose compiler plugin | 2.3.21, downloaded by Gradle | `android/build.gradle.kts` |
| SDK build tools | CI installs 36.0.0; AGP 8.13 defaults to 35.0.0 | CI and official AGP compatibility table |
| Backend Python | 3.12 in Lambda and CI | `infra/api.yaml`, `.github/workflows/checks.yml` |
| Web extraction | `trafilatura>=2.0,<3` | `mcp/web-extract/requirements.txt` |
| Local AWS Python operations | boto3, including botocore | `scripts/create-license-key.py`, Lambda imports |
| Admin website | Plain HTML, CSS, JavaScript ES modules; no npm build | `ultronapp/README.md` |
| Local publishing | AWS CLI for backend/OTA; Firebase CLI for hosting/rules | Infrastructure templates, publishing script, Firebase configuration |

## What is available

| Item | Observed state |
|---|---|
| Windows | Windows 11 Home Single Language, 64-bit |
| Git | 2.56.0.windows.4; works |
| Java / javac | Temurin 17.0.21; both work |
| JAVA_HOME | Set at machine scope to the installed JDK |
| Android CLI | Installed; reports version 1.0.16625370 and launcher 1.1.16717390 |
| Gradle wrapper | Scripts and JAR exist; extracted Gradle 8.14.6 also exists in the user cache |
| Node.js / npm | 26.13.0 / 11.20.0; both work |
| Python / pip | Python 3.15.2 / pip 26.2.2; work outside sandbox restrictions |
| VS Code | Installed and available on PATH |
| PowerShell | Available in the current environment; a standalone PowerShell 7 installation was not separately verified |

Initial WindowsApps Python and Android CLI checks were blocked by the Codex sandbox. Retesting outside it confirmed that both tools work; these are not missing installations.

## What to install or configure

| Priority | Item | Reason / action |
|---|---|---|
| Required for Android builds | Android SDK platform 36, platform-tools, build-tools | The configured SDK directory `C:\Users\devxj\AppData\Local\Android\Sdk` does not exist. Install the packages below. |
| Required for the OTA publishing script | ANDROID_HOME | Currently unset. Point it at the SDK after installation. The app's existing `local.properties` already points to the same location. |
| Needed for local backend deployment and OTA publishing | AWS CLI v2 | `aws` is unavailable and no installation was found in the checked standard locations. Install it, then verify or renew authentication. |
| Needed for website/rules publishing | Firebase CLI | `firebase` is unavailable; the standard global npm installation was not found. Install with npm, then sign in. |
| Recommended for backend development | Python 3.12 alongside 3.15 | Match the deployed Lambda runtime and CI. Python 3.15 passes the existing offline tests, but that does not establish production-runtime or dependency compatibility. |
| Needed when running backend features locally | boto3 and trafilatura | Neither is installed in the checked Python environment. Use a Python 3.12 virtual environment. boto3 supplies botocore. Offline tests do not require these packages. |
| Optional | Android Studio | Helpful for the IDE, previews, SDK manager, and emulator management. Command-line builds can use the installed Android CLI. |
| Optional | Emulator and a system image | Needed only if testing without a physical Android phone. |
| Conditional | Phone manufacturer's USB debugging driver | Needed only if Windows/ADB cannot recognize the phone. Check after platform-tools are installed. |

Suggested Android package installation, using the Android CLI already installed:

```powershell
android --sdk="C:\Users\devxj\AppData\Local\Android\Sdk" sdk install platforms/android-36 platform-tools build-tools/35.0.0 build-tools/36.0.0
```

Both build-tools versions are listed intentionally: 35.0.0 is the plugin default, and 36.0.0 matches the repository's CI setup. SDK license acceptance may be required during setup.

After installation, set `ANDROID_HOME` to that SDK path. Add its `platform-tools` directory to the user PATH if you want to run `adb` directly. Reopen terminals after persistent environment changes. `ANDROID_SDK_ROOT` is unnecessary; if already set in another environment, it should agree with `ANDROID_HOME`.

For Firebase publishing:

```powershell
npm.cmd install -g firebase-tools
firebase login
```

For backend work, after adding Python 3.12, create a virtual environment outside tracked source and use that environment's Python:

```powershell
py -3.12 -m venv X:\tmp\kira-py312
X:\tmp\kira-py312\Scripts\python.exe -m pip install -r X:\KIRA\mcp\web-extract\requirements.txt boto3
```

The extraction packaging script downloads Linux x86_64 wheels for Python 3.12, so its Windows build path does not require Docker or WSL.

## Configuration and signing files survived

- `android/local.properties` exists, including release signing properties and a GitHub client ID. Secret values were not included in the audit.
- `android/app/google-services.json` exists, parses as JSON, and contains the correct Android application package.
- `android/keystore/nivi-release.jks` exists at the configured release path.
- `C:\Users\devxj\.ultron-android\debug.keystore` exists. Preserve it: this is the key selected by the project for debug/OTA update continuity.
- The default `.android/debug.keystore` also exists.
- `.aws/config` and AWS login/cache directories exist. Authentication was not verified because the AWS CLI is missing. An absent `.aws/credentials` file alone does not imply missing access when login-based authentication is used.

File presence does not verify keystore passwords, installed-phone certificate matching, Firebase authorization, or live AWS sessions. There is no need to regenerate these files as part of reinstalling the SDK.

## Verification performed

The existing backend test command completed successfully under installed Python 3.15.2:

```text
python -B -m unittest discover -s mcp/tests -q
Ran 63 tests — OK
```

Android compilation, Android unit tests, lint, APK installation, and connected-device checks remain unverified because the configured SDK is missing. After SDK setup, run from `X:\KIRA\android`:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
```

Gradle downloads project libraries and plugins from Google Maven, Maven Central, the plugin portal, and JitPack as needed. A cached Gradle distribution does not guarantee that every project dependency is already cached.

## Additional software not required by the current workflow

No separate system Gradle or Kotlin installation, Docker, WSL, AWS SAM CLI, local database server, Flutter, or full Visual Studio C++ toolchain is required by the checked build and deployment paths. ObjectBox and Firebase Android libraries are resolved through Gradle. The SAM-format infrastructure template explicitly documents deployment using AWS CLI CloudFormation commands without SAM CLI.

## Official references

- [Android CLI and SDK package management](https://developer.android.com/tools/sdkmanager)
- [AGP 8.13 compatibility, including JDK 17 and default build-tools 35.0.0](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [AWS CLI v2 installation](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
- [Firebase CLI installation and login](https://firebase.google.com/docs/cli)

