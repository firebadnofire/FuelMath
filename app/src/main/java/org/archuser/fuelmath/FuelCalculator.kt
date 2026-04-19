package org.archuser.fuelmath

import java.time.format.DateTimeFormatter
import kotlin.math.max

object FuelCalculator {
    private val dateLabelFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d")

    fun entriesForVehicle(entries: List<FuelEntry>, vehicleId: String): List<FuelEntry> =
        entries.filter { it.vehicleId == vehicleId }

    fun maintenanceForVehicle(entries: List<MaintenanceEntry>, vehicleId: String): List<MaintenanceEntry> =
        entries.filter { it.vehicleId == vehicleId }

    fun buildVehicleSummary(
        vehicle: Vehicle,
        fuelEntries: List<FuelEntry>,
        maintenanceEntries: List<MaintenanceEntry>,
    ): VehicleSummary {
        val vehicleFuelEntries = entriesForVehicle(fuelEntries, vehicle.id)
        val vehicleMaintenanceEntries = maintenanceForVehicle(maintenanceEntries, vehicle.id)
        val totalFuelCost = vehicleFuelEntries.sumOfSafe { it.totalCost }
        val totalDistance = calculateTotalDistance(vehicleFuelEntries)
        val costPerDistance = totalDistance?.takeIf { it > 0.0 }?.let { totalFuelCost / it }
        val lastEfficiency = calculateEfficiencySegments(vehicle, vehicleFuelEntries).lastOrNull()?.value
        val lastFillUpDate = vehicleFuelEntries.maxByOrNull { it.dateTime }?.dateTime
        val estimatedRange = estimateRemainingRange(vehicle, lastEfficiency)
        val lastOdometer = vehicleFuelEntries.maxByOrNull { it.odometer }?.odometer

        return VehicleSummary(
            vehicle = vehicle,
            totalFuelCost = totalFuelCost,
            totalDistance = totalDistance,
            costPerDistance = costPerDistance,
            lastEfficiency = lastEfficiency,
            lastFillUpDate = lastFillUpDate,
            estimatedRange = estimatedRange,
            lastOdometer = lastOdometer,
            fuelEntryCount = vehicleFuelEntries.size,
            maintenanceEntryCount = vehicleMaintenanceEntries.size,
        )
    }

    fun calculateEfficiencySegments(
        vehicle: Vehicle,
        fuelEntries: List<FuelEntry>,
    ): List<EfficiencySegment> {
        val sorted = sortedByOdometer(fuelEntries)
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

    fun buildExportCsv(data: FuelMathData): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(
            "record_type",
            "vehicle_id",
            "vehicle_name",
            "distance_unit",
            "volume_unit",
            "tank_capacity",
            "entry_id",
            "date_time",
            "odometer",
            "fuel_amount",
            "price_per_unit",
            "total_cost",
            "is_full_tank",
            "maintenance_type",
            "maintenance_cost",
            "notes",
        )

        data.vehicles.sortedBy { it.name.lowercase() }.forEach { vehicle ->
            rows += listOf(
                "vehicle",
                vehicle.id,
                vehicle.name,
                vehicle.distanceUnit.storageValue,
                vehicle.volumeUnit.storageValue,
                vehicle.tankCapacity.toStorageString(),
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
                    vehicle?.distanceUnit?.storageValue.orEmpty(),
                    vehicle?.volumeUnit?.storageValue.orEmpty(),
                    vehicle?.tankCapacity?.toStorageString().orEmpty(),
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
                )
            }

        data.maintenanceEntries
            .sortedWith(compareBy<MaintenanceEntry> { it.vehicleId }.thenBy { it.dateTime }.thenBy { it.odometer })
            .forEach { entry ->
                val vehicle = data.vehicles.firstOrNull { it.id == entry.vehicleId }
                rows += listOf(
                    "maintenance_entry",
                    entry.vehicleId,
                    vehicle?.name.orEmpty(),
                    vehicle?.distanceUnit?.storageValue.orEmpty(),
                    vehicle?.volumeUnit?.storageValue.orEmpty(),
                    vehicle?.tankCapacity?.toStorageString().orEmpty(),
                    entry.id,
                    entry.dateTime.toString(),
                    entry.odometer.toStorageString(),
                    "",
                    "",
                    "",
                    "",
                    entry.type,
                    entry.cost.toStorageString(),
                    entry.notes,
                )
            }

        return rows.joinToString(separator = "\n", postfix = "\n") { row ->
            row.joinToString(",") { it.toCsvCell() }
        }
    }

    fun efficiencyUnitLabel(vehicle: Vehicle): String =
        when {
            vehicle.distanceUnit == DistanceUnit.MILES && vehicle.volumeUnit == VolumeUnit.GALLONS -> "MPG"
            vehicle.distanceUnit == DistanceUnit.KILOMETERS && vehicle.volumeUnit == VolumeUnit.LITERS -> "L/100km"
            else -> "${vehicle.distanceUnit.displayLabel}/${vehicle.volumeUnit.displayLabel}"
        }

    fun distanceUnitLabel(vehicle: Vehicle): String = vehicle.distanceUnit.displayLabel

    fun volumeUnitLabel(vehicle: Vehicle): String = vehicle.volumeUnit.displayLabel

    private fun calculateTotalDistance(entries: List<FuelEntry>): Double? {
        val sorted = sortedByOdometer(entries)
        if (sorted.size < 2) return null
        val distance = sorted.last().odometer - sorted.first().odometer
        return distance.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun calculateEfficiencyValue(vehicle: Vehicle, distance: Double, fuelUsed: Double): Double =
        if (vehicle.distanceUnit == DistanceUnit.KILOMETERS && vehicle.volumeUnit == VolumeUnit.LITERS) {
            (fuelUsed / distance) * 100.0
        } else {
            distance / fuelUsed
        }

    private fun estimateRemainingRange(vehicle: Vehicle, lastEfficiency: Double?): Double? {
        val efficiency = lastEfficiency?.takeIf { it.isFinite() && it > 0.0 } ?: return null
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

    private fun Iterable<FuelEntry>.sumOfSafe(selector: (FuelEntry) -> Double): Double =
        fold(0.0) { total, entry ->
            val value = selector(entry)
            if (value.isFinite() && value >= 0.0) total + value else total
        }

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
