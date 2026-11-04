# Dokumentacja projektu

Końcowe rozwiązanie zawiera:
- **Aplikację mobilną** (później nazywaną *aplikacją*)
- **Backend** (nazywany *backendem*)
- **Aplikację internetową** (nazywaną *panelem admina*)

Nawigacja opiera się o sieć nadajników **iNode**, używających technologii **BLE** do propagacji swojego ID, dzięki czemu użytkownik wie, gdzie się znajduje.

---

## MVP 1

### Opis
- **Aplikacja** pokazuje realną lokalizację względem kilku pobliskich nadajników.
- **Panel admina** pozwala na dodawanie beaconów i przypisywanie im lokalizacji względem punktu.
- **Backend** obsługuje wszystkie wymagane dla tego etapu API, m.in.:
  - `POST /beacon` (`/POST/beacon`)
  - `DELETE /beacon/id?=ID` (`/DELETE/beacon/id?=ID`)
  - `GET /beacon/place/id?=ID` (`/GET/beacon/place/id?=ID`)
  - `DO UZGODNIENIA`

---

## MVP 2

### Opis
- **Aplikacja** jest w stanie prowadzić użytkownika w zakresie jednego budynku.
- **Aplikacja** pokazuje dane pokoju po kliknięciu, tj.:
  - **Sala zajęciowa** – nazwa, typ sali
  - **Pokój / biuro** – kto urzęduje w tym pokoju
  - **Inne** – w jakich godzinach jest otwarte
- **Panel admina** pozwala na przypisanie beaconom budynku i piętra, a także na określanie danych pokoi.

---

## MVP 3

### Opis
- **Aplikacja** pozwala na nawigację międzybudynkową.
- **Aplikacja** pokazuje dane pokoju po kliknięciu, tj.:
  - **Sala zajęciowa** – nazwa, typ sali, rozkład kiedy sala jest zajęta
  - **Pokój / biuro** – kto urzęduje w tym pokoju, kiedy ma konsultacje
  - **Inne** – w jakich godzinach jest otwarte

---

## Dodatkowe featury (jak zostanie czas)
- **TTS** – głosowe kierowanie dla osób ze znacznymi problemami wzrokowymi.
