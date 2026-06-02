# Vehicle Maintenance Checkbook – Full Scope (MVP → v1)

## 1. Goal

Build a per-vehicle preventative maintenance system that helps users keep vehicles healthy long-term by:

* Tracking completed maintenance
* Predicting upcoming maintenance
* Surfacing what should be done next
* Measuring cost and efficiency over time

---

## 2. Core Principles

* Prioritize actionable insights over raw data
* Keep UI simple and consistent across all maintenance items
* Support both mileage-based and time-based intervals
* Ensure all data structures are reusable and scalable

---

## 3. Data Model

### Vehicle

* id
* name (user-defined)
* make
* model
* year
* current_mileage
* fuel_type (gasoline/diesel/etc)
* tank_capacity

### MaintenanceCategory

* id
* name (Engine, Fluids, Wear Items, Electrical, Critical)

### MaintenanceItem

* id
* vehicle_id
* category_id
* name (e.g. Oil Change, Spark Plugs)
* interval_miles (nullable)
* interval_time_days (nullable)
* last_service_mileage
* last_service_date
* last_service_cost
* notes

### FuelLog

* id
* vehicle_id
* date
* mileage
* fuel_amount
* price_per_unit

### OilLog

* id
* vehicle_id
* date
* mileage
* oil_type
* cost

---

## 4. Maintenance Categories & Items

### Engine

* Oil Change
* Spark Plugs
* Engine Air Filter
* Fuel System Service

### Fluids

* Coolant
* Brake Fluid
* Transmission Fluid
* Differential Fluid

### Wear Items

* Tires
* Brake Pads
* Brake Rotors
* Wiper Blades

### Electrical

* Battery

### Critical (Long Interval)

* Timing Belt
* Water Pump

---

## 5. Core Features

### 5.1 Maintenance Tracking

* Log service event with:

  * mileage
  * date
  * cost
  * notes
* Auto-update last_service fields

### 5.2 Interval Tracking

* Calculate next due mileage
* Calculate next due date
* Support:

  * mileage-only
  * time-only
  * hybrid

### 5.3 Status System

Each item has status:

* Good
* Due Soon
* Overdue

Configurable threshold (e.g. 10% remaining = Due Soon)

### 5.4 Dashboard

Per vehicle:

* Overdue items (highest priority)
* Due soon items
* Upcoming maintenance
* Quick add buttons

### 5.5 Fuel Tracking

* Log refuels
* Calculate MPG
* Track fuel cost over time

### 5.6 Cost Tracking

* Total maintenance cost per vehicle
* Cost per mile
* Fuel vs maintenance breakdown

---

## 6. Advanced Features (v1)

### 6.1 Maintenance Health Score

* Percentage-based score per vehicle
* Weighted by importance of items
* Example weights:

  * Oil: high
  * Brakes: high
  * Cabin filter: low

### 6.2 Alerts & Notifications

* Overdue alerts
* Due soon alerts
* Configurable reminders

### 6.3 Trends & Insights

* MPG trends over time
* Seasonal MPG comparison
* Cost trends

### 6.4 Smart Recommendations

* Highlight most urgent task
* Suggest maintenance based on usage patterns

---

## 7. UI Structure

### Home Screen

* List of vehicles
* Health score per vehicle
* Quick summary (MPG, cost)

### Vehicle Detail Screen

* Current mileage
* Maintenance health score
* Sections:

  * Overdue
  * Due soon
  * All maintenance

### Maintenance Item Screen

* Last service details
* Interval settings
* Next due info
* History log

### Add Log Screen

* Select type (fuel, maintenance)
* Input fields

---

## 8. Calculations

### MPG

MPG = miles driven / fuel used

### Cost per Mile

(cost of fuel + maintenance) / miles driven

### Next Due (Mileage)

last_service_mileage + interval_miles

### Next Due (Time)

last_service_date + interval_time_days

---

## 9. Constraints

* Must work offline-first
* Data stored locally (sync optional later)
* All operations must be idempotent

---

## 10. Future Expansion (Not MVP)

* OBD integration
* Cloud sync / multi-device
* Export (CSV/JSON)
* VIN decoding for auto-fill
* Maintenance presets per vehicle model
* AI-based predictions

---

## 11. MVP Checklist

* [ ] Create vehicle
* [ ] Add maintenance items
* [ ] Log maintenance
* [ ] Log fuel
* [ ] View dashboard with statuses
* [ ] Calculate MPG
* [ ] Calculate next due

---

## 12. Definition of Done

* User can manage multiple vehicles
* User can see what maintenance is due or overdue
* User can track fuel and cost
* User can maintain a vehicle proactively without external tools

