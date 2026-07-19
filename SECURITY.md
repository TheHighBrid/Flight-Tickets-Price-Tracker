# Security policy

## Secrets

Do not commit or paste any of the following into the repository:

- Amadeus API keys or secrets
- backend access tokens
- Android signing keystores or passwords
- production database credentials
- cloud deployment credentials

Amadeus automatically revokes API credentials that become publicly searchable. Backend credentials must be stored as deployment environment variables or secret-manager values.

## Android direct mode

Direct mode encrypts credentials with Android Keystore and AES-GCM. It is intended for a privately controlled device. It cannot provide the same secret isolation as a server because a compromised or rooted device can expose application data.

## Backend mode

Use HTTPS only. Set `FLIGHT_API_ACCESS_TOKEN` to a long random value and enter the same value in the Android configuration. Apply rate limiting at the hosting edge before broad distribution.

## Reporting

Open a private security advisory for vulnerabilities that could expose credentials, tokens, personal data, or paid API usage.
