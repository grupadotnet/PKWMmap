# PKWMmap

System nawigacji wewnątrzbudynkowej i kampusowej dla **Wydziału Mechanicznego Politechniki Krakowskiej (PK WM)** oparty na sieci nadajników **iNode BLE (Bluetooth Low Energy)**.

---

## Architektura i Stack Technologiczny

Projekt jest zorganizowany jako monorepo składające się z dwóch głównych części:

1. **Aplikacja mobilna (`frontend/`)** — **Kotlin Multiplatform (KMP) + Compose Multiplatform** (`Android` oraz `iOS`):
   - Cienki klient (*thin client*) komunikujący się z backendem po REST API (`GET`).
   - Skanowanie nadajników BLE **iNode** (`ID / MAC`, `RSSI`) i wyznaczanie pozycji użytkownika `(x, y)` w czasie rzeczywistym.
   - Wyświetlanie interaktywnych planów pięter (25 wyciętych schematów dla budynków `A, B, C, D, E, G, J, K`), rysowanie trasy nawigacji oraz podgląd szczegółów sal i biur.
2. **Backend (`backend/`)** — **Go + Docker**:
   - Przechowywanie rozmieszczenia beaconów, budynków, pięter, pokoi oraz harmonogramów zajęć i konsultacji.
   - Przechowywanie grafu nawigacji (korytarze, klatki schodowe, windy, łączniki na 1. piętrze, przejścia między budynkami) oraz wyznaczanie najkrótszej trasy (`GET /route`).

---

## Struktura Repozytorium

```text
pkwmmap/
├── backend/                  # Backend w Go + konfiguracja Docker
├── frontend/                 # Aplikacja mobilna KMP (Android & iOS)
│   ├── composeApp/src/
│   │   ├── commonMain/       # Współdzielone UI (Compose), modele, API (Ktor), pozycjonowanie BLE
│   │   ├── commonTest/       # Testy jednostkowe logiki współdzielonej
│   │   ├── androidMain/      # Implementacje natywne Android (BLE, TTS)
│   │   └── iosMain/          # Implementacje natywne iOS (CoreBluetooth, TTS)
│   ├── gradle/
│   └── iosApp/               # Projekt Xcode dla iOS
├── docs/
│   ├── api-contract.md       # Pełna specyfikacja REST API i payloadów JSON (MVP 1–3)
│   └── building-schemas/     # Schematy budynków A, B, C, D, E, G, J, K
│       └── floors/           # 25 wyciętych planów pojedynczych pięter (building_{code}_f{floor}.png)
├── documentation.md          # Wymagania funkcjonalne i opis etapów MVP 1–3
├── AGENTS.md                 # Wytyczne architektoniczne dla agentów AI
└── GEMINI.md                 # Konfiguracja kontekstu Gemini
```

---

## Roadmapa / Milestones

Szczegółowy zakres funkcjonalny znajduje się w [`documentation.md`](documentation.md), a pełny kontrakt endpointów i formatów JSON w [`docs/api-contract.md`](docs/api-contract.md).

- **[MVP 1 — Lokalizacja BLE (iNode)](https://github.com/grupadotnet/PKWMmap/milestone/1)**
  - Skanowanie pobliskich beaconów BLE i pokazywanie realnej lokalizacji użytkownika `(x, y)` na planie.
  - Endpointy backendu: `POST /beacon`, `DELETE /beacon?id=ID`, `GET /beacon/place?id=ID`, `GET /beacons`.
- **[MVP 2 — Nawigacja w jednym budynku i dane pokoi](https://github.com/grupadotnet/PKWMmap/milestone/2)**
  - Prowadzenie użytkownika do wybranego pokoju w obrębie jednego budynku (`GET /route`).
  - Szczegóły pokoju po kliknięciu (`GET /rooms/{id}`):
    - **Sala zajęciowa** – nazwa, typ sali
    - **Pokój / biuro** – osoby urzędujące w pokoju
    - **Inne** – godziny otwarcia
- **[MVP 3 — Nawigacja międzybudynkowa i harmonogramy](https://github.com/grupadotnet/PKWMmap/milestone/3)**
  - Nawigacja między budynkami kampusu (przez łączniki wewnątrzbudynkowe oraz przejścia zewnętrzne).
  - Rozszerzone dane pokoi: rozkład zajętości sal zajęciowych oraz godziny konsultacji pracowników.
  - Wyszukiwarka sal i prowadzących (`GET /search?q=...`).
- **[Extra — TTS (Głosowa nawigacja)](https://github.com/grupadotnet/PKWMmap/milestone/4)**
  - Głosowe komunikaty nawigacyjne (*Text-to-Speech*) dla osób ze znacznymi problemami wzrokowymi.

---

## Obsługiwane Budynki i Plany Pięter

W katalogu [`docs/building-schemas/floors/`](docs/building-schemas/floors/) (oraz w zasobach aplikacji mobilnej `composeResources/drawable/`) znajduje się **25 gotowych planów pięter**:

| Budynek | Liczba pięter | Identyfikatory pięter (`floor_id`) | Uwagi |
| :--- | :---: | :--- | :--- |
| **Budynek A** | 7 | `a-f0` … `a-f6` (Parter – Piętro VI) | 2 wejścia główne, windy, stołówka/bufet |
| **Budynek B** | 3 | `b-f0` … `b-f2` (Parter – Piętro II) | Łącznik międzybudynkowy na Piętrze I |
| **Budynek C** | 3 | `c-f0` … `c-f2` (Parter – Piętro II) | Aula C, łącznik na Piętrze I |
| **Budynek D** | 3 | `d-f0` … `d-f2` (Parter – Piętro II) | Łącznik na Piętrze I |
| **Budynek E** | 1 | `e-f0` (Parter) | Układ parterowy |
| **Budynek G** | 2 | `g-f0` … `g-f1` (Parter – Piętro I) | Układ z dużymi salami `G18`, `G23`, `G127` |
| **Budynek J** | 3 | `j-f0` … `j-f2` (Parter – Piętro II) | Łącznik na Parterze i Piętrze I |
| **Budynek K** | 3 | `k-f0` … `k-f2` (Parter – Piętro II) | Łącznik na Parterze i Piętrze I |

---

## Dokumentacja Techniczna

- [`documentation.md`](documentation.md) — wymagania projektowe i podział na MVP.
- [`docs/api-contract.md`](docs/api-contract.md) — specyfikacja REST API, układ współrzędnych `(x, y)` (`0.0..1.0`), wymiary grafik pięter oraz przykładowe odpowiedzi JSON dla zespołu backendowego i mobilnego.
