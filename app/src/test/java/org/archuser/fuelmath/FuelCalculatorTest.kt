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
            lastServiceMileage = 10_000.0,
            lastServiceDate = LocalDate.of(2025, 10, 1),
        )
        val timeOverdue = maintenanceItem(
            id = "brake-fluid",
            name = "Brake Fluid",
            categoryId = MaintenanceDefaults.CATEGORY_FLUIDS,
            intervalTimeDays = 180,
            lastServiceDate = LocalDate.of(2025, 1, 1),
        )
        val hybridOverdueByTime = maintenanceItem(
            id = "coolant",
            name = "Coolant",
            categoryId = MaintenanceDefaults.CATEGORY_FLUIDS,
            intervalMiles = 30_000.0,
            intervalTimeDays = 90,
            lastServiceMileage = 11_500.0,
            lastServiceDate = LocalDate.of(2025, 9, 1),
        )
        val states = FuelCalculator.calculateMaintenanceStates(
            vehicle.copy(currentMileage = 14_600.0),
            data(items = listOf(mileageDueSoon, timeOverdue, hybridOverdueByTime)),
            asOfDate,
        ).associateBy { it.item.id }

        assertEquals(MaintenanceStatus.DUE_SOON, states.getValue("oil").status)
        assertEquals(MaintenanceStatus.OVERDUE, states.getValue("brake-fluid").status)
        assertEquals(MaintenanceStatus.OVERDUE, states.getValue("coolant").status)
    }

    @Test
    fun healthScoreAndRecommendationPrioritizeWeightedUrgentItems() {
        val overdueHigh = maintenanceItem(
            id = "brakes",
            name = "Brake Pads",
            categoryId = MaintenanceDefaults.CATEGORY_WEAR_ITEMS,
            intervalMiles = 10_000.0,
            lastServiceMileage = 1_000.0,
            importance = MaintenanceImportance.HIGH,
        )
        val goodLow = maintenanceItem(
            id = "wipers",
            name = "Wiper Blades",
            categoryId = MaintenanceDefaults.CATEGORY_WEAR_ITEMS,
            intervalMiles = 20_000.0,
            lastServiceMileage = 11_900.0,
            importance = MaintenanceImportance.LOW,
        )
        val states = FuelCalculator.calculateMaintenanceStates(
            vehicle,
            data(items = listOf(overdueHigh, goodLow)),
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
    fun jsonRoundTripPreservesSchemaV2Data() {
        val item = maintenanceItem(
            id = "oil",
            name = "Oil Change",
            intervalMiles = 5_000.0,
            intervalTimeDays = 180,
            lastServiceMileage = 12_000.0,
            lastServiceDate = LocalDate.of(2026, 1, 5),
            lastServiceCost = 45.25,
        )
        val roundTripData = data(
            fuelEntries = listOf(fuelEntry("fuel-1", odometer = 1234.0)),
            items = listOf(item),
            serviceLogs = listOf(serviceLog("maint-1", itemId = item.id)),
            settings = AppSettings(dueSoonThresholdPercent = 15, maintenanceRemindersEnabled = true),
        )

        val decoded = FuelJsonCodec.decode(FuelJsonCodec.encode(roundTripData))

        assertEquals(roundTripData, decoded)
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
            lastServiceMileage = 1_000.0,
        )
        val reminder = FuelCalculator.buildReminderSnapshot(
            data(items = listOf(item)),
            asOfDate,
        )

        assertNotNull(reminder)
        assertEquals(1, reminder?.overdueCount)
        assertEquals(0, reminder?.dueSoonCount)
        assertTrue(reminder?.message?.contains("Oil Change") == true)
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
        lastServiceMileage: Double? = null,
        lastServiceDate: LocalDate? = null,
        lastServiceCost: Double? = null,
        importance: MaintenanceImportance = MaintenanceImportance.NORMAL,
    ): MaintenanceItem =
        MaintenanceItem(
            id = id,
            vehicleId = vehicle.id,
            categoryId = categoryId,
            name = name,
            intervalMiles = intervalMiles,
            intervalTimeDays = intervalTimeDays,
            lastServiceMileage = lastServiceMileage,
            lastServiceDate = lastServiceDate,
            lastServiceCost = lastServiceCost,
            importance = importance,
        )

    private fun serviceLog(
        id: String,
        itemId: String = "item-1",
        cost: Double = 45.25,
        notes: String = "Synthetic",
    ): MaintenanceServiceLog =
        MaintenanceServiceLog(
            id = id,
            vehicleId = vehicle.id,
            maintenanceItemId = itemId,
            dateTime = LocalDateTime.of(2026, 1, 5, 8, 30),
            odometer = 12_500.0,
            cost = cost,
            notes = notes,
        )

    private fun fuelEntry(
        id: String,
        odometer: Double,
        fuelAmount: Double = 5.0,
        pricePerUnit: Double = 3.0,
        isFullTank: Boolean = true,
    ): FuelEntry =
        FuelEntry(
            id = id,
            vehicleId = vehicle.id,
            dateTime = LocalDateTime.of(2026, 1, 1, 12, 0).plusDays(id.last().code.toLong()),
            odometer = odometer,
            fuelAmount = fuelAmount,
            pricePerUnit = pricePerUnit,
            isFullTank = isFullTank,
        )
}
