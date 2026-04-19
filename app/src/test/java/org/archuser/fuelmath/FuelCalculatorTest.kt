package org.archuser.fuelmath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class FuelCalculatorTest {
    private val vehicle = Vehicle(
        id = "vehicle-1",
        name = "2009 Smart Fortwo",
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

        val summary = FuelCalculator.buildVehicleSummary(vehicle, entries, emptyList())

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
    fun summaryCalculatesTotalCostDistanceCostPerMileAndRange() {
        val entries = listOf(
            fuelEntry("a", odometer = 1000.0, fuelAmount = 8.0, pricePerUnit = 3.0, isFullTank = true),
            fuelEntry("b", odometer = 1100.0, fuelAmount = 5.0, pricePerUnit = 4.0, isFullTank = false),
            fuelEntry("c", odometer = 1200.0, fuelAmount = 5.0, pricePerUnit = 4.0, isFullTank = true),
        )

        val summary = FuelCalculator.buildVehicleSummary(vehicle, entries, emptyList())

        assertEquals(64.0, summary.totalFuelCost, 0.0001)
        assertEquals(200.0, summary.totalDistance ?: -1.0, 0.0001)
        assertEquals(0.32, summary.costPerDistance ?: -1.0, 0.0001)
        assertEquals(20.0, summary.lastEfficiency ?: -1.0, 0.0001)
        assertEquals(174.0, summary.estimatedRange ?: -1.0, 0.0001)
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
    fun jsonRoundTripPreservesData() {
        val data = FuelMathData(
            vehicles = listOf(vehicle),
            fuelEntries = listOf(fuelEntry("fuel-1", odometer = 1234.0)),
            maintenanceEntries = listOf(
                MaintenanceEntry(
                    id = "maint-1",
                    vehicleId = vehicle.id,
                    dateTime = LocalDateTime.of(2026, 1, 5, 8, 30),
                    odometer = 1250.0,
                    type = "Oil change",
                    cost = 45.25,
                    notes = "Synthetic",
                ),
            ),
        )

        val decoded = FuelJsonCodec.decode(FuelJsonCodec.encode(data))

        assertEquals(data, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun jsonRejectsUnknownSchemaVersionBeforeRestore() {
        FuelJsonCodec.decode("""{"schemaVersion":999,"vehicles":[],"fuelEntries":[],"maintenanceEntries":[]}""")
    }

    @Test
    fun csvExportContainsVehiclesFuelEntriesAndMaintenanceEntries() {
        val data = FuelMathData(
            vehicles = listOf(vehicle),
            fuelEntries = listOf(fuelEntry("fuel-1", odometer = 1234.0, pricePerUnit = 3.5)),
            maintenanceEntries = listOf(
                MaintenanceEntry(
                    id = "maint-1",
                    vehicleId = vehicle.id,
                    dateTime = LocalDateTime.of(2026, 1, 5, 8, 30),
                    odometer = 1250.0,
                    type = "Oil change",
                    cost = 45.25,
                    notes = "Includes cabin filter",
                ),
            ),
        )

        val csv = FuelCalculator.buildExportCsv(data)

        assertTrue(csv.contains("vehicle,vehicle-1,2009 Smart Fortwo"))
        assertTrue(csv.contains("fuel_entry,vehicle-1,2009 Smart Fortwo"))
        assertTrue(csv.contains("maintenance_entry,vehicle-1,2009 Smart Fortwo"))
    }

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
