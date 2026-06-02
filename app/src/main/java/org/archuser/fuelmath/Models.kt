package org.archuser.fuelmath

import java.time.LocalDate
import java.time.LocalDateTime

const val CURRENT_SCHEMA_VERSION = 2

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

enum class FuelType(val storageValue: String, val displayLabel: String) {
    GASOLINE("gasoline", "Gasoline"),
    DIESEL("diesel", "Diesel"),
    HYBRID("hybrid", "Hybrid"),
    ELECTRIC("electric", "Electric"),
    OTHER("other", "Other");

    companion object {
        fun fromStorage(value: String): FuelType =
            entries.firstOrNull { it.storageValue == value } ?: OTHER
    }
}

enum class MaintenanceImportance(
    val storageValue: String,
    val displayLabel: String,
    val scoreWeight: Double,
) {
    LOW("low", "Low", 1.0),
    NORMAL("normal", "Normal", 2.0),
    HIGH("high", "High", 3.0),
    CRITICAL("critical", "Critical", 4.0);

    companion object {
        fun fromStorage(value: String): MaintenanceImportance =
            entries.firstOrNull { it.storageValue == value }
                ?: throw IllegalArgumentException("Unknown maintenance importance: $value")
    }
}

enum class MaintenanceStatus(val displayLabel: String) {
    GOOD("Good"),
    DUE_SOON("Due Soon"),
    OVERDUE("Overdue"),
}

data class Vehicle(
    val id: String,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val currentMileage: Double = 0.0,
    val fuelType: FuelType = FuelType.GASOLINE,
    val tankCapacity: Double,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
)

data class MaintenanceCategory(
    val id: String,
    val name: String,
)

data class MaintenanceItem(
    val id: String,
    val vehicleId: String,
    val categoryId: String,
    val name: String,
    val intervalMiles: Double? = null,
    val intervalTimeDays: Int? = null,
    val lastServiceMileage: Double? = null,
    val lastServiceDate: LocalDate? = null,
    val lastServiceCost: Double? = null,
    val notes: String = "",
    val importance: MaintenanceImportance = MaintenanceImportance.NORMAL,
)

data class MaintenanceServiceLog(
    val id: String,
    val vehicleId: String,
    val maintenanceItemId: String,
    val dateTime: LocalDateTime,
    val odometer: Double,
    val cost: Double,
    val notes: String = "",
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

data class AppSettings(
    val dueSoonThresholdPercent: Int = 10,
    val maintenanceRemindersEnabled: Boolean = false,
)

data class FuelMathData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val vehicles: List<Vehicle> = emptyList(),
    val fuelEntries: List<FuelEntry> = emptyList(),
    val maintenanceCategories: List<MaintenanceCategory> = MaintenanceDefaults.categories,
    val maintenanceItems: List<MaintenanceItem> = emptyList(),
    val maintenanceServiceLogs: List<MaintenanceServiceLog> = emptyList(),
    val settings: AppSettings = AppSettings(),
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

data class MaintenanceItemState(
    val item: MaintenanceItem,
    val category: MaintenanceCategory,
    val status: MaintenanceStatus,
    val lastServiceLog: MaintenanceServiceLog?,
    val nextDueMileage: Double?,
    val nextDueDate: LocalDate?,
    val milesRemaining: Double?,
    val daysRemaining: Long?,
    val urgencySortValue: Double,
)

data class SeasonalEfficiencyComparison(
    val currentSeason: String,
    val currentSeasonAverage: Double?,
    val overallAverage: Double?,
)

data class VehicleSummary(
    val vehicle: Vehicle,
    val totalFuelCost: Double,
    val totalMaintenanceCost: Double,
    val totalCost: Double,
    val totalDistance: Double?,
    val costPerDistance: Double?,
    val lastEfficiency: Double?,
    val lastFillUpDate: LocalDateTime?,
    val estimatedRange: Double?,
    val lastOdometer: Double?,
    val currentMileage: Double,
    val healthScore: Int,
    val overdueCount: Int,
    val dueSoonCount: Int,
    val smartRecommendation: MaintenanceItemState?,
    val fuelEntryCount: Int,
    val maintenanceItemCount: Int,
    val maintenanceServiceLogCount: Int,
)

data class MaintenanceReminderSnapshot(
    val title: String,
    val message: String,
    val overdueCount: Int,
    val dueSoonCount: Int,
)

object MaintenanceDefaults {
    const val CATEGORY_ENGINE = "engine"
    const val CATEGORY_FLUIDS = "fluids"
    const val CATEGORY_WEAR_ITEMS = "wear_items"
    const val CATEGORY_ELECTRICAL = "electrical"
    const val CATEGORY_CRITICAL = "critical"

    val categories: List<MaintenanceCategory> = listOf(
        MaintenanceCategory(CATEGORY_ENGINE, "Engine"),
        MaintenanceCategory(CATEGORY_FLUIDS, "Fluids"),
        MaintenanceCategory(CATEGORY_WEAR_ITEMS, "Wear Items"),
        MaintenanceCategory(CATEGORY_ELECTRICAL, "Electrical"),
        MaintenanceCategory(CATEGORY_CRITICAL, "Critical"),
    )

    fun categoryIdForLegacyType(type: String): String {
        val normalized = type.lowercase()
        return when {
            "brake" in normalized || "tire" in normalized || "wiper" in normalized -> CATEGORY_WEAR_ITEMS
            "coolant" in normalized || "fluid" in normalized -> CATEGORY_FLUIDS
            "battery" in normalized -> CATEGORY_ELECTRICAL
            "timing" in normalized || "water pump" in normalized -> CATEGORY_CRITICAL
            else -> CATEGORY_ENGINE
        }
    }

    fun importanceForName(name: String): MaintenanceImportance {
        val normalized = name.lowercase()
        return when {
            "timing" in normalized -> MaintenanceImportance.CRITICAL
            "brake" in normalized || "oil" in normalized || "water pump" in normalized -> MaintenanceImportance.HIGH
            "wiper" in normalized -> MaintenanceImportance.LOW
            else -> MaintenanceImportance.NORMAL
        }
    }
}
