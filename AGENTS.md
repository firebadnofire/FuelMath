# Fuel Math – MVP Scope

## 1. Goal

Build a simple, fast app to track fuel usage per vehicle and calculate real-world cost and efficiency metrics.

The app must be faster and more convenient than using a spreadsheet.

---

## 2. Core Features

### 2.1 Vehicle Management

Users can create and manage multiple vehicles.

Each vehicle includes:
- Name (e.g., "2009 Smart Fortwo")
- Fuel tank capacity (gallons or liters)
- Unit system:
  - Distance: miles or kilometers
  - Volume: gallons or liters

Minimum requirements:
- Create vehicle
- Select vehicle
- Delete vehicle

---

### 2.2 Fuel Log Entries

Each vehicle has its own fuel log.

Each entry includes:
- Date
- Time
- Odometer reading
- Fuel amount (gallons/liters)
- Price per unit (per gallon/liter)
- Full tank flag (yes/no)

Derived per entry:
- Total cost = fuel amount × price per unit

Minimum requirements:
- Add entry
- View entry list (per vehicle)
- Delete entry

---

### 2.3 Core Calculations

Calculations must update automatically when entries are added or removed.

#### MPG / Efficiency

Only calculated between **full tank → full tank** entries.

- Distance = difference in odometer readings
- Fuel used = sum of fuel between full tanks
- MPG (or L/100km equivalent)

If no valid full-to-full data exists:
- Do not display MPG

---

#### Cost Tracking

Per vehicle:
- Total fuel cost (lifetime)
- Cost per mile (or km):
  - total fuel cost / total distance

---

#### Distance Tracking

- Distance per entry:
  - current odometer - previous odometer

- Total distance:
  - last odometer - first odometer

---

### 2.4 Vehicle Summary Card (Main Screen)

Each vehicle displays:

- Name
- Last recorded MPG (if available)
- Total fuel cost
- Cost per mile/km
- Last fill-up date

---

## 3. UI Requirements

### 3.1 Main Screen

- List of vehicles (cards)
- Each card shows summary stats
- Tap → opens vehicle detail screen
- Button: "Add Vehicle"

---

### 3.2 Vehicle Detail Screen

- Fuel log list (newest first)
- Button: "Add Entry"

Each log item shows:
- Date/time
- Fuel amount
- Total cost
- Odometer
- Indicator if full tank

---

### 3.3 Add Entry Screen

Form inputs:
- Date (default: today)
- Time (default: now)
- Odometer
- Fuel amount
- Price per unit
- Full tank toggle

Submit:
- Saves entry
- Recalculates all metrics
- Returns to vehicle detail screen

---

## 4. Data Model (Simplified)

### Vehicle
- id
- name
- tank_capacity
- distance_unit (mi/km)
- volume_unit (gal/L)

### FuelEntry
- id
- vehicle_id
- date_time
- odometer
- fuel_amount
- price_per_unit
- is_full_tank

Derived (not stored or optional cache):
- total_cost

---

## 5. Non-Functional Requirements

- Fast entry: adding a log should take <10 seconds
- Offline-first (no account required)
- Data stored locally (persistent)
- Calculations must be deterministic and repeatable
- App must handle:
  - missing full tank data
  - irregular fill-ups

---

## 6. Explicitly Out of Scope (MVP)

Do NOT implement:

- GPS tracking
- Gas station lookup
- Social features
- Maintenance tracking (oil, tires, etc.)
- Cloud sync / accounts
- Notifications
- Graphs / analytics dashboards

---

## 7. Success Criteria

MVP is complete when:

- User can:
  - create a vehicle
  - log multiple fuel entries
  - see accurate MPG (with full tank logic)
  - see total cost and cost per mile/km

- App is:
  - stable
  - fast to use
  - requires minimal input friction

---

## 8. additional requirements

- Graphs (MPG, cost over time)
- Fuel price trends
- Remaining range estimation
- Export (CSV)
- Backup / restore
- Multi-device sync
- Maintenance tracking

---
