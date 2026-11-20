# PKWMmap — Backend REST API Contract (MVP 1 – MVP 3)

This document defines the HTTP/JSON contract between the **KMP Mobile App (`frontend/`)** and the **Go Backend (`backend/`)**.

## Design Principles (Thin Mobile Client)

1. **Read-Only Mobile Client**: The mobile app only consumes `GET` endpoints. All data mutation (`POST`, `PUT`, `DELETE` for beacons, buildings, floors, rooms, schedules, and navigation graphs) is performed on the backend (via DB migrations/seed scripts, Swagger UI, or admin scripts).
2. **Per-Floor Cropped Schemas & Normalized `(x, y)` Coordinates**:
   - All composite building schemas in `docs/building-schemas/building_{a..k}.png` have been sliced into **25 individual per-floor images** stored in:
     - `docs/building-schemas/floors/building_{code}_f{floor}.png` (for backend reference / coordinate mapping)
     - `frontend/composeApp/src/commonMain/composeResources/drawable/building_{code}_f{floor}.png` (bundled directly in the mobile app)
   - All `(x, y)` coordinates for beacons, rooms, and route waypoints are **normalized (`0.0` to `1.0`)** relative to the cropped floor image (`x = px / width_px`, `y = py / height_px`, where `(0.0, 0.0)` is the top-left corner and `(1.0, 1.0)` is the bottom-right corner of `building_{code}_f{floor}.png`).
   - `tx_power` is the calibrated RSSI at 1 meter (in dBm, e.g. `-59`), used to estimate distance from BLE RSSI.
3. **Server-Side Pathfinding**: The backend stores the walkable navigation graph (corridor nodes, doors, stairs, elevators, 1st-floor indoor *łączniki*, and outdoor connectors) and runs shortest-path routing (A* / Dijkstra). The mobile app simply requests `GET /route` and draws the returned polyline points on the floor image.

---

## Reference Table: Buildings & Cropped Floor Schemas

| Building ID | Building Name | Floor ID | Floor Number | Label | Image File (`docs/building-schemas/floors/`) | Image Size (`width_px × height_px`) |
| :--- | :--- | :--- | :---: | :--- | :--- | :---: |
| `a` | Budynek A | `a-f0` | `0` | Parter | `building_a_f0.png` | `221 × 950` |
| `a` | Budynek A | `a-f1` | `1` | Piętro I | `building_a_f1.png` | `223 × 931` |
| `a` | Budynek A | `a-f2` | `2` | Piętro II | `building_a_f2.png` | `203 × 952` |
| `a` | Budynek A | `a-f3` | `3` | Piętro III | `building_a_f3.png` | `192 × 949` |
| `a` | Budynek A | `a-f4` | `4` | Piętro IV | `building_a_f4.png` | `189 × 949` |
| `a` | Budynek A | `a-f5` | `5` | Piętro V | `building_a_f5.png` | `189 × 947` |
| `a` | Budynek A | `a-f6` | `6` | Piętro VI | `building_a_f6.png` | `189 × 949` |
| `b` | Budynek B | `b-f0` | `0` | Parter | `building_b_f0.png` | `361 × 782` |
| `b` | Budynek B | `b-f1` | `1` | Piętro I *(łącznik)* | `building_b_f1.png` | `558 × 767` |
| `b` | Budynek B | `b-f2` | `2` | Piętro II | `building_b_f2.png` | `276 × 783` |
| `c` | Budynek C | `c-f0` | `0` | Parter *(Aula C)* | `building_c_f0.png` | `335 × 813` |
| `c` | Budynek C | `c-f1` | `1` | Piętro I *(łącznik)* | `building_c_f1.png` | `286 × 816` |
| `c` | Budynek C | `c-f2` | `2` | Piętro II | `building_c_f2.png` | `287 × 813` |
| `d` | Budynek D | `d-f0` | `0` | Parter | `building_d_f0.png` | `374 × 822` |
| `d` | Budynek D | `d-f1` | `1` | Piętro I *(łącznik)* | `building_d_f1.png` | `297 × 823` |
| `d` | Budynek D | `d-f2` | `2` | Piętro II | `building_d_f2.png` | `286 × 824` |
| `e` | Budynek E | `e-f0` | `0` | Parter | `building_e_f0.png` | `1467 × 825` |
| `g` | Budynek G | `g-f0` | `0` | Parter | `building_g_f0.png` | `719 × 810` |
| `g` | Budynek G | `g-f1` | `1` | Piętro I | `building_g_f1.png` | `474 × 829` |
| `j` | Budynek J | `j-f0` | `0` | Parter | `building_j_f0.png` | `700 × 836` |
| `j` | Budynek J | `j-f1` | `1` | Piętro I *(łącznik)* | `building_j_f1.png` | `653 × 860` |
| `j` | Budynek J | `j-f2` | `2` | Piętro II | `building_j_f2.png` | `277 × 820` |
| `k` | Budynek K | `k-f0` | `0` | Parter | `building_k_f0.png` | `652 × 865` |
| `k` | Budynek K | `k-f1` | `1` | Piętro I *(łącznik)* | `building_k_f1.png` | `650 × 906` |
| `k` | Budynek K | `k-f2` | `2` | Piętro II | `building_k_f2.png` | `279 × 829` |

---

## MVP 1 — Beacons & Live BLE Positioning

### 1. `GET /beacon/place?id={id}`
Returns the placement of a single beacon by its iNode hardware ID / MAC address (or internal ID).

**Response `200 OK`**:
```json
{
  "id": "inode-a0-01",
  "mac": "D0:39:72:A1:B2:C3",
  "name": "Budynek A - Parter Wejście",
  "building_id": "a",
  "floor_id": "a-f0",
  "x": 0.52,
  "y": 0.48,
  "tx_power": -59
}
```

### 2. `GET /beacons`
Optional query parameter: `?floor_id={floorId}` or `?building_id={buildingId}`.
Returns all registered beacons so the mobile app can preload/cache them and compute live `(x, y)` at 5–10 Hz without waiting for network calls on every BLE packet.

**Response `200 OK`**:
```json
{
  "beacons": [
    {
      "id": "inode-a0-01",
      "mac": "D0:39:72:A1:B2:C3",
      "name": "Budynek A - Parter Północ",
      "building_id": "a",
      "floor_id": "a-f0",
      "x": 0.55,
      "y": 0.22,
      "tx_power": -59
    },
    {
      "id": "inode-a0-02",
      "mac": "D0:39:72:A1:B2:C4",
      "name": "Budynek A - Parter Środek",
      "building_id": "a",
      "floor_id": "a-f0",
      "x": 0.55,
      "y": 0.48,
      "tx_power": -59
    }
  ]
}
```

### 3. `POST /beacon` *(Backend Admin / Seed)*
Registers or updates a beacon.

**Request Body**:
```json
{
  "id": "inode-a0-01",
  "mac": "D0:39:72:A1:B2:C3",
  "name": "Budynek A - Parter Północ",
  "building_id": "a",
  "floor_id": "a-f0",
  "x": 0.55,
  "y": 0.22,
  "tx_power": -59
}
```

### 4. `DELETE /beacon?id={id}` *(Backend Admin / Seed)*
Deletes a beacon by ID. Returns `200 OK` or `204 No Content`.

---

## MVP 2 — Single-Building Navigation & Room Details

### 1. `GET /buildings`
Lists available buildings (`a`, `b`, `c`, `d`, `e`, `g`, `j`, `k`) and their floors.

**Response `200 OK`**:
```json
{
  "buildings": [
    {
      "id": "a",
      "name": "Budynek A",
      "code": "A",
      "floors": [
        { "id": "a-f0", "number": 0, "name": "Parter", "image_name": "building_a_f0.png" },
        { "id": "a-f1", "number": 1, "name": "Piętro I", "image_name": "building_a_f1.png" },
        { "id": "a-f2", "number": 2, "name": "Piętro II", "image_name": "building_a_f2.png" },
        { "id": "a-f3", "number": 3, "name": "Piętro III", "image_name": "building_a_f3.png" },
        { "id": "a-f4", "number": 4, "name": "Piętro IV", "image_name": "building_a_f4.png" },
        { "id": "a-f5", "number": 5, "name": "Piętro V", "image_name": "building_a_f5.png" },
        { "id": "a-f6", "number": 6, "name": "Piętro VI", "image_name": "building_a_f6.png" }
      ]
    }
  ]
}
```

### 2. `GET /floors/{floorId}`
Returns the floor dimensions, drawable image name, all clickable rooms on the floor, and beacons assigned to this floor.

**Response `200 OK`**:
```json
{
  "id": "a-f1",
  "building_id": "a",
  "number": 1,
  "name": "Piętro I",
  "image_name": "building_a_f1.png",
  "width_px": 223,
  "height_px": 931,
  "beacons": [
    {
      "id": "inode-a1-01",
      "mac": "D0:39:72:A1:B2:D1",
      "x": 0.55,
      "y": 0.30,
      "tx_power": -59
    }
  ],
  "rooms": [
    {
      "id": "A104",
      "name": "A104",
      "type": "classroom",
      "x": 0.34,
      "y": 0.24
    },
    {
      "id": "A112",
      "name": "A112",
      "type": "office",
      "x": 0.72,
      "y": 0.23
    },
    {
      "id": "A123",
      "name": "A123 (Stołówka / Bufet)",
      "type": "other",
      "x": 0.36,
      "y": 0.65
    }
  ]
}
```

### 3. `GET /rooms/{roomId}`
Returns room metadata when the user taps a room on the map.
- `type` is one of: `"classroom"` | `"office"` | `"other"`.
- In **MVP 2**, `classroom_type`, `occupants`, and `opening_hours` are populated.
- In **MVP 3**, `schedule` (for `classroom`) and `consultations` inside `occupants` (for `office`) are populated.

**Response `200 OK` (Classroom — Sala zajęciowa)**:
```json
{
  "id": "A104",
  "building_id": "a",
  "floor_id": "a-f1",
  "name": "Sala A104",
  "type": "classroom",
  "classroom_type": "Sala wykładowa / ćwiczeniowa",
  "occupants": [],
  "opening_hours": null,
  "schedule": []
}
```

**Response `200 OK` (Office — Pokój / biuro)**:
```json
{
  "id": "A112",
  "building_id": "a",
  "floor_id": "a-f1",
  "name": "Pokój A112",
  "type": "office",
  "classroom_type": null,
  "occupants": [
    {
      "name": "dr inż. Jan Kowalski",
      "title": "Adiunkt",
      "consultations": []
    }
  ],
  "opening_hours": null,
  "schedule": []
}
```

**Response `200 OK` (Other — Inne, np. Stołówka / Sklep / Dziekanat)**:
```json
{
  "id": "A123",
  "building_id": "a",
  "floor_id": "a-f1",
  "name": "Bufet A123",
  "type": "other",
  "classroom_type": null,
  "occupants": [],
  "opening_hours": "Pon-Pt 08:00 - 16:00",
  "schedule": []
}
```

### 4. `GET /route`
Computes the shortest walkable path from the user's current position to a target room.

**Query Parameters**:
- `from_floor` (required) — current `floor_id` (e.g. `a-f0`)
- `from_x` (required) — current normalized user X (`0.0..1.0`, e.g. `0.52`)
- `from_y` (required) — current normalized user Y (`0.0..1.0`, e.g. `0.48`)
- `to_room_id` (required) — destination `room_id` (e.g. `A104`)
- `from_building` (optional in MVP 2, used in MVP 3) — current `building_id` (e.g. `a`)

**Response `200 OK`**:
```json
{
  "destination_room_id": "A104",
  "steps": [
    {
      "step_index": 0,
      "building_id": "a",
      "floor_id": "a-f0",
      "is_outdoor": false,
      "instruction": "Idź do klatki schodowej lub windy na środku korytarza",
      "points": [
        { "x": 0.52, "y": 0.48 },
        { "x": 0.45, "y": 0.51 }
      ]
    },
    {
      "step_index": 1,
      "building_id": "a",
      "floor_id": "a-f1",
      "is_outdoor": false,
      "instruction": "Na 1. piętrze idź korytarzem w górę planu do sali A104 po lewej",
      "points": [
        { "x": 0.45, "y": 0.51 },
        { "x": 0.55, "y": 0.51 },
        { "x": 0.55, "y": 0.24 },
        { "x": 0.34, "y": 0.24 }
      ]
    }
  ]
}
```

---

## MVP 3 — Multi-Building Navigation, Schedules & Search

### 1. Extended `GET /rooms/{roomId}` (with `schedule` & `consultations`)

**Classroom with occupancy schedule**:
```json
{
  "id": "A104",
  "building_id": "a",
  "floor_id": "a-f1",
  "name": "Sala A104",
  "type": "classroom",
  "classroom_type": "Sala ćwiczeniowa",
  "occupants": [],
  "opening_hours": null,
  "schedule": [
    {
      "day_of_week": "MONDAY",
      "start_time": "08:15",
      "end_time": "09:45",
      "subject": "Programowanie Urządzeń Mobilnych",
      "lecturer": "dr inż. Jan Kowalski"
    },
    {
      "day_of_week": "MONDAY",
      "start_time": "10:00",
      "end_time": "11:30",
      "subject": "Sieci Komputerowe",
      "lecturer": "dr Anna Nowak"
    }
  ]
}
```

**Office with consultation hours**:
```json
{
  "id": "A112",
  "building_id": "a",
  "floor_id": "a-f1",
  "name": "Pokój A112",
  "type": "office",
  "classroom_type": null,
  "occupants": [
    {
      "name": "dr inż. Jan Kowalski",
      "title": "Adiunkt",
      "consultations": [
        {
          "day_of_week": "WEDNESDAY",
          "start_time": "12:00",
          "end_time": "13:30",
          "notes": "Pokój A112"
        }
      ]
    }
  ],
  "opening_hours": null,
  "schedule": []
}
```

### 2. Multi-Building `GET /route`
Uses the same schema as MVP 2 `GET /route`.
- Buildings **B, C, D, J, K** are connected indoors via **Piętro I corridors (*łączniki*)** (`b-f1`, `c-f1`, `d-f1`, `j-f1`, `k-f1`), so a route from Building B (`b-f0`) to Building C (`C204` on `c-f2`) can go:
  1. `b-f0` → stairs to `b-f1`
  2. `b-f1` → through 1st-floor *łącznik* into `c-f1`
  3. `c-f1` → stairs to `c-f2` → `C204`
- For unconnected buildings (e.g. Building A to Building G), the route includes an outdoor transition step (`"is_outdoor": true`) from Building A's Parter entrance (`a-f0`) to Building G's Parter entrance (`g-f0`).

### 3. `GET /search?q={query}`
Searches rooms across all buildings (`A, B, C, D, E, G, J, K`) by room code (`A104`, `Aula C`, `G23`), classroom type, or lecturer surname.

**Response `200 OK`**:
```json
{
  "results": [
    {
      "room_id": "A112",
      "room_name": "Pokój A112",
      "type": "office",
      "building_id": "a",
      "building_name": "Budynek A",
      "floor_id": "a-f1",
      "floor_name": "Piętro I",
      "matched_text": "dr inż. Jan Kowalski"
    }
  ]
}
```

---

## Standard Error Format

All `4xx` / `5xx` responses from the backend should return:
```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Beacon with id 'inode-99' was not found"
  }
}
```
