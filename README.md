# Vhaya (Android)

Native Kotlin and Jetpack Compose ride-request, driver dispatch, and operations oversight application for Polokwane and South Africa.

## Core Features

- **Authentication & Role Routing**: Firebase Email/Password sign-in, account creation, email verification gate, password reset, and automatic role routing across **Requester**, **Driver**, and **Admin** experiences.
- **Requester (Rider) Flow**: Street-level OpenStreetMap canvas (`OsmRouteMap`) with interactive pin picking, place search, route previews, fare estimates & custom fare offers, scheduled pickups, Paystack card/wallet/cash payment options, live driver tracking, 6-digit pickup PIN verification, in-trip driver messaging, support case reporting, and post-trip ratings.
- **Driver Flow**: Onboarding application with vehicle catalog search, SA registration formatting, licence expiry tracking, and document upload (ID, driver licence, vehicle registration, operating permit); online/offline dispatch toggle, incoming ride request alerts with counter-offer/accept actions, active trip progression (arrive, PIN verification, complete), Weekly Driver Pass subscription, and earnings ledger.
- **Admin Operations Console**: Platform KPIs, activity log, user & role management, suspension controls, driver compliance & application reviews, support case replies/triage, and driver payout recording & weekly batch reconciliation.
- **Offline Caching**: Local Room database (`WayfareDatabase`) caching trips, saved destinations, trusted contacts, wallet transactions, driver applications, support cases, payouts, and activity logs.

## Configuration

Configure the following environment variables in the **Secrets** panel in AI Studio (or in a local `.env` file, never committed):

- `VHAYA_API_BASE_URL`: Base URL of the Vhaya backend API server.
- `FIREBASE_WEB_API_KEY`: Firebase Web API key used for Identity Toolkit authentication.

## Release safety

- Mock payment UI and result calls are enabled only in debug builds. Release builds fail closed if the payment provider does not return a checkout URL; the backend must also reject mock results in production.
- Release APK/AAB packaging is blocked until a dedicated release keystore is available. Configure `VHAYA_RELEASE_KEYSTORE_PATH`, `VHAYA_RELEASE_KEYSTORE_PASSWORD`, `VHAYA_RELEASE_KEY_ALIAS`, and `VHAYA_RELEASE_KEY_PASSWORD` in the build environment. Store passwords in AI Studio Secrets or CI environment secrets, keep the keystore file outside source control, and do not reuse a debug key.

## Build & Run

- Built with Gradle (Kotlin DSL), AGP 9.1.1, Kotlin 2.3.20, Jetpack Compose, Material 3, Room (KSP), Kotlinx Serialization, and Coil.
- Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`.
