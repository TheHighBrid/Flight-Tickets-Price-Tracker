# Changelog

## 1.1.1-beta

- Fixed CI after the Android Gradle Plugin 9.3.0 upgrade by moving the build to Gradle 9.5.0.
- Removed a duplicate Android workflow that depended on a missing Gradle wrapper.
- Removed an invalid GitHub Packages workflow that called an unconfigured `publish` task.
- Added explicit Android SDK package installation and a core Java smoke test to CI.
- Hardened the release workflow with version and release-note validation.
- Added APK checksum generation and automatic GitHub prerelease publishing.

## 1.1.0-beta

- Fixed airport normalization that caused the existing San Francisco unit test to fail.
- Added strict route, date, passenger, and target validation.
- Added airport suggestions, trip dates, display currency, and improved fare details.
- Added multiple locally stored alerts with safe legacy migration and deletion.
- Added manual alert evaluation with explicit demo-mode messaging.
- Fixed stop-label grammar and corrupted-alert startup crashes.
- Removed forced portrait orientation and disabled Android backup.
- Added Java 17 desugaring, stronger tests, Android lint, pull-request CI, and Dependabot.
- Reworked beta releases to publish an installable debug-signed APK instead of an unsigned release APK.
