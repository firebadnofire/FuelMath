package org.archuser.fuelmath

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val CURRENT_SCHEMA_VERSION = 6

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

enum class AssetCategory(val storageValue: String, val displayLabel: String) {
    VEHICLE("vehicle", "Vehicle"),
    EQUIPMENT("equipment", "Equipment");

    companion object {
        fun fromStorage(value: String): AssetCategory =
            entries.firstOrNull { it.storageValue == value } ?: VEHICLE
    }
}

enum class AssetType(
    val storageValue: String,
    val displayLabel: String,
    val category: AssetCategory,
) {
    CAR_OR_TRUCK("car_or_truck", "Car or Truck", AssetCategory.VEHICLE),
    MOTORCYCLE("motorcycle", "Motorcycle", AssetCategory.VEHICLE),
    RV("rv", "RV", AssetCategory.VEHICLE),
    TRAILER("trailer", "Trailer", AssetCategory.VEHICLE),
    VEHICLE_OTHER("other", "Other", AssetCategory.VEHICLE),

    SKID_LOADER("skid_loader", "Skid Loader", AssetCategory.EQUIPMENT),
    EXCAVATOR("excavator", "Excavator", AssetCategory.EQUIPMENT),
    BACKHOE("backhoe", "Backhoe", AssetCategory.EQUIPMENT),
    TRACTOR("tractor", "Tractor", AssetCategory.EQUIPMENT),
    ZERO_TURN_MOWER("zero_turn_mower", "Zero-Turn Mower", AssetCategory.EQUIPMENT),
    LAWN_TRACTOR("lawn_tractor", "Lawn Tractor", AssetCategory.EQUIPMENT),
    PUSH_MOWER("push_mower", "Push Mower", AssetCategory.EQUIPMENT),
    CHAINSAW("chainsaw", "Chainsaw", AssetCategory.EQUIPMENT),
    GENERATOR("generator", "Generator", AssetCategory.EQUIPMENT),
    PRESSURE_WASHER("pressure_washer", "Pressure Washer", AssetCategory.EQUIPMENT),
    ATV("atv", "ATV", AssetCategory.EQUIPMENT),
    UTV("utv", "UTV", AssetCategory.EQUIPMENT),
    GOLF_CART("golf_cart", "Golf Cart", AssetCategory.EQUIPMENT),
    SNOW_BLOWER("snow_blower", "Snow Blower", AssetCategory.EQUIPMENT),
    WOOD_CHIPPER("wood_chipper", "Wood Chipper", AssetCategory.EQUIPMENT),
    MINI_EXCAVATOR("mini_excvator", "Mini Excavator", AssetCategory.EQUIPMENT),
    FORKLIFT("forklift", "Forklift", AssetCategory.EQUIPMENT),
    COMPACT_TRACK_LOADER("compact_track_loader", "Compact Track Loader", AssetCategory.EQUIPMENT),
    EQUIPMENT_OTHER("other", "Other", AssetCategory.EQUIPMENT);

    companion object {
        fun defaultForCategory(category: AssetCategory): AssetType =
            when (category) {
                AssetCategory.VEHICLE -> CAR_OR_TRUCK
                AssetCategory.EQUIPMENT -> EQUIPMENT_OTHER
            }

        fun forCategory(category: AssetCategory): List<AssetType> =
            entries.filter { it.category == category }

        fun fromStorage(value: String, category: AssetCategory): AssetType =
            entries.firstOrNull { it.storageValue == value && it.category == category }
                ?: entries.firstOrNull { it.storageValue == value }
                ?: defaultForCategory(category)

        fun fromVehicleType(vehicleType: VehicleType): AssetType =
            when (vehicleType) {
                VehicleType.MOTORCYCLE -> MOTORCYCLE
                else -> CAR_OR_TRUCK
            }
    }
}

enum class FuelType(
    val storageValue: String,
    val displayLabel: String,
    val usesFuelLog: Boolean,
    val usesChargingLog: Boolean,
    val usesTankCapacity: Boolean,
    val usesBatteryCapacity: Boolean,
) {
    GASOLINE("gasoline", "Gasoline", true, false, true, false),
    DIESEL("diesel", "Diesel", true, false, true, false),
    MIXED_GAS("mixed_gas", "Mixed Gas", true, false, true, false),
    PROPANE("propane", "Propane", true, false, true, false),
    NATURAL_GAS("natural_gas", "Natural Gas", true, false, true, false),
    ELECTRIC("electric", "Electric", false, true, false, true),
    HYBRID_GASOLINE("hybrid_gasoline", "Hybrid Gasoline", true, false, true, false),
    HYBRID_DIESEL("hybrid_diesel", "Hybrid Diesel", true, false, true, false),
    PLUG_IN_HYBRID_GASOLINE("plug_in_hybrid_gasoline", "Plug-in Hybrid Gasoline", true, true, true, true),
    PLUG_IN_HYBRID_DIESEL("plug_in_hybrid_diesel", "Plug-in Hybrid Diesel", true, true, true, true),
    NONE("none", "None", false, false, false, false),
    OTHER("other", "Other", false, false, false, false);

    val isFuelPowered: Boolean
        get() = usesFuelLog

    val isElectricOnly: Boolean
        get() = this == ELECTRIC

    val isPlugInHybrid: Boolean
        get() = this == PLUG_IN_HYBRID_GASOLINE || this == PLUG_IN_HYBRID_DIESEL

    val isGasolineLike: Boolean
        get() = this == GASOLINE ||
            this == HYBRID_GASOLINE ||
            this == PLUG_IN_HYBRID_GASOLINE ||
            this == MIXED_GAS ||
            this == PROPANE ||
            this == NATURAL_GAS

    val isDieselLike: Boolean
        get() = this == DIESEL || this == HYBRID_DIESEL || this == PLUG_IN_HYBRID_DIESEL

    companion object {
        fun fromStorage(value: String): FuelType =
            when (value) {
                "hybrid" -> HYBRID_GASOLINE
                "plug_in_hybrid" -> PLUG_IN_HYBRID_GASOLINE
                "ev" -> ELECTRIC
                else -> entries.firstOrNull { it.storageValue == value } ?: OTHER
            }
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
                ?: when (FuelType.fromStorage(value)) {
                    FuelType.ELECTRIC -> EV
                    FuelType.DIESEL -> DIESEL
                    FuelType.HYBRID_DIESEL,
                    FuelType.HYBRID_GASOLINE,
                    -> HYBRID
                    FuelType.PLUG_IN_HYBRID_DIESEL,
                    FuelType.PLUG_IN_HYBRID_GASOLINE,
                    -> PLUG_IN_HYBRID
                    FuelType.GASOLINE,
                    FuelType.MIXED_GAS,
                    FuelType.PROPANE,
                    FuelType.NATURAL_GAS,
                    -> GASOLINE
                    FuelType.NONE,
                    FuelType.OTHER,
                    -> OTHER
                }

        fun fromFuelType(value: FuelType): VehicleType =
            when (value) {
                FuelType.GASOLINE,
                FuelType.MIXED_GAS,
                FuelType.PROPANE,
                FuelType.NATURAL_GAS,
                -> GASOLINE
                FuelType.DIESEL -> DIESEL
                FuelType.HYBRID_GASOLINE,
                FuelType.HYBRID_DIESEL,
                -> HYBRID
                FuelType.PLUG_IN_HYBRID_GASOLINE,
                FuelType.PLUG_IN_HYBRID_DIESEL,
                -> PLUG_IN_HYBRID
                FuelType.ELECTRIC -> EV
                FuelType.NONE,
                FuelType.OTHER,
                -> OTHER
            }

        fun fromAsset(assetCategory: AssetCategory, assetType: AssetType, fuelType: FuelType): VehicleType =
            if (assetCategory == AssetCategory.VEHICLE && assetType == AssetType.MOTORCYCLE) {
                MOTORCYCLE
            } else {
                fromFuelType(fuelType)
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

enum class MeterLogSource(val storageValue: String) {
    MANUAL("manual"),
    FUEL_LOG("fuel_log"),
    CHARGING_LOG("charging_log"),
    MAINTENANCE_LOG("maintenance_log"),
    IMPORT("import"),
    OBD_FUTURE("obd_future"),
    HOUR_METER("hour_meter");

    companion object {
        fun fromStorage(value: String): MeterLogSource =
            entries.firstOrNull { it.storageValue == value } ?: MANUAL
    }
}

data class Vehicle(
    val id: String,
    val name: String,
    val make: String = "",
    val model: String = "",
    val year: Int? = null,
    val assetCategory: AssetCategory = AssetCategory.VEHICLE,
    val assetType: AssetType = AssetType.defaultForCategory(assetCategory),
    val currentMileage: Double = 0.0,
    val currentHours: Double? = null,
    val fuelType: FuelType = FuelType.GASOLINE,
    val vehicleType: VehicleType = VehicleType.fromAsset(assetCategory, assetType, fuelType),
    val tankCapacity: Double = 0.0,
    val batteryCapacity: Double? = null,
    val recommendedFrontTirePsi: Double? = null,
    val recommendedRearTirePsi: Double? = null,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val energyUnit: EnergyUnit = EnergyUnit.KILOWATT_HOURS,
    val archived: Boolean = false,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
)

data class MaintenanceCategory(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
)

data class MaintenanceItem(
    val id: String,
    val vehicleId: String,
    val categoryId: String,
    val name: String,
    val intervalMiles: Double? = null,
    val intervalHours: Double? = null,
    val intervalTimeDays: Int? = null,
    val notes: String = "",
    val importance: MaintenanceImportance = MaintenanceImportance.MEDIUM,
    val active: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = createdAt,
)

data class MaintenanceServiceLog(
    val id: String,
    val vehicleId: String,
    val maintenanceItemId: String,
    val dateTime: LocalDateTime,
    val odometer: Double,
    val hours: Double? = null,
    val cost: Double,
    val notes: String = "",
    val receiptPath: String? = null,
    val clientGeneratedId: String = id,
    val createdAt: LocalDateTime = dateTime,
    val updatedAt: LocalDateTime = createdAt,
)

data class FuelEntry(
    val id: String,
    val vehicleId: String,
    val dateTime: LocalDateTime,
    val odometer: Double,
    val hours: Double? = null,
    val fuelAmount: Double,
    val pricePerUnit: Double,
    val isFullTank: Boolean,
    val entryType: EnergyEntryType = EnergyEntryType.FUEL,
    val station: String = "",
    val oilMixRatio: String = "",
    val notes: String = "",
    val chargePercentBefore: Double? = null,
    val chargePercentAfter: Double? = null,
    val clientGeneratedId: String = id,
    val createdAt: LocalDateTime = dateTime,
    val updatedAt: LocalDateTime = createdAt,
) {
    val totalCost: Double
        get() = fuelAmount * pricePerUnit
}

data class MeterLog(
    val id: String,
    val vehicleId: String,
    val dateTime: LocalDateTime,
    val mileage: Double? = null,
    val hours: Double? = null,
    val source: MeterLogSource = MeterLogSource.MANUAL,
    val notes: String = "",
    val clientGeneratedId: String = id,
    val createdAt: LocalDateTime = dateTime,
    val updatedAt: LocalDateTime = createdAt,
)

data class AppSettings(
    val dueSoonThresholdPercent: Int = 10,
    val maintenanceRemindersEnabled: Boolean = false,
)

data class FuelMathData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val vehicles: List<Vehicle> = emptyList(),
    val fuelEntries: List<FuelEntry> = emptyList(),
    val meterLogs: List<MeterLog> = emptyList(),
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
    val nextDueHours: Double?,
    val nextDueDate: LocalDate?,
    val milesRemaining: Double?,
    val hoursRemaining: Double?,
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
    val totalHours: Double?,
    val costPerDistance: Double?,
    val costPerHour: Double?,
    val lastEfficiency: Double?,
    val lastFillUpDate: LocalDateTime?,
    val estimatedRange: Double?,
    val lastOdometer: Double?,
    val currentMileage: Double,
    val currentHours: Double?,
    val healthScore: Int,
    val overdueCount: Int,
    val dueSoonCount: Int,
    val unknownCount: Int,
    val smartRecommendation: MaintenanceItemState?,
    val fuelEntryCount: Int,
    val chargingEntryCount: Int,
    val meterLogCount: Int,
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
    val intervalHours: Double? = null,
    val intervalTimeDays: Int? = null,
    val importance: MaintenanceImportance = MaintenanceImportance.MEDIUM,
)

object AssetRules {
    fun supportsMileage(vehicle: Vehicle): Boolean =
        when (vehicle.assetCategory) {
            AssetCategory.VEHICLE -> true
            AssetCategory.EQUIPMENT -> vehicle.assetType in setOf(
                AssetType.SKID_LOADER,
                AssetType.BACKHOE,
                AssetType.TRACTOR,
                AssetType.ATV,
                AssetType.UTV,
                AssetType.GOLF_CART,
                AssetType.FORKLIFT,
                AssetType.COMPACT_TRACK_LOADER,
                AssetType.EQUIPMENT_OTHER,
            )
        }

    fun requiresMileage(vehicle: Vehicle): Boolean =
        vehicle.assetCategory == AssetCategory.VEHICLE &&
            vehicle.assetType in setOf(AssetType.CAR_OR_TRUCK, AssetType.MOTORCYCLE, AssetType.RV)

    fun supportsHours(vehicle: Vehicle): Boolean =
        vehicle.assetCategory == AssetCategory.EQUIPMENT ||
            vehicle.assetType in setOf(AssetType.RV, AssetType.VEHICLE_OTHER)

    fun requiresHours(vehicle: Vehicle): Boolean =
        vehicle.assetCategory == AssetCategory.EQUIPMENT &&
            vehicle.assetType in setOf(
                AssetType.SKID_LOADER,
                AssetType.EXCAVATOR,
                AssetType.BACKHOE,
                AssetType.TRACTOR,
                AssetType.ZERO_TURN_MOWER,
                AssetType.LAWN_TRACTOR,
                AssetType.GENERATOR,
                AssetType.COMPACT_TRACK_LOADER,
            )

    fun usesFuelLogs(vehicle: Vehicle): Boolean = vehicle.fuelType.usesFuelLog

    fun usesChargingLogs(vehicle: Vehicle): Boolean = vehicle.fuelType.usesChargingLog

    fun showsTankCapacity(vehicle: Vehicle): Boolean = vehicle.fuelType.usesTankCapacity

    fun showsBatteryCapacity(vehicle: Vehicle): Boolean = vehicle.fuelType.usesBatteryCapacity

    fun prefersHourEfficiency(vehicle: Vehicle): Boolean =
        supportsHours(vehicle) && !requiresMileage(vehicle)
}

object MaintenanceDefaults {
    const val CATEGORY_ENGINE = "engine"
    const val CATEGORY_FLUIDS = "fluids"
    const val CATEGORY_WEAR_ITEMS = "wear_items"
    const val CATEGORY_ELECTRICAL = "electrical"
    const val CATEGORY_CRITICAL = "critical"
    const val CATEGORY_EV_SYSTEMS = "ev_systems"
    const val CATEGORY_HYBRID_SYSTEMS = "hybrid_systems"
    const val CATEGORY_HYDRAULICS = "hydraulics"
    const val CATEGORY_DRIVETRAIN = "drivetrain"
    const val CATEGORY_CUTTING_SYSTEM = "cutting_system"
    const val CATEGORY_UNDERCARRIAGE = "undercarriage"
    const val CATEGORY_ATTACHMENTS = "attachments"
    const val CATEGORY_OTHER = "other"

    val categories: List<MaintenanceCategory> = listOf(
        MaintenanceCategory(CATEGORY_ENGINE, "Engine", 10),
        MaintenanceCategory(CATEGORY_FLUIDS, "Fluids", 20),
        MaintenanceCategory(CATEGORY_WEAR_ITEMS, "Wear Items", 30),
        MaintenanceCategory(CATEGORY_ELECTRICAL, "Electrical", 40),
        MaintenanceCategory(CATEGORY_CRITICAL, "Critical", 50),
        MaintenanceCategory(CATEGORY_EV_SYSTEMS, "EV Systems", 60),
        MaintenanceCategory(CATEGORY_HYBRID_SYSTEMS, "Hybrid Systems", 70),
        MaintenanceCategory(CATEGORY_HYDRAULICS, "Hydraulics", 80),
        MaintenanceCategory(CATEGORY_DRIVETRAIN, "Drivetrain", 90),
        MaintenanceCategory(CATEGORY_CUTTING_SYSTEM, "Cutting System", 100),
        MaintenanceCategory(CATEGORY_UNDERCARRIAGE, "Undercarriage", 110),
        MaintenanceCategory(CATEGORY_ATTACHMENTS, "Attachments", 120),
        MaintenanceCategory(CATEGORY_OTHER, "Other", 130),
    )

    private fun splitTireTemplates(
        intervalMiles: Double,
        intervalTimeDays: Int,
        importance: MaintenanceImportance,
    ): List<MaintenanceTemplateDefinition> = listOf(
        MaintenanceTemplateDefinition("Front Tires", CATEGORY_WEAR_ITEMS, intervalMiles = intervalMiles, intervalTimeDays = intervalTimeDays, importance = importance),
        MaintenanceTemplateDefinition("Rear Tires", CATEGORY_WEAR_ITEMS, intervalMiles = intervalMiles, intervalTimeDays = intervalTimeDays, importance = importance),
        MaintenanceTemplateDefinition("Tire Rotation", CATEGORY_WEAR_ITEMS, intervalMiles = intervalMiles, intervalTimeDays = intervalTimeDays, importance = importance),
    )

    private val liquidFuelRoadTemplates: List<MaintenanceTemplateDefinition> = listOf(
        MaintenanceTemplateDefinition("Oil Change", CATEGORY_ENGINE, intervalMiles = 5_000.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Engine Air Filter", CATEGORY_ENGINE, intervalMiles = 15_000.0, intervalTimeDays = 365),
        MaintenanceTemplateDefinition("Fuel System Service", CATEGORY_ENGINE, intervalMiles = 30_000.0, intervalTimeDays = 730),
        MaintenanceTemplateDefinition("Spark Plugs", CATEGORY_ENGINE, intervalMiles = 60_000.0, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Serpentine Belt", CATEGORY_ENGINE, intervalMiles = 60_000.0, intervalTimeDays = 1_095),
        MaintenanceTemplateDefinition("Transmission Fluid", CATEGORY_FLUIDS, intervalMiles = 60_000.0, intervalTimeDays = 1_095, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Coolant", CATEGORY_FLUIDS, intervalMiles = 50_000.0, intervalTimeDays = 730, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Brake Fluid", CATEGORY_FLUIDS, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Brake Pads", CATEGORY_WEAR_ITEMS, intervalMiles = 25_000.0, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Brake Rotors", CATEGORY_WEAR_ITEMS, intervalMiles = 50_000.0, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Wiper Blades", CATEGORY_WEAR_ITEMS, intervalTimeDays = 365, importance = MaintenanceImportance.LOW),
        MaintenanceTemplateDefinition("12V Battery", CATEGORY_ELECTRICAL, intervalTimeDays = 1_460, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Exterior Lights", CATEGORY_ELECTRICAL, intervalTimeDays = 180, importance = MaintenanceImportance.LOW),
    ) + splitTireTemplates(
        intervalMiles = 6_000.0,
        intervalTimeDays = 180,
        importance = MaintenanceImportance.HIGH,
    )

    private val evRoadTemplates: List<MaintenanceTemplateDefinition> = listOf(
        MaintenanceTemplateDefinition("High Voltage Battery Health Inspection", CATEGORY_EV_SYSTEMS, intervalMiles = 15_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("High Voltage System Inspection", CATEGORY_EV_SYSTEMS, intervalMiles = 15_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Charge Port Inspection", CATEGORY_EV_SYSTEMS, intervalMiles = 12_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Charging Cable Inspection", CATEGORY_EV_SYSTEMS, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Cabin Air Filter", CATEGORY_FLUIDS, intervalMiles = 15_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Brake Fluid", CATEGORY_FLUIDS, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
        MaintenanceTemplateDefinition("Coolant", CATEGORY_FLUIDS, intervalMiles = 100_000.0, intervalTimeDays = 1_825, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Brake Pads", CATEGORY_WEAR_ITEMS, intervalMiles = 35_000.0, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Brake Rotors", CATEGORY_WEAR_ITEMS, intervalMiles = 70_000.0, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Wiper Blades", CATEGORY_WEAR_ITEMS, intervalTimeDays = 365, importance = MaintenanceImportance.LOW),
        MaintenanceTemplateDefinition("12V Battery", CATEGORY_ELECTRICAL, intervalTimeDays = 1_460, importance = MaintenanceImportance.MEDIUM),
        MaintenanceTemplateDefinition("Exterior Lights", CATEGORY_ELECTRICAL, intervalTimeDays = 180, importance = MaintenanceImportance.LOW),
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
        templatesForAsset(
            assetCategory = AssetCategory.VEHICLE,
            assetType = AssetType.fromVehicleType(vehicleType),
            fuelType = when (vehicleType) {
                VehicleType.GASOLINE,
                VehicleType.MOTORCYCLE,
                -> FuelType.GASOLINE
                VehicleType.DIESEL -> FuelType.DIESEL
                VehicleType.HYBRID -> FuelType.HYBRID_GASOLINE
                VehicleType.PLUG_IN_HYBRID -> FuelType.PLUG_IN_HYBRID_GASOLINE
                VehicleType.EV -> FuelType.ELECTRIC
                VehicleType.OTHER -> FuelType.OTHER
            },
        )

    fun templatesForAsset(vehicle: Vehicle): List<MaintenanceTemplateDefinition> =
        templatesForAsset(vehicle.assetCategory, vehicle.assetType, vehicle.fuelType)

    fun templatesForAsset(
        assetCategory: AssetCategory,
        assetType: AssetType,
        fuelType: FuelType,
    ): List<MaintenanceTemplateDefinition> =
        when (assetCategory) {
            AssetCategory.VEHICLE -> vehicleTemplates(assetType, fuelType)
            AssetCategory.EQUIPMENT -> equipmentTemplates(assetType, fuelType)
        }

    private fun vehicleTemplates(assetType: AssetType, fuelType: FuelType): List<MaintenanceTemplateDefinition> {
        if (assetType == AssetType.TRAILER || fuelType == FuelType.NONE) {
            return listOf(
                MaintenanceTemplateDefinition("Tire Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 6_000.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Wheel Bearing Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 12_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Brake Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 12_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Exterior Lights", CATEGORY_ELECTRICAL, intervalTimeDays = 180, importance = MaintenanceImportance.MEDIUM),
            )
        }

        val base = if (fuelType.isElectricOnly) evRoadTemplates else liquidFuelRoadTemplates
        val hybrid = if (fuelType == FuelType.HYBRID_GASOLINE || fuelType == FuelType.HYBRID_DIESEL || fuelType.isPlugInHybrid) {
            listOf(
                MaintenanceTemplateDefinition("Hybrid System Inspection", CATEGORY_HYBRID_SYSTEMS, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("High Voltage Battery Health Inspection", CATEGORY_HYBRID_SYSTEMS, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Regenerative Brake Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
        } else {
            emptyList()
        }
        val charging = if (fuelType.isPlugInHybrid) {
            listOf(
                MaintenanceTemplateDefinition("Charge Port Inspection", CATEGORY_EV_SYSTEMS, intervalMiles = 12_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Charging Cable Inspection", CATEGORY_EV_SYSTEMS, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
        } else {
            emptyList()
        }
        val diesel = if (fuelType.isDieselLike) {
            listOf(
                MaintenanceTemplateDefinition("Fuel Filter", CATEGORY_ENGINE, intervalMiles = 20_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Glow Plug Inspection", CATEGORY_ENGINE, intervalMiles = 60_000.0, intervalTimeDays = 1_095, importance = MaintenanceImportance.MEDIUM),
            )
        } else {
            emptyList()
        }
        val motorcycle = if (assetType == AssetType.MOTORCYCLE) {
            listOf(
                MaintenanceTemplateDefinition("Chain Service", CATEGORY_CRITICAL, intervalMiles = 600.0, intervalTimeDays = 90, importance = MaintenanceImportance.HIGH),
            )
        } else {
            emptyList()
        }
        return (base + hybrid + charging + diesel + motorcycle).filterImpossible(fuelType)
    }

    private fun equipmentTemplates(assetType: AssetType, fuelType: FuelType): List<MaintenanceTemplateDefinition> {
        val fuelPowered = fuelType.usesFuelLog
        val electric = fuelType.usesChargingLog
        val engineItems = if (fuelPowered) {
            listOf(
                MaintenanceTemplateDefinition("Engine Oil", CATEGORY_ENGINE, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Engine Oil Filter", CATEGORY_ENGINE, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Engine Air Filter", CATEGORY_ENGINE, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Fuel Filter", CATEGORY_ENGINE, intervalHours = 250.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            ) + if (fuelType.isGasolineLike) {
                listOf(MaintenanceTemplateDefinition("Spark Plug", CATEGORY_ENGINE, intervalHours = 200.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM))
            } else {
                listOf(MaintenanceTemplateDefinition("Glow Plug Inspection", CATEGORY_ENGINE, intervalHours = 500.0, intervalTimeDays = 730, importance = MaintenanceImportance.MEDIUM))
            }
        } else {
            emptyList()
        }
        val electricItems = if (electric) {
            listOf(
                MaintenanceTemplateDefinition("Battery Inspection", CATEGORY_EV_SYSTEMS, intervalHours = 100.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Charger Inspection", CATEGORY_EV_SYSTEMS, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Charge Port Inspection", CATEGORY_EV_SYSTEMS, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
        } else {
            emptyList()
        }

        return when (assetType) {
            AssetType.SKID_LOADER,
            AssetType.COMPACT_TRACK_LOADER,
            -> engineItems + electricItems + listOf(
                MaintenanceTemplateDefinition("Hydraulic Oil", CATEGORY_HYDRAULICS, intervalHours = 500.0, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Hydraulic Filter", CATEGORY_HYDRAULICS, intervalHours = 250.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Hydraulic Hoses Inspection", CATEGORY_HYDRAULICS, intervalHours = 100.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Grease Pivot Points", CATEGORY_CRITICAL, intervalHours = 10.0, importance = MaintenanceImportance.CRITICAL),
                MaintenanceTemplateDefinition("Safety Interlock Inspection", CATEGORY_CRITICAL, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.CRITICAL),
                MaintenanceTemplateDefinition("Tires or Tracks", CATEGORY_WEAR_ITEMS, intervalHours = 100.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
            )
            AssetType.EXCAVATOR,
            AssetType.MINI_EXCAVATOR,
            -> engineItems + electricItems + listOf(
                MaintenanceTemplateDefinition("Hydraulic Oil", CATEGORY_HYDRAULICS, intervalHours = 500.0, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Hydraulic Filter", CATEGORY_HYDRAULICS, intervalHours = 250.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Hydraulic Hoses Inspection", CATEGORY_HYDRAULICS, intervalHours = 100.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Track Tension Inspection", CATEGORY_UNDERCARRIAGE, intervalHours = 50.0, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Track Roller Inspection", CATEGORY_UNDERCARRIAGE, intervalHours = 250.0, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Bucket Teeth Inspection", CATEGORY_ATTACHMENTS, intervalHours = 50.0, importance = MaintenanceImportance.MEDIUM),
            )
            AssetType.BACKHOE,
            AssetType.TRACTOR,
            -> engineItems + electricItems + listOf(
                MaintenanceTemplateDefinition("Hydraulic Fluid", CATEGORY_HYDRAULICS, intervalHours = 500.0, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Hydraulic Filter", CATEGORY_HYDRAULICS, intervalHours = 250.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Transmission Fluid", CATEGORY_FLUIDS, intervalHours = 500.0, intervalTimeDays = 730, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Grease Points", CATEGORY_CRITICAL, intervalHours = 10.0, importance = MaintenanceImportance.CRITICAL),
                MaintenanceTemplateDefinition("PTO Inspection", CATEGORY_ATTACHMENTS, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
            AssetType.ZERO_TURN_MOWER,
            AssetType.LAWN_TRACTOR,
            AssetType.PUSH_MOWER,
            -> engineItems + electricItems + listOf(
                MaintenanceTemplateDefinition("Blade Sharpening", CATEGORY_CUTTING_SYSTEM, intervalHours = 25.0, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Mower Deck Cleaning", CATEGORY_CUTTING_SYSTEM, intervalHours = 10.0, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Deck Belt Inspection", CATEGORY_CUTTING_SYSTEM, intervalHours = 50.0, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Spindle Bearing Inspection", CATEGORY_CUTTING_SYSTEM, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
            )
            AssetType.CHAINSAW -> listOf(
                MaintenanceTemplateDefinition("Chain Sharpening", CATEGORY_CUTTING_SYSTEM, intervalHours = 5.0, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Chain Tension Inspection", CATEGORY_CUTTING_SYSTEM, intervalHours = 2.0, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Bar Inspection", CATEGORY_CUTTING_SYSTEM, intervalHours = 10.0, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Bar Oil System Inspection", CATEGORY_FLUIDS, intervalHours = 10.0, importance = MaintenanceImportance.MEDIUM),
            ) + engineItems.filter { it.name in setOf("Engine Air Filter", "Spark Plug", "Fuel Filter") } + electricItems
            AssetType.GENERATOR -> engineItems + electricItems + listOf(
                MaintenanceTemplateDefinition("Load Test", CATEGORY_ELECTRICAL, intervalHours = 50.0, intervalTimeDays = 180, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Outlet Inspection", CATEGORY_ELECTRICAL, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Grounding Inspection", CATEGORY_ELECTRICAL, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
            )
            AssetType.PRESSURE_WASHER -> engineItems + electricItems + listOf(
                MaintenanceTemplateDefinition("Pump Oil", CATEGORY_FLUIDS, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Pump Inspection", CATEGORY_OTHER, intervalHours = 50.0, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Hose Inspection", CATEGORY_WEAR_ITEMS, intervalHours = 25.0, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Inlet Filter Cleaning", CATEGORY_OTHER, intervalHours = 25.0, importance = MaintenanceImportance.MEDIUM),
            )
            AssetType.ATV,
            AssetType.UTV,
            -> engineItems + electricItems + listOf(
                MaintenanceTemplateDefinition("Differential Fluid", CATEGORY_DRIVETRAIN, intervalMiles = 1_000.0, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Drive Belt Inspection", CATEGORY_DRIVETRAIN, intervalMiles = 1_000.0, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                MaintenanceTemplateDefinition("Brake Pads", CATEGORY_WEAR_ITEMS, intervalMiles = 1_000.0, intervalHours = 100.0, importance = MaintenanceImportance.HIGH),
                MaintenanceTemplateDefinition("Tires", CATEGORY_WEAR_ITEMS, intervalMiles = 500.0, intervalHours = 50.0, importance = MaintenanceImportance.HIGH),
            )
            AssetType.GOLF_CART -> if (electric) {
                electricItems + listOf(
                    MaintenanceTemplateDefinition("Battery Terminal Cleaning", CATEGORY_EV_SYSTEMS, intervalTimeDays = 180, importance = MaintenanceImportance.MEDIUM),
                    MaintenanceTemplateDefinition("Brake Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 1_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                    MaintenanceTemplateDefinition("Tire Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 500.0, intervalTimeDays = 180, importance = MaintenanceImportance.MEDIUM),
                    MaintenanceTemplateDefinition("Steering Inspection", CATEGORY_WEAR_ITEMS, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                )
            } else {
                engineItems + listOf(
                    MaintenanceTemplateDefinition("Drive Belt", CATEGORY_DRIVETRAIN, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM),
                    MaintenanceTemplateDefinition("Brake Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 1_000.0, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
                    MaintenanceTemplateDefinition("Tire Inspection", CATEGORY_WEAR_ITEMS, intervalMiles = 500.0, intervalTimeDays = 180, importance = MaintenanceImportance.MEDIUM),
                )
            }
            else -> genericEquipmentTemplates(fuelType)
        }.filterImpossible(fuelType)
    }

    private fun genericEquipmentTemplates(fuelType: FuelType): List<MaintenanceTemplateDefinition> =
        listOf(
            MaintenanceTemplateDefinition("General Inspection", CATEGORY_OTHER, intervalTimeDays = 180, importance = MaintenanceImportance.MEDIUM),
            MaintenanceTemplateDefinition("Fastener Check", CATEGORY_OTHER, intervalTimeDays = 180, importance = MaintenanceImportance.MEDIUM),
            MaintenanceTemplateDefinition("Safety Equipment Inspection", CATEGORY_CRITICAL, intervalTimeDays = 365, importance = MaintenanceImportance.HIGH),
        ) + if (fuelType.usesFuelLog) {
            listOf(MaintenanceTemplateDefinition("Air Filter", CATEGORY_ENGINE, intervalHours = 100.0, intervalTimeDays = 365, importance = MaintenanceImportance.MEDIUM))
        } else if (fuelType.usesChargingLog) {
            listOf(MaintenanceTemplateDefinition("Battery Inspection", CATEGORY_EV_SYSTEMS, intervalTimeDays = 180, importance = MaintenanceImportance.MEDIUM))
        } else {
            emptyList()
        }

    private fun List<MaintenanceTemplateDefinition>.filterImpossible(fuelType: FuelType): List<MaintenanceTemplateDefinition> =
        filterNot { template ->
            val normalized = template.name.lowercase()
            fuelType.isElectricOnly && (
                "oil change" in normalized ||
                    normalized == "engine oil" ||
                    "spark plug" in normalized ||
                    "fuel filter" in normalized ||
                    "fuel system" in normalized ||
                    "serpentine" in normalized
                )
        }

    fun baselineServiceLogsForNewVehicle(
        vehicle: Vehicle,
        items: List<MaintenanceItem>,
        createdAt: LocalDateTime,
        notes: String = "Baseline recorded automatically for new asset setup.",
    ): List<MaintenanceServiceLog> =
        items.map { item ->
            MaintenanceServiceLog(
                id = UUID.randomUUID().toString(),
                vehicleId = vehicle.id,
                maintenanceItemId = item.id,
                dateTime = createdAt,
                odometer = if (AssetRules.supportsMileage(vehicle)) vehicle.currentMileage else 0.0,
                hours = vehicle.currentHours?.takeIf { AssetRules.supportsHours(vehicle) },
                cost = 0.0,
                notes = notes,
            )
        }
}
