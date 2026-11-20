# AGENTS.md — PKWMmap (Frontend / Mobile)

Instructions for AI coding agents working in the **PKWMmap** monorepo.

## Role & Ownership Boundary

We own **Frontend / Mobile (`frontend/`)** and **API Contract Docs (`docs/`)**.
- **DO NOT** create, modify, or delete files inside `backend/` unless explicitly instructed by the user. The Go + Docker backend is developed by a separate backend team.
- All backend integration expectations must be documented in `documentation.md` and `docs/api-contract.md`.

## Project Overview

**PKWMmap** is an indoor/campus navigation system based on **iNode BLE beacons**.
Functional requirements and MVP milestones live in `documentation.md`, and the HTTP/JSON contract lives in `docs/api-contract.md`.

Core components:
1. **Mobile App (`frontend/`)** — Kotlin Multiplatform (KMP) + Compose Multiplatform thin client targeting **Android** (`androidMain`) and **iOS** (`iosMain`, `iosApp`).
2. **Backend (`backend/`)** — External Go + Docker service (owned by the backend team) providing beacon coordinates, floor maps, room details/schedules, and shortest-path routing (`GET /route`).

## Thin Mobile Client Architecture

- **Read-Only Mobile Client**: The mobile app only calls `GET` endpoints. Beacon registration (`POST /beacon`, `DELETE /beacon`), building/floor setup, and room data seeding happen strictly on the backend.
- **BLE Positioning (MVP 1)**: The mobile app scans iNode BLE advertisements (`id`/`mac`, `rssi`), caches beacon coordinates from `GET /beacon/place?id=ID` / `GET /beacons`, and computes live user `(x, y)` coordinates locally in `commonMain` for smooth 5–10 Hz UI updates.
- **Server-Side Routing (MVP 2 & MVP 3)**: Pathfinding (A* / Dijkstra) runs on the backend via `GET /route`. The mobile app sends the user's current `(floor_id, x, y)` and target `room_id`, and renders the returned polyline waypoints and step instructions.
- **Mock-First Development**: Always provide an in-memory `FakeNavigationRepository` and simulated `BleScanner` in `commonMain` alongside the Ktor HTTP client so mobile UI and positioning can be developed and tested without waiting on the live backend or physical beacons.

## Repository Structure

```text
pkwmmap/
├── backend/          # Owned by Backend Team (DO NOT TOUCH)
├── frontend/         # KMP Mobile App (Android & iOS)
│   ├── composeApp/
│   │   └── src/
│   │       ├── commonMain/
│   │       │   ├── composeResources/
│   │       │   └── kotlin/pkwmmap/
│   │       │       ├── data/
│   │       │       │   ├── api/          # Ktor client & JSON DTOs
│   │       │       │   └── repository/   # Interfaces + Fake & Remote repos
│   │       │       ├── domain/
│   │       │       │   ├── model/        # Beacon, Building, Floor, Room, RouteStep
│   │       │       │   └── positioning/  # RSSI filtering & trilateration solver
│   │       │       ├── platform/
│   │       │       │   ├── ble/          # BleScanner expect/actual interface
│   │       │       │   └── tts/          # TtsSpeaker expect/actual interface
│   │       │       └── ui/
│   │       │           ├── map/          # Floor canvas, live dot, route polyline
│   │       │           ├── room/         # Room bottom sheet (MVP 2 & 3 details)
│   │       │           └── search/       # Room & lecturer search UI
│   │       ├── commonTest/kotlin/        # Unit tests (trilateration, ViewModels, DTOs)
│   │       ├── androidMain/kotlin/       # Android BLE scanner & Android TTS
│   │       └── iosMain/kotlin/           # iOS CoreBluetooth scanner & iOS TTS
│   ├── gradle/
│   └── iosApp/
├── docs/
│   └── api-contract.md  # Full REST API contract & JSON payloads for Backend Team
├── documentation.md     # Product requirements & MVP roadmap (Polish)
└── README.md
```

## Engineering Guidelines (KMP Mobile)

- Put all shared UI, state management (`ViewModel` / `StateFlow`), DTOs, Ktor networking, and BLE positioning math in `commonMain`.
- Isolate platform-specific hardware APIs (BLE scanning, runtime permissions, TTS) behind interfaces or `expect`/`actual` declarations in `androidMain` and `iosMain`.
- Keep changes scoped to the requested task; do not refactor unrelated packages.
- Update `docs/api-contract.md` whenever mobile-backend endpoint requirements or JSON payloads evolve.

## Verification Before Finishing

- Run Gradle build/tests for the affected KMP targets (`./gradlew check` or target compile tasks) once Gradle wrapper is initialized.
- Check `git status` and `git diff` to ensure `backend/` remains untouched and no temporary files are left behind.
