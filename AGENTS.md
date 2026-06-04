# AGENTS.md

## Project: Vehicle Maintenance Checkbook

This repository implements an **offline-first preventative maintenance app for personally owned vehicles**.

The product goal is intentionally narrow:

> Help a user answer: **"What maintenance does this vehicle need next, how urgent is it, and what has it cost me to operate?"**

Build a clear maintenance checkbook with reliable local records, deterministic due-status calculation, and a dashboard that makes the next action obvious.

Do **not** turn this into a generic spreadsheet, enterprise fleet system, cloud SaaS product, VIN decoder, OBD platform, marketplace, or AI prediction product.

---

## 1. Scope of the MVP

The MVP must support:

- Multiple vehicles
- Local offline data storage
- Vehicle creation, editing, and archiving
- Vehicle-type-aware data entry
- Maintenance item setup per vehicle
- Recurring mileage-based, time-based, and hybrid maintenance intervals
- Historical maintenance service logs
- Manual mileage updates
- Fuel purchase logs for liquid-fuel vehicles
- Charging logs for electric vehicles and plug-in hybrids
- Efficiency calculations appropriate to the vehicle type
- Basic operating cost summaries
- A dashboard that prioritizes urgent maintenance

The MVP is complete only when a user can manage their vehicles without internet access and clearly see what needs attention next.

---

## 2. Hard Non-Goals for MVP

Do not implement these unless explicitly requested later:

- Cloud sync
- User accounts
- VIN decoding
- OBD integration
- AI-based predictions
- Model-specific manufacturer maintenance presets
- Shop marketplace features
- Complex fleet management
- Multi-user collaboration
- Payment systems
- Social features
- Insurance tracking
- Registration tracking
- Loan or financing tracking
- General household expense tracking

If a feature does not directly support local preventative maintenance, due-status calculation, mileage tracking, energy/fuel tracking, or operating cost summaries, it is out of scope for the MVP.

---

## 3. Core Product Rules

These rules apply throughout the app:

1. **Offline-first local data is the source of truth.**
2. **Maintenance history must be preserved permanently.**
3. **Derived values must be calculated from logs, not manually maintained.**
4. **Do not duplicate canonical state unless the cache can be rebuilt from logs.**
5. **Mileage-based, time-based, and hybrid intervals must all be supported.**
6. **All write operations should be safe to retry.**
7. **The data model should allow future sync without requiring a rewrite.**
8. **Archived vehicles must be hidden by default, not deleted.**
9. **Unknown maintenance status must be visible and actionable.**
10. **The dashboard must show urgent work before historical data or charts.**

---

## 4. Vehicle-Type Awareness

The selected vehicle type controls field labels, units, validation, logs, efficiency calculations, default maintenance items, dashboard summaries, and cost calculations.

The app must never show fields or calculations that do not make sense for the selected vehicle type.

Example:

- If the user selects **EV**, the Add Vehicle screen must show **Battery Capacity** in **kWh**, not **Tank Capacity** in gallons.
- EVs must use charging logs and electric efficiency metrics, not fuel logs and MPG.
- Gasoline and diesel vehicles must use tank capacity, fuel logs, and liquid-fuel efficiency metrics.
- Plug-in hybrids must support both liquid fuel and charging.

### 4.1 Vehicle Types

Supported MVP vehicle types:

- gasoline
- diesel
- hybrid
- plug_in_hybrid
- ev
- motorcycle
- other

### 4.2 Vehicle Type Behavior

| Vehicle Type | Capacity Fields | Log Types | Efficiency Metrics | Default Maintenance Bias |
|---|---|---|---|---|
| gasoline | Tank capacity | Fuel logs | MPG or L/100km | ICE maintenance |
| diesel | Tank capacity | Fuel logs | MPG or L/100km | Diesel/ICE maintenance |
| hybrid | Tank capacity | Fuel logs | MPG or L/100km | ICE plus hybrid-aware items |
| plug_in_hybrid | Tank capacity and battery capacity | Fuel logs and charging logs | MPG plus mi/kWh or kWh/100mi | ICE plus EV-related items |
| ev | Battery capacity | Charging logs | mi/kWh or kWh/100mi | EV maintenance |
| motorcycle | Tank capacity, if applicable | Fuel logs, if applicable | MPG or L/100km | Motorcycle-appropriate items |
| other | User-selected fields | User-selected logs | Optional | Minimal generic items |

### 4.3 Required UI Behavior

When vehicle type changes during vehicle creation or editing:

- Update labels immediately.
- Update units immediately.
- Hide irrelevant fields.
- Show newly relevant fields.
- Reset or require confirmation before discarding incompatible entered values.
- Update validation rules.
- Update default maintenance suggestions.
- Update log options.
- Update dashboard terminology.

Bad behavior:

```text
EV
Tank Capacity: 14 gallons
Recent MPG: 0
Fuel purchase required
```

Good behavior:

```text
EV
Battery Capacity: 75 kWh
Recent Efficiency: 3.4 mi/kWh
Charging Cost: $42.18 this month
```

---

## 5. MVP Feature Checklist

The MVP is acceptable when the user can:

- Create vehicles
- Edit vehicles
- Archive vehicles
- Select a vehicle type
- See vehicle-type-appropriate fields and units
- Add maintenance categories
- Add maintenance items per vehicle
- Edit maintenance intervals
- Log completed maintenance
- Update mileage manually
- Log fuel purchases for liquid-fuel vehicles
- Log charging sessions for EVs and plug-in hybrids
- View overdue maintenance
- View due-soon maintenance
- View upcoming maintenance
- Calculate next due mileage
- Calculate next due date
- Calculate MPG where applicable
- Calculate EV efficiency where applicable
- Track fuel cost where applicable
- Track charging cost where applicable
- Track maintenance cost
- Track total operating cost
- Use the app without internet access

---

## 6. Recommended Data Model

Use this model unless the existing codebase already has a clearly better equivalent.

Prefer stable IDs and timestamps on every persistent entity. Every log-like write should support a `client_generated_id` for idempotent creation.

---

### 6.1 Vehicle

Represents one user-owned vehicle.

Fields:

- id
- name
- make
- model
- year
- vehicle_type
- fuel_type
- tank_capacity
- tank_capacity_unit
- battery_capacity
- battery_capacity_unit
- current_mileage
- mileage_unit
- archived
- created_at
- updated_at

Rules:

- `vehicle_type` is required.
- `current_mileage` must be user-editable.
- `mileage_unit` should support miles first. Kilometers may be added if the app supports metric units.
- `tank_capacity` applies only to liquid-fuel vehicles.
- `battery_capacity` applies only to EVs and plug-in hybrids.
- `fuel_type` applies only to vehicles that use liquid fuel.
- The app may suggest mileage updates from newer logs.
- Do not delete old vehicles by default. Use `archived`.

Vehicle type field requirements:

| Vehicle Type | fuel_type | tank_capacity | battery_capacity |
|---|---:|---:|---:|
| gasoline | required | required | hidden |
| diesel | required | required | hidden |
| hybrid | required | required | optional |
| plug_in_hybrid | required | required | required |
| ev | hidden | hidden | required |
| motorcycle | optional | optional | hidden unless electric |
| other | optional | optional | optional |

---

### 6.2 MaintenanceCategory

Groups maintenance items into simple sections.

Fields:

- id
- name
- sort_order

Default categories:

- Engine
- Fluids
- Wear Items
- Electrical
- Critical
- EV Systems
- Hybrid Systems
- Other

Rules:

- Categories are organizational only.
- Categories must not be used as a substitute for due-status calculation.

---

### 6.3 MaintenanceItem

Represents a recurring maintenance task for one vehicle.

Fields:

- id
- vehicle_id
- category_id
- name
- interval_miles
- interval_time_days
- importance
- active
- notes
- created_at
- updated_at

Valid `importance` values:

- low
- medium
- high
- critical

Rules:

- Maintenance items belong to one vehicle.
- Maintenance items may use mileage intervals, time intervals, or both.
- Items with neither interval are allowed only when they are manually tracked inspections.
- Inactive items should not appear in urgent dashboard sections.
- Do not store `last_service_mileage`, `last_service_date`, or `last_service_cost` as canonical fields on `MaintenanceItem`.

Bad design:

```text
MaintenanceItem.last_service_date
MaintenanceItem.last_service_mileage
MaintenanceItem.last_service_cost
```

Good design:

```text
latest MaintenanceServiceLog for this MaintenanceItem
```

If cached last-service fields are ever added for performance, they must be rebuildable from logs and must not become the canonical source of truth.

---

### 6.4 MaintenanceServiceLog

Represents a completed service event.

Fields:

- id
- vehicle_id
- maintenance_item_id
- date
- mileage
- cost
- notes
- receipt_path
- created_at
- updated_at
- client_generated_id

Rules:

- Every completed maintenance event must create a log.
- New service must never overwrite old service history.
- `client_generated_id` must be unique and stable for idempotent writes.
- `receipt_path` is optional and may be ignored in the MVP UI.
- Maintenance logs may contribute mileage data.

---

### 6.5 FuelLog

Represents a liquid-fuel purchase.

Use this only for vehicles that consume liquid fuel.

Fields:

- id
- vehicle_id
- date
- mileage
- fuel_amount
- fuel_unit
- price_per_unit
- total_cost
- is_full_tank
- station
- notes
- created_at
- updated_at
- client_generated_id

Rules:

- Gasoline, diesel, hybrid, plug-in hybrid, and applicable motorcycle vehicles may use fuel logs.
- EVs must not show fuel logs.
- Partial fill-ups count toward cost.
- MPG should only be calculated from reliable full-tank intervals.
- If `total_cost` is provided but `price_per_unit` is missing, derive `price_per_unit`.
- If `price_per_unit` and `fuel_amount` are provided but `total_cost` is missing, derive `total_cost`.
- Do not generate misleading MPG values from partial fill-ups.

---

### 6.6 ChargingLog

Represents an EV or plug-in hybrid charging session.

Use this for EVs and plug-in hybrids.

Fields:

- id
- vehicle_id
- date
- mileage
- energy_added
- energy_unit
- price_per_unit
- total_cost
- charge_percent_before
- charge_percent_after
- is_full_charge
- location
- notes
- created_at
- updated_at
- client_generated_id

Rules:

- EVs must use charging logs instead of fuel logs.
- Plug-in hybrids may use both charging logs and fuel logs.
- `energy_unit` should default to kWh.
- If `total_cost` is provided but `price_per_unit` is missing, derive `price_per_unit`.
- If `price_per_unit` and `energy_added` are provided but `total_cost` is missing, derive `total_cost`.
- Electric efficiency should be calculated only when enough reliable mileage and energy data exists.
- Partial charges count toward cost.
- Do not require charge percentage fields if the user only knows kWh added and cost.

---

### 6.7 MileageLog

Represents a mileage update not necessarily tied to fuel, charging, or maintenance.

Fields:

- id
- vehicle_id
- date
- mileage
- source
- notes
- created_at
- updated_at
- client_generated_id

Valid `source` values:

- manual
- fuel_log
- charging_log
- maintenance_log
- import
- obd_future

Rules:

- Users must be able to update mileage without creating fake fuel, charging, or maintenance records.
- Fuel logs, charging logs, and maintenance logs may also contribute mileage data.
- The latest known mileage may be derived from the newest reliable mileage-bearing record, but the user must be able to override the displayed current mileage.

---

### 6.8 MaintenanceTemplate

Optional for MVP, useful soon after.

Fields:

- id
- name
- category_id
- supported_vehicle_types
- default_interval_miles
- default_interval_time_days
- default_importance

Purpose:

- Seed common maintenance items when a vehicle is created.
- Avoid forcing users to create every common item manually.
- Keep defaults generic and vehicle-type-aware.

Rules:

- Do not make templates manufacturer-specific in the MVP.
- Do not seed impossible tasks for the selected vehicle type.

Bad behavior:

```text
Seed Oil Change for EV
```

Good behavior:

```text
Seed Brake Fluid, Tires, Wiper Blades, Cabin Air Filter, and Battery Health Inspection for EV
```

---

## 7. Default Maintenance Items

Seed generic defaults when appropriate. Defaults must be filtered by vehicle type.

---

### 7.1 Gasoline and Diesel Defaults

#### Engine

- Oil Change
- Spark Plugs, if gasoline
- Glow Plug Inspection, if diesel and applicable
- Engine Air Filter
- Fuel System Service
- Serpentine Belt

#### Fluids

- Coolant
- Brake Fluid
- Transmission Fluid
- Differential Fluid
- Power Steering Fluid, if applicable

#### Wear Items

- Tires
- Brake Pads
- Brake Rotors
- Wiper Blades

#### Electrical

- 12V Battery
- Alternator Check
- Exterior Lights

#### Critical Long-Interval Items

- Timing Belt, if applicable
- Water Pump, if applicable
- Timing Chain Inspection, if applicable

---

### 7.2 Hybrid Defaults

Use gasoline defaults plus hybrid-aware items where appropriate:

- Hybrid System Inspection
- High Voltage Battery Health Inspection
- Inverter Coolant, if applicable
- Regenerative Brake Inspection

Do not assume every hybrid has the same service needs.

---

### 7.3 Plug-In Hybrid Defaults

Use hybrid defaults plus charging-related items:

- Charge Port Inspection
- Charging Cable Inspection
- High Voltage Battery Health Inspection
- Inverter Coolant, if applicable

Plug-in hybrids must support both fuel and charging records.

---

### 7.4 EV Defaults

#### EV Systems

- High Voltage Battery Health Inspection
- High Voltage System Inspection
- Charge Port Inspection
- Charging Cable Inspection
- Inverter Coolant, if applicable

#### Fluids

- Brake Fluid
- Coolant, if applicable
- Gear Reduction Fluid, if applicable

#### Wear Items

- Tires
- Brake Pads
- Brake Rotors
- Wiper Blades
- Cabin Air Filter

#### Electrical

- 12V Battery
- Exterior Lights

Do not seed these for EVs:

- Oil Change
- Spark Plugs
- Fuel System Service
- Serpentine Belt, unless the specific vehicle needs one
- Exhaust-related maintenance

---

## 8. Maintenance Status Calculation

Each active maintenance item must have exactly one calculated status:

- Unknown
- Good
- Due Soon
- Overdue

Severity order:

```text
Unknown < Good < Due Soon < Overdue
```

Important: `Unknown` is not failure. It means the app lacks baseline service data.

---

### 8.1 Unknown Status

An item is `Unknown` if no baseline service log exists and the app cannot determine the last service mileage or date.

The UI must prompt the user to enter a baseline service event.

Example:

```text
Oil Change
Status: Unknown
Action: Enter last known oil change
```

Do not hide unknown items. Unknown maintenance is actionable because the user needs to establish a baseline.

---

### 8.2 Mileage-Based Status

For mileage intervals:

```text
next_due_mileage = latest_service_mileage + interval_miles
miles_remaining = next_due_mileage - current_vehicle_mileage
```

Status rules:

```text
if no latest_service_mileage:
    status = Unknown
else if miles_remaining < 0:
    status = Overdue
else if miles_remaining <= interval_miles * due_soon_threshold:
    status = Due Soon
else:
    status = Good
```

Default `due_soon_threshold`:

```text
0.10
```

Example:

```text
interval_miles = 5000
due_soon_threshold = 10%
due_soon_miles = 500
```

---

### 8.3 Time-Based Status

For time intervals:

```text
next_due_date = latest_service_date + interval_time_days
days_remaining = next_due_date - current_date
```

Status rules:

```text
if no latest_service_date:
    status = Unknown
else if days_remaining < 0:
    status = Overdue
else if days_remaining <= interval_time_days * due_soon_threshold:
    status = Due Soon
else:
    status = Good
```

---

### 8.4 Hybrid Status

For items with both mileage and time intervals:

1. Calculate mileage status.
2. Calculate time status.
3. Use the worse actionable status.

Severity order for comparison:

```text
Unknown < Good < Due Soon < Overdue
```

Example:

```text
Oil Change:
- Good by mileage
- Overdue by date

Final status: Overdue
```

Maintenance can age out even when the vehicle is not driven much.

---

## 9. Fuel, Charging, and Efficiency Calculation

Efficiency calculations must match the vehicle type.

Do not show MPG for EVs. Do not show mi/kWh for gasoline-only vehicles.

---

### 9.1 Liquid-Fuel Efficiency

Applicable vehicle types:

- gasoline
- diesel
- hybrid
- plug_in_hybrid
- applicable motorcycles

For full-tank MPG:

```text
MPG = miles driven since previous full tank / fuel added at current full tank
```

Example:

```text
Previous full tank mileage: 80000
Current full tank mileage: 80350
Fuel added at current fill: 10 gallons

MPG = 350 / 10
MPG = 35
```

Rules:

- MPG should be calculated using full-tank entries.
- Partial fill-ups must be saved.
- Partial fill-ups count toward cost.
- Partial fill-ups must not automatically produce standalone MPG values.

---

### 9.2 Electric Efficiency

Applicable vehicle types:

- ev
- plug_in_hybrid

Preferred electric efficiency metrics:

- mi/kWh
- kWh/100mi

Basic formula:

```text
mi_per_kwh = miles_driven / energy_added_kwh
```

Alternative formula:

```text
kwh_per_100_mi = energy_added_kwh / miles_driven * 100
```

Rules:

- Calculate electric efficiency only when enough reliable mileage and charging data exists.
- Do not divide by zero.
- Partial charges count toward cost.
- Partial charges may contribute to efficiency only if the calculation method remains reliable and clearly explained.
- If reliability is uncertain, show "No reliable efficiency data yet".

---

### 9.3 Plug-In Hybrid Efficiency

Plug-in hybrids must support both liquid-fuel and electric tracking.

Show separate summaries when possible:

- Fuel MPG
- Electric mi/kWh or kWh/100mi
- Fuel cost
- Charging cost
- Combined operating cost

Do not collapse PHEV efficiency into a single vague number unless the app clearly defines the calculation.

---

## 10. Cost Calculation

### 10.1 Fuel Cost

For liquid fuel:

```text
total_cost = fuel_amount * price_per_unit
```

If total cost is entered directly:

```text
price_per_unit = total_cost / fuel_amount
```

Handle missing or zero fuel amount safely.

---

### 10.2 Charging Cost

For charging sessions:

```text
total_cost = energy_added * price_per_unit
```

If total cost is entered directly:

```text
price_per_unit = total_cost / energy_added
```

Handle missing or zero energy amount safely.

---

### 10.3 Maintenance Cost

Maintenance cost is the sum of `MaintenanceServiceLog.cost` for a vehicle.

Missing cost values should be treated as unknown for that entry, not as proof the service was free unless the user explicitly enters zero.

---

### 10.4 Total Operating Cost

For gasoline, diesel, hybrid, and fuel-only motorcycles:

```text
total_operating_cost = total_fuel_cost + total_maintenance_cost
```

For EVs:

```text
total_operating_cost = total_charging_cost + total_maintenance_cost
```

For plug-in hybrids:

```text
total_operating_cost = total_fuel_cost + total_charging_cost + total_maintenance_cost
```

---

### 10.5 Cost Per Mile

```text
cost_per_mile = total_operating_cost / miles_tracked
```

`miles_tracked` should be based on the earliest and latest known mileage records for the vehicle.

Rules:

- Handle zero-mile cases safely.
- Handle insufficient data safely.
- Do not divide by zero.
- If mileage data is insufficient, show "Not enough mileage data yet".

---

## 11. Dashboard Requirements

The vehicle dashboard must prioritize urgency.

Show, in order:

1. Critical overdue items
2. High-importance overdue items
3. Other overdue items
4. Unknown critical or high-importance items needing baseline data
5. Due-soon items
6. Upcoming maintenance
7. Recent logs
8. Fuel, charging, and cost summary appropriate to the vehicle type

The top of the dashboard must answer:

```text
What needs attention right now?
```

Do not make the user scroll through all maintenance items before showing overdue work.

Good dashboard summary:

```text
Needs attention now:
Brake Fluid is overdue by 48 days.
Oil Change is due in 320 miles.
Tire Rotation needs baseline data.
```

Bad dashboard summary:

```text
12 maintenance items
4 fuel records
3 notes
```

---

## 12. Screens and Required Flows

Adapt naming to the app framework, but preserve these flows.

---

### 12.1 Home Screen

Shows all active vehicles.

Each vehicle card should show:

- Vehicle name
- Year, make, model
- Vehicle type
- Current mileage
- Overdue count
- Due-soon count
- Recent efficiency metric, if available and applicable
- Basic cost summary, if available

Archived vehicles should not appear here by default.

Vehicle-type examples:

```text
Gasoline vehicle: Recent MPG
EV: Recent mi/kWh or kWh/100mi
PHEV: Fuel MPG and electric efficiency, if available
```

---

### 12.2 Add/Edit Vehicle Screen

Must support:

- Name
- Make
- Model
- Year
- Vehicle type
- Current mileage
- Vehicle-type-specific capacity fields
- Vehicle-type-specific fuel or battery fields

Required dynamic behavior:

- Selecting EV hides tank capacity and fuel type.
- Selecting EV shows battery capacity in kWh.
- Selecting gasoline or diesel shows tank capacity.
- Selecting gasoline or diesel hides battery capacity.
- Selecting plug-in hybrid shows both tank capacity and battery capacity.
- Selecting hybrid shows tank capacity and may optionally show battery information.
- Units and labels must update immediately when vehicle type changes.

---

### 12.3 Vehicle Detail Screen

Shows one vehicle's maintenance state.

Include:

- Current mileage
- Vehicle type
- Most urgent task
- Overdue items
- Due-soon items
- Unknown baseline items
- Upcoming items
- All maintenance items
- Recent maintenance logs
- Fuel summary, if applicable
- Charging summary, if applicable
- Cost summary

---

### 12.4 Maintenance Item Screen

Shows one maintenance task.

Include:

- Item name
- Category
- Importance
- Current status
- Last service date
- Last service mileage
- Next due date
- Next due mileage
- Interval settings
- Service history

Actions:

- Log service
- Edit interval
- Disable item
- Add note

Display plain-language action text before raw data.

Good UX:

```text
Brake Fluid
Overdue by 48 days
High importance
Log service
```

Bad UX:

```text
Brake Fluid
Last: 2022-11-01
Interval: 730 days
Next: 2024-10-31
```

Raw data can exist, but the primary UI must translate it into action.

---

### 12.5 Add Log Screen

Must support these log types where applicable:

- Maintenance log
- Fuel log
- Charging log
- Mileage update

Maintenance log fields:

- Vehicle
- Maintenance item
- Date
- Mileage
- Cost
- Notes

Fuel log fields:

- Vehicle
- Date
- Mileage
- Fuel amount
- Fuel unit
- Price per unit
- Total cost
- Full tank toggle
- Station
- Notes

Charging log fields:

- Vehicle
- Date
- Mileage
- Energy added
- Energy unit
- Price per unit
- Total cost
- Charge percent before
- Charge percent after
- Full charge toggle
- Location
- Notes

Mileage log fields:

- Vehicle
- Date
- Mileage
- Notes

Rules:

- EVs should show charging logs, not fuel logs.
- Plug-in hybrids should show both fuel and charging logs.
- Gasoline and diesel vehicles should show fuel logs, not charging logs.
- Manual mileage updates must not require fake maintenance, fuel, or charging entries.

---

## 13. Recommended Implementation Order

Follow this order unless explicitly told otherwise.

---

### Phase 1: Local Data Foundation

- Create local database schema
- Add models/entities
- Add migrations if the stack supports them
- Add repository/data-access layer
- Add idempotent create behavior using `client_generated_id`
- Add vehicle type enum and type-aware validation helpers

Do not start with charts, AI, cloud sync, or visual polish.

---

### Phase 2: Vehicle CRUD

- Create vehicle
- Edit vehicle
- Archive vehicle
- Update mileage
- Implement vehicle-type-aware Add/Edit Vehicle behavior
- Ensure irrelevant fields are hidden or disabled by vehicle type

---

### Phase 3: Maintenance Core

- Add maintenance categories
- Add vehicle-type-aware maintenance templates
- Add maintenance items
- Add maintenance service logs
- Derive latest service from logs
- Calculate next due mileage
- Calculate next due date
- Calculate item status

This is the heart of the app. Get it correct before building polish.

---

### Phase 4: Dashboard

- Show overdue items
- Show due-soon items
- Show unknown baseline items
- Show upcoming items
- Show most urgent item
- Sort by severity and importance
- Use vehicle-type-appropriate terminology

---

### Phase 5: Fuel and Charging Tracking

- Add fuel logs for liquid-fuel vehicles
- Add charging logs for EVs and plug-in hybrids
- Support full-tank toggle
- Support full-charge toggle
- Calculate MPG from reliable full-tank intervals
- Calculate electric efficiency from reliable charging intervals
- Track fuel cost
- Track charging cost

---

### Phase 6: Cost Summary

- Calculate maintenance total
- Calculate fuel total where applicable
- Calculate charging total where applicable
- Calculate total operating cost
- Calculate cost per mile when enough mileage data exists

---

### Phase 7: v1 Enhancements

Only after the MVP works:

- Maintenance health score
- Trends
- Notifications
- Receipt attachments
- CSV export
- JSON export
- Backup and restore
- Smarter deterministic recommendations

---

## 14. Smart Recommendations

Do not use AI for MVP recommendations.

Use deterministic rules.

Priority order:

1. Critical overdue item
2. High-importance overdue item
3. Item most overdue by percentage
4. Unknown critical item needing baseline data
5. Unknown high-importance item needing baseline data
6. Due-soon critical item
7. Due-soon high-importance item
8. Lowest remaining miles or days

Example output:

```text
Most urgent: Oil Change
Reason: Overdue by 620 miles and marked high importance.
```

This is better than vague generated advice.

---

## 15. Health Score for v1

Do not build this before the basic status system is correct.

Suggested weights:

```text
low = 1
medium = 2
high = 3
critical = 5
```

Suggested credit:

```text
Good = full credit
Due Soon = partial credit
Overdue = no credit
Unknown = reduced credit or excluded until baseline exists
```

Formula:

```text
health_score = earned_weight / total_active_weight * 100
```

The health score should punish overdue oil, brakes, timing belts, high-voltage system inspections, and other high-importance items more than low-importance items like wiper blades.

---

## 16. Testing Requirements

Add tests for calculation-heavy logic.

At minimum, test:

- Vehicle-type field visibility rules
- Vehicle-type validation rules
- EV hides fuel fields
- EV shows battery capacity in kWh
- Plug-in hybrid supports fuel and charging logs
- Gasoline and diesel vehicles do not show charging logs by default
- Mileage-only due status
- Time-only due status
- Hybrid due status
- Due-soon threshold
- Overdue status
- Unknown status
- Latest service derivation from logs
- Full-tank MPG calculation
- Partial fuel fill behavior
- EV charging cost calculation
- EV efficiency calculation
- Partial charging behavior
- Fuel total calculation
- Charging total calculation
- Maintenance total calculation
- Cost-per-mile zero-mile safety
- Archived vehicles hidden from default home view
- Idempotent log creation using `client_generated_id`

Calculation logic should be unit-tested outside UI code.

---

## 17. Data Integrity Rules

Follow these strictly:

- Do not delete maintenance logs when editing a maintenance item.
- Do not mutate old logs when adding new service.
- Do not calculate MPG from unreliable partial-fill intervals.
- Do not calculate EV efficiency from insufficient or zero-energy data.
- Do not rely on duplicated last-service fields as source of truth.
- Do not allow cost-per-mile division by zero.
- Do not show archived vehicles by default.
- Do not require internet access for core features.
- Do not require accounts for MVP.
- Do not hide unknown maintenance status. Prompt for baseline data instead.
- Do not show fuel-only concepts for EVs.
- Do not show charging-only concepts for gasoline or diesel vehicles.
- Do not seed maintenance items that are impossible for the selected vehicle type.

---

## 18. UX Rules

The app should feel like a checkbook plus a maintenance advisor.

Use plain language:

- "Due in 320 miles"
- "Overdue by 42 days"
- "Needs baseline"
- "Last done 5,240 miles ago"
- "No full-tank MPG data yet"
- "No reliable charging efficiency data yet"
- "Charging cost this month"
- "Fuel cost this month"

The UI should prioritize action over raw records.

Good UX:

```text
Brake Fluid
Overdue by 48 days
High importance
Log service
```

Bad UX:

```text
Brake Fluid
Last: 2022-11-01
Interval: 730 days
Next: 2024-10-31
```

Vehicle-type language must be correct:

Bad UX:

```text
Tesla Model 3
Tank Capacity
Recent MPG
Fuel Log
```

Good UX:

```text
Tesla Model 3
Battery Capacity
Recent mi/kWh
Charging Log
```

---

## 19. Acceptance Criteria

The implementation is acceptable when:

- Multiple vehicles can be managed independently.
- Vehicles have a clear vehicle type.
- Vehicle type controls fields, units, labels, logs, defaults, and summaries.
- EVs use battery capacity, charging logs, charging costs, and electric efficiency.
- Liquid-fuel vehicles use tank capacity, fuel logs, fuel costs, and MPG where applicable.
- Plug-in hybrids support both fuel and charging workflows.
- Maintenance items are per-vehicle.
- Maintenance logs are historical and preserved.
- Last service values are derived from logs.
- Due status works for mileage, time, and hybrid intervals.
- Fuel logs support full and partial fills.
- Charging logs support full and partial charges.
- MPG is calculated only from reliable full-tank intervals.
- Electric efficiency is calculated only from reliable charging/mileage data.
- Costs are summarized per vehicle.
- Cost-per-mile handles insufficient data safely.
- The dashboard clearly shows what needs attention next.
- Archived vehicles are hidden by default.
- The app works offline.
- The code has tests for core calculations.

---

## 20. Final Product Standard

When the user opens the app, they should immediately understand:

```text
What is overdue?
What is due soon?
What needs baseline data?
What should I do next?
What has this vehicle cost me?
How efficiently is it running, using the correct metric for this vehicle type?
```

If the implementation does not answer those questions clearly, keep improving it.

The MVP succeeds when it behaves like a trustworthy local maintenance checkbook, not a bloated fleet platform.
