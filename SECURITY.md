# Security policy

## Sensitive material

Do not commit or embed any of the following:

- Flight-provider API keys or client secrets
- Android signing keystores or passwords
- Private backend credentials
- User access tokens
- Production database connection strings

Mobile applications can be inspected and decompiled. Provider authentication must happen on a backend service controlled by the project.

## Reporting a vulnerability

Open a private GitHub security advisory for the repository when available. Avoid placing exploitable details, credentials, or personal data in a public issue.

## Current data handling

Version `1.1.0-beta` stores search alerts locally in Android `SharedPreferences`. Android backup is disabled in the manifest. The app does not collect accounts, payment information, or live booking data.
