package org.archuser.fuelmath

import java.time.LocalDateTime

const val CURRENT_SCHEMA_VERSION = 1

enum class DistanceUnit(val storageValue: String, val displayLabel: String) {
    MILES("mi", "mi"),
    KILOMETERS("km", "km");

    companion object {
        fun fromStorage(value: String): DistanceUnit =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown distance unit: $value")
    }
}

enum class VolumeUnit(val storageValue: String, val displayLabel: String) {
    GALLONS("gal", "gal"),
    LITERS("L", "L");

    companion object {
        fun fromStorage(value: String): VolumeUnit =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown volume unit: $value")
    }
}

data class Vehicle(
    val id: String,
    val name: String,
    val tankCapacity: Double,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
)

data class FuelEntry(
    val id: String,
    val vehicleId: String,
    val dateTime: LocalDateTime,
    val odometer: Double,
    val fuelAmount: Double,
    val pricePerUnit: Double,
    val isFullTank: Boolean,
) {
    val totalCost: Double
        get() = fuelAmount * pricePerUnit
}

data class MaintenanceEntry(
    val id: String,
    val vehicleId: String,
    val dateTime: LocalDateTime,
    val odometer: Double,
    val type: String,
    val cost: Double,
    val notes: String,
)

data class FuelMathData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val vehicles: List<Vehicle> = emptyList(),
    val fuelEntries: List<FuelEntry> = emptyList(),
    val maintenanceEntries: List<MaintenanceEntry> = emptyList(),
)

data class EntryDistance(
    val entry: FuelEntry,
    val distanceFromPrevious: Double?,
)

data class EfficiencySegment(
    val startEntryId: String,
    val endEntryId: String,
    val dateTime: LocalDateTime,
    val distance: Double,
    val fuelUsed: Double,
    val value: Double,
)

data class ChartPoint(
    val label: String,
    val dateTime: LocalDateTime,
    val value: Double,
)

data class VehicleSummary(
    val vehicle: Vehicle,
    val totalFuelCost: Double,
    val totalDistance: Double?,
    val costPerDistance: Double?,
    val lastEfficiency: Double?,
    val lastFillUpDate: LocalDateTime?,
    val estimatedRange: Double?,
    val lastOdometer: Double?,
    val fuelEntryCount: Int,
    val maintenanceEntryCount: Int,
)
