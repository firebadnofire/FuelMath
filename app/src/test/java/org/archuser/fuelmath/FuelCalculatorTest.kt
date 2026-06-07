package org.archuser.fuelmath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FuelCalculatorTest {
    private val asOfDate = LocalDate.of(2026, 1, 1)
    private val vehicle = Vehicle(
        id = "vehicle-1",
        name = "2009 Smart Fortwo",
        make = "Smart",
        model = "Fortwo",
        year = 2009,
        currentMileage = 12_000.0,
        fuelType = FuelType.GASOLINE,
        tankCapacity = 8.7,
        distanceUnit = DistanceUnit.MILES,
        volumeUnit = VolumeUnit.GALLONS,
    )

    @Test
    fun noMpgWhenNoFullToFullSegmentExists() {
        val entries = listOf(
            fuelEntry("a", odometer = 1000.0, fuelAmount = 8.0, isFullTank = true),
            fuelEntry("b", odometer = 1120.0, fuelAmount = 4.0, isFullTank = false),
        )
        val data = data(fuelEntries = entries)

        val summary = FuelCalculator.buildVehicleSummary(vehicle, data, asOfDate)

        assertTrue(FuelCalculator.calculateEfficiencySegments(vehicle, entries).isEmpty())
        assertNull(summary.lastEfficiency)
    }

    @Test
    fun mpgUsesFuelBetweenFullTankEntriesIncludingInterveningPartials() {
        val entries = listOf(
            fuelEntry("a", odometer = 1000.0, fuelAmount = 8.0, isFullTank = true),
            fuelEntry("b", odometer = 1100.0, fuelAmount = 5.0, isFullTank = false),
            fuelEntry("c", odometer = 1200.0, fuelAmount = 6.0, isFullTank = true),
        )

        val segments = FuelCalculator.calculateEfficiencySegments(vehicle, entries)

        assertEquals(1, segments.size)
        assertEquals(200.0, segments.single().distance, 0.0001)
        assertEquals(11.0, segments.single().fuelUsed, 0.0001)
        assertEquals(18.1818, segments.single().value, 0.0001)
    }

    @Test
    fun metricEfficiencyUsesLitersPer100Km() {
        val metricVehicle = vehicle.copy(
            tankCapacity = 45.0,
            distanceUnit = DistanceUnit.KILOMETERS,
            volumeUnit = VolumeUnit.LITERS,
        )
        val entries = listOf(
            fuelEntry("a", odometer = 10_000.0, fuelAmount = 40.0, isFullTank = true),
            fuelEntry("b", odometer = 10_500.0, fuelAmount = 25.0, isFullTank = true),
        )

        val segments = FuelCalculator.calculateEfficiencySegments(metricVehicle, entries)

        assertEquals(5.0, segments.single().value, 0.0001)
    }

    @Test
    fun vehicleTypeRulesDistinguishEvFromLiquidFuelVehicles() {
        assertTrue(VehicleType.EV.usesBattery)
        assertTrue(!VehicleType.EV.usesLiquidFuel)
        assertTrue(VehicleType.GASOLINE.usesLiquidFuel)
        assertTrue(!VehicleType.GASOLINE.usesBattery)
        assertTrue(VehicleType.PLUG_IN_HYBRID.usesLiquidFuel)
        assertTrue(VehicleType.PLUG_IN_HYBRID.usesBattery)
    }

    @Test
    fun evTemplatesExcludeOilChangeAndIncludeChargingMaintenance() {
        val templates = MaintenanceDefaults.templatesForVehicleType(VehicleType.EV)

        assertTrue(templates.none { it.name == "Oil Change" })
        assertTrue(templates.any { it.name == "Charge Port Inspection" })
        assertTrue(templates.any { it.name == "Charging Cable Inspection" })
        assertTrue(templates.any { it.name == "Front Tires" })
        assertTrue(templates.any { it.name == "Rear Tires" })
        assertTrue(templates.any { it.name == "Tire Rotation" })
        assertTrue(templates.none { it.name == "Tires" })
    }

    @Test
    fun plugInHybridTemplatesIncludeFuelAndChargingMaintenance() {
        val templates = MaintenanceDefaults.templatesForVehicleType(VehicleType.PLUG_IN_HYBRID)

        assertTrue(templates.any { it.name == "Oil Change" })
        assertTrue(templates.any { it.name == "Charge Port Inspection" })
        assertTrue(templates.any { it.name == "Hybrid System Inspection" })
        assertTrue(templates.any { it.name == "Front Tires" })
        assertTrue(templates.any { it.name == "Rear Tires" })
        assertTrue(templates.any { it.name == "Tire Rotation" })
        assertTrue(templates.none { it.name == "Tires" })
    }

    @Test
    fun electricEfficiencyUsesChargingEntriesAndKwh() {
        val ev = vehicle.copy(
            fuelType = FuelType.ELECTRIC,
            vehicleType = VehicleType.EV,
            tankCapacity = 0.0,
            batteryCapacity = 75.0,
            energyUnit = EnergyUnit.KILOWATT_HOURS,
        )
        val chargingEntries = listOf(
            fuelEntry("a", odometer = 1000.0, fuelAmount = 50.0, pricePerUnit = 0.20, entryType = EnergyEntryType.CHARGING),
            fuelEntry("b", odometer = 1100.0, fuelAmount = 20.0, pricePerUnit = 0.30, entryType = EnergyEntryType.CHARGING),
            fuelEntry("c", odometer = 1110.0, fuelAmount = 2.0, pricePerUnit = 3.00, entryType = EnergyEntryType.FUEL),
        )

        val segments = FuelCalculator.calculateEfficiencySegments(ev, chargingEntries)
        val summary = FuelCalculator.buildVehicleSummary(
            ev,
            FuelMathData(vehicles = listOf(ev), fuelEntries = chargingEntries),
            asOfDate,
        )

        assertEquals("mi/kWh", FuelCalculator.efficiencyUnitLabel(ev))
        assertEquals(1, segments.size)
        assertEquals(5.0, segments.single().value, 0.0001)
        assertEquals(0.0, summary.totalFuelCost, 0.0001)
        assertEquals(16.0, summary.totalChargingCost, 0.0001)
        assertEquals(5.0, summary.lastEfficiency ?: -1.0, 0.0001)
        assertEquals(375.0, summary.estimatedRange ?: -1.0, 0.0001)
    }

    @Test
    fun summaryCalculatesFuelMaintenanceDistanceCostPerMileAndRange() {
        val entries = listOf(
            fuelEntry("a", odometer = 1000.0, fuelAmount = 8.0, pricePerUnit = 3.0, isFullTank = true),
            fuelEntry("b", odometer = 1100.0, fuelAmount = 5.0, pricePerUnit = 4.0, isFullTank = false),
            fuelEntry("c", odometer = 1200.0, fuelAmount = 5.0, pricePerUnit = 4.0, isFullTank = true),
        )
        val logs = listOf(serviceLog("oil-log", cost = 36.0))

        val summary = FuelCalculator.buildVehicleSummary(
            vehicle.copy(currentMileage = 1200.0),
            data(fuelEntries = entries, serviceLogs = logs),
            asOfDate,
        )

        assertEquals(64.0, summary.totalFuelCost, 0.0001)
        assertEquals(36.0, summary.totalMaintenanceCost, 0.0001)
        assertEquals(100.0, summary.totalCost, 0.0001)
        assertEquals(200.0, summary.totalDistance ?: -1.0, 0.0001)
        assertEquals(0.5, summary.costPerDistance ?: -1.0, 0.0001)
        assertEquals(20.0, summary.lastEfficiency ?: -1.0, 0.0001)
        assertEquals(174.0, summary.estimatedRange ?: -1.0, 0.0001)
    }

    @Test
    fun maintenanceStatusSupportsMileageTimeAndHybridIntervals() {
        val mileageDueSoon = maintenanceItem(
            id = "oil",
            name = "Oil Change",
            intervalMiles = 5_000.0,
        )
        val timeOverdue = maintenanceItem(
            id = "brake-fluid",
            name = "Brake Fluid",
            categoryId = MaintenanceDefaults.CATEGORY_FLUIDS,
            intervalTimeDays = 180,
        )
        val hybridOverdueByTime = maintenanceItem(
            id = "coolant",
            name = "Coolant",
            categoryId = MaintenanceDefaults.CATEGORY_FLUIDS,
            intervalMiles = 30_000.0,
            intervalTimeDays = 90,
        )
        val logs = listOf(
            serviceLog("oil-log", itemId = "oil", odometer = 10_000.0, dateTime = LocalDateTime.of(2025, 10, 1, 8, 30)),
            serviceLog("brake-log", itemId = "brake-fluid", odometer = 12_000.0, dateTime = LocalDateTime.of(2025, 1, 1, 8, 30)),
            serviceLog("coolant-log", itemId = "coolant", odometer = 11_500.0, dateTime = LocalDateTime.of(2025, 9, 1, 8, 30)),
        )
        val states = FuelCalculator.calculateMaintenanceStates(
            vehicle.copy(currentMileage = 14_600.0),
            data(items = listOf(mileageDueSoon, timeOverdue, hybridOverdueByTime), serviceLogs = logs),
            asOfDate,
        ).associateBy { it.item.id }

        assertEquals(MaintenanceStatus.DUE_SOON, states.getValue("oil").status)
        assertEquals(MaintenanceStatus.OVERDUE, states.getValue("brake-fluid").status)
        assertEquals(MaintenanceStatus.OVERDUE, states.getValue("coolant").status)
    }

    @Test
    fun maintenanceStatusIsUnknownWhenBaselineServiceLogIsMissing() {
        val item = maintenanceItem(
            id = "battery",
            name = "Battery",
            categoryId = MaintenanceDefaults.CATEGORY_ELECTRICAL,
            intervalTimeDays = 365,
        )

        val states = FuelCalculator.calculateMaintenanceStates(
            vehicle,
            data(items = listOf(item)),
            asOfDate,
        )

        assertEquals(MaintenanceStatus.UNKNOWN, states.single().status)
        assertEquals("Battery", FuelCalculator.chooseSmartRecommendation(states)?.item?.name)
    }

    @Test
    fun equipmentCanUseHourOnlyMaintenanceWithoutMileage() {
        val skidLoader = vehicle.copy(
            id = "skid-loader-1",
            name = "Skid Loader",
            assetCategory = AssetCategory.EQUIPMENT,
            assetType = AssetType.SKID_LOADER,
            fuelType = FuelType.DIESEL,
            vehicleType = VehicleType.DIESEL,
            currentMileage = 0.0,
            currentHours = 126.0,
            tankCapacity = 14.0,
        )
        val hydraulicFilter = MaintenanceItem(
            id = "hydraulic-filter",
            vehicleId = skidLoader.id,
            categoryId = MaintenanceDefaults.CATEGORY_HYDRAULICS,
            name = "Hydraulic Filter",
            intervalHours = 50.0,
            importance = MaintenanceImportance.HIGH,
        )
        val logs = listOf(
            MaintenanceServiceLog(
                id = "hydraulic-filter-log",
                vehicleId = skidLoader.id,
                maintenanceItemId = hydraulicFilter.id,
                dateTime = LocalDateTime.of(2025, 12, 1, 8, 0),
                odometer = 0.0,
                hours = 75.0,
                cost = 42.0,
            ),
        )

        val states = FuelCalculator.calculateMaintenanceStates(
            skidLoader,
            FuelMathData(
                vehicles = listOf(skidLoader),
                maintenanceItems = listOf(hydraulicFilter),
                maintenanceServiceLogs = logs,
            ),
            asOfDate,
        )

        assertEquals(MaintenanceStatus.OVERDUE, states.single().status)
        assertEquals(125.0, states.single().nextDueHours ?: -1.0, 0.0001)
        assertEquals(-1.0, states.single().hoursRemaining ?: 1.0, 0.0001)
        assertNull(states.single().nextDueMileage)
    }

    @Test
    fun archivedAssetsAreHiddenFromReminderSnapshots() {
        val archivedVehicle = vehicle.copy(archived = true, currentMileage = 20_000.0)
        val item = maintenanceItem(
            id = "oil",
            name = "Oil Change",
            intervalMiles = 5_000.0,
        )
        val log = serviceLog("oil-log", itemId = item.id, odometer = 1_000.0)

        val reminder = FuelCalculator.buildReminderSnapshot(
            FuelMathData(
                vehicles = listOf(archivedVehicle),
                maintenanceItems = listOf(item.copy(vehicleId = archivedVehicle.id)),
                maintenanceServiceLogs = listOf(log.copy(vehicleId = archivedVehicle.id)),
            ),
            asOfDate,
        )

        assertNull(reminder)
    }

    @Test
    fun removeVehicleAndRelatedDataDeletesArchivedAssetRecords() {
        val archivedVehicle = vehicle.copy(id = "archived-1", name = "Old Truck", archived = true)
        val archivedItem = MaintenanceItem(
            id = "archived-item",
            vehicleId = archivedVehicle.id,
            categoryId = MaintenanceDefaults.CATEGORY_ENGINE,
            name = "Oil Change",
            intervalMiles = 5_000.0,
        )
        val original = FuelMathData(
            vehicles = listOf(vehicle, archivedVehicle),
            fuelEntries = listOf(
                fuelEntry("fuel-active", odometer = 12_300.0),
                FuelEntry(
                    id = "fuel-archived",
                    vehicleId = archivedVehicle.id,
                    dateTime = LocalDateTime.of(2026, 1, 2, 8, 0),
                    odometer = 80_000.0,
                    fuelAmount = 10.0,
                    pricePerUnit = 3.5,
                    isFullTank = true,
                ),
            ),
            meterLogs = listOf(
                MeterLog(
                    id = "meter-archived",
                    vehicleId = archivedVehicle.id,
                    dateTime = LocalDateTime.of(2026, 1, 3, 8, 0),
                    mileage = 80_100.0,
                    source = MeterLogSource.MANUAL,
                ),
            ),
            maintenanceItems = listOf(maintenanceItem(), archivedItem),
            maintenanceServiceLogs = listOf(
                serviceLog("service-active"),
                MaintenanceServiceLog(
                    id = "service-archived",
                    vehicleId = archivedVehicle.id,
                    maintenanceItemId = archivedItem.id,
                    dateTime = LocalDateTime.of(2026, 1, 4, 8, 0),
                    odometer = 80_100.0,
                    cost = 60.0,
                ),
            ),
        )

        val updated = FuelCalculator.removeVehicleAndRelatedData(original, archivedVehicle.id)

        assertEquals(listOf(vehicle), updated.vehicles)
        assertTrue(updated.fuelEntries.all { it.vehicleId != archivedVehicle.id })
        assertTrue(updated.meterLogs.all { it.vehicleId != archivedVehicle.id })
        assertTrue(updated.maintenanceItems.all { it.vehicleId != archivedVehicle.id })
        assertTrue(updated.maintenanceServiceLogs.all { it.vehicleId != archivedVehicle.id })
        assertEquals(1, updated.fuelEntries.size)
        assertEquals(1, updated.maintenanceItems.size)
        assertEquals(1, updated.maintenanceServiceLogs.size)
    }

    @Test
    fun equipmentTemplatesAreAssetAndFuelAware() {
        val electricGolfCart = MaintenanceDefaults.templatesForAsset(
            assetCategory = AssetCategory.EQUIPMENT,
            assetType = AssetType.GOLF_CART,
            fuelType = FuelType.ELECTRIC,
        )
        val mixedGasChainsaw = MaintenanceDefaults.templatesForAsset(
            assetCategory = AssetCategory.EQUIPMENT,
            assetType = AssetType.CHAINSAW,
            fuelType = FuelType.MIXED_GAS,
        )

        assertTrue(electricGolfCart.any { it.name == "Charger Inspection" })
        assertTrue(electricGolfCart.none { it.name == "Engine Oil" })
        assertTrue(electricGolfCart.none { it.name == "Spark Plug" })
        assertTrue(mixedGasChainsaw.any { it.name == "Chain Sharpening" })
        assertTrue(mixedGasChainsaw.any { it.name == "Spark Plug" })
        assertTrue(mixedGasChainsaw.none { it.name == "Tire Rotation" })
    }

    @Test
    fun healthScoreAndRecommendationPrioritizeWeightedUrgentItems() {
        val overdueHigh = maintenanceItem(
            id = "brakes",
            name = "Brake Pads",
            categoryId = MaintenanceDefaults.CATEGORY_WEAR_ITEMS,
            intervalMiles = 10_000.0,
            importance = MaintenanceImportance.HIGH,
        )
        val goodLow = maintenanceItem(
            id = "wipers",
            name = "Wiper Blades",
            categoryId = MaintenanceDefaults.CATEGORY_WEAR_ITEMS,
            intervalMiles = 20_000.0,
            importance = MaintenanceImportance.LOW,
        )
        val logs = listOf(
            serviceLog("brakes-log", itemId = "brakes", odometer = 1_000.0),
            serviceLog("wipers-log", itemId = "wipers", odometer = 11_900.0),
        )
        val states = FuelCalculator.calculateMaintenanceStates(
            vehicle,
            data(items = listOf(overdueHigh, goodLow), serviceLogs = logs),
            asOfDate,
        )

        assertEquals(25, FuelCalculator.calculateHealthScore(states))
        assertEquals("Brake Pads", FuelCalculator.chooseSmartRecommendation(states)?.item?.name)
    }

    @Test
    fun entryDistancesAreStableByOdometerOrder() {
        val entries = listOf(
            fuelEntry("c", odometer = 220.0),
            fuelEntry("a", odometer = 100.0),
            fuelEntry("b", odometer = 160.0),
        )

        val distances = FuelCalculator.calculateEntryDistances(entries)

        assertEquals("a", distances[0].entry.id)
        assertNull(distances[0].distanceFromPrevious)
        assertEquals(60.0, distances[1].distanceFromPrevious ?: -1.0, 0.0001)
        assertEquals(60.0, distances[2].distanceFromPrevious ?: -1.0, 0.0001)
    }

    @Test
    fun jsonRoundTripPreservesSchemaV3Data() {
        val item = maintenanceItem(
            id = "oil",
            name = "Oil Change",
            intervalMiles = 5_000.0,
            intervalTimeDays = 180,
        )
        val roundTripData = data(
            fuelEntries = listOf(fuelEntry("fuel-1", odometer = 1234.0)),
            items = listOf(item),
            serviceLogs = listOf(serviceLog("maint-1", itemId = item.id, odometer = 12_000.0)),
            settings = AppSettings(dueSoonThresholdPercent = 15, maintenanceRemindersEnabled = true),
        )

        val decoded = FuelJsonCodec.decode(FuelJsonCodec.encode(roundTripData))

        assertEquals(roundTripData, decoded)
    }

    @Test
    fun jsonRoundTripPreservesEvVehicleTypeAndChargingEntries() {
        val ev = vehicle.copy(
            fuelType = FuelType.ELECTRIC,
            vehicleType = VehicleType.EV,
            tankCapacity = 0.0,
            batteryCapacity = 75.0,
            recommendedFrontTirePsi = 41.0,
            recommendedRearTirePsi = 39.0,
            energyUnit = EnergyUnit.KILOWATT_HOURS,
        )
        val roundTripData = FuelMathData(
            vehicles = listOf(ev),
            fuelEntries = listOf(
                fuelEntry("charge-1", odometer = 1234.0, fuelAmount = 25.0, entryType = EnergyEntryType.CHARGING),
            ),
        )

        val decoded = FuelJsonCodec.decode(FuelJsonCodec.encode(roundTripData))

        assertEquals(roundTripData, decoded)
        assertEquals(VehicleType.EV, decoded.vehicles.single().vehicleType)
        assertEquals(EnergyEntryType.CHARGING, decoded.fuelEntries.single().entryType)
        assertEquals(41.0, decoded.vehicles.single().recommendedFrontTirePsi ?: -1.0, 0.0001)
        assertEquals(39.0, decoded.vehicles.single().recommendedRearTirePsi ?: -1.0, 0.0001)
    }

    @Test
    fun jsonRoundTripPreservesAssetStationSuggestions() {
        val asset = vehicle.copy(stationSuggestions = listOf("Main Street Fuel", "Home Charger"))
        val roundTripData = FuelMathData(vehicles = listOf(asset))

        val decoded = FuelJsonCodec.decode(FuelJsonCodec.encode(roundTripData))

        assertEquals(asset.stationSuggestions, decoded.vehicles.single().stationSuggestions)
    }

    @Test
    fun jsonBackfillsMissingStationSuggestionsFromAssetFuelHistory() {
        val oldJson = FuelJsonCodec.encode(
            FuelMathData(
                vehicles = listOf(vehicle),
                fuelEntries = listOf(
                    fuelEntry("station-a", odometer = 12_100.0, station = "Main Street Fuel"),
                    fuelEntry("station-b", odometer = 12_200.0, station = "main street fuel"),
                    fuelEntry("station-c", odometer = 12_300.0, station = "Home Charger"),
                ),
            ),
        ).replace("\"stationSuggestions\":[],", "")

        val decoded = FuelJsonCodec.decode(oldJson)

        assertEquals(listOf("Main Street Fuel", "Home Charger"), decoded.vehicles.single().stationSuggestions)
    }

    @Test
    fun jsonRoundTripPreservesEquipmentHoursAndMeterLogs() {
        val generator = vehicle.copy(
            id = "generator-1",
            name = "Generator",
            assetCategory = AssetCategory.EQUIPMENT,
            assetType = AssetType.GENERATOR,
            fuelType = FuelType.PROPANE,
            vehicleType = VehicleType.GASOLINE,
            currentMileage = 0.0,
            currentHours = 42.5,
            tankCapacity = 7.0,
        )
        val item = MaintenanceItem(
            id = "load-test",
            vehicleId = generator.id,
            categoryId = MaintenanceDefaults.CATEGORY_ELECTRICAL,
            name = "Load Test",
            intervalHours = 50.0,
            intervalTimeDays = 180,
            importance = MaintenanceImportance.HIGH,
        )
        val roundTripData = FuelMathData(
            vehicles = listOf(generator),
            fuelEntries = listOf(
                FuelEntry(
                    id = "propane-1",
                    vehicleId = generator.id,
                    dateTime = LocalDateTime.of(2026, 1, 1, 8, 0),
                    odometer = 0.0,
                    hours = 40.0,
                    fuelAmount = 2.5,
                    pricePerUnit = 3.25,
                    isFullTank = true,
                    station = "Home",
                ),
            ),
            meterLogs = listOf(
                MeterLog(
                    id = "hours-1",
                    vehicleId = generator.id,
                    dateTime = LocalDateTime.of(2026, 1, 2, 8, 0),
                    hours = 42.5,
                    source = MeterLogSource.HOUR_METER,
                ),
            ),
            maintenanceItems = listOf(item),
            maintenanceServiceLogs = listOf(
                MaintenanceServiceLog(
                    id = "load-test-log",
                    vehicleId = generator.id,
                    maintenanceItemId = item.id,
                    dateTime = LocalDateTime.of(2026, 1, 3, 8, 0),
                    odometer = 0.0,
                    hours = 42.5,
                    cost = 0.0,
                ),
            ),
        )

        val decoded = FuelJsonCodec.decode(FuelJsonCodec.encode(roundTripData))

        assertEquals(roundTripData, decoded)
        assertEquals(42.5, decoded.vehicles.single().currentHours ?: -1.0, 0.0001)
        assertEquals(50.0, decoded.maintenanceItems.single().intervalHours ?: -1.0, 0.0001)
        assertEquals(MeterLogSource.HOUR_METER, decoded.meterLogs.single().source)
    }

    @Test
    fun jsonRoundTripAllowsOptionalElectricBatteryCapacity() {
        val golfCart = vehicle.copy(
            id = "golf-cart-1",
            name = "Golf Cart",
            assetCategory = AssetCategory.EQUIPMENT,
            assetType = AssetType.GOLF_CART,
            fuelType = FuelType.ELECTRIC,
            vehicleType = VehicleType.EV,
            currentMileage = 0.0,
            currentHours = null,
            tankCapacity = 0.0,
            batteryCapacity = null,
        )

        val decoded = FuelJsonCodec.decode(FuelJsonCodec.encode(FuelMathData(vehicles = listOf(golfCart))))

        assertEquals(FuelType.ELECTRIC, decoded.vehicles.single().fuelType)
        assertNull(decoded.vehicles.single().batteryCapacity)
    }

    @Test
    fun jsonMigratesSchemaV4VehiclesWithoutTirePsiFields() {
        val decoded = FuelJsonCodec.decode(
            """
            {
              "schemaVersion": 4,
              "vehicles": [
                {
                  "id": "vehicle-1",
                  "name": "Truck",
                  "tankCapacity": 20,
                  "distanceUnit": "mi",
                  "volumeUnit": "gal",
                  "vehicleType": "gasoline",
                  "fuelType": "gasoline",
                  "energyUnit": "kwh"
                }
              ],
              "fuelEntries": [],
              "maintenanceCategories": [],
              "maintenanceItems": [],
              "maintenanceServiceLogs": []
            }
            """.trimIndent(),
        )

        assertEquals(CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertNull(decoded.vehicles.single().recommendedFrontTirePsi)
        assertNull(decoded.vehicles.single().recommendedRearTirePsi)
    }

    @Test
    fun jsonMigratesSchemaV2BaselineFieldsIntoServiceLogs() {
        val decoded = FuelJsonCodec.decode(
            """
            {
              "schemaVersion": 2,
              "vehicles": [
                {
                  "id": "vehicle-1",
                  "name": "Truck",
                  "tankCapacity": 20,
                  "distanceUnit": "mi",
                  "volumeUnit": "gal"
                }
              ],
              "fuelEntries": [],
              "maintenanceCategories": [
                { "id": "engine", "name": "Engine" }
              ],
              "maintenanceItems": [
                {
                  "id": "oil",
                  "vehicleId": "vehicle-1",
                  "categoryId": "engine",
                  "name": "Oil Change",
                  "intervalMiles": 5000,
                  "lastServiceMileage": 12000,
                  "lastServiceDate": "2026-01-05",
                  "lastServiceCost": 45.25,
                  "importance": "normal"
                }
              ],
              "maintenanceServiceLogs": []
            }
            """.trimIndent(),
        )

        assertEquals(CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1, decoded.maintenanceItems.size)
        assertEquals(1, decoded.maintenanceServiceLogs.size)
        assertEquals("oil", decoded.maintenanceServiceLogs.single().maintenanceItemId)
        assertEquals(12_000.0, decoded.maintenanceServiceLogs.single().odometer, 0.0001)
    }

    @Test
    fun jsonMigratesSchemaV1MaintenanceEntriesToServiceLogs() {
        val decoded = FuelJsonCodec.decode(
            """
            {
              "schemaVersion": 1,
              "vehicles": [
                {
                  "id": "vehicle-1",
                  "name": "Truck",
                  "tankCapacity": 20,
                  "distanceUnit": "mi",
                  "volumeUnit": "gal"
                }
              ],
              "fuelEntries": [
                {
                  "id": "fuel-1",
                  "vehicleId": "vehicle-1",
                  "dateTime": "2026-01-01T09:00",
                  "odometer": 9000,
                  "fuelAmount": 10,
                  "pricePerUnit": 3.5,
                  "isFullTank": true
                }
              ],
              "maintenanceEntries": [
                {
                  "id": "maint-1",
                  "vehicleId": "vehicle-1",
                  "dateTime": "2026-01-02T09:00",
                  "odometer": 9100,
                  "type": "Oil Change",
                  "cost": 45.25,
                  "notes": "Synthetic"
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(1, decoded.maintenanceItems.size)
        assertEquals(1, decoded.maintenanceServiceLogs.size)
        assertEquals("Oil Change", decoded.maintenanceItems.single().name)
        assertEquals(9_100.0, decoded.vehicles.single().currentMileage, 0.0001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun jsonRejectsUnknownFutureSchemaVersionBeforeRestore() {
        FuelJsonCodec.decode("""{"schemaVersion":999,"vehicles":[],"fuelEntries":[]}""")
    }

    @Test
    fun csvExportContainsVehiclesFuelEntriesMaintenanceItemsAndServiceLogs() {
        val item = maintenanceItem(id = "oil", name = "Oil Change")
        val exportData = data(
            fuelEntries = listOf(fuelEntry("fuel-1", odometer = 1234.0, pricePerUnit = 3.5)),
            items = listOf(item),
            serviceLogs = listOf(serviceLog("maint-1", itemId = item.id, notes = "Includes cabin filter")),
        )

        val csv = FuelCalculator.buildExportCsv(exportData)

        assertTrue(csv.contains("vehicle,vehicle-1,2009 Smart Fortwo"))
        assertTrue(csv.contains("fuel_entry,vehicle-1,2009 Smart Fortwo"))
        assertTrue(csv.contains("maintenance_item,vehicle-1,2009 Smart Fortwo"))
        assertTrue(csv.contains("maintenance_service_log,vehicle-1,2009 Smart Fortwo"))
    }

    @Test
    fun reminderSnapshotReturnsDueCountsAndMessage() {
        val item = maintenanceItem(
            id = "oil",
            name = "Oil Change",
            intervalMiles = 5_000.0,
        )
        val reminder = FuelCalculator.buildReminderSnapshot(
            data(items = listOf(item), serviceLogs = listOf(serviceLog("oil-log", itemId = "oil", odometer = 1_000.0))),
            asOfDate,
        )

        assertNotNull(reminder)
        assertEquals(1, reminder?.overdueCount)
        assertEquals(0, reminder?.dueSoonCount)
        assertTrue(reminder?.message?.contains("Oil Change") == true)
    }

    @Test
    fun newCarBaselineLogsMatchSeededMaintenanceItems() {
        val createdAt = LocalDateTime.of(2026, 6, 4, 9, 15)
        val seededItems = MaintenanceDefaults.templatesForVehicleType(VehicleType.GASOLINE)
            .mapIndexed { index, template ->
                MaintenanceItem(
                    id = "seed-$index",
                    vehicleId = vehicle.id,
                    categoryId = template.categoryId,
                    name = template.name,
                    intervalMiles = template.intervalMiles,
                    intervalTimeDays = template.intervalTimeDays,
                    importance = template.importance,
                )
            }

        val logs = MaintenanceDefaults.baselineServiceLogsForNewVehicle(vehicle, seededItems, createdAt)

        assertEquals(seededItems.size, logs.size)
        assertTrue(logs.all { it.vehicleId == vehicle.id })
        assertTrue(logs.all { it.dateTime == createdAt })
        assertTrue(logs.all { it.odometer == vehicle.currentMileage })
        assertTrue(logs.all { it.cost == 0.0 })
        assertEquals(
            seededItems.map { it.id }.sorted(),
            logs.map { it.maintenanceItemId }.sorted(),
        )
    }

    @Test
    fun newCarBaselineLogsMakeSeededItemsStartAsGood() {
        val createdAt = LocalDateTime.of(2026, 6, 4, 9, 15)
        val startingVehicle = vehicle.copy(currentMileage = 500.0)
        val seededItems = MaintenanceDefaults.templatesForVehicleType(VehicleType.GASOLINE)
            .mapIndexed { index, template ->
                MaintenanceItem(
                    id = "seed-state-$index",
                    vehicleId = startingVehicle.id,
                    categoryId = template.categoryId,
                    name = template.name,
                    intervalMiles = template.intervalMiles,
                    intervalTimeDays = template.intervalTimeDays,
                    importance = template.importance,
                )
            }
        val logs = MaintenanceDefaults.baselineServiceLogsForNewVehicle(startingVehicle, seededItems, createdAt)
        val seededData = FuelMathData(
            vehicles = listOf(startingVehicle),
            maintenanceItems = seededItems,
            maintenanceServiceLogs = logs,
        )

        val states = FuelCalculator.calculateMaintenanceStates(
            startingVehicle,
            seededData,
            createdAt.toLocalDate(),
        )

        assertTrue(states.isNotEmpty())
        assertTrue(states.all { it.status == MaintenanceStatus.GOOD })
    }

    private fun data(
        fuelEntries: List<FuelEntry> = emptyList(),
        items: List<MaintenanceItem> = listOf(maintenanceItem()),
        serviceLogs: List<MaintenanceServiceLog> = emptyList(),
        settings: AppSettings = AppSettings(),
    ): FuelMathData =
        FuelMathData(
            vehicles = listOf(vehicle),
            fuelEntries = fuelEntries,
            maintenanceItems = items,
            maintenanceServiceLogs = serviceLogs,
            settings = settings,
        )

    private fun maintenanceItem(
        id: String = "item-1",
        name: String = "Oil Change",
        categoryId: String = MaintenanceDefaults.CATEGORY_ENGINE,
        intervalMiles: Double? = null,
        intervalTimeDays: Int? = null,
        importance: MaintenanceImportance = MaintenanceImportance.MEDIUM,
    ): MaintenanceItem =
        MaintenanceItem(
            id = id,
            vehicleId = vehicle.id,
            categoryId = categoryId,
            name = name,
            intervalMiles = intervalMiles,
            intervalTimeDays = intervalTimeDays,
            importance = importance,
        )

    private fun serviceLog(
        id: String,
        itemId: String = "item-1",
        dateTime: LocalDateTime = LocalDateTime.of(2026, 1, 5, 8, 30),
        odometer: Double = 12_500.0,
        cost: Double = 45.25,
        notes: String = "Synthetic",
    ): MaintenanceServiceLog =
        MaintenanceServiceLog(
            id = id,
            vehicleId = vehicle.id,
            maintenanceItemId = itemId,
            dateTime = dateTime,
            odometer = odometer,
            cost = cost,
            notes = notes,
        )

    private fun fuelEntry(
        id: String,
        odometer: Double,
        fuelAmount: Double = 5.0,
        pricePerUnit: Double = 3.0,
        isFullTank: Boolean = true,
        entryType: EnergyEntryType = EnergyEntryType.FUEL,
        station: String = "",
    ): FuelEntry =
        FuelEntry(
            id = id,
            vehicleId = vehicle.id,
            dateTime = LocalDateTime.of(2026, 1, 1, 12, 0).plusDays(id.last().code.toLong()),
            odometer = odometer,
            fuelAmount = fuelAmount,
            pricePerUnit = pricePerUnit,
            isFullTank = isFullTank,
            entryType = entryType,
            station = station,
        )
}
