# AGENTS.md

## Project: Vehicle and Equipment Maintenance Checkbook

This repository implements an **offline-first preventative maintenance app for personally owned vehicles and equipment**.

The product goal is intentionally narrow:

> Help a user answer: **"What maintenance does this asset need next, how urgent is it, and what has it cost me to operate?"**

Build a clear maintenance checkbook with reliable local records, deterministic due-status calculation, and a dashboard that makes the next action obvious.

Do **not** turn this into a generic spreadsheet, enterprise fleet system, cloud SaaS product, VIN decoder, OBD platform, marketplace, or AI prediction product.

The word **asset** means either a vehicle or a piece of equipment. Use asset-level language in the data model. UI labels may still say "Vehicle" where that is clearer to the user, but the app must support equipment as a first-class asset.

---

## 1. Scope of the MVP

The MVP must support:

- Multiple assets
- Local offline data storage
- Asset creation, editing, and archiving
- Vehicle and equipment creation flows
- Asset-type-aware data entry
- A primary category selection for `vehicle` or `equipment`
- A secondary type selection for the selected category
- A separate fuel or energy type selection
- Maintenance item setup per asset
- Recurring mileage-based, hour-based, time-based, and hybrid maintenance intervals
- Historical maintenance service logs
- Manual mileage updates for mileage-bearing assets
- Manual hour-meter updates for hour-meter equipment
- Fuel purchase logs for liquid-fuel assets
- Charging logs for electric assets and plug-in hybrids
- Efficiency calculations appropriate to the asset category, asset type, and fuel type
- Basic operating cost summaries
- A dashboard that prioritizes urgent maintenance

The MVP is complete only when a user can manage vehicles and equipment without internet access and clearly see what needs attention next.

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
- Parts marketplace features
- Warranty claim management
- Commercial dispatching
- Operator assignment

If a feature does not directly support local preventative maintenance, due-status calculation, mileage or hour tracking, energy/fuel tracking, or operating cost summaries, it is out of scope for the MVP.

---

## 3. Core Product Rules

These rules apply throughout the app:

1. **Offline-first local data is the source of truth.**
2. **Maintenance history must be preserved permanently.**
3. **Derived values must be calculated from logs, not manually maintained.**
4. **Do not duplicate canonical state unless the cache can be rebuilt from logs.**
5. **Mileage-based, hour-based, time-based, and hybrid intervals must all be supported.**
6. **All write operations should be safe to retry.**
7. **The data model should allow future sync without requiring a rewrite.**
8. **Archived assets must be hidden by default, not deleted.**
9. **Unknown maintenance status must be visible and actionable.**
10. **The dashboard must show urgent work before historical data or charts.**
11. **Fuel type must not be inferred permanently from asset type.**
12. **Equipment must not be forced into mileage-only vehicle behavior.**
13. **The app must never show fields or calculations that do not make sense for the selected asset.**

---

## 4. Asset-Type Awareness

The selected asset category, asset type, and fuel type control field labels, units, validation, logs, efficiency calculations, default maintenance items, dashboard summaries, and cost calculations.

The app must treat these as separate dimensions:

```text
asset_category = what broad group is this? vehicle or equipment
asset_type = what kind of asset is this? car, motorcycle, skid loader, chainsaw, etc.
fuel_type = what powers it? gasoline, diesel, mixed gas, electric, propane, etc.
```

Do **not** add `equipment` as a peer of `gasoline`, `diesel`, and `ev` in the persisted data model. That is a bad shape because it mixes "what it is" with "what powers it." If the current UI already has a dropdown labeled **Vehicle Type**, it may contain **Equipment** during the creation flow, but internally this should map to:

```text
asset_category = equipment
```

When `asset_category = equipment`, show a second dropdown for `equipment_type` or `asset_type` immediately below the category/type selector.

A separate **Fuel / Energy Type** dropdown must be shown wherever relevant. Do not assume all skid loaders are diesel, all ATVs are gasoline, all golf carts are electric, or all equipment is liquid-fuel powered.

Examples:

- If the user selects **EV**, the Add Asset screen must show **Battery Capacity** in **kWh**, not **Tank Capacity** in gallons.
- EVs must use charging logs and electric efficiency metrics, not fuel logs and MPG.
- Gasoline and diesel assets must use tank capacity, fuel logs, and liquid-fuel cost tracking.
- Plug-in hybrids must support both liquid fuel and charging.
- Skid loaders must allow diesel, gasoline, propane, or electric where applicable.
- ATVs and UTVs must allow gasoline, diesel, electric, or other where applicable.
- Chainsaws must allow mixed gas, electric, battery electric, or other where applicable.
- Equipment with hour meters must support hour-based maintenance intervals.

---

### 4.1 Core Type Fields

Use these conceptual fields even if the existing codebase uses slightly different table names.

```text
asset_category
asset_type
fuel_type
```

If the existing app already has a `Vehicle` model, it may remain named `Vehicle` during MVP development, but it must behave like an asset model. Do not create a separate equipment subsystem unless the codebase already strongly requires it.

Recommended values:

#### `asset_category`

```text
vehicle
equipment
```

#### `asset_type` when `asset_category = vehicle`

```text
car_or_truck
motorcycle
rv
trailer
other
```

#### `asset_type` when `asset_category = equipment`

```text
skid_loader
excavator
backhoe
tractor
zero_turn_mower
lawn_tractor
push_mower
chainsaw
generator
pressure_washer
atv
utv
golf_cart
snow_blower
wood_chipper
mini_excvator
forklift
compact_track_loader
other
```

Use stable enum values in storage. Display names may use spaces and normal capitalization.

Good display labels:

```text
Skid Loader
Zero-Turn Mower
Golf Cart
Compact Track Loader
```

Bad stored enum values:

```text
Skid Loader
Zero Turn Mower!!!
golf cart
```

Good stored enum values:

```text
skid_loader
zero_turn_mower
golf_cart
```

---

### 4.2 Fuel / Energy Types

Supported MVP fuel or energy types:

```text
gasoline
diesel
mixed_gas
propane
natural_gas
electric
hybrid_gasoline
hybrid_diesel
plug_in_hybrid_gasoline
plug_in_hybrid_diesel
none
other
```

Rules:

- `gasoline`, `diesel`, `mixed_gas`, `propane`, and `natural_gas` use liquid or gas fuel logs.
- `electric` uses charging logs.
- `hybrid_gasoline` and `hybrid_diesel` use fuel logs and may optionally show battery-related fields only when useful.
- `plug_in_hybrid_gasoline` and `plug_in_hybrid_diesel` use both fuel logs and charging logs.
- `mixed_gas` should support an optional oil mix ratio.
- `none` is for trailers, hand tools, or manually powered assets that have maintenance but no fuel or charging logs.
- `other` should keep the asset usable without forcing bad assumptions.

The app may show the UI label **Fuel Type** or **Fuel / Energy Type**. Prefer **Fuel / Energy Type** because electric equipment is not literally fueled.

---

### 4.3 Asset Type Behavior

| Asset Category | Asset Type Examples | Fuel Type Examples | Capacity Fields | Meter Fields | Log Types | Efficiency Metrics | Default Maintenance Bias |
|---|---|---|---|---|---|---|---|
| vehicle | car_or_truck | gasoline, diesel | Tank capacity | Mileage | Fuel logs | MPG or L/100km | ICE maintenance |
| vehicle | car_or_truck | hybrid_gasoline | Tank capacity | Mileage | Fuel logs | MPG or L/100km | ICE plus hybrid-aware items |
| vehicle | car_or_truck | plug_in_hybrid_gasoline | Tank and battery capacity | Mileage | Fuel and charging logs | MPG plus mi/kWh or kWh/100mi | ICE plus EV-related items |
| vehicle | car_or_truck | electric | Battery capacity | Mileage | Charging logs | mi/kWh or kWh/100mi | EV maintenance |
| vehicle | motorcycle | gasoline, electric, other | Tank or battery capacity if applicable | Mileage | Fuel or charging logs if applicable | MPG or mi/kWh where applicable | Motorcycle-appropriate items |
| vehicle | trailer | none | Hidden by default | Optional mileage | Maintenance and mileage logs | Optional | Tires, bearings, brakes, lights |
| equipment | skid_loader | diesel, gasoline, propane, electric | Tank or battery capacity | Hours, optional mileage | Fuel or charging logs | gal/hr, kWh/hr, optional MPG | Hydraulics, engine, grease points |
| equipment | excavator | diesel, electric | Tank or battery capacity | Hours | Fuel or charging logs | gal/hr or kWh/hr | Hydraulics, undercarriage, grease points |
| equipment | chainsaw | mixed_gas, electric | Fuel capacity or battery capacity optional | Hours optional | Fuel or charging logs optional | Optional | Chain, bar, air filter, spark plug |
| equipment | atv | gasoline, diesel, electric | Tank or battery capacity | Mileage and/or hours | Fuel or charging logs | MPG, gal/hr, or mi/kWh | Drivetrain, tires, fluids |
| equipment | golf_cart | electric, gasoline | Battery or tank capacity | Hours or mileage optional | Charging or fuel logs | kWh/hr, mi/kWh, or MPG | Batteries, brakes, tires, charger |
| equipment | generator | gasoline, diesel, propane, natural_gas | Tank capacity optional | Hours | Fuel logs | gal/hr or cost/hr | Oil, air filter, spark plug, load test |
| equipment | other | any | User-selected fields | User-selected mileage and/or hours | Optional | Optional | Minimal generic items |

---

### 4.4 Required UI Behavior

When asset category, asset type, or fuel type changes during creation or editing:

- Update labels immediately.
- Update units immediately.
- Hide irrelevant fields.
- Show newly relevant fields.
- Reset or require confirmation before discarding incompatible entered values.
- Update validation rules.
- Update default maintenance suggestions.
- Update log options.
- Update dashboard terminology.

Creation flow should behave like this:

1. User selects category: `vehicle` or `equipment`.
2. If category is `vehicle`, show vehicle asset type dropdown.
3. If category is `equipment`, show equipment type dropdown immediately below it.
4. Show fuel or energy type dropdown.
5. Show capacity, mileage, hour, fuel, and charging fields based on the selected values.

Bad behavior:

```text
Equipment
Tank Capacity: 14 gallons
Recent MPG: 0
Fuel purchase required
No hour meter support
```

Good behavior:

```text
Equipment
Type: Skid Loader
Fuel / Energy Type: Diesel
Hour Meter: 1,245.7 hours
Recent Fuel Use: No reliable gal/hr data yet
Next Action: Hydraulic Filter needs baseline data
```

Bad behavior:

```text
Electric Golf Cart
Tank Capacity: 5 gallons
Recent MPG
Fuel Log
```

Good behavior:

```text
Electric Golf Cart
Battery Capacity: 5.2 kWh
Charging Log
Charging Cost This Month
Battery Inspection needs baseline data
```

---

## 5. MVP Feature Checklist

The MVP is acceptable when the user can:

- Create assets
- Edit assets
- Archive assets
- Select vehicle or equipment category
- Select a category-specific asset type
- Select a fuel or energy type
- See asset-type-appropriate fields and units
- Add maintenance categories
- Add maintenance items per asset
- Edit maintenance intervals
- Use mileage-based intervals where applicable
- Use hour-based intervals where applicable
- Use time-based intervals where applicable
- Log completed maintenance
- Update mileage manually
- Update hour meter manually
- Log fuel purchases for liquid-fuel assets
- Log charging sessions for electric assets and plug-in hybrids
- View overdue maintenance
- View due-soon maintenance
- View upcoming maintenance
- Calculate next due mileage
- Calculate next due hours
- Calculate next due date
- Calculate MPG where applicable
- Calculate gal/hr where applicable
- Calculate EV efficiency where applicable
- Track fuel cost where applicable
- Track charging cost where applicable
- Track maintenance cost
- Track total operating cost
- Track cost per mile where applicable
- Track cost per hour where applicable
- Use the app without internet access

---

## 6. Recommended Data Model

Use this model unless the existing codebase already has a clearly better equivalent.

Prefer stable IDs and timestamps on every persistent entity. Every log-like write should support a `client_generated_id` for idempotent creation.

Use asset terminology in the model. If the existing table is named `vehicles`, either migrate it to `assets` or keep the table name while expanding the semantics. Do not fork vehicle and equipment into two unrelated systems.

---

### 6.1 Asset

Represents one user-owned vehicle or piece of equipment.

Fields:

- id
- name
- make
- model
- year
- asset_category
- asset_type
- fuel_type
- tank_capacity
- tank_capacity_unit
- battery_capacity
- battery_capacity_unit
- current_mileage
- mileage_unit
- current_hours
- archived
- created_at
- updated_at

Rules:

- `asset_category` is required.
- `asset_type` is required.
- `fuel_type` is required unless the asset truly has no fuel or energy system.
- `current_mileage` must be user-editable when the asset is mileage-bearing.
- `current_hours` must be user-editable when the asset is hour-meter-bearing.
- `mileage_unit` should support miles first. Kilometers may be added if the app supports metric units.
- `tank_capacity` applies only to liquid-fuel, mixed-gas, propane, natural-gas, and similar fuel assets.
- `battery_capacity` applies to electric assets and plug-in hybrids.
- The app may suggest mileage updates from newer logs.
- The app may suggest hour updates from newer logs.
- Do not delete old assets by default. Use `archived`.
- Do not make asset type determine fuel type permanently. It may provide a default suggestion, but the user must be able to change it.

Fuel type field requirements:

| Fuel Type | tank_capacity | battery_capacity | fuel logs | charging logs |
|---|---:|---:|---:|---:|
| gasoline | optional or required by asset type | hidden | yes | no |
| diesel | optional or required by asset type | hidden | yes | no |
| mixed_gas | optional | hidden | yes | no |
| propane | optional | hidden | yes | no |
| natural_gas | optional | hidden | yes | no |
| electric | hidden | optional or required by asset type | no | yes |
| hybrid_gasoline | required for road vehicles | optional | yes | no by default |
| hybrid_diesel | required for road vehicles | optional | yes | no by default |
| plug_in_hybrid_gasoline | required | required | yes | yes |
| plug_in_hybrid_diesel | required | required | yes | yes |
| none | hidden | hidden | no | no |
| other | optional | optional | optional | optional |

Asset meter requirements:

| Asset Category | Asset Type | current_mileage | current_hours |
|---|---|---:|---:|
| vehicle | car_or_truck | required | optional hidden by default |
| vehicle | motorcycle | required | optional hidden by default |
| vehicle | rv | required | optional |
| vehicle | trailer | optional | hidden |
| vehicle | other | optional | optional |
| equipment | skid_loader | optional | required |
| equipment | excavator | hidden or optional | required |
| equipment | backhoe | optional | required |
| equipment | tractor | optional | required |
| equipment | zero_turn_mower | hidden or optional | required |
| equipment | chainsaw | hidden | optional |
| equipment | generator | hidden | required |
| equipment | pressure_washer | hidden | optional |
| equipment | atv | optional | optional |
| equipment | utv | optional | optional |
| equipment | golf_cart | optional | optional |
| equipment | other | optional | optional |

Do not require mileage for equipment where mileage is meaningless. That is a bad solution.

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
- Hydraulics
- Drivetrain
- Cutting System
- Undercarriage
- Attachments
- Other

Rules:

- Categories are organizational only.
- Categories must not be used as a substitute for due-status calculation.

---

### 6.3 MaintenanceItem

Represents a recurring maintenance task for one asset.

Fields:

- id
- asset_id
- category_id
- name
- interval_miles
- interval_hours
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

- Maintenance items belong to one asset.
- Maintenance items may use mileage intervals, hour intervals, time intervals, or any combination of those.
- Items with no interval are allowed only when they are manually tracked inspections.
- Inactive items should not appear in urgent dashboard sections.
- Do not store `last_service_mileage`, `last_service_hours`, `last_service_date`, or `last_service_cost` as canonical fields on `MaintenanceItem`.

Bad design:

```text
MaintenanceItem.last_service_date
MaintenanceItem.last_service_mileage
MaintenanceItem.last_service_hours
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
- asset_id
- maintenance_item_id
- date
- mileage
- hours
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
- Maintenance logs may contribute hour-meter data.
- Do not require mileage on equipment maintenance logs where mileage is irrelevant.
- Do not require hours on road vehicle maintenance logs where hours are irrelevant.

---

### 6.5 FuelLog

Represents a liquid-fuel, mixed-gas, propane, natural-gas, or similar fuel purchase.

Use this only for assets that consume fuel.

Fields:

- id
- asset_id
- date
- mileage
- hours
- fuel_amount
- fuel_unit
- price_per_unit
- total_cost
- is_full_tank
- station
- oil_mix_ratio
- notes
- created_at
- updated_at
- client_generated_id

Rules:

- Gasoline, diesel, mixed-gas, propane, natural-gas, hybrid, plug-in hybrid, and applicable equipment assets may use fuel logs.
- Electric-only assets must not show fuel logs.
- Partial fill-ups count toward cost.
- MPG should only be calculated from reliable full-tank intervals on mileage-bearing assets.
- Gal/hr or fuel-per-hour should only be calculated when reliable hour data exists.
- If `total_cost` is provided but `price_per_unit` is missing, derive `price_per_unit`.
- If `price_per_unit` and `fuel_amount` are provided but `total_cost` is missing, derive `total_cost`.
- Do not generate misleading MPG values from partial fill-ups.
- Do not generate misleading gal/hr values without reliable hour deltas.
- `oil_mix_ratio` applies mainly to `mixed_gas` assets such as chainsaws and some two-stroke equipment.

---

### 6.6 ChargingLog

Represents an EV, electric equipment, or plug-in hybrid charging session.

Use this for electric assets and plug-in hybrids.

Fields:

- id
- asset_id
- date
- mileage
- hours
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

- Electric-only assets must use charging logs instead of fuel logs.
- Plug-in hybrids may use both charging logs and fuel logs.
- `energy_unit` should default to kWh.
- If `total_cost` is provided but `price_per_unit` is missing, derive `price_per_unit`.
- If `price_per_unit` and `energy_added` are provided but `total_cost` is missing, derive `total_cost`.
- Electric efficiency should be calculated only when enough reliable mileage, hour, and energy data exists.
- Partial charges count toward cost.
- Do not require charge percentage fields if the user only knows kWh added and cost.
- For electric equipment, kWh/hr may be more useful than mi/kWh.

---

### 6.7 MeterLog

Represents a mileage or hour-meter update not necessarily tied to fuel, charging, or maintenance.

This replaces the narrower concept of a mileage-only log.

Fields:

- id
- asset_id
- date
- mileage
- hours
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
- hour_meter

Rules:

- Users must be able to update mileage without creating fake fuel, charging, or maintenance records.
- Users must be able to update hours without creating fake fuel, charging, or maintenance records.
- Fuel logs, charging logs, and maintenance logs may also contribute mileage data.
- Fuel logs, charging logs, and maintenance logs may also contribute hour-meter data.
- The latest known mileage may be derived from the newest reliable mileage-bearing record, but the user must be able to override the displayed current mileage.
- The latest known hours may be derived from the newest reliable hour-bearing record, but the user must be able to override the displayed current hours.

---

### 6.8 MaintenanceTemplate

Optional for MVP, useful soon after.

Fields:

- id
- name
- category_id
- supported_asset_categories
- supported_asset_types
- supported_fuel_types
- default_interval_miles
- default_interval_hours
- default_interval_time_days
- default_importance

Purpose:

- Seed common maintenance items when an asset is created.
- Avoid forcing users to create every common item manually.
- Keep defaults generic and asset-type-aware.

Rules:

- Do not make templates manufacturer-specific in the MVP.
- Do not seed impossible tasks for the selected asset type or fuel type.
- Do not seed road-vehicle-only tasks for equipment.
- Do not seed diesel-only tasks for gasoline equipment.
- Do not seed fuel-system tasks for electric-only assets.
- Do not seed oil changes for electric-only assets.

Bad behavior:

```text
Seed Oil Change for Electric Golf Cart
Seed Spark Plugs for Diesel Skid Loader
Seed Hydraulic Filter for Chainsaw
```

Good behavior:

```text
Seed Battery Inspection, Charger Inspection, Brake Inspection, and Tire Inspection for Electric Golf Cart
Seed Hydraulic Fluid, Hydraulic Filter, Engine Oil, Air Filter, and Grease Points for Diesel Skid Loader
Seed Chain Sharpening, Bar Inspection, Air Filter Cleaning, and Spark Plug Inspection for Mixed-Gas Chainsaw
```

---

## 7. Default Maintenance Items

Seed generic defaults when appropriate. Defaults must be filtered by asset category, asset type, and fuel type.

---

### 7.1 Gasoline and Diesel Road Vehicle Defaults

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

### 7.2 Hybrid Road Vehicle Defaults

Use gasoline or diesel road vehicle defaults plus hybrid-aware items where appropriate:

- Hybrid System Inspection
- High Voltage Battery Health Inspection
- Inverter Coolant, if applicable
- Regenerative Brake Inspection

Do not assume every hybrid has the same service needs.

---

### 7.3 Plug-In Hybrid Road Vehicle Defaults

Use hybrid defaults plus charging-related items:

- Charge Port Inspection
- Charging Cable Inspection
- High Voltage Battery Health Inspection
- Inverter Coolant, if applicable

Plug-in hybrids must support both fuel and charging records.

---

### 7.4 Electric Road Vehicle Defaults

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

Do not seed these for electric-only assets:

- Oil Change
- Spark Plugs
- Fuel System Service
- Serpentine Belt, unless the specific asset needs one
- Exhaust-related maintenance

---

### 7.5 Skid Loader and Compact Track Loader Defaults

Use these for `skid_loader` and `compact_track_loader`, filtered by fuel type.

#### Engine

- Engine Oil
- Engine Oil Filter
- Engine Air Filter
- Fuel Filter, if fuel-powered
- Coolant, if liquid-cooled
- Spark Plugs, if gasoline
- Glow Plug Inspection, if diesel and applicable

#### Hydraulics

- Hydraulic Oil
- Hydraulic Filter
- Hydraulic Hoses Inspection
- Lift Cylinder Inspection
- Auxiliary Hydraulics Inspection, if applicable

#### Drivetrain and Wear Items

- Drive Belt or Drive Chain Inspection, if applicable
- Tires or Tracks
- Wheel Bearings or Track Rollers
- Brake Inspection, if applicable

#### Critical

- Grease Pivot Points
- Safety Interlock Inspection
- ROPS or Operator Protection Inspection, if applicable

---

### 7.6 Excavator and Mini Excavator Defaults

#### Engine

- Engine Oil
- Engine Oil Filter
- Engine Air Filter
- Fuel Filter, if fuel-powered
- Coolant, if liquid-cooled

#### Hydraulics

- Hydraulic Oil
- Hydraulic Filter
- Hydraulic Hoses Inspection
- Boom Cylinder Inspection
- Stick Cylinder Inspection
- Bucket Cylinder Inspection

#### Undercarriage

- Track Tension Inspection
- Track Roller Inspection
- Final Drive Oil
- Swing Bearing Grease

#### Attachments

- Bucket Teeth Inspection
- Quick Coupler Inspection, if applicable

---

### 7.7 Backhoe and Tractor Defaults

#### Engine

- Engine Oil
- Engine Oil Filter
- Engine Air Filter
- Fuel Filter, if fuel-powered
- Coolant

#### Fluids

- Hydraulic Fluid
- Hydraulic Filter
- Transmission Fluid
- Front Axle Fluid, if applicable
- Differential Fluid, if applicable

#### Wear Items

- Tires
- Brakes
- Belts
- Grease Points

#### Attachments

- Loader Pins and Bushings
- Three-Point Hitch Inspection, if applicable
- PTO Inspection, if applicable

---

### 7.8 Zero-Turn Mower and Lawn Tractor Defaults

#### Engine

- Engine Oil
- Engine Oil Filter
- Air Filter
- Spark Plug
- Fuel Filter, if applicable

#### Cutting System

- Blade Sharpening
- Blade Replacement
- Mower Deck Cleaning
- Deck Belt Inspection
- Spindle Bearing Inspection

#### Drivetrain and Wear Items

- Hydrostatic Fluid, if applicable
- Tires
- Brake Inspection
- Grease Points

#### Electrical

- Battery
- Starter Solenoid Inspection, if applicable

---

### 7.9 Chainsaw Defaults

Filter by fuel type.

#### Cutting System

- Chain Sharpening
- Chain Replacement
- Bar Inspection
- Bar Cleaning
- Chain Tension Inspection
- Sprocket Inspection

#### Engine or Motor

- Air Filter Cleaning, if fuel-powered
- Spark Plug, if fuel-powered
- Fuel Filter, if fuel-powered
- Carburetor Inspection, if applicable
- Battery Inspection, if electric
- Charger Inspection, if electric

#### Fluids

- Bar Oil Refill
- Bar Oil System Inspection

Do not require mileage or a tank-size workflow for chainsaws.

---

### 7.10 Generator Defaults

#### Engine

- Engine Oil
- Engine Oil Filter, if applicable
- Air Filter
- Spark Plug, if gasoline, propane, or natural gas
- Fuel Filter, if applicable

#### Electrical

- Battery, if electric start
- Outlet Inspection
- Grounding Inspection
- Load Test

#### Fuel System

- Fuel Line Inspection
- Carburetor Inspection, if applicable
- Propane Regulator Inspection, if propane

Use hour-based intervals heavily for generators.

---

### 7.11 Pressure Washer Defaults

#### Engine or Motor

- Engine Oil, if fuel-powered
- Air Filter, if fuel-powered
- Spark Plug, if fuel-powered
- Battery Inspection, if electric start or electric-powered

#### Pump System

- Pump Oil, if applicable
- Pump Inspection
- Hose Inspection
- Wand and Nozzle Inspection
- Inlet Filter Cleaning

---

### 7.12 ATV and UTV Defaults

Filter by fuel type and whether the asset is mileage-bearing, hour-bearing, or both.

#### Engine

- Engine Oil
- Engine Oil Filter
- Air Filter
- Spark Plug, if gasoline
- Fuel Filter, if applicable
- Coolant, if liquid-cooled

#### Drivetrain

- Transmission Fluid
- Differential Fluid
- Drive Belt Inspection, if CVT
- Chain Inspection, if chain-driven
- CV Boots Inspection

#### Wear Items

- Tires
- Brake Pads
- Brake Fluid
- Wheel Bearings

#### Electrical

- Battery
- Lights
- Winch Inspection, if applicable

---

### 7.13 Golf Cart Defaults

Filter by fuel type.

#### Electric Golf Cart

- Battery Inspection
- Battery Water Level, if flooded lead-acid
- Battery Terminal Cleaning
- Charger Inspection
- Charge Port Inspection
- Brake Inspection
- Tire Inspection
- Steering Inspection

#### Gasoline Golf Cart

- Engine Oil
- Air Filter
- Spark Plug
- Fuel Filter
- Drive Belt
- Brake Inspection
- Tire Inspection

---

### 7.14 Generic Equipment Defaults

For `equipment` with `asset_type = other`, seed only safe generic items:

- General Inspection
- Fastener Check
- Safety Equipment Inspection
- Lubrication Check, if applicable
- Air Filter, if fuel-powered
- Battery Inspection, if electric or electric start
- Tire or Track Inspection, if applicable

Do not guess complex equipment-specific maintenance for unknown equipment.

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

An item is `Unknown` if no baseline service log exists and the app cannot determine the last service mileage, hours, or date required by that item's interval.

The UI must prompt the user to enter a baseline service event.

Example:

```text
Hydraulic Filter
Status: Unknown
Action: Enter last known hydraulic filter service
```

Do not hide unknown items. Unknown maintenance is actionable because the user needs to establish a baseline.

---

### 8.2 Mileage-Based Status

For mileage intervals:

```text
next_due_mileage = latest_service_mileage + interval_miles
miles_remaining = next_due_mileage - current_asset_mileage
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

### 8.3 Hour-Based Status

For hour intervals:

```text
next_due_hours = latest_service_hours + interval_hours
hours_remaining = next_due_hours - current_asset_hours
```

Status rules:

```text
if no latest_service_hours:
    status = Unknown
else if hours_remaining < 0:
    status = Overdue
else if hours_remaining <= interval_hours * due_soon_threshold:
    status = Due Soon
else:
    status = Good
```

Example:

```text
interval_hours = 250
due_soon_threshold = 10%
due_soon_hours = 25
```

Hour-based status is required for equipment such as skid loaders, excavators, tractors, generators, zero-turn mowers, and similar assets.

---

### 8.4 Time-Based Status

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

### 8.5 Hybrid Status

For items with more than one interval type:

1. Calculate mileage status if `interval_miles` exists.
2. Calculate hour status if `interval_hours` exists.
3. Calculate time status if `interval_time_days` exists.
4. Use the worst actionable status.

Severity order for comparison:

```text
Unknown < Good < Due Soon < Overdue
```

Example:

```text
Engine Oil:
- Good by hours
- Overdue by date

Final status: Overdue
```

Maintenance can age out even when the asset is not used much.

---

## 9. Fuel, Charging, and Efficiency Calculation

Efficiency calculations must match the asset category, asset type, and fuel type.

Do not show MPG for electric-only assets. Do not show mi/kWh for gasoline-only assets. Do not show road-mile efficiency for equipment where hours are the meaningful meter.

---

### 9.1 Liquid-Fuel Efficiency for Mileage-Bearing Assets

Applicable examples:

- gasoline road vehicles
- diesel road vehicles
- hybrid road vehicles
- plug-in hybrids for liquid-fuel operation
- applicable motorcycles
- applicable ATVs and UTVs

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
- Do not show MPG for equipment that does not track mileage.

---

### 9.2 Liquid-Fuel Efficiency for Hour-Meter Equipment

Applicable examples:

- skid loaders
- excavators
- tractors
- generators
- zero-turn mowers
- other hour-meter equipment

Preferred metric:

```text
gallons_per_hour = fuel_used / hours_used
```

Alternative display:

```text
hours_per_gallon = hours_used / fuel_used
```

Rules:

- Use hour deltas only when reliable hour-meter readings exist.
- Partial fills count toward cost.
- Partial fills may contribute to long-window fuel burn calculations only if the method is clearly reliable.
- Do not produce gal/hr from a single fuel log with no previous reliable hour reading.
- If reliability is uncertain, show "No reliable fuel-per-hour data yet".

---

### 9.3 Electric Efficiency for Mileage-Bearing Assets

Applicable examples:

- EV road vehicles
- plug-in hybrids for electric operation
- electric motorcycles
- electric golf carts or ATVs if mileage is tracked

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
- If reliability is uncertain, show "No reliable charging efficiency data yet".

---

### 9.4 Electric Efficiency for Hour-Meter Equipment

Applicable examples:

- electric skid loaders
- electric golf carts when hours are tracked
- electric mowers
- battery-powered tools where runtime is tracked

Preferred metric:

```text
kwh_per_hour = energy_added_kwh / hours_used
```

Alternative display:

```text
hours_per_kwh = hours_used / energy_added_kwh
```

Rules:

- Calculate electric equipment efficiency only when reliable hour and kWh data exists.
- Do not require this metric for MVP if the asset does not naturally expose hour data.
- If reliability is uncertain, show "No reliable electric usage data yet".

---

### 9.5 Plug-In Hybrid Efficiency

Plug-in hybrids must support both liquid-fuel and electric tracking.

Show separate summaries when possible:

- Fuel MPG
- Electric mi/kWh or kWh/100mi
- Fuel cost
- Charging cost
- Combined operating cost

Do not collapse PHEV efficiency into a single vague number unless the app clearly defines the calculation.

---

### 9.6 Mixed-Gas Assets

Applicable examples:

- chainsaws
- some trimmers
- some blowers
- older two-stroke equipment

Rules:

- Use fuel logs for mixed gas.
- Support optional `oil_mix_ratio`, such as `50:1` or `40:1`.
- Do not require MPG.
- Do not require tank capacity.
- Cost tracking is more important than efficiency for many small mixed-gas tools.

---

## 10. Cost Calculation

### 10.1 Fuel Cost

For liquid fuel, mixed gas, propane, natural gas, or similar fuel:

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

Maintenance cost is the sum of `MaintenanceServiceLog.cost` for an asset.

Missing cost values should be treated as unknown for that entry, not as proof the service was free unless the user explicitly enters zero.

---

### 10.4 Total Operating Cost

For fuel-only assets:

```text
total_operating_cost = total_fuel_cost + total_maintenance_cost
```

For electric-only assets:

```text
total_operating_cost = total_charging_cost + total_maintenance_cost
```

For plug-in hybrids:

```text
total_operating_cost = total_fuel_cost + total_charging_cost + total_maintenance_cost
```

For assets with no fuel or charging logs:

```text
total_operating_cost = total_maintenance_cost
```

---

### 10.5 Cost Per Mile

```text
cost_per_mile = total_operating_cost / miles_tracked
```

`miles_tracked` should be based on the earliest and latest known mileage records for the asset.

Rules:

- Use only for mileage-bearing assets.
- Handle zero-mile cases safely.
- Handle insufficient data safely.
- Do not divide by zero.
- If mileage data is insufficient, show "Not enough mileage data yet".

---

### 10.6 Cost Per Hour

```text
cost_per_hour = total_operating_cost / hours_tracked
```

`hours_tracked` should be based on the earliest and latest known hour-meter records for the asset.

Rules:

- Use only for hour-meter-bearing assets.
- Handle zero-hour cases safely.
- Handle insufficient data safely.
- Do not divide by zero.
- If hour data is insufficient, show "Not enough hour data yet".

---

## 11. Dashboard Requirements

The asset dashboard must prioritize urgency.

Show, in order:

1. Critical overdue items
2. High-importance overdue items
3. Other overdue items
4. Unknown critical or high-importance items needing baseline data
5. Due-soon items
6. Upcoming maintenance
7. Recent logs
8. Fuel, charging, and cost summary appropriate to the asset type

The top of the dashboard must answer:

```text
What needs attention right now?
```

Do not make the user scroll through all maintenance items before showing overdue work.

Good dashboard summary for a road vehicle:

```text
Needs attention now:
Brake Fluid is overdue by 48 days.
Oil Change is due in 320 miles.
Tire Rotation needs baseline data.
```

Good dashboard summary for equipment:

```text
Needs attention now:
Hydraulic Filter is overdue by 12.4 hours.
Grease Pivot Points is due in 3.2 hours.
Track Tension Inspection needs baseline data.
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

Shows all active assets.

Each asset card should show:

- Asset name
- Year, make, model, if applicable
- Asset category
- Asset type
- Fuel or energy type
- Current mileage, if applicable
- Current hours, if applicable
- Overdue count
- Due-soon count
- Recent efficiency metric, if available and applicable
- Basic cost summary, if available

Archived assets should not appear here by default.

Examples:

```text
Gasoline road vehicle: Recent MPG
EV: Recent mi/kWh or kWh/100mi
PHEV: Fuel MPG and electric efficiency, if available
Diesel skid loader: Recent gal/hr, if available
Electric golf cart: Charging cost this month
Chainsaw: Maintenance cost this year
```

---

### 12.2 Add/Edit Asset Screen

Must support:

- Name
- Make
- Model
- Year
- Asset category
- Vehicle type, when category is vehicle
- Equipment type, when category is equipment
- Fuel or energy type
- Current mileage, if applicable
- Current hours, if applicable
- Asset-type-specific capacity fields
- Asset-type-specific fuel or battery fields

Required dynamic behavior:

- Selecting `vehicle` shows the vehicle type dropdown.
- Selecting `equipment` shows the equipment type dropdown immediately below the category/type selector.
- Selecting `equipment` also shows the fuel or energy type dropdown.
- Selecting `electric` hides tank capacity and fuel log options.
- Selecting `electric` shows battery capacity in kWh when useful.
- Selecting `gasoline`, `diesel`, `mixed_gas`, `propane`, or `natural_gas` shows fuel log options.
- Selecting `plug_in_hybrid_gasoline` or `plug_in_hybrid_diesel` shows both tank capacity and battery capacity.
- Selecting `hybrid_gasoline` or `hybrid_diesel` shows tank capacity and may optionally show battery information.
- Selecting hour-meter equipment shows current hours.
- Selecting non-mileage equipment hides current mileage unless the user enables it.
- Units and labels must update immediately when type or fuel selection changes.

Bad behavior:

```text
Chainsaw
Current Mileage: required
Tank Capacity: required
Recent MPG
```

Good behavior:

```text
Chainsaw
Fuel / Energy Type: Mixed Gas
Oil Mix Ratio: 50:1
Hours: optional
Chain Sharpening needs baseline data
```

---

### 12.3 Asset Detail Screen

Shows one asset's maintenance state.

Include:

- Current mileage, if applicable
- Current hours, if applicable
- Asset category
- Asset type
- Fuel or energy type
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
- Last service mileage, if applicable
- Last service hours, if applicable
- Next due date, if applicable
- Next due mileage, if applicable
- Next due hours, if applicable
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
Hydraulic Filter
Overdue by 12.4 hours
High importance
Log service
```

Bad UX:

```text
Hydraulic Filter
Last: 1120.6 hours
Interval: 250 hours
Next: 1370.6 hours
```

Raw data can exist, but the primary UI must translate it into action.

---

### 12.5 Add Log Screen

Must support these log types where applicable:

- Maintenance log
- Fuel log
- Charging log
- Meter update

Maintenance log fields:

- Asset
- Maintenance item
- Date
- Mileage, if applicable
- Hours, if applicable
- Cost
- Notes

Fuel log fields:

- Asset
- Date
- Mileage, if applicable
- Hours, if applicable
- Fuel amount
- Fuel unit
- Price per unit
- Total cost
- Full tank toggle, if applicable
- Station
- Oil mix ratio, if mixed gas
- Notes

Charging log fields:

- Asset
- Date
- Mileage, if applicable
- Hours, if applicable
- Energy added
- Energy unit
- Price per unit
- Total cost
- Charge percent before
- Charge percent after
- Full charge toggle
- Location
- Notes

Meter update fields:

- Asset
- Date
- Mileage, if applicable
- Hours, if applicable
- Notes

Rules:

- Electric-only assets should show charging logs, not fuel logs.
- Plug-in hybrids should show both fuel and charging logs.
- Gasoline and diesel assets should show fuel logs, not charging logs.
- Mixed-gas assets should show fuel logs with optional oil mix ratio.
- Assets with `fuel_type = none` should not show fuel or charging logs.
- Manual mileage updates must not require fake maintenance, fuel, or charging entries.
- Manual hour updates must not require fake maintenance, fuel, or charging entries.

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
- Add `asset_category`, `asset_type`, and `fuel_type` enums
- Add type-aware validation helpers
- Add hour-meter support before equipment UI is considered complete

Do not start with charts, AI, cloud sync, or visual polish.

---

### Phase 2: Asset CRUD

- Create asset
- Edit asset
- Archive asset
- Update mileage
- Update hours
- Implement category-aware Add/Edit Asset behavior
- Implement equipment type dropdown
- Implement fuel or energy type dropdown
- Ensure irrelevant fields are hidden or disabled by asset type and fuel type

---

### Phase 3: Maintenance Core

- Add maintenance categories
- Add asset-type-aware maintenance templates
- Add maintenance items
- Add maintenance service logs
- Derive latest service from logs
- Calculate next due mileage
- Calculate next due hours
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
- Use asset-type-appropriate terminology
- Show miles, hours, or dates based on what matters for the asset

---

### Phase 5: Fuel and Charging Tracking

- Add fuel logs for fuel-powered assets
- Add charging logs for electric assets and plug-in hybrids
- Support full-tank toggle
- Support full-charge toggle
- Support optional oil mix ratio for mixed-gas equipment
- Calculate MPG from reliable full-tank intervals
- Calculate gal/hr from reliable hour and fuel intervals
- Calculate electric road efficiency from reliable charging and mileage intervals
- Calculate electric equipment usage from reliable charging and hour intervals
- Track fuel cost
- Track charging cost

---

### Phase 6: Cost Summary

- Calculate maintenance total
- Calculate fuel total where applicable
- Calculate charging total where applicable
- Calculate total operating cost
- Calculate cost per mile when enough mileage data exists
- Calculate cost per hour when enough hour data exists

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
- Asset photos
- Attachments and implements
- Seasonal maintenance reminders

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
8. Lowest remaining miles, hours, or days

Example output for a road vehicle:

```text
Most urgent: Oil Change
Reason: Overdue by 620 miles and marked high importance.
```

Example output for equipment:

```text
Most urgent: Hydraulic Filter
Reason: Overdue by 12.4 hours and marked high importance.
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

The health score should punish overdue oil, brakes, timing belts, hydraulic filters, high-voltage system inspections, track tension inspections, and other high-importance items more than low-importance items like wiper blades.

---

## 16. Testing Requirements

Add tests for calculation-heavy logic.

At minimum, test:

- Asset category field visibility rules
- Asset type field visibility rules
- Fuel type field visibility rules
- Vehicle category shows vehicle type dropdown
- Equipment category shows equipment type dropdown
- Fuel or energy type dropdown appears when applicable
- Electric assets hide fuel fields
- Electric assets show battery capacity in kWh when useful
- Plug-in hybrids support fuel and charging logs
- Gasoline and diesel assets do not show charging logs by default
- Mixed-gas assets support optional oil mix ratio
- Equipment can be hour-meter-bearing without mileage
- Road vehicles can be mileage-bearing without hours
- Mileage-only due status
- Hour-only due status
- Time-only due status
- Hybrid due status using mileage and time
- Hybrid due status using hours and time
- Hybrid due status using mileage, hours, and time
- Due-soon threshold
- Overdue status
- Unknown status
- Latest service derivation from logs
- Full-tank MPG calculation
- Partial fuel fill behavior
- Fuel-per-hour calculation
- Fuel-per-hour insufficient-data behavior
- EV charging cost calculation
- EV road efficiency calculation
- Electric equipment kWh/hr calculation
- Partial charging behavior
- Fuel total calculation
- Charging total calculation
- Maintenance total calculation
- Cost-per-mile zero-mile safety
- Cost-per-hour zero-hour safety
- Archived assets hidden from default home view
- Idempotent log creation using `client_generated_id`
- Impossible maintenance templates are not seeded

Calculation logic should be unit-tested outside UI code.

---

## 17. Data Integrity Rules

Follow these strictly:

- Do not delete maintenance logs when editing a maintenance item.
- Do not mutate old logs when adding new service.
- Do not calculate MPG from unreliable partial-fill intervals.
- Do not calculate gal/hr from insufficient or unreliable hour data.
- Do not calculate EV efficiency from insufficient or zero-energy data.
- Do not rely on duplicated last-service fields as source of truth.
- Do not allow cost-per-mile division by zero.
- Do not allow cost-per-hour division by zero.
- Do not show archived assets by default.
- Do not require internet access for core features.
- Do not require accounts for MVP.
- Do not hide unknown maintenance status. Prompt for baseline data instead.
- Do not show fuel-only concepts for electric-only assets.
- Do not show charging-only concepts for fuel-only assets.
- Do not seed maintenance items that are impossible for the selected asset type or fuel type.
- Do not force equipment into mileage workflows.
- Do not infer permanent fuel type from equipment type.

---

## 18. UX Rules

The app should feel like a checkbook plus a maintenance advisor.

Use plain language:

- "Due in 320 miles"
- "Due in 12.5 hours"
- "Overdue by 42 days"
- "Overdue by 8.2 hours"
- "Needs baseline"
- "Last done 5,240 miles ago"
- "Last done 76.3 hours ago"
- "No full-tank MPG data yet"
- "No reliable fuel-per-hour data yet"
- "No reliable charging efficiency data yet"
- "Charging cost this month"
- "Fuel cost this month"
- "Maintenance cost this year"

The UI should prioritize action over raw records.

Good UX:

```text
Brake Fluid
Overdue by 48 days
High importance
Log service
```

Good equipment UX:

```text
Grease Pivot Points
Due in 3.2 hours
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

Asset-type language must be correct.

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

Bad UX:

```text
Chainsaw
Odometer
Recent MPG
Tire Rotation
```

Good UX:

```text
Chainsaw
Fuel / Energy Type: Mixed Gas
Chain Sharpening
Bar Oil System Inspection
No mileage required
```

---

## 19. Acceptance Criteria

The implementation is acceptable when:

- Multiple assets can be managed independently.
- Assets have a clear category.
- Vehicles and equipment are both supported.
- Equipment has a second dropdown for equipment type.
- Fuel or energy type is selected separately from asset type.
- Asset category, asset type, and fuel type control fields, units, labels, logs, defaults, and summaries.
- Electric assets use battery capacity, charging logs, charging costs, and electric efficiency where applicable.
- Liquid-fuel assets use tank capacity, fuel logs, fuel costs, and MPG or fuel-per-hour where applicable.
- Mixed-gas assets can track fuel and optional oil mix ratio without forcing mileage.
- Plug-in hybrids support both fuel and charging workflows.
- Hour-meter equipment supports hour updates and hour-based maintenance.
- Mileage-bearing assets support mileage updates and mileage-based maintenance.
- Maintenance items are per-asset.
- Maintenance logs are historical and preserved.
- Last service values are derived from logs.
- Due status works for mileage, hours, time, and hybrid intervals.
- Fuel logs support full and partial fills.
- Charging logs support full and partial charges.
- MPG is calculated only from reliable full-tank intervals.
- Fuel-per-hour is calculated only from reliable hour and fuel data.
- Electric efficiency is calculated only from reliable charging, mileage, or hour data.
- Costs are summarized per asset.
- Cost-per-mile handles insufficient data safely.
- Cost-per-hour handles insufficient data safely.
- The dashboard clearly shows what needs attention next.
- Archived assets are hidden by default.
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
What has this asset cost me?
How efficiently is it running, using the correct metric for this asset?
Is this asset tracked by miles, hours, dates, or a combination?
```

If the implementation does not answer those questions clearly, keep improving it.

The MVP succeeds when it behaves like a trustworthy local maintenance checkbook for vehicles and equipment, not a bloated fleet platform.
