# Dokumentacja projektu

Końcowe rozwiązanie zawiera:
- **Aplikację mobilną** (później nazywaną *aplikacją*) – cienki klient (KMP: Android / iOS)
- **Backend** (nazywany *backendem*) – Go + Docker (zarządzanie danymi, graf nawigacji, wyznaczanie tras)

Nawigacja opiera się o sieć nadajników **iNode**, używających technologii **BLE** do propagacji swojego ID, dzięki czemu użytkownik wie, gdzie się znajduje. Zarządzanie beaconami, budynkami, piętrami i pokojami odbywa się bezpośrednio po stronie backendu (seed bazy danych / Swagger / endpointy administracyjne).

Szczegółowa specyfikacja payloadów JSON dla każdego etapu znajduje się w pliku [`docs/api-contract.md`](docs/api-contract.md).

---

## MVP 1

### Opis
- **Aplikacja** skanuje pobliskie nadajniki BLE (ID + RSSI), pobiera ich współrzędne z backendu i pokazuje realną lokalizację użytkownika `(x, y)` względem kilku pobliskich nadajników.
- **Backend** przechowuje dane beaconów oraz obsługuje wszystkie wymagane dla tego etapu API:
  - `POST /beacon` – dodanie / aktualizacja beacona i jego pozycji `(id, mac, x, y, tx_power, floor_id)`
  - `DELETE /beacon?id=ID` – usunięcie beacona
  - `GET /beacon/place?id=ID` – pobranie pozycji `(x, y, tx_power, floor_id)` konkretnego beacona
  - `GET /beacons` – pobranie listy wszystkich zarejestrowanych beaconów (do lokalnego cache'u aplikacji mobilnej)

---

## MVP 2

### Opis
- **Aplikacja** jest w stanie prowadzić użytkownika w zakresie jednego budynku (rysowanie trasy wyznaczonej przez backend na planie piętra).
- **Aplikacja** pokazuje dane pokoju po kliknięciu, tj.:
  - **Sala zajęciowa** (`classroom`) – nazwa, typ sali
  - **Pokój / biuro** (`office`) – kto urzęduje w tym pokoju
  - **Inne** (`other`) – w jakich godzinach jest otwarte
- **Backend** przechowuje strukturę budynku (piętra, pokoje, graf korytarzy/schodów/wind), wyznacza najkrótszą ścieżkę (np. A* / Dijkstra) i udostępnia API dla aplikacji:
  - `GET /buildings` oraz `GET /buildings/{id}/floors` – lista budynków i ich pięter
  - `GET /floors/{id}` – plan piętra (wymiary / URL obrazu), lista pokoi (współrzędne na mapie) oraz lista beaconów przypisanych do piętra
  - `GET /rooms/{id}` – szczegóły pokoju (`type`: `classroom` | `office` | `other`, nazwa, typ sali, osoby urzędujące, godziny otwarcia)
  - `GET /route?from_floor={floorId}&from_x={x}&from_y={y}&to_room_id={roomId}` – wyznaczenie trasy wewnątrz budynku (zwraca uporządkowaną listę odcinków `[{floor_id, points: [{x, y}], instruction}]` gotową do narysowania w aplikacji)
  - Endpointy administracyjne / seedujące (`POST` / `PUT` / `DELETE`) dla budynków, pięter, pokoi oraz węzłów/krawędzi grafu nawigacji

---

## MVP 3

### Opis
- **Aplikacja** pozwala na nawigację międzybudynkową oraz wyszukiwanie sal i pracowników.
- **Aplikacja** pokazuje dane pokoju po kliknięciu, tj.:
  - **Sala zajęciowa** (`classroom`) – nazwa, typ sali, rozkład kiedy sala jest zajęta
  - **Pokój / biuro** (`office`) – kto urzęduje w tym pokoju, kiedy ma konsultacje
  - **Inne** (`other`) – w jakich godzinach jest otwarte
- **Backend** rozszerza graf nawigacji o połączenia między budynkami oraz harmonogramy pokoi:
  - `GET /rooms/{id}` (lub `GET /rooms/{id}/schedule`) – rozszerzone o harmonogram zajętości sali (`schedule`) oraz godziny konsultacji (`consultations`)
  - `GET /route?from_building={bId}&from_floor={fId}&from_x={x}&from_y={y}&to_room_id={roomId}` – wyznaczanie trasy międzybudynkowej (odcinki zawierają `building_id`, `floor_id`, punkty `[{x, y}]` oraz instrukcję tekstową)
  - `GET /search?q={query}` – wyszukiwanie pokoi po numerze/nazwie sali, typie sali lub nazwisku prowadzącego

---

## Dodatkowe featury (jak zostanie czas)
- **TTS** – głosowe kierowanie dla osób ze znacznymi problemami wzrokowymi (aplikacja odczytuje pole `instruction` zwracane przez backend dla kolejnych kroków w `GET /route`).
