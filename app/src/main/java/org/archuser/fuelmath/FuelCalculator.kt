package org.archuser.fuelmath

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.roundToInt

object FuelCalculator {
    private val dateLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

    fun entriesForVehicle(entries: List<FuelEntry>, vehicleId: String): List<FuelEntry> =
        entries.filter { it.vehicleId == vehicleId }

    fun maintenanceItemsForVehicle(items: List<MaintenanceItem>, vehicleId: String): List<MaintenanceItem> =
        items.filter { it.vehicleId == vehicleId }

    fun serviceLogsForVehicle(logs: List<MaintenanceServiceLog>, vehicleId: String): List<MaintenanceServiceLog> =
        logs.filter { it.vehicleId == vehicleId }

    fun buildVehicleSummary(
        vehicle: Vehicle,
        data: FuelMathData,
        asOfDate: LocalDate = LocalDate.now(),
    ): VehicleSummary {
        val vehicleEnergyEntries = entriesForVehicle(data.fuelEntries, vehicle.id)
        val vehicleFuelEntries = vehicleEnergyEntries.filter { it.entryType == EnergyEntryType.FUEL }
        val vehicleChargingEntries = vehicleEnergyEntries.filter { it.entryType == EnergyEntryType.CHARGING }
        val vehicleServiceLogs = serviceLogsForVehicle(data.maintenanceServiceLogs, vehicle.id)
        val vehicleMaintenanceItems = maintenanceItemsForVehicle(data.maintenanceItems, vehicle.id)
        val totalFuelCost = if (vehicle.vehicleType.usesLiquidFuel) vehicleFuelEntries.sumEnergyCostSafe { it.totalCost } else 0.0
        val totalChargingCost = if (vehicle.vehicleType.usesBattery) vehicleChargingEntries.sumEnergyCostSafe { it.totalCost } else 0.0
        val totalMaintenanceCost = vehicleServiceLogs.sumMaintenanceCostSafe { it.cost }
        val totalCost = totalFuelCost + totalChargingCost + totalMaintenanceCost
        val totalDistance = calculateTotalDistance(vehicleEnergyEntries)
        val costPerDistance = totalDistance?.takeIf { it > 0.0 }?.let { totalCost / it }
        val lastEfficiency = calculateEfficiencySegments(vehicle, vehicleEnergyEntries).lastOrNull()?.value
        val lastFillUpDate = vehicleEnergyEntries.maxByOrNull { it.dateTime }?.dateTime
        val estimatedRange = estimateRemainingRange(vehicle, lastEfficiency)
        val lastOdometer = vehicleEnergyEntries.maxByOrNull { it.odometer }?.odometer
        val currentMileage = currentMileageForVehicle(vehicle, vehicleEnergyEntries, vehicleServiceLogs)
        val states = calculateMaintenanceStates(vehicle, data, asOfDate)

        return VehicleSummary(
            vehicle = vehicle,
            totalFuelCost = totalFuelCost,
            totalChargingCost = totalChargingCost,
            totalMaintenanceCost = totalMaintenanceCost,
            totalCost = totalCost,
            totalDistance = totalDistance,
            costPerDistance = costPerDistance,
            lastEfficiency = lastEfficiency,
            lastFillUpDate = lastFillUpDate,
            estimatedRange = estimatedRange,
            lastOdometer = lastOdometer,
            currentMileage = currentMileage,
            healthScore = calculateHealthScore(states),
            overdueCount = states.count { it.status == MaintenanceStatus.OVERDUE },
            dueSoonCount = states.count { it.status == MaintenanceStatus.DUE_SOON },
            unknownCount = states.count { it.status == MaintenanceStatus.UNKNOWN },
            smartRecommendation = chooseSmartRecommendation(states),
            fuelEntryCount = vehicleFuelEntries.size,
            chargingEntryCount = vehicleChargingEntries.size,
            maintenanceItemCount = vehicleMaintenanceItems.size,
            maintenanceServiceLogCount = vehicleServiceLogs.size,
        )
    }

    fun calculateMaintenanceStates(
        vehicle: Vehicle,
        data: FuelMathData,
        asOfDate: LocalDate = LocalDate.now(),
    ): List<MaintenanceItemState> =
        calculateMaintenanceStates(
            vehicle = vehicle,
            categories = data.maintenanceCategories,
            items = data.maintenanceItems,
            serviceLogs = data.maintenanceServiceLogs,
            fuelEntries = data.fuelEntries,
            thresholdPercent = data.settings.dueSoonThresholdPercent,
            asOfDate = asOfDate,
        )

    fun calculateMaintenanceStates(
        vehicle: Vehicle,
        categories: List<MaintenanceCategory>,
        items: List<MaintenanceItem>,
        serviceLogs: List<MaintenanceServiceLog>,
        fuelEntries: List<FuelEntry>,
        thresholdPercent: Int,
        asOfDate: LocalDate = LocalDate.now(),
    ): List<MaintenanceItemState> {
        val categoriesById = (MaintenanceDefaults.categories + categories).associateBy { it.id }
        val vehicleItems = maintenanceItemsForVehicle(items, vehicle.id)
        val vehicleServiceLogs = serviceLogsForVehicle(serviceLogs, vehicle.id)
        val logsByItem = vehicleServiceLogs.groupBy { it.maintenanceItemId }
        val currentMileage = currentMileageForVehicle(vehicle, entriesForVehicle(fuelEntries, vehicle.id), vehicleServiceLogs)
        val dueSoonRatio = thresholdPercent.coerceIn(0, 100) / 100.0

        return vehicleItems.map { item ->
            val itemLogs = logsByItem[item.id].orEmpty()
            val lastLog = itemLogs.maxWithOrNull(compareBy<MaintenanceServiceLog> { it.dateTime }.thenBy { it.odometer })
            val lastMileage = lastLog?.odometer?.takeIf { it.isFinite() && it >= 0.0 }
            val lastDate = lastLog?.dateTime?.toLocalDate()
            val nextMileage = item.intervalMiles
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { interval -> lastMileage?.plus(interval) }
            val nextDate = item.intervalTimeDays
                ?.takeIf { it > 0 }
                ?.let { days -> lastDate?.plusDays(days.toLong()) }
            val milesRemaining = nextMileage?.let { it - currentMileage }
            val daysRemaining = nextDate?.let { ChronoUnit.DAYS.between(asOfDate, it) }
            val hasMileageInterval = item.intervalMiles?.let { it.isFinite() && it > 0.0 } == true
            val hasTimeInterval = item.intervalTimeDays?.let { it > 0 } == true
            val needsBaseline = (hasMileageInterval && lastMileage == null) || (hasTimeInterval && lastDate == null)

            val mileageRatio = item.intervalMiles
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { interval -> milesRemaining?.div(interval) }
            val timeRatio = item.intervalTimeDays
                ?.takeIf { it > 0 }
                ?.let { interval -> daysRemaining?.toDouble()?.div(interval.toDouble()) }
            val lowestRatio = listOfNotNull(mileageRatio, timeRatio).minOrNull()

            val status = when {
                needsBaseline -> MaintenanceStatus.UNKNOWN
                milesRemaining?.let { it < 0.0 } == true -> MaintenanceStatus.OVERDUE
                daysRemaining?.let { it < 0L } == true -> MaintenanceStatus.OVERDUE
                lowestRatio?.let { it <= dueSoonRatio } == true -> MaintenanceStatus.DUE_SOON
                else -> MaintenanceStatus.GOOD
            }

            MaintenanceItemState(
                item = item,
                category = categoriesById[item.categoryId] ?: MaintenanceDefaults.categories.first(),
                status = status,
                lastServiceLog = lastLog,
                nextDueMileage = nextMileage,
                nextDueDate = nextDate,
                milesRemaining = milesRemaining,
                daysRemaining = daysRemaining,
                urgencySortValue = when {
                    needsBaseline -> -1.0
                    lowestRatio != null -> lowestRatio
                    else -> Double.MAX_VALUE
                },
            )
        }.sortedWith(
            compareBy<MaintenanceItemState> { statusSortRank(it.status) }
                .thenBy { it.urgencySortValue }
                .thenBy { it.item.name.lowercase() },
        )
    }

    fun calculateHealthScore(states: List<MaintenanceItemState>): Int {
        if (states.isEmpty()) return 100
        val totalWeight = states.sumOf { it.item.importance.scoreWeight }
        if (totalWeight <= 0.0) return 100
        val score = states.sumOf { state ->
            val statusScore = when (state.status) {
                MaintenanceStatus.UNKNOWN -> 0.3
                MaintenanceStatus.GOOD -> 1.0
                MaintenanceStatus.DUE_SOON -> 0.6
                MaintenanceStatus.OVERDUE -> 0.0
            }
            state.item.importance.scoreWeight * statusScore
        }
        return ((score / totalWeight) * 100.0).roundToInt().coerceIn(0, 100)
    }

    fun chooseSmartRecommendation(states: List<MaintenanceItemState>): MaintenanceItemState? =
        states.minWithOrNull(
            compareBy<MaintenanceItemState> { recommendationRank(it) }
                .thenBy { recommendationProgressValue(it) }
                .thenBy { it.item.name.lowercase() },
        )

    fun buildReminderSnapshot(
        data: FuelMathData,
        asOfDate: LocalDate = LocalDate.now(),
    ): MaintenanceReminderSnapshot? {
        val allStates = data.vehicles.flatMap { calculateMaintenanceStates(it, data, asOfDate) }
        val overdue = allStates.count { it.status == MaintenanceStatus.OVERDUE }
        val dueSoon = allStates.count { it.status == MaintenanceStatus.DUE_SOON }
        if (overdue == 0 && dueSoon == 0) return null

        val first = allStates.firstOrNull {
            it.status == MaintenanceStatus.OVERDUE || it.status == MaintenanceStatus.DUE_SOON
        }
        val title = when {
            overdue > 0 -> "$overdue maintenance item${plural(overdue)} overdue"
            else -> "$dueSoon maintenance item${plural(dueSoon)} due soon"
        }
        val message = first?.let { "${it.item.name}: ${it.status.displayLabel}" }
            ?: "Open Fuel Math to review maintenance."
        return MaintenanceReminderSnapshot(
            title = title,
            message = message,
            overdueCount = overdue,
            dueSoonCount = dueSoon,
        )
    }

    fun currentMileageForVehicle(
        vehicle: Vehicle,
        fuelEntries: List<FuelEntry>,
        serviceLogs: List<MaintenanceServiceLog>,
    ): Double {
        val latestFuel = fuelEntries.maxOfOrNull { it.odometer.takeIf(Double::isFinite) ?: 0.0 } ?: 0.0
        val latestService = serviceLogs.maxOfOrNull { it.odometer.takeIf(Double::isFinite) ?: 0.0 } ?: 0.0
        return max(vehicle.currentMileage.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0, max(latestFuel, latestService))
    }

    fun calculateEfficiencySegments(
        vehicle: Vehicle,
        fuelEntries: List<FuelEntry>,
    ): List<EfficiencySegment> {
        val entryType = preferredEfficiencyEntryType(vehicle)
        val sorted = sortedByOdometer(fuelEntries.filter { it.entryType == entryType })
        val segments = mutableListOf<EfficiencySegment>()
        var anchor: FuelEntry? = null
        var fuelSinceAnchor = 0.0

        for (entry in sorted) {
            if (!entry.hasValidFuelInputs()) continue

            if (entry.isFullTank) {
                val start = anchor
                if (start == null) {
                    anchor = entry
                    fuelSinceAnchor = 0.0
                } else {
                    fuelSinceAnchor += entry.fuelAmount
                    val distance = entry.odometer - start.odometer
                    if (distance > 0.0 && fuelSinceAnchor > 0.0) {
                        segments += EfficiencySegment(
                            startEntryId = start.id,
                            endEntryId = entry.id,
                            dateTime = entry.dateTime,
                            distance = distance,
                            fuelUsed = fuelSinceAnchor,
                            value = calculateEfficiencyValue(vehicle, distance, fuelSinceAnchor),
                        )
                    }
                    anchor = entry
                    fuelSinceAnchor = 0.0
                }
            } else if (anchor != null) {
                fuelSinceAnchor += entry.fuelAmount
            }
        }

        return segments
    }

    fun calculateEntryDistances(fuelEntries: List<FuelEntry>): List<EntryDistance> {
        val sorted = sortedByOdometer(fuelEntries)
        return sorted.mapIndexed { index, entry ->
            val distance = sorted.getOrNull(index - 1)?.let { previous ->
                max(0.0, entry.odometer - previous.odometer)
            }
            EntryDistance(entry, distance)
        }
    }

    fun buildEfficiencyChart(vehicle: Vehicle, fuelEntries: List<FuelEntry>): List<ChartPoint> =
        calculateEfficiencySegments(vehicle, fuelEntries).mapIndexed { index, segment ->
            ChartPoint(
                label = segment.dateTime.format(dateLabelFormatter).ifBlank { "S${index + 1}" },
                dateTime = segment.dateTime,
                value = segment.value,
            )
        }

    fun buildFuelPriceTrend(fuelEntries: List<FuelEntry>): List<ChartPoint> =
        fuelEntries
            .filter { it.pricePerUnit.isFinite() && it.pricePerUnit >= 0.0 }
            .sortedWith(compareBy<FuelEntry> { it.dateTime }.thenBy { it.odometer })
            .map {
                ChartPoint(
                    label = it.dateTime.format(dateLabelFormatter),
                    dateTime = it.dateTime,
                    value = it.pricePerUnit,
                )
            }

    fun buildCostOverTime(fuelEntries: List<FuelEntry>): List<ChartPoint> {
        var runningTotal = 0.0
        return fuelEntries
            .filter { it.hasValidFuelInputs() }
            .sortedWith(compareBy<FuelEntry> { it.dateTime }.thenBy { it.odometer })
            .map {
                runningTotal += it.totalCost
                ChartPoint(
                    label = it.dateTime.format(dateLabelFormatter),
                    dateTime = it.dateTime,
                    value = runningTotal,
                )
            }
    }

    fun buildMaintenanceCostOverTime(serviceLogs: List<MaintenanceServiceLog>): List<ChartPoint> {
        var runningTotal = 0.0
        return serviceLogs
            .filter { it.cost.isFinite() && it.cost >= 0.0 }
            .sortedWith(compareBy<MaintenanceServiceLog> { it.dateTime }.thenBy { it.odometer })
            .map {
                runningTotal += it.cost
                ChartPoint(
                    label = it.dateTime.format(dateLabelFormatter),
                    dateTime = it.dateTime,
                    value = runningTotal,
                )
            }
    }

    fun buildTotalCostOverTime(
        fuelEntries: List<FuelEntry>,
        serviceLogs: List<MaintenanceServiceLog>,
    ): List<ChartPoint> {
        data class CostEvent(val dateTime: LocalDateTime, val odometer: Double, val cost: Double)

        var runningTotal = 0.0
        return (
            fuelEntries
                .filter { it.hasValidFuelInputs() }
                .map { CostEvent(it.dateTime, it.odometer, it.totalCost) } +
                serviceLogs
                    .filter { it.cost.isFinite() && it.cost >= 0.0 }
                    .map { CostEvent(it.dateTime, it.odometer, it.cost) }
            )
            .sortedWith(compareBy<CostEvent> { it.dateTime }.thenBy { it.odometer })
            .map {
                runningTotal += it.cost
                ChartPoint(
                    label = it.dateTime.format(dateLabelFormatter),
                    dateTime = it.dateTime,
                    value = runningTotal,
                )
            }
    }

    fun buildSeasonalEfficiencyComparison(
        vehicle: Vehicle,
        fuelEntries: List<FuelEntry>,
        asOfDate: LocalDate = LocalDate.now(),
    ): SeasonalEfficiencyComparison {
        val segments = calculateEfficiencySegments(vehicle, fuelEntries)
        val currentSeason = seasonLabel(asOfDate.month)
        val seasonValues = segments.filter { seasonLabel(it.dateTime.month) == currentSeason }.map { it.value }
        val allValues = segments.map { it.value }
        return SeasonalEfficiencyComparison(
            currentSeason = currentSeason,
            currentSeasonAverage = seasonValues.averageOrNull(),
            overallAverage = allValues.averageOrNull(),
        )
    }

    fun buildExportCsv(data: FuelMathData): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(
            "record_type",
            "vehicle_id",
            "vehicle_name",
            "make",
            "model",
            "year",
            "current_mileage",
            "fuel_type",
            "vehicle_type",
            "distance_unit",
            "volume_unit",
            "energy_unit",
            "tank_capacity",
            "battery_capacity",
            "entry_id",
            "date_time",
            "odometer",
            "fuel_amount",
            "price_per_unit",
            "total_cost",
            "is_full_tank",
            "category_id",
            "category_name",
            "maintenance_item_id",
            "maintenance_item_name",
            "interval_miles",
            "interval_time_days",
            "maintenance_cost",
            "notes",
        )

        data.vehicles.sortedBy { it.name.lowercase() }.forEach { vehicle ->
            rows += listOf(
                "vehicle",
                vehicle.id,
                vehicle.name,
                vehicle.make,
                vehicle.model,
                vehicle.year?.toString().orEmpty(),
                vehicle.currentMileage.toStorageString(),
                vehicle.fuelType.storageValue,
                vehicle.vehicleType.storageValue,
                vehicle.distanceUnit.storageValue,
                vehicle.volumeUnit.storageValue,
                vehicle.energyUnit.storageValue,
                vehicle.tankCapacity.toStorageString(),
                vehicle.batteryCapacity?.toStorageString().orEmpty(),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
            )
        }

        data.fuelEntries
            .sortedWith(compareBy<FuelEntry> { it.vehicleId }.thenBy { it.dateTime }.thenBy { it.odometer })
            .forEach { entry ->
                val vehicle = data.vehicles.firstOrNull { it.id == entry.vehicleId }
                rows += listOf(
                    "fuel_entry",
                    entry.vehicleId,
                    vehicle?.name.orEmpty(),
                    vehicle?.make.orEmpty(),
                    vehicle?.model.orEmpty(),
                    vehicle?.year?.toString().orEmpty(),
                    vehicle?.currentMileage?.toStorageString().orEmpty(),
                    vehicle?.fuelType?.storageValue.orEmpty(),
                    vehicle?.vehicleType?.storageValue.orEmpty(),
                    vehicle?.distanceUnit?.storageValue.orEmpty(),
                    vehicle?.volumeUnit?.storageValue.orEmpty(),
                    vehicle?.energyUnit?.storageValue.orEmpty(),
                    vehicle?.tankCapacity?.toStorageString().orEmpty(),
                    vehicle?.batteryCapacity?.toStorageString().orEmpty(),
                    entry.id,
                    entry.dateTime.toString(),
                    entry.odometer.toStorageString(),
                    entry.fuelAmount.toStorageString(),
                    entry.pricePerUnit.toStorageString(),
                    entry.totalCost.toStorageString(),
                    entry.isFullTank.toString(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                )
            }

        data.maintenanceItems
            .sortedWith(compareBy<MaintenanceItem> { it.vehicleId }.thenBy { it.name.lowercase() })
            .forEach { item ->
                val vehicle = data.vehicles.firstOrNull { it.id == item.vehicleId }
                val category = data.maintenanceCategories.firstOrNull { it.id == item.categoryId }
                rows += listOf(
                    "maintenance_item",
                    item.vehicleId,
                    vehicle?.name.orEmpty(),
                    vehicle?.make.orEmpty(),
                    vehicle?.model.orEmpty(),
                    vehicle?.year?.toString().orEmpty(),
                    vehicle?.currentMileage?.toStorageString().orEmpty(),
                    vehicle?.fuelType?.storageValue.orEmpty(),
                    vehicle?.vehicleType?.storageValue.orEmpty(),
                    vehicle?.distanceUnit?.storageValue.orEmpty(),
                    vehicle?.volumeUnit?.storageValue.orEmpty(),
                    vehicle?.energyUnit?.storageValue.orEmpty(),
                    vehicle?.tankCapacity?.toStorageString().orEmpty(),
                    vehicle?.batteryCapacity?.toStorageString().orEmpty(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    item.categoryId,
                    category?.name.orEmpty(),
                    item.id,
                    item.name,
                    item.intervalMiles?.toStorageString().orEmpty(),
                    item.intervalTimeDays?.toString().orEmpty(),
                    "",
                    item.notes,
                )
            }

        data.maintenanceServiceLogs
            .sortedWith(compareBy<MaintenanceServiceLog> { it.vehicleId }.thenBy { it.dateTime }.thenBy { it.odometer })
            .forEach { log ->
                val vehicle = data.vehicles.firstOrNull { it.id == log.vehicleId }
                val item = data.maintenanceItems.firstOrNull { it.id == log.maintenanceItemId }
                val category = data.maintenanceCategories.firstOrNull { it.id == item?.categoryId }
                rows += listOf(
                    "maintenance_service_log",
                    log.vehicleId,
                    vehicle?.name.orEmpty(),
                    vehicle?.make.orEmpty(),
                    vehicle?.model.orEmpty(),
                    vehicle?.year?.toString().orEmpty(),
                    vehicle?.currentMileage?.toStorageString().orEmpty(),
                    vehicle?.fuelType?.storageValue.orEmpty(),
                    vehicle?.vehicleType?.storageValue.orEmpty(),
                    vehicle?.distanceUnit?.storageValue.orEmpty(),
                    vehicle?.volumeUnit?.storageValue.orEmpty(),
                    vehicle?.energyUnit?.storageValue.orEmpty(),
                    vehicle?.tankCapacity?.toStorageString().orEmpty(),
                    vehicle?.batteryCapacity?.toStorageString().orEmpty(),
                    log.id,
                    log.dateTime.toString(),
                    log.odometer.toStorageString(),
                    "",
                    "",
                    "",
                    "",
                    item?.categoryId.orEmpty(),
                    category?.name.orEmpty(),
                    log.maintenanceItemId,
                    item?.name.orEmpty(),
                    item?.intervalMiles?.toStorageString().orEmpty(),
                    item?.intervalTimeDays?.toString().orEmpty(),
                    log.cost.toStorageString(),
                    log.notes,
                )
            }

        return rows.joinToString(separator = "\n", postfix = "\n") { row ->
            row.joinToString(",") { it.toCsvCell() }
        }
    }

    fun efficiencyUnitLabel(vehicle: Vehicle): String =
        when {
            preferredEfficiencyEntryType(vehicle) == EnergyEntryType.CHARGING -> "${vehicle.distanceUnit.displayLabel}/${vehicle.energyUnit.displayLabel}"
            vehicle.distanceUnit == DistanceUnit.MILES && vehicle.volumeUnit == VolumeUnit.GALLONS -> "MPG"
            vehicle.distanceUnit == DistanceUnit.KILOMETERS && vehicle.volumeUnit == VolumeUnit.LITERS -> "L/100km"
            else -> "${vehicle.distanceUnit.displayLabel}/${vehicle.volumeUnit.displayLabel}"
        }

    fun distanceUnitLabel(vehicle: Vehicle): String = vehicle.distanceUnit.displayLabel

    fun volumeUnitLabel(vehicle: Vehicle): String = vehicle.volumeUnit.displayLabel

    fun entryUnitLabel(vehicle: Vehicle, entryType: EnergyEntryType): String =
        when (entryType) {
            EnergyEntryType.FUEL -> vehicle.volumeUnit.displayLabel
            EnergyEntryType.CHARGING -> vehicle.energyUnit.displayLabel
        }

    fun energyLogLabel(vehicle: Vehicle): String =
        when {
            vehicle.vehicleType.usesLiquidFuel && vehicle.vehicleType.usesBattery -> "Energy Log"
            vehicle.vehicleType.usesBattery -> "Charging Log"
            else -> "Fuel Log"
        }

    fun preferredEfficiencyEntryType(vehicle: Vehicle): EnergyEntryType =
        if (vehicle.vehicleType.usesBattery && !vehicle.vehicleType.usesLiquidFuel) {
            EnergyEntryType.CHARGING
        } else {
            EnergyEntryType.FUEL
        }

    private fun statusSortRank(status: MaintenanceStatus): Int =
        when (status) {
            MaintenanceStatus.OVERDUE -> 0
            MaintenanceStatus.DUE_SOON -> 1
            MaintenanceStatus.UNKNOWN -> 2
            MaintenanceStatus.GOOD -> 3
        }

    private fun recommendationRank(state: MaintenanceItemState): Int =
        when (state.status) {
            MaintenanceStatus.OVERDUE -> when (state.item.importance) {
                MaintenanceImportance.CRITICAL -> 0
                MaintenanceImportance.HIGH -> 1
                else -> 2
            }
            MaintenanceStatus.DUE_SOON -> when (state.item.importance) {
                MaintenanceImportance.CRITICAL -> 3
                MaintenanceImportance.HIGH -> 4
                else -> 5
            }
            MaintenanceStatus.UNKNOWN -> 6
            MaintenanceStatus.GOOD -> 7
        }

    private fun recommendationProgressValue(state: MaintenanceItemState): Double =
        when (state.status) {
            MaintenanceStatus.OVERDUE,
            MaintenanceStatus.DUE_SOON,
            -> state.urgencySortValue
            MaintenanceStatus.UNKNOWN -> Double.NEGATIVE_INFINITY
            MaintenanceStatus.GOOD -> state.urgencySortValue
        }

    private fun calculateTotalDistance(entries: List<FuelEntry>): Double? {
        val sorted = sortedByOdometer(entries)
        if (sorted.size < 2) return null
        val distance = sorted.last().odometer - sorted.first().odometer
        return distance.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun calculateEfficiencyValue(vehicle: Vehicle, distance: Double, fuelUsed: Double): Double =
        if (preferredEfficiencyEntryType(vehicle) == EnergyEntryType.CHARGING) {
            distance / fuelUsed
        } else if (vehicle.distanceUnit == DistanceUnit.KILOMETERS && vehicle.volumeUnit == VolumeUnit.LITERS) {
            (fuelUsed / distance) * 100.0
        } else {
            distance / fuelUsed
        }

    private fun estimateRemainingRange(vehicle: Vehicle, lastEfficiency: Double?): Double? {
        val efficiency = lastEfficiency?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (preferredEfficiencyEntryType(vehicle) == EnergyEntryType.CHARGING) {
            val batteryCapacity = vehicle.batteryCapacity?.takeIf { it.isFinite() && it > 0.0 } ?: return null
            return batteryCapacity * efficiency
        }
        if (!vehicle.tankCapacity.isFinite() || vehicle.tankCapacity <= 0.0) return null
        return if (vehicle.distanceUnit == DistanceUnit.KILOMETERS && vehicle.volumeUnit == VolumeUnit.LITERS) {
            (vehicle.tankCapacity / efficiency) * 100.0
        } else {
            vehicle.tankCapacity * efficiency
        }
    }

    private fun sortedByOdometer(entries: List<FuelEntry>): List<FuelEntry> =
        entries
            .filter { it.odometer.isFinite() && it.odometer >= 0.0 }
            .sortedWith(compareBy<FuelEntry> { it.odometer }.thenBy { it.dateTime }.thenBy { it.id })

    private fun FuelEntry.hasValidFuelInputs(): Boolean =
        odometer.isFinite() &&
            odometer >= 0.0 &&
            fuelAmount.isFinite() &&
            fuelAmount > 0.0 &&
            pricePerUnit.isFinite() &&
            pricePerUnit >= 0.0

    private fun Iterable<FuelEntry>.sumEnergyCostSafe(selector: (FuelEntry) -> Double): Double =
        fold(0.0) { total, entry ->
            val value = selector(entry)
            if (value.isFinite() && value >= 0.0) total + value else total
        }

    private fun Iterable<MaintenanceServiceLog>.sumMaintenanceCostSafe(selector: (MaintenanceServiceLog) -> Double): Double =
        fold(0.0) { total, entry ->
            val value = selector(entry)
            if (value.isFinite() && value >= 0.0) total + value else total
        }

    private fun List<Double>.averageOrNull(): Double? =
        takeIf { it.isNotEmpty() }?.average()

    private fun seasonLabel(month: Month): String =
        when (month) {
            Month.DECEMBER, Month.JANUARY, Month.FEBRUARY -> "Winter"
            Month.MARCH, Month.APRIL, Month.MAY -> "Spring"
            Month.JUNE, Month.JULY, Month.AUGUST -> "Summer"
            Month.SEPTEMBER, Month.OCTOBER, Month.NOVEMBER -> "Fall"
        }

    private fun plural(count: Int): String = if (count == 1) "" else "s"

    private fun Double.toStorageString(): String =
        if (this % 1.0 == 0.0) toLong().toString() else toString()

    private fun String.toCsvCell(): String {
        val escaped = replace("\"", "\"\"")
        return if (any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }
}
