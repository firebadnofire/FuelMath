package org.archuser.fuelmath

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.LinkedHashMap

object FuelJsonCodec {
    fun encode(data: FuelMathData): String = buildString {
        appendObjectStart()
        appendField("schemaVersion", CURRENT_SCHEMA_VERSION)
        appendComma()
        appendObjectField("settings") {
            appendField("dueSoonThresholdPercent", data.settings.dueSoonThresholdPercent)
            appendComma()
            appendField("maintenanceRemindersEnabled", data.settings.maintenanceRemindersEnabled)
        }
        appendComma()
        appendArrayField("vehicles", data.vehicles) { vehicle ->
            appendObjectStart()
            appendField("id", vehicle.id)
            appendComma()
            appendField("name", vehicle.name)
            appendComma()
            appendField("make", vehicle.make)
            appendComma()
            appendField("model", vehicle.model)
            appendComma()
            appendNullableField("year", vehicle.year)
            appendComma()
            appendField("currentMileage", vehicle.currentMileage)
            appendComma()
            appendField("vehicleType", vehicle.vehicleType.storageValue)
            appendComma()
            appendField("fuelType", vehicle.fuelType.storageValue)
            appendComma()
            appendField("tankCapacity", vehicle.tankCapacity)
            appendComma()
            appendNullableField("batteryCapacity", vehicle.batteryCapacity)
            appendComma()
            appendNullableField("recommendedFrontTirePsi", vehicle.recommendedFrontTirePsi)
            appendComma()
            appendNullableField("recommendedRearTirePsi", vehicle.recommendedRearTirePsi)
            appendComma()
            appendField("distanceUnit", vehicle.distanceUnit.storageValue)
            appendComma()
            appendField("volumeUnit", vehicle.volumeUnit.storageValue)
            appendComma()
            appendField("energyUnit", vehicle.energyUnit.storageValue)
            appendObjectEnd()
        }
        appendComma()
        appendArrayField("fuelEntries", data.fuelEntries) { entry ->
            appendObjectStart()
            appendField("id", entry.id)
            appendComma()
            appendField("vehicleId", entry.vehicleId)
            appendComma()
            appendField("dateTime", entry.dateTime.toString())
            appendComma()
            appendField("odometer", entry.odometer)
            appendComma()
            appendField("fuelAmount", entry.fuelAmount)
            appendComma()
            appendField("pricePerUnit", entry.pricePerUnit)
            appendComma()
            appendField("isFullTank", entry.isFullTank)
            appendComma()
            appendField("entryType", entry.entryType.storageValue)
            appendObjectEnd()
        }
        appendComma()
        appendArrayField("maintenanceCategories", normalizedCategories(data.maintenanceCategories)) { category ->
            appendObjectStart()
            appendField("id", category.id)
            appendComma()
            appendField("name", category.name)
            appendObjectEnd()
        }
        appendComma()
        appendArrayField("maintenanceItems", data.maintenanceItems) { item ->
            appendObjectStart()
            appendField("id", item.id)
            appendComma()
            appendField("vehicleId", item.vehicleId)
            appendComma()
            appendField("categoryId", item.categoryId)
            appendComma()
            appendField("name", item.name)
            appendComma()
            appendNullableField("intervalMiles", item.intervalMiles)
            appendComma()
            appendNullableField("intervalTimeDays", item.intervalTimeDays)
            appendComma()
            appendField("notes", item.notes)
            appendComma()
            appendField("importance", item.importance.storageValue)
            appendObjectEnd()
        }
        appendComma()
        appendArrayField("maintenanceServiceLogs", data.maintenanceServiceLogs) { log ->
            appendObjectStart()
            appendField("id", log.id)
            appendComma()
            appendField("vehicleId", log.vehicleId)
            appendComma()
            appendField("maintenanceItemId", log.maintenanceItemId)
            appendComma()
            appendField("dateTime", log.dateTime.toString())
            appendComma()
            appendField("odometer", log.odometer)
            appendComma()
            appendField("cost", log.cost)
            appendComma()
            appendField("notes", log.notes)
            appendObjectEnd()
        }
        appendObjectEnd()
    }

    fun decode(json: String): FuelMathData {
        val root = SimpleJsonParser(json).parseRoot().asObject("root")
        val schemaVersion = root.optionalNumber("schemaVersion")?.toInt() ?: 1
        require(schemaVersion <= CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schema version: $schemaVersion"
        }

        return when (schemaVersion) {
            1 -> decodeSchemaV1(root)
            2 -> decodeSchemaV2(root)
            3 -> decodeSchemaV3(root)
            4 -> decodeSchemaV4(root)
            CURRENT_SCHEMA_VERSION -> decodeSchemaV5(root)
            else -> throw IllegalArgumentException("Unsupported backup schema version: $schemaVersion")
        }
    }

    private fun decodeSchemaV5(root: Map<String, Any?>): FuelMathData {
        val settingsObject = root.optionalObject("settings")
        val settings = AppSettings(
            dueSoonThresholdPercent = settingsObject?.optionalNumber("dueSoonThresholdPercent")?.toInt() ?: 10,
            maintenanceRemindersEnabled = settingsObject?.optionalBoolean("maintenanceRemindersEnabled") ?: false,
        )

        val vehicles = root.requiredList("vehicles").map { value ->
            val item = value.asObject("vehicle")
            Vehicle(
                id = item.requiredString("id"),
                name = item.requiredString("name"),
                make = item.optionalString("make").orEmpty(),
                model = item.optionalString("model").orEmpty(),
                year = item.optionalNumber("year")?.toInt(),
                currentMileage = item.optionalNumber("currentMileage") ?: 0.0,
                fuelType = FuelType.fromStorage(item.optionalString("fuelType") ?: FuelType.GASOLINE.storageValue),
                vehicleType = parseVehicleType(item),
                tankCapacity = item.requiredNumber("tankCapacity"),
                batteryCapacity = item.optionalNumber("batteryCapacity"),
                recommendedFrontTirePsi = item.optionalNumber("recommendedFrontTirePsi"),
                recommendedRearTirePsi = item.optionalNumber("recommendedRearTirePsi"),
                distanceUnit = DistanceUnit.fromStorage(item.requiredString("distanceUnit")),
                volumeUnit = VolumeUnit.fromStorage(item.requiredString("volumeUnit")),
                energyUnit = EnergyUnit.fromStorage(item.optionalString("energyUnit") ?: EnergyUnit.KILOWATT_HOURS.storageValue),
            )
        }

        val fuelEntries = parseFuelEntries(root)
        val categories = normalizedCategories(
            root.optionalList("maintenanceCategories").orEmpty().map { value ->
                val item = value.asObject("maintenance category")
                MaintenanceCategory(
                    id = item.requiredString("id"),
                    name = item.requiredString("name"),
                )
            },
        )

        val maintenanceItems = root.optionalList("maintenanceItems").orEmpty().map { value ->
            val item = value.asObject("maintenance item")
            MaintenanceItem(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                categoryId = item.requiredString("categoryId"),
                name = item.requiredString("name"),
                intervalMiles = item.optionalNumber("intervalMiles"),
                intervalTimeDays = item.optionalNumber("intervalTimeDays")?.toInt(),
                notes = item.optionalString("notes").orEmpty(),
                importance = MaintenanceImportance.fromStorage(
                    item.optionalString("importance") ?: MaintenanceImportance.MEDIUM.storageValue,
                ),
            )
        }

        val maintenanceServiceLogs = root.optionalList("maintenanceServiceLogs").orEmpty().map { value ->
            val item = value.asObject("maintenance service log")
            MaintenanceServiceLog(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                maintenanceItemId = item.requiredString("maintenanceItemId"),
                dateTime = LocalDateTime.parse(item.requiredString("dateTime")),
                odometer = item.requiredNumber("odometer"),
                cost = item.requiredNumber("cost"),
                notes = item.optionalString("notes").orEmpty(),
            )
        }

        val data = FuelMathData(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            vehicles = vehicles,
            fuelEntries = fuelEntries,
            maintenanceCategories = categories,
            maintenanceItems = maintenanceItems,
            maintenanceServiceLogs = maintenanceServiceLogs,
            settings = settings,
        )
        validateData(data)
        return data
    }

    private fun decodeSchemaV4(root: Map<String, Any?>): FuelMathData {
        val upgradedRoot = root.toMutableMap()
        val upgradedVehicles = root.requiredList("vehicles").map { value ->
            val item = value.asObject("vehicle").toMutableMap()
            item.putIfAbsent("recommendedFrontTirePsi", null)
            item.putIfAbsent("recommendedRearTirePsi", null)
            item
        }
        upgradedRoot["vehicles"] = upgradedVehicles
        return decodeSchemaV5(upgradedRoot)
    }

    private fun decodeSchemaV2(root: Map<String, Any?>): FuelMathData {
        val settingsObject = root.optionalObject("settings")
        val settings = AppSettings(
            dueSoonThresholdPercent = settingsObject?.optionalNumber("dueSoonThresholdPercent")?.toInt() ?: 10,
            maintenanceRemindersEnabled = settingsObject?.optionalBoolean("maintenanceRemindersEnabled") ?: false,
        )

        val vehicles = root.requiredList("vehicles").map { value ->
            val item = value.asObject("vehicle")
            Vehicle(
                id = item.requiredString("id"),
                name = item.requiredString("name"),
                make = item.optionalString("make").orEmpty(),
                model = item.optionalString("model").orEmpty(),
                year = item.optionalNumber("year")?.toInt(),
                currentMileage = item.optionalNumber("currentMileage") ?: 0.0,
                fuelType = FuelType.fromStorage(item.optionalString("fuelType") ?: FuelType.GASOLINE.storageValue),
                vehicleType = parseVehicleType(item),
                tankCapacity = item.requiredNumber("tankCapacity"),
                batteryCapacity = item.optionalNumber("batteryCapacity"),
                recommendedFrontTirePsi = item.optionalNumber("recommendedFrontTirePsi"),
                recommendedRearTirePsi = item.optionalNumber("recommendedRearTirePsi"),
                distanceUnit = DistanceUnit.fromStorage(item.requiredString("distanceUnit")),
                volumeUnit = VolumeUnit.fromStorage(item.requiredString("volumeUnit")),
                energyUnit = EnergyUnit.fromStorage(item.optionalString("energyUnit") ?: EnergyUnit.KILOWATT_HOURS.storageValue),
            )
        }

        val fuelEntries = parseFuelEntries(root)
        val categories = normalizedCategories(
            root.optionalList("maintenanceCategories").orEmpty().map { value ->
                val item = value.asObject("maintenance category")
                MaintenanceCategory(
                    id = item.requiredString("id"),
                    name = item.requiredString("name"),
                )
            },
        )

        val legacyBaselines = mutableListOf<LegacyMaintenanceBaseline>()
        val maintenanceItems = root.optionalList("maintenanceItems").orEmpty().map { value ->
            val item = value.asObject("maintenance item")
            val itemId = item.requiredString("id")
            val vehicleId = item.requiredString("vehicleId")
            legacyBaselines += LegacyMaintenanceBaseline(
                maintenanceItemId = itemId,
                vehicleId = vehicleId,
                lastServiceMileage = item.optionalNumber("lastServiceMileage"),
                lastServiceDate = item.optionalString("lastServiceDate")?.let(LocalDate::parse),
                lastServiceCost = item.optionalNumber("lastServiceCost"),
            )
            MaintenanceItem(
                id = itemId,
                vehicleId = vehicleId,
                categoryId = item.requiredString("categoryId"),
                name = item.requiredString("name"),
                intervalMiles = item.optionalNumber("intervalMiles"),
                intervalTimeDays = item.optionalNumber("intervalTimeDays")?.toInt(),
                notes = item.optionalString("notes").orEmpty(),
                importance = MaintenanceImportance.fromStorage(
                    item.optionalString("importance") ?: MaintenanceImportance.MEDIUM.storageValue,
                ),
            )
        }

        val decodedLogs = root.optionalList("maintenanceServiceLogs").orEmpty().map { value ->
            val item = value.asObject("maintenance service log")
            MaintenanceServiceLog(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                maintenanceItemId = item.requiredString("maintenanceItemId"),
                dateTime = LocalDateTime.parse(item.requiredString("dateTime")),
                odometer = item.requiredNumber("odometer"),
                cost = item.requiredNumber("cost"),
                notes = item.optionalString("notes").orEmpty(),
            )
        }
        val synthesizedLogs = synthesizeLegacyBaselineLogs(decodedLogs, legacyBaselines)

        val data = FuelMathData(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            vehicles = vehicles,
            fuelEntries = fuelEntries,
            maintenanceCategories = categories,
            maintenanceItems = maintenanceItems,
            maintenanceServiceLogs = decodedLogs + synthesizedLogs,
            settings = settings,
        )
        validateData(data)
        return data
    }

    private fun decodeSchemaV3(root: Map<String, Any?>): FuelMathData = decodeSchemaV4(root)

    private fun decodeSchemaV1(root: Map<String, Any?>): FuelMathData {
        val rawVehicles = root.requiredList("vehicles").map { value ->
            val item = value.asObject("vehicle")
            Vehicle(
                id = item.requiredString("id"),
                name = item.requiredString("name"),
                tankCapacity = item.requiredNumber("tankCapacity"),
                recommendedFrontTirePsi = item.optionalNumber("recommendedFrontTirePsi"),
                recommendedRearTirePsi = item.optionalNumber("recommendedRearTirePsi"),
                distanceUnit = DistanceUnit.fromStorage(item.requiredString("distanceUnit")),
                volumeUnit = VolumeUnit.fromStorage(item.requiredString("volumeUnit")),
            )
        }
        val fuelEntries = parseFuelEntries(root)
        val legacyMaintenanceEntries = root.optionalList("maintenanceEntries").orEmpty().map { value ->
            val item = value.asObject("legacy maintenance entry")
            LegacyMaintenanceEntry(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                dateTime = LocalDateTime.parse(item.requiredString("dateTime")),
                odometer = item.requiredNumber("odometer"),
                type = item.requiredString("type"),
                cost = item.requiredNumber("cost"),
                notes = item.optionalString("notes").orEmpty(),
            )
        }
        val migratedItems = legacyMaintenanceEntries
            .groupBy { it.vehicleId to it.type.ifBlank { "Maintenance" } }
            .map { (key, entries) ->
                val vehicleId = key.first
                val type = key.second
                MaintenanceItem(
                    id = legacyItemId(vehicleId, type),
                    vehicleId = vehicleId,
                    categoryId = MaintenanceDefaults.categoryIdForLegacyType(type),
                    name = type,
                    notes = "",
                    importance = MaintenanceDefaults.importanceForName(type),
                )
            }
        val migratedLogs = legacyMaintenanceEntries.map { entry ->
            MaintenanceServiceLog(
                id = entry.id.ifBlank { legacyLogId(entry.vehicleId, entry.type, entry.dateTime, entry.odometer) },
                vehicleId = entry.vehicleId,
                maintenanceItemId = legacyItemId(entry.vehicleId, entry.type.ifBlank { "Maintenance" }),
                dateTime = entry.dateTime,
                odometer = entry.odometer,
                cost = entry.cost,
                notes = entry.notes,
            )
        }
        val vehicles = rawVehicles.map { vehicle ->
            val latestFuel = fuelEntries
                .filter { it.vehicleId == vehicle.id }
                .maxOfOrNull { it.odometer }
                ?: 0.0
            val latestMaintenance = migratedLogs
                .filter { it.vehicleId == vehicle.id }
                .maxOfOrNull { it.odometer }
                ?: 0.0
            vehicle.copy(currentMileage = maxOf(0.0, latestFuel, latestMaintenance))
        }

        val data = FuelMathData(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            vehicles = vehicles,
            fuelEntries = fuelEntries,
            maintenanceCategories = MaintenanceDefaults.categories,
            maintenanceItems = migratedItems,
            maintenanceServiceLogs = migratedLogs,
            settings = AppSettings(),
        )
        validateData(data)
        return data
    }

    private fun parseFuelEntries(root: Map<String, Any?>): List<FuelEntry> =
        root.requiredList("fuelEntries").map { value ->
            val item = value.asObject("fuel entry")
            FuelEntry(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                dateTime = LocalDateTime.parse(item.requiredString("dateTime")),
                odometer = item.requiredNumber("odometer"),
                fuelAmount = item.requiredNumber("fuelAmount"),
                pricePerUnit = item.requiredNumber("pricePerUnit"),
                isFullTank = item.requiredBoolean("isFullTank"),
                entryType = EnergyEntryType.fromStorage(item.optionalString("entryType") ?: EnergyEntryType.FUEL.storageValue),
            )
        }

    private fun validateData(data: FuelMathData) {
        val vehicleIds = data.vehicles.map { it.id }.toSet()
        val categoryIds = data.maintenanceCategories.map { it.id }.toSet()
        val itemIds = data.maintenanceItems.map { it.id }.toSet()
        require(data.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unexpected decoded schema version: ${data.schemaVersion}"
        }
        require(data.settings.dueSoonThresholdPercent in 0..100) {
            "Due soon threshold must be between 0 and 100"
        }
        require(data.vehicles.size == vehicleIds.size) {
            "Vehicle ids must be unique"
        }
        require(data.vehicles.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Vehicle id and name are required"
        }
        require(data.vehicles.all { vehicle ->
            (!vehicle.vehicleType.usesLiquidFuel || vehicle.tankCapacity.isFinite() && vehicle.tankCapacity > 0.0) &&
                (vehicle.vehicleType.usesLiquidFuel || vehicle.tankCapacity.isFinite() && vehicle.tankCapacity >= 0.0) &&
                (!vehicle.vehicleType.usesBattery || vehicle.batteryCapacity?.let { it.isFinite() && it > 0.0 } == true) &&
                (vehicle.vehicleType.usesBattery || vehicle.batteryCapacity == null || vehicle.batteryCapacity.isFinite() && vehicle.batteryCapacity >= 0.0)
        }) {
            "Vehicle capacities do not match their vehicle types"
        }
        require(data.vehicles.all { it.currentMileage.isFinite() && it.currentMileage >= 0.0 }) {
            "Vehicle current mileage values cannot be negative"
        }
        require(
            data.vehicles.all {
                (it.recommendedFrontTirePsi == null || it.recommendedFrontTirePsi.isFinite() && it.recommendedFrontTirePsi > 0.0) &&
                    (it.recommendedRearTirePsi == null || it.recommendedRearTirePsi.isFinite() && it.recommendedRearTirePsi > 0.0)
            },
        ) {
            "Vehicle tire PSI reminders must be positive when present"
        }
        require(data.maintenanceCategories.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Maintenance category id and name are required"
        }
        require(data.fuelEntries.all { it.vehicleId in vehicleIds }) {
            "Backup contains a fuel entry for an unknown vehicle"
        }
        require(data.fuelEntries.all { it.odometer.isFinite() && it.odometer >= 0.0 && it.fuelAmount.isFinite() && it.fuelAmount > 0.0 && it.pricePerUnit.isFinite() && it.pricePerUnit >= 0.0 }) {
            "Fuel entries contain invalid numeric values"
        }
        require(data.maintenanceItems.all { it.vehicleId in vehicleIds && it.categoryId in categoryIds }) {
            "Backup contains a maintenance item with an unknown vehicle or category"
        }
        require(data.maintenanceItems.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Maintenance item id and name are required"
        }
        require(
            data.maintenanceItems.all { item ->
                (item.intervalMiles == null || item.intervalMiles.isFinite() && item.intervalMiles > 0.0) &&
                    (item.intervalTimeDays == null || item.intervalTimeDays > 0)
            },
        ) {
            "Maintenance items contain invalid interval values"
        }
        require(data.maintenanceServiceLogs.all { it.vehicleId in vehicleIds && it.maintenanceItemId in itemIds }) {
            "Backup contains a maintenance service log with an unknown vehicle or item"
        }
        require(data.maintenanceServiceLogs.all { log ->
            val item = data.maintenanceItems.firstOrNull { it.id == log.maintenanceItemId }
            item != null && item.vehicleId == log.vehicleId
        }) {
            "Maintenance service logs must belong to an item on the same vehicle"
        }
        require(data.maintenanceServiceLogs.all { it.id.isNotBlank() && it.odometer.isFinite() && it.odometer >= 0.0 && it.cost.isFinite() && it.cost >= 0.0 }) {
            "Maintenance service logs contain invalid numeric values"
        }
    }

    private fun normalizedCategories(categories: List<MaintenanceCategory>): List<MaintenanceCategory> {
        val byId = LinkedHashMap<String, MaintenanceCategory>()
        categories.forEach { category -> byId[category.id] = category }
        MaintenanceDefaults.categories.forEach { category -> byId.putIfAbsent(category.id, category) }
        return byId.values.toList()
    }

    private fun parseVehicleType(item: Map<String, Any?>): VehicleType =
        item.optionalString("vehicleType")?.let(VehicleType::fromStorage)
            ?: VehicleType.fromFuelType(FuelType.fromStorage(item.optionalString("fuelType") ?: FuelType.GASOLINE.storageValue))

    private fun legacyItemId(vehicleId: String, type: String): String =
        "legacy-${vehicleId.toStableIdPart()}-${type.toStableIdPart()}-${type.hashCode().toUInt().toString(16)}"

    private fun legacyLogId(vehicleId: String, type: String, dateTime: LocalDateTime, odometer: Double): String =
        "legacy-log-${vehicleId.toStableIdPart()}-${type.toStableIdPart()}-${dateTime}-${odometer.toString().toStableIdPart()}"

    private fun synthesizeLegacyBaselineLogs(
        decodedLogs: List<MaintenanceServiceLog>,
        baselines: List<LegacyMaintenanceBaseline>,
    ): List<MaintenanceServiceLog> =
        baselines.mapNotNull { baseline ->
            val date = baseline.lastServiceDate
            val odometer = baseline.lastServiceMileage
            if (date == null || odometer == null) return@mapNotNull null
            val alreadyPresent = decodedLogs.any { log ->
                log.maintenanceItemId == baseline.maintenanceItemId &&
                    log.vehicleId == baseline.vehicleId &&
                    log.odometer == odometer &&
                    log.dateTime.toLocalDate() == date
            }
            if (alreadyPresent) return@mapNotNull null
            MaintenanceServiceLog(
                id = "migrated-baseline-${baseline.maintenanceItemId}",
                vehicleId = baseline.vehicleId,
                maintenanceItemId = baseline.maintenanceItemId,
                dateTime = date.atStartOfDay(),
                odometer = odometer,
                cost = baseline.lastServiceCost?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0,
                notes = "Migrated baseline from schema v2 backup",
            )
        }

    private fun String.toStableIdPart(): String =
        lowercase()
            .map { char -> if (char.isLetterOrDigit()) char else '-' }
            .joinToString("")
            .trim('-')
            .ifBlank { "item" }

    private data class LegacyMaintenanceEntry(
        val id: String,
        val vehicleId: String,
        val dateTime: LocalDateTime,
        val odometer: Double,
        val type: String,
        val cost: Double,
        val notes: String,
    )

    private data class LegacyMaintenanceBaseline(
        val maintenanceItemId: String,
        val vehicleId: String,
        val lastServiceMileage: Double?,
        val lastServiceDate: LocalDate?,
        val lastServiceCost: Double?,
    )

    private fun StringBuilder.appendObjectStart() {
        append('{')
    }

    private fun StringBuilder.appendObjectEnd() {
        append('}')
    }

    private fun StringBuilder.appendComma() {
        append(',')
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        appendJsonString(name)
        append(':')
        appendJsonString(value)
    }

    private fun StringBuilder.appendField(name: String, value: Number) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendField(name: String, value: Boolean) {
        appendJsonString(name)
        append(':')
        append(value)
    }

    private fun StringBuilder.appendNullableField(name: String, value: String?) {
        appendJsonString(name)
        append(':')
        if (value == null) append("null") else appendJsonString(value)
    }

    private fun StringBuilder.appendNullableField(name: String, value: Number?) {
        appendJsonString(name)
        append(':')
        if (value == null) append("null") else append(value)
    }

    private fun StringBuilder.appendObjectField(
        name: String,
        appendObject: StringBuilder.() -> Unit,
    ) {
        appendJsonString(name)
        append(':')
        appendObjectStart()
        appendObject()
        appendObjectEnd()
    }

    private fun <T> StringBuilder.appendArrayField(
        name: String,
        items: List<T>,
        appendItem: StringBuilder.(T) -> Unit,
    ) {
        appendJsonString(name)
        append(':')
        append('[')
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            appendItem(item)
        }
        append(']')
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
}

private class SimpleJsonParser(private val source: String) {
    private var index = 0

    fun parseRoot(): Any? {
        val value = parseValue()
        skipWhitespace()
        require(index == source.length) { "Unexpected trailing JSON content at index $index" }
        return value
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        require(index < source.length) { "Unexpected end of JSON" }
        return when (source[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            '-', in '0'..'9' -> parseNumber()
            else -> error("Unexpected JSON value at index $index")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val values = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek('}')) {
            index++
            return values
        }

        while (true) {
            val name = parseString()
            skipWhitespace()
            expect(':')
            values[name] = parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    return values
                }
                else -> error("Expected ',' or '}' at index $index")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val values = mutableListOf<Any?>()
        skipWhitespace()
        if (peek(']')) {
            index++
            return values
        }

        while (true) {
            values += parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    return values
                }
                else -> error("Expected ',' or ']' at index $index")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()
        while (index < source.length) {
            val char = source[index++]
            when (char) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscape())
                else -> builder.append(char)
            }
        }
        error("Unterminated JSON string")
    }

    private fun parseEscape(): Char {
        require(index < source.length) { "Unterminated JSON escape" }
        return when (val escaped = source[index++]) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                require(index + 4 <= source.length) { "Incomplete unicode escape" }
                val hex = source.substring(index, index + 4)
                index += 4
                hex.toInt(16).toChar()
            }
            else -> error("Unknown JSON escape: \\$escaped")
        }
    }

    private fun parseNumber(): Double {
        val start = index
        if (peek('-')) index++
        while (index < source.length && source[index].isDigit()) index++
        if (peek('.')) {
            index++
            while (index < source.length && source[index].isDigit()) index++
        }
        if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
            index++
            if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
            while (index < source.length && source[index].isDigit()) index++
        }
        return source.substring(start, index).toDoubleOrNull()
            ?: error("Invalid JSON number at index $start")
    }

    private fun parseLiteral(literal: String, value: Any?): Any? {
        require(source.startsWith(literal, index)) { "Expected '$literal' at index $index" }
        index += literal.length
        return value
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index++
    }

    private fun expect(expected: Char) {
        skipWhitespace()
        require(index < source.length && source[index] == expected) {
            "Expected '$expected' at index $index"
        }
        index++
    }

    private fun peek(char: Char): Boolean = index < source.length && source[index] == char
}

private fun Any?.asObject(label: String): Map<String, Any?> {
    val map = this as? Map<*, *> ?: throw IllegalArgumentException("Expected $label to be an object")
    return map.entries.associate { (key, value) ->
        val stringKey = key as? String ?: throw IllegalArgumentException("Expected $label object keys to be strings")
        stringKey to value
    }
}

private fun Map<String, Any?>.requiredString(name: String): String =
    this[name] as? String ?: throw IllegalArgumentException("Expected '$name' to be a string")

private fun Map<String, Any?>.optionalString(name: String): String? =
    this[name] as? String

private fun Map<String, Any?>.requiredNumber(name: String): Double =
    (this[name] as? Number)?.toDouble() ?: throw IllegalArgumentException("Expected '$name' to be a number")

private fun Map<String, Any?>.optionalNumber(name: String): Double? =
    (this[name] as? Number)?.toDouble()

private fun Map<String, Any?>.requiredBoolean(name: String): Boolean =
    this[name] as? Boolean ?: throw IllegalArgumentException("Expected '$name' to be a boolean")

private fun Map<String, Any?>.optionalBoolean(name: String): Boolean? =
    this[name] as? Boolean

private fun Map<String, Any?>.requiredList(name: String): List<Any?> =
    this[name] as? List<Any?> ?: throw IllegalArgumentException("Expected '$name' to be an array")

private fun Map<String, Any?>.optionalList(name: String): List<Any?>? =
    this[name] as? List<Any?>

private fun Map<String, Any?>.optionalObject(name: String): Map<String, Any?>? =
    this[name]?.asObject(name)
