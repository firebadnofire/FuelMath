package org.archuser.fuelmath

import java.time.LocalDate
import java.time.LocalDateTime

const val CURRENT_SCHEMA_VERSION = 5

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

enum class EnergyUnit(val storageValue: String, val displayLabel: String) {
    KILOWATT_HOURS("kwh", "kWh");

    companion object {
        fun fromStorage(value: String): EnergyUnit =
            entries.firstOrNull { it.storageValue == value } ?: KILOWATT_HOURS
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

enum class VehicleType(
    val storageValue: String,
    val displayLabel: String,
    val usesLiquidFuel: Boolean,
    val usesBattery: Boolean,
) {
    GASOLINE("gasoline", "Gasoline", usesLiquidFuel = true, usesBattery = false),
    DIESEL("diesel", "Diesel", usesLiquidFuel = true, usesBattery = false),
    HYBRID("hybrid", "Hybrid", usesLiquidFuel = true, usesBattery = false),
    PLUG_IN_HYBRID("plug_in_hybrid", "Plug-in Hybrid", usesLiquidFuel = true, usesBattery = true),
    EV("ev", "EV", usesLiquidFuel = false, usesBattery = true),
    MOTORCYCLE("motorcycle", "Motorcycle", usesLiquidFuel = true, usesBattery = false),
    OTHER("other", "Other", usesLiquidFuel = false, usesBattery = false);

    companion object {
        fun fromStorage(value: String): VehicleType =
            entries.firstOrNull { it.storageValue == value }
                ?: when (value) {
                    FuelType.ELECTRIC.storageValue -> EV
                    else -> OTHER
                }

        fun fromFuelType(value: FuelType): VehicleType =
            when (value) {
                FuelType.GASOLINE -> GASOLINE
                FuelType.DIESEL -> DIESEL
                FuelType.HYBRID -> HYBRID
                FuelType.ELECTRIC -> EV
                FuelType.OTHER -> OTHER
            }
    }
}

enum class MaintenanceImportance(
    val storageValue: String,
    val displayLabel: String,
    val scoreWeight: Double,
) {
    LOW("low", "Low", 1.0),
    MEDIUM("medium", "Medium", 2.0),
    HIGH("high", "High", 3.0),
    CRITICAL("critical", "Critical", 5.0);

    companion object {
        fun fromStorage(value: String): MaintenanceImportance =
            when (value) {
                "normal" -> MEDIUM
                else -> entries.firstOrNull { it.storageValue == value }
            } ?: throw IllegalArgumentException("Unknown maintenance importance: $value")
    }
}

enum class MaintenanceStatus(val displayLabel: String) {
    UNKNOWN("Unknown"),
    GOOD("Good"),
    DUE_SOON("Due Soon"),
    OVERDUE("Overdue"),
}

enum class EnergyEntryType(val storageValue: String) {
    FUEL("fuel"),
    CHARGING("charging");

    companion object {
        fun fromStorage(value: String): EnergyEntryType =
            entries.firstOrNull { it.storageValue == value } ?: FUEL
    }
}

data class Vehicle(
    val id: String,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val currentMileage: Double = 0.0,
    val fuelType: FuelType = FuelType.GASOLINE,
    val vehicleType: VehicleType = VehicleType.fromFuelType(fuelType),
    val tankCapacity: Double,
    val batteryCapacity: Double? = null,
    val recommendedFrontTirePsi: Double? = null,
    val recommendedRearTirePsi: Double? = null,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val energyUnit: EnergyUnit = EnergyUnit.KILOWATT_HOURS,
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
    val notes: String = "",
    val importance: MaintenanceImportance = MaintenanceImportance.MEDIUM,
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
    val entryType: EnergyEntryType = EnergyEntryType.FUEL,
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
    val totalChargingCost: Double,
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
    val unknownCount: Int,
    val smartRecommendation: MaintenanceItemState?,
    val fuelEntryCount: Int,
    val chargingEntryCount: Int,
    val maintenanceItemCount: Int,
    val maintenanceServiceLogCount: Int,
)

data class MaintenanceReminderSnapshot(
    val title: String,
    val message: String,
    val overdueCount: Int,
    val dueSoonCount: Int,
)

data class MaintenanceTemplateDefinition(
    val name: String,
    val categoryId: String,
    val intervalMiles: Double? = null,
    val intervalTimeDays: Int? = null,
    val importance: MaintenanceImportance = MaintenanceImportance.MEDIUM,
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

    private fun splitTireTemplates(
        intervalMiles: Double,
        intervalTimeDays: Int,
        importance: MaintenanceImportance,
    ): List<MaintenanceTemplateDefinition> = listOf(
        MaintenanceTemplateDefinition("Front Tires", CATEGORY_WEAR_ITEMS, intervalMiles = intervalMiles, intervalTimeDays = intervalTimeDays, importance = importance),
        MaintenanceTemplateDefinition("Rear Tires", CATEGORY_WEAR_ITEMS, intervalMiles = intervalMiles, intervalTimeDays = intervalTimeDays, importance = importance),
    )

    private val liquidFuelTemplates: List<MaintenanceTemplateDefinition> = listOf(
        MaintenanceTemplateDefinition("Oil Change", CATEGORY_ENGINE, intervalMiles = 5_000.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Engine Air Filter", CATEGORY_ENGINE, intervalMiles = 15_000.0, intervalTimeDays = 365),
        MaintenanceTemplateDefinition("Spark Plugs", CATEGORY_ENGINE, intervalMiles = 60_000.0, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Transmission Fluid", CATEGORY_FLUIDS, intervalMiles = 60_000.0, intervalTimeDays = 1_095, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Coolant", CATEGORY_FLUIDS, intervalMiles = 50_000.0, intervalTimeDays = 730, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Brake Fluid", CATEGORY_FLUIDS, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Brake Pads", CATEGORY_WEAR_ITEMS, intervalMiles = 25_000.0, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Wiper Blades", CATEGORY_WEAR_ITEMS, intervalTimeDays = 365, importance = MaintenanceImportance.LOW),
        MaintenanceTemplateDefinition("12V Battery", CATEGORY_ELECTRICAL, intervalTimeDays = 1_460, importance = MaintenanceImportance.MEDIUM),
    ) + splitTireTemplates(
        intervalMiles = 6_000.0,
        intervalTimeDays = 180,
        importance = MaintenanceImportance.HIGH,
    )

    private val evTemplates: List<MaintenanceTemplateDefinition> = listOf(
        MaintenanceTemplateDefinition("Battery Health Inspection", CATEGORY_ELECTRICAL, intervalMiles = 15_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Charge Port Inspection", CATEGORY_ELECTRICAL, intervalMiles = 12_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Charging Cable Inspection", CATEGORY_ELECTRICAL, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Cabin Air Filter", CATEGORY_ENGINE, intervalMiles = 15_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Brake Fluid", CATEGORY_FLUIDS, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Coolant", CATEGORY_FLUIDS, intervalMiles = 100_000.0, intervalTimeDays = 1_825, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Brake Pads", CATEGORY_WEAR_ITEMS, intervalMiles = 35_000.0, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Wiper Blades", CATEGORY_WEAR_ITEMS, intervalTimeDays = 365, importance = MaintenanceImportance.LOW),
        MaintenanceTemplateDefinition("12V Battery", CATEGORY_ELECTRICAL, intervalTimeDays = 1_460, importance = MaintenanceImportance.MEDIUM),
    ) + splitTireTemplates(
        intervalMiles = 6_000.0,
        intervalTimeDays = 180,
        importance = MaintenanceImportance.HIGH,
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
            else -> MaintenanceImportance.MEDIUM
        }
    }

    fun templatesForVehicleType(vehicleType: VehicleType): List<MaintenanceTemplateDefinition> =
        when (vehicleType) {
            VehicleType.GASOLINE -> liquidFuelTemplates
            VehicleType.DIESEL -> liquidFuelTemplates + listOf(
                MaintenanceTemplateDefinition("Fuel Filter", CATEGORY_ENGINE, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
            VehicleType.HYBRID -> liquidFuelTemplates + listOf(
                MaintenanceTemplateDefinition("Hybrid System Inspection", CATEGORY_ELECTRICAL, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Regenerative Brake Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
            VehicleType.PLUG_IN_HYBRID -> liquidFuelTemplates + listOf(
                MaintenanceTemplateDefinition("Hybrid System Inspection", CATEGORY_ELECTRICAL, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Charge Port Inspection", CATEGORY_ELECTRICAL, intervalMiles = 12_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Charging Cable Inspection", CATEGORY_ELECTRICAL, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
            VehicleType.EV -> evTemplates
            VehicleType.MOTORCYCLE -> listOf(
                MaintenanceTemplateDefinition("Oil Change", CATEGORY_ENGINE, intervalMiles = 4_000.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Chain Service", CATEGORY_CRITICAL, intervalMiles = 600.0, intervalTimeDays = 90, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Brake Fluid", CATEGORY_FLUIDS, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Brake Pads", CATEGORY_WEAR_ITEMS, intervalMiles = 12_000.0, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Coolant", CATEGORY_FLUIDS, intervalMiles = 24_000.0, intervalTimeDays = 730, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("12V Battery", CATEGORY_ELECTRICAL, intervalTimeDays = 1_460, importance = MaintenanceImportance.MEDIUM),
            ) + splitTireTemplates(
                intervalMiles = 4_000.0,
                intervalTimeDays = 180,
                importance = MaintenanceImportance.HIGH,
            )
            VehicleType.OTHER -> listOf(
                MaintenanceTemplateDefinition("Brake Fluid", CATEGORY_FLUIDS, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Wiper Blades", CATEGORY_WEAR_ITEMS, intervalTimeDays = 365, importance = MaintenanceImportance.LOW),
                MaintenanceTemplateDefinition("12V Battery", CATEGORY_ELECTRICAL, intervalTimeDays = 1_460, importance = MaintenanceImportance.MEDIUM),
            ) + splitTireTemplates(
                intervalMiles = 6_000.0,
                intervalTimeDays = 180,
                importance = MaintenanceImportance.HIGH,
            )
        }
}
