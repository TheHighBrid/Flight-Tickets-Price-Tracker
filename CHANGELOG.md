# Changelog

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
