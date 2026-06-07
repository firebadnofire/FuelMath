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
            appendField("assetCategory", vehicle.assetCategory.storageValue)
            appendComma()
            appendField("assetType", vehicle.assetType.storageValue)
            appendComma()
            appendField("currentMileage", vehicle.currentMileage)
            appendComma()
            appendNullableField("currentHours", vehicle.currentHours)
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
            appendArrayField("stationSuggestions", normalizedStationSuggestions(vehicle.stationSuggestions)) { station ->
                appendJsonString(station)
            }
            appendComma()
            appendField("distanceUnit", vehicle.distanceUnit.storageValue)
            appendComma()
            appendField("volumeUnit", vehicle.volumeUnit.storageValue)
            appendComma()
            appendField("energyUnit", vehicle.energyUnit.storageValue)
            appendComma()
            appendField("archived", vehicle.archived)
            appendComma()
            appendField("createdAt", vehicle.createdAt.toString())
            appendComma()
            appendField("updatedAt", vehicle.updatedAt.toString())
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
            appendNullableField("hours", entry.hours)
            appendComma()
            appendField("fuelAmount", entry.fuelAmount)
            appendComma()
            appendField("pricePerUnit", entry.pricePerUnit)
            appendComma()
            appendField("isFullTank", entry.isFullTank)
            appendComma()
            appendField("entryType", entry.entryType.storageValue)
            appendComma()
            appendField("station", entry.station)
            appendComma()
            appendField("oilMixRatio", entry.oilMixRatio)
            appendComma()
            appendField("notes", entry.notes)
            appendComma()
            appendNullableField("chargePercentBefore", entry.chargePercentBefore)
            appendComma()
            appendNullableField("chargePercentAfter", entry.chargePercentAfter)
            appendComma()
            appendField("clientGeneratedId", entry.clientGeneratedId)
            appendComma()
            appendField("createdAt", entry.createdAt.toString())
            appendComma()
            appendField("updatedAt", entry.updatedAt.toString())
            appendObjectEnd()
        }
        appendComma()
        appendArrayField("meterLogs", data.meterLogs) { log ->
            appendObjectStart()
            appendField("id", log.id)
            appendComma()
            appendField("vehicleId", log.vehicleId)
            appendComma()
            appendField("dateTime", log.dateTime.toString())
            appendComma()
            appendNullableField("mileage", log.mileage)
            appendComma()
            appendNullableField("hours", log.hours)
            appendComma()
            appendField("source", log.source.storageValue)
            appendComma()
            appendField("notes", log.notes)
            appendComma()
            appendField("clientGeneratedId", log.clientGeneratedId)
            appendComma()
            appendField("createdAt", log.createdAt.toString())
            appendComma()
            appendField("updatedAt", log.updatedAt.toString())
            appendObjectEnd()
        }
        appendComma()
        appendArrayField("maintenanceCategories", normalizedCategories(data.maintenanceCategories)) { category ->
            appendObjectStart()
            appendField("id", category.id)
            appendComma()
            appendField("name", category.name)
            appendComma()
            appendField("sortOrder", category.sortOrder)
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
            appendNullableField("intervalHours", item.intervalHours)
            appendComma()
            appendNullableField("intervalTimeDays", item.intervalTimeDays)
            appendComma()
            appendField("notes", item.notes)
            appendComma()
            appendField("importance", item.importance.storageValue)
            appendComma()
            appendField("active", item.active)
            appendComma()
            appendField("createdAt", item.createdAt.toString())
            appendComma()
            appendField("updatedAt", item.updatedAt.toString())
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
            appendNullableField("hours", log.hours)
            appendComma()
            appendField("cost", log.cost)
            appendComma()
            appendField("notes", log.notes)
            appendComma()
            appendNullableField("receiptPath", log.receiptPath)
            appendComma()
            appendField("clientGeneratedId", log.clientGeneratedId)
            appendComma()
            appendField("createdAt", log.createdAt.toString())
            appendComma()
            appendField("updatedAt", log.updatedAt.toString())
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
            5 -> decodeSchemaV6(root)
            CURRENT_SCHEMA_VERSION -> decodeSchemaV6(root)
            else -> throw IllegalArgumentException("Unsupported backup schema version: $schemaVersion")
        }
    }

    private fun decodeSchemaV6(root: Map<String, Any?>): FuelMathData {
        val settingsObject = root.optionalObject("settings")
        val settings = AppSettings(
            dueSoonThresholdPercent = settingsObject?.optionalNumber("dueSoonThresholdPercent")?.toInt() ?: 10,
            maintenanceRemindersEnabled = settingsObject?.optionalBoolean("maintenanceRemindersEnabled") ?: false,
        )

        val stationSuggestionsByVehicleId = mutableMapOf<String, List<String>?>()
        val rawVehicles = root.requiredList("vehicles").map { value ->
            val item = value.asObject("vehicle")
            val vehicleId = item.requiredString("id")
            stationSuggestionsByVehicleId[vehicleId] = parseStationSuggestions(item)
            val fuelType = parseFuelType(item)
            val assetCategory = AssetCategory.fromStorage(item.optionalString("assetCategory") ?: AssetCategory.VEHICLE.storageValue)
            val assetType = AssetType.fromStorage(
                item.optionalString("assetType")
                    ?: item.optionalString("vehicleType")
                    ?: AssetType.defaultForCategory(assetCategory).storageValue,
                assetCategory,
            )
            val createdAt = item.optionalString("createdAt")?.let(LocalDateTime::parse) ?: LocalDateTime.now()
            Vehicle(
                id = vehicleId,
                name = item.requiredString("name"),
                make = item.optionalString("make").orEmpty(),
                model = item.optionalString("model").orEmpty(),
                year = item.optionalNumber("year")?.toInt(),
                assetCategory = assetCategory,
                assetType = assetType,
                currentMileage = item.optionalNumber("currentMileage") ?: 0.0,
                currentHours = item.optionalNumber("currentHours"),
                fuelType = fuelType,
                vehicleType = parseVehicleType(item, fuelType, assetCategory, assetType),
                tankCapacity = item.optionalNumber("tankCapacity") ?: 0.0,
                batteryCapacity = item.optionalNumber("batteryCapacity"),
                recommendedFrontTirePsi = item.optionalNumber("recommendedFrontTirePsi"),
                recommendedRearTirePsi = item.optionalNumber("recommendedRearTirePsi"),
                stationSuggestions = stationSuggestionsByVehicleId.getValue(vehicleId).orEmpty(),
                distanceUnit = DistanceUnit.fromStorage(item.requiredString("distanceUnit")),
                volumeUnit = VolumeUnit.fromStorage(item.requiredString("volumeUnit")),
                energyUnit = EnergyUnit.fromStorage(item.optionalString("energyUnit") ?: EnergyUnit.KILOWATT_HOURS.storageValue),
                archived = item.optionalBoolean("archived") ?: false,
                createdAt = createdAt,
                updatedAt = item.optionalString("updatedAt")?.let(LocalDateTime::parse) ?: createdAt,
            )
        }

        val fuelEntries = parseFuelEntries(root)
        val vehicles = rawVehicles.withBackfilledStationSuggestions(fuelEntries, stationSuggestionsByVehicleId)
        val meterLogs = parseMeterLogs(root)
        val categories = normalizedCategories(
            root.optionalList("maintenanceCategories").orEmpty().map { value ->
                val item = value.asObject("maintenance category")
                MaintenanceCategory(
                    id = item.requiredString("id"),
                    name = item.requiredString("name"),
                    sortOrder = item.optionalNumber("sortOrder")?.toInt() ?: 0,
                )
            },
        )

        val maintenanceItems = root.optionalList("maintenanceItems").orEmpty().map { value ->
            val item = value.asObject("maintenance item")
            val createdAt = item.optionalString("createdAt")?.let(LocalDateTime::parse) ?: LocalDateTime.now()
            MaintenanceItem(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                categoryId = item.requiredString("categoryId"),
                name = item.requiredString("name"),
                intervalMiles = item.optionalNumber("intervalMiles"),
                intervalHours = item.optionalNumber("intervalHours"),
                intervalTimeDays = item.optionalNumber("intervalTimeDays")?.toInt(),
                notes = item.optionalString("notes").orEmpty(),
                importance = MaintenanceImportance.fromStorage(
                    item.optionalString("importance") ?: MaintenanceImportance.MEDIUM.storageValue,
                ),
                active = item.optionalBoolean("active") ?: true,
                createdAt = createdAt,
                updatedAt = item.optionalString("updatedAt")?.let(LocalDateTime::parse) ?: createdAt,
            )
        }

        val maintenanceServiceLogs = root.optionalList("maintenanceServiceLogs").orEmpty().map { value ->
            val item = value.asObject("maintenance service log")
            val dateTime = LocalDateTime.parse(item.requiredString("dateTime"))
            val createdAt = item.optionalString("createdAt")?.let(LocalDateTime::parse) ?: dateTime
            MaintenanceServiceLog(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                maintenanceItemId = item.requiredString("maintenanceItemId"),
                dateTime = dateTime,
                odometer = item.requiredNumber("odometer"),
                hours = item.optionalNumber("hours"),
                cost = item.requiredNumber("cost"),
                notes = item.optionalString("notes").orEmpty(),
                receiptPath = item.optionalString("receiptPath"),
                clientGeneratedId = item.optionalString("clientGeneratedId") ?: item.requiredString("id"),
                createdAt = createdAt,
                updatedAt = item.optionalString("updatedAt")?.let(LocalDateTime::parse) ?: createdAt,
            )
        }

        val data = FuelMathData(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            vehicles = vehicles,
            fuelEntries = fuelEntries,
            meterLogs = meterLogs,
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
        return decodeSchemaV6(upgradedRoot)
    }

    private fun decodeSchemaV2(root: Map<String, Any?>): FuelMathData {
        val settingsObject = root.optionalObject("settings")
        val settings = AppSettings(
            dueSoonThresholdPercent = settingsObject?.optionalNumber("dueSoonThresholdPercent")?.toInt() ?: 10,
            maintenanceRemindersEnabled = settingsObject?.optionalBoolean("maintenanceRemindersEnabled") ?: false,
        )

        val stationSuggestionsByVehicleId = mutableMapOf<String, List<String>?>()
        val rawVehicles = root.requiredList("vehicles").map { value ->
            val item = value.asObject("vehicle")
            val vehicleId = item.requiredString("id")
            stationSuggestionsByVehicleId[vehicleId] = parseStationSuggestions(item)
            val fuelType = parseFuelType(item)
            val assetCategory = AssetCategory.fromStorage(item.optionalString("assetCategory") ?: AssetCategory.VEHICLE.storageValue)
            val assetType = AssetType.fromStorage(
                item.optionalString("assetType")
                    ?: item.optionalString("vehicleType")
                    ?: AssetType.defaultForCategory(assetCategory).storageValue,
                assetCategory,
            )
            Vehicle(
                id = vehicleId,
                name = item.requiredString("name"),
                make = item.optionalString("make").orEmpty(),
                model = item.optionalString("model").orEmpty(),
                year = item.optionalNumber("year")?.toInt(),
                assetCategory = assetCategory,
                assetType = assetType,
                currentMileage = item.optionalNumber("currentMileage") ?: 0.0,
                currentHours = item.optionalNumber("currentHours"),
                fuelType = fuelType,
                vehicleType = parseVehicleType(item, fuelType, assetCategory, assetType),
                tankCapacity = item.optionalNumber("tankCapacity") ?: 0.0,
                batteryCapacity = item.optionalNumber("batteryCapacity"),
                recommendedFrontTirePsi = item.optionalNumber("recommendedFrontTirePsi"),
                recommendedRearTirePsi = item.optionalNumber("recommendedRearTirePsi"),
                stationSuggestions = stationSuggestionsByVehicleId.getValue(vehicleId).orEmpty(),
                distanceUnit = DistanceUnit.fromStorage(item.requiredString("distanceUnit")),
                volumeUnit = VolumeUnit.fromStorage(item.requiredString("volumeUnit")),
                energyUnit = EnergyUnit.fromStorage(item.optionalString("energyUnit") ?: EnergyUnit.KILOWATT_HOURS.storageValue),
                archived = item.optionalBoolean("archived") ?: false,
            )
        }

        val fuelEntries = parseFuelEntries(root)
        val vehicles = rawVehicles.withBackfilledStationSuggestions(fuelEntries, stationSuggestionsByVehicleId)
        val categories = normalizedCategories(
            root.optionalList("maintenanceCategories").orEmpty().map { value ->
                val item = value.asObject("maintenance category")
                MaintenanceCategory(
                    id = item.requiredString("id"),
                    name = item.requiredString("name"),
                    sortOrder = item.optionalNumber("sortOrder")?.toInt() ?: 0,
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
                intervalHours = item.optionalNumber("intervalHours"),
                intervalTimeDays = item.optionalNumber("intervalTimeDays")?.toInt(),
                notes = item.optionalString("notes").orEmpty(),
                importance = MaintenanceImportance.fromStorage(
                    item.optionalString("importance") ?: MaintenanceImportance.MEDIUM.storageValue,
                ),
                active = item.optionalBoolean("active") ?: true,
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
                hours = item.optionalNumber("hours"),
                cost = item.requiredNumber("cost"),
                notes = item.optionalString("notes").orEmpty(),
                receiptPath = item.optionalString("receiptPath"),
                clientGeneratedId = item.optionalString("clientGeneratedId") ?: item.requiredString("id"),
            )
        }
        val synthesizedLogs = synthesizeLegacyBaselineLogs(decodedLogs, legacyBaselines)

        val data = FuelMathData(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            vehicles = vehicles,
            fuelEntries = fuelEntries,
            meterLogs = parseMeterLogs(root),
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
        }.withBackfilledStationSuggestions(fuelEntries, emptyMap())

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
            val dateTime = LocalDateTime.parse(item.requiredString("dateTime"))
            val createdAt = item.optionalString("createdAt")?.let(LocalDateTime::parse) ?: dateTime
            FuelEntry(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                dateTime = dateTime,
                odometer = item.requiredNumber("odometer"),
                hours = item.optionalNumber("hours"),
                fuelAmount = item.requiredNumber("fuelAmount"),
                pricePerUnit = item.requiredNumber("pricePerUnit"),
                isFullTank = item.requiredBoolean("isFullTank"),
                entryType = EnergyEntryType.fromStorage(item.optionalString("entryType") ?: EnergyEntryType.FUEL.storageValue),
                station = item.optionalString("station").orEmpty(),
                oilMixRatio = item.optionalString("oilMixRatio").orEmpty(),
                notes = item.optionalString("notes").orEmpty(),
                chargePercentBefore = item.optionalNumber("chargePercentBefore"),
                chargePercentAfter = item.optionalNumber("chargePercentAfter"),
                clientGeneratedId = item.optionalString("clientGeneratedId") ?: item.requiredString("id"),
                createdAt = createdAt,
                updatedAt = item.optionalString("updatedAt")?.let(LocalDateTime::parse) ?: createdAt,
            )
        }

    private fun parseMeterLogs(root: Map<String, Any?>): List<MeterLog> =
        root.optionalList("meterLogs").orEmpty().map { value ->
            val item = value.asObject("meter log")
            val dateTime = LocalDateTime.parse(item.requiredString("dateTime"))
            val createdAt = item.optionalString("createdAt")?.let(LocalDateTime::parse) ?: dateTime
            MeterLog(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                dateTime = dateTime,
                mileage = item.optionalNumber("mileage"),
                hours = item.optionalNumber("hours"),
                source = MeterLogSource.fromStorage(item.optionalString("source") ?: MeterLogSource.MANUAL.storageValue),
                notes = item.optionalString("notes").orEmpty(),
                clientGeneratedId = item.optionalString("clientGeneratedId") ?: item.requiredString("id"),
                createdAt = createdAt,
                updatedAt = item.optionalString("updatedAt")?.let(LocalDateTime::parse) ?: createdAt,
            )
        }

    private fun parseStationSuggestions(item: Map<String, Any?>): List<String>? =
        item.optionalList("stationSuggestions")?.let { values ->
            normalizedStationSuggestions(values.mapNotNull { it as? String })
        }

    private fun List<Vehicle>.withBackfilledStationSuggestions(
        fuelEntries: List<FuelEntry>,
        stationSuggestionsByVehicleId: Map<String, List<String>?>,
    ): List<Vehicle> =
        map { vehicle ->
            val explicitSuggestions = stationSuggestionsByVehicleId[vehicle.id]
            if (explicitSuggestions != null) {
                return@map vehicle.copy(stationSuggestions = normalizedStationSuggestions(explicitSuggestions))
            }
            val historicalStations = fuelEntries
                .asSequence()
                .filter { it.vehicleId == vehicle.id }
                .map { it.station }
                .toList()
            vehicle.copy(
                stationSuggestions = normalizedStationSuggestions(vehicle.stationSuggestions + historicalStations),
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
        require(data.vehicles.all { it.assetType.category == it.assetCategory }) {
            "Vehicle asset types must match their asset categories"
        }
        require(data.vehicles.all { vehicle ->
            (!vehicle.fuelType.usesTankCapacity || vehicle.tankCapacity.isFinite() && vehicle.tankCapacity >= 0.0) &&
                (vehicle.fuelType.usesTankCapacity || vehicle.tankCapacity.isFinite() && vehicle.tankCapacity >= 0.0) &&
                (!vehicle.fuelType.usesBatteryCapacity || vehicle.batteryCapacity == null || vehicle.batteryCapacity.isFinite() && vehicle.batteryCapacity > 0.0) &&
                (vehicle.fuelType.usesBatteryCapacity || vehicle.batteryCapacity == null || vehicle.batteryCapacity.isFinite() && vehicle.batteryCapacity >= 0.0)
        }) {
            "Vehicle capacities do not match their fuel or energy types"
        }
        require(data.vehicles.all { it.currentMileage.isFinite() && it.currentMileage >= 0.0 }) {
            "Vehicle current mileage values cannot be negative"
        }
        require(data.vehicles.all { it.currentHours == null || it.currentHours.isFinite() && it.currentHours >= 0.0 }) {
            "Vehicle current hour values cannot be negative"
        }
        require(
            data.vehicles.all {
                (it.recommendedFrontTirePsi == null || it.recommendedFrontTirePsi.isFinite() && it.recommendedFrontTirePsi > 0.0) &&
                    (it.recommendedRearTirePsi == null || it.recommendedRearTirePsi.isFinite() && it.recommendedRearTirePsi > 0.0)
            },
        ) {
            "Vehicle tire PSI reminders must be positive when present"
        }
        require(data.vehicles.all { it.stationSuggestions == normalizedStationSuggestions(it.stationSuggestions) }) {
            "Vehicle station suggestions must be unique and non-blank"
        }
        require(data.maintenanceCategories.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Maintenance category id and name are required"
        }
        require(data.fuelEntries.all { it.vehicleId in vehicleIds }) {
            "Backup contains a fuel entry for an unknown vehicle"
        }
        require(data.fuelEntries.all { entry ->
            entry.id.isNotBlank() &&
                entry.clientGeneratedId.isNotBlank() &&
                entry.odometer.isFinite() &&
                entry.odometer >= 0.0 &&
                (entry.hours == null || entry.hours.isFinite() && entry.hours >= 0.0) &&
                entry.fuelAmount.isFinite() &&
                entry.fuelAmount > 0.0 &&
                entry.pricePerUnit.isFinite() &&
                entry.pricePerUnit >= 0.0 &&
                (entry.chargePercentBefore == null || entry.chargePercentBefore.isFinite() && entry.chargePercentBefore in 0.0..100.0) &&
                (entry.chargePercentAfter == null || entry.chargePercentAfter.isFinite() && entry.chargePercentAfter in 0.0..100.0)
        }) {
            "Fuel entries contain invalid numeric values"
        }
        require(data.meterLogs.all { it.vehicleId in vehicleIds }) {
            "Backup contains a meter log for an unknown vehicle"
        }
        require(data.meterLogs.all { log ->
            log.id.isNotBlank() &&
                log.clientGeneratedId.isNotBlank() &&
                (log.mileage == null || log.mileage.isFinite() && log.mileage >= 0.0) &&
                (log.hours == null || log.hours.isFinite() && log.hours >= 0.0) &&
                (log.mileage != null || log.hours != null)
        }) {
            "Meter logs contain invalid numeric values"
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
                    (item.intervalHours == null || item.intervalHours.isFinite() && item.intervalHours > 0.0) &&
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
        require(data.maintenanceServiceLogs.all {
            it.id.isNotBlank() &&
                it.clientGeneratedId.isNotBlank() &&
                it.odometer.isFinite() &&
                it.odometer >= 0.0 &&
                (it.hours == null || it.hours.isFinite() && it.hours >= 0.0) &&
                it.cost.isFinite() &&
                it.cost >= 0.0
        }) {
            "Maintenance service logs contain invalid numeric values"
        }
    }

    private fun normalizedCategories(categories: List<MaintenanceCategory>): List<MaintenanceCategory> {
        val byId = LinkedHashMap<String, MaintenanceCategory>()
        categories.forEach { category -> byId[category.id] = category }
        MaintenanceDefaults.categories.forEach { category -> byId.putIfAbsent(category.id, category) }
        return byId.values.toList()
    }

    private fun normalizedStationSuggestions(stations: List<String>): List<String> {
        val byName = LinkedHashMap<String, String>()
        stations.forEach { station ->
            val trimmed = station.trim()
            if (trimmed.isNotBlank()) byName.putIfAbsent(trimmed.lowercase(), trimmed)
        }
        return byName.values.toList()
    }

    private fun parseFuelType(item: Map<String, Any?>): FuelType {
        val fuelType = FuelType.fromStorage(item.optionalString("fuelType") ?: FuelType.GASOLINE.storageValue)
        return when (item.optionalString("vehicleType")?.let(VehicleType::fromStorage)) {
            VehicleType.PLUG_IN_HYBRID -> when (fuelType) {
                FuelType.DIESEL,
                FuelType.HYBRID_DIESEL,
                FuelType.PLUG_IN_HYBRID_DIESEL,
                -> FuelType.PLUG_IN_HYBRID_DIESEL
                else -> FuelType.PLUG_IN_HYBRID_GASOLINE
            }
            VehicleType.HYBRID -> when (fuelType) {
                FuelType.DIESEL,
                FuelType.HYBRID_DIESEL,
                -> FuelType.HYBRID_DIESEL
                else -> FuelType.HYBRID_GASOLINE
            }
            VehicleType.EV -> FuelType.ELECTRIC
            VehicleType.DIESEL -> FuelType.DIESEL
            VehicleType.GASOLINE,
            VehicleType.MOTORCYCLE,
            -> fuelType
            VehicleType.OTHER,
            null,
            -> fuelType
        }
    }

    private fun parseVehicleType(
        item: Map<String, Any?>,
        fuelType: FuelType,
        assetCategory: AssetCategory,
        assetType: AssetType,
    ): VehicleType =
        item.optionalString("vehicleType")?.let(VehicleType::fromStorage)
            ?: VehicleType.fromAsset(assetCategory, assetType, fuelType)

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
