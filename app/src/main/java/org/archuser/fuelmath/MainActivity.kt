package org.archuser.fuelmath

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.IOException
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private lateinit var repository: FuelRepository
    private var data: FuelMathData = FuelMathData()
    private var selectedVehicleId: String? = null

    private val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance()
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
    private val inputDateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val inputTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val parseTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = FuelRepository(this)

        val loadError = try {
            data = repository.loadData()
            null
        } catch (error: RuntimeException) {
            data = FuelMathData()
            error
        }

        MaintenanceReminderScheduler.sync(this, data)
        renderMainScreen()
        loadError?.let {
            showError(
                "Stored app data could not be loaded. The app started with an empty local dataset; use restore if you have a valid backup.",
                it,
            )
        }
    }

    private fun renderMainScreen() {
        selectedVehicleId = null
        title = getString(R.string.app_name)
        val root = createScreenRoot(getString(R.string.app_name))

        root.addSectionHeader("Vehicles")
        root.addText("What needs attention right now? Start with the vehicles that show overdue work or need a maintenance baseline.", colorRes = R.color.text_secondary)
        root.addActions(
            "Add Vehicle" to { showAddVehicleDialog() },
            "Settings" to { showSettingsDialog() },
            "Backup" to { createDocument(REQUEST_BACKUP_JSON, "application/json", "fuel-math-backup.json") },
            "Restore" to { openRestoreDocument() },
        )

        if (data.vehicles.isEmpty()) {
            root.addText("No vehicles yet. Add one to start tracking maintenance, fuel, and operating costs offline.", 16f, Typeface.NORMAL, R.color.text_secondary)
            return
        }

        data.vehicles
            .map { FuelCalculator.buildVehicleSummary(it, data) }
            .sortedWith(
                compareByDescending<VehicleSummary> { it.overdueCount }
                    .thenByDescending { it.dueSoonCount }
                    .thenByDescending { it.unknownCount }
                    .thenBy { it.vehicle.name.lowercase() },
            )
            .forEach { summary -> root.addView(buildVehicleCard(summary)) }
    }

    private fun renderVehicleDetail(vehicleId: String) {
        val vehicle = data.vehicles.firstOrNull { it.id == vehicleId }
        if (vehicle == null) {
            selectedVehicleId = null
            renderMainScreen()
            return
        }

        selectedVehicleId = vehicleId
        title = vehicle.name
        val root = createScreenRoot(vehicle.name) { renderMainScreen() }
        val fuelEntries = FuelCalculator.entriesForVehicle(data.fuelEntries, vehicle.id)
        val serviceLogs = FuelCalculator.serviceLogsForVehicle(data.maintenanceServiceLogs, vehicle.id)
        val states = FuelCalculator.calculateMaintenanceStates(vehicle, data)
        val summary = FuelCalculator.buildVehicleSummary(vehicle, data)

        root.addActions(*vehicleDetailActions(vehicle))

        root.addView(buildSummaryCard(summary))
        summary.smartRecommendation?.let { root.addView(buildRecommendationCard(vehicle, it)) }

        root.addSectionHeader("Needs Baseline")
        addMaintenanceStateList(
            root = root,
            vehicle = vehicle,
            states = states.filter { it.status == MaintenanceStatus.UNKNOWN },
            emptyMessage = "All active maintenance items have a service baseline.",
        )

        root.addSectionHeader("Overdue")
        addMaintenanceStateList(
            root = root,
            vehicle = vehicle,
            states = states.filter { it.status == MaintenanceStatus.OVERDUE },
            emptyMessage = "No overdue maintenance.",
        )

        root.addSectionHeader("Due Soon")
        addMaintenanceStateList(
            root = root,
            vehicle = vehicle,
            states = states.filter { it.status == MaintenanceStatus.DUE_SOON },
            emptyMessage = "No maintenance due soon.",
        )

        root.addSectionHeader("Upcoming")
        addMaintenanceStateList(
            root = root,
            vehicle = vehicle,
            states = states.filter { it.status == MaintenanceStatus.GOOD },
            emptyMessage = "No upcoming maintenance items.",
        )

        root.addSectionHeader("All Maintenance")
        if (states.isEmpty()) {
            root.addText("No maintenance items yet.", 16f, Typeface.NORMAL, R.color.text_secondary)
        } else {
            states.sortedBy { it.item.name.lowercase() }.forEach { state ->
                root.addView(buildMaintenanceItemCard(vehicle, state))
            }
        }

        root.addSectionHeader("Trends")
        root.addView(
            buildChartCard(
                title = "Efficiency",
                unitLabel = FuelCalculator.efficiencyUnitLabel(vehicle),
                points = FuelCalculator.buildEfficiencyChart(vehicle, fuelEntries),
            ),
        )
        root.addView(
            buildChartCard(
                title = energyCostTitle(vehicle),
                unitLabel = currencyFormatter.currency?.currencyCode.orEmpty(),
                points = FuelCalculator.buildCostOverTime(fuelEntries),
            ),
        )
        root.addView(
            buildChartCard(
                title = "Maintenance Cost",
                unitLabel = currencyFormatter.currency?.currencyCode.orEmpty(),
                points = FuelCalculator.buildMaintenanceCostOverTime(serviceLogs),
            ),
        )
        root.addView(
            buildChartCard(
                title = "Total Cost",
                unitLabel = currencyFormatter.currency?.currencyCode.orEmpty(),
                points = FuelCalculator.buildTotalCostOverTime(fuelEntries, serviceLogs),
            ),
        )
        root.addView(buildSeasonalCard(vehicle, fuelEntries))

        root.addSectionHeader(FuelCalculator.energyLogLabel(vehicle))
        if (fuelEntries.isEmpty()) {
            root.addText("No ${FuelCalculator.energyLogLabel(vehicle).lowercase(Locale.US)} entries yet.", 16f, Typeface.NORMAL, R.color.text_secondary)
        } else {
            val distances = FuelCalculator.calculateEntryDistances(fuelEntries)
                .associate { it.entry.id to it.distanceFromPrevious }
            fuelEntries.sortedWith(compareByDescending<FuelEntry> { it.dateTime }.thenByDescending { it.odometer })
                .forEach { entry ->
                    root.addView(buildFuelEntryCard(vehicle, entry, distances[entry.id]))
                }
        }

        root.addSectionHeader("Service History")
        if (serviceLogs.isEmpty()) {
            root.addText("No maintenance service logs yet.", 16f, Typeface.NORMAL, R.color.text_secondary)
        } else {
            serviceLogs.sortedWith(compareByDescending<MaintenanceServiceLog> { it.dateTime }.thenByDescending { it.odometer })
                .forEach { log ->
                    root.addView(buildServiceLogCard(vehicle, log))
                }
        }
    }

    private fun addMaintenanceStateList(
        root: LinearLayout,
        vehicle: Vehicle,
        states: List<MaintenanceItemState>,
        emptyMessage: String,
    ) {
        if (states.isEmpty()) {
            root.addText(emptyMessage, 16f, Typeface.NORMAL, R.color.text_secondary)
        } else {
            states.forEach { root.addView(buildMaintenanceItemCard(vehicle, it)) }
        }
    }

    private fun buildVehicleCard(summary: VehicleSummary): View {
        val vehicle = summary.vehicle
        return buildCard(clickable = true).apply {
            setOnClickListener { renderVehicleDetail(vehicle.id) }
            setOnLongClickListener {
                confirmDeleteVehicle(vehicle)
                true
            }
            val content = cardContent()
            content.addText(vehicle.name, 20f, Typeface.BOLD)
            val descriptor = vehicleDescriptor(vehicle)
            if (descriptor.isNotBlank()) content.addText(descriptor, colorRes = R.color.text_secondary)
            content.addText("Health score: ${summary.healthScore}%")
            content.addText("Current mileage: ${formatDistance(vehicle, summary.currentMileage)}")
            content.addText(formatTirePsiReminder(vehicle))
            content.addText("Maintenance: ${summary.overdueCount} overdue, ${summary.dueSoonCount} due soon, ${summary.unknownCount} needs baseline")
            content.addText("Recommendation: ${formatRecommendation(summary.smartRecommendation)}")
            content.addText("Efficiency: ${formatEfficiency(vehicle, summary.lastEfficiency)}")
            content.addText("Total cost: ${currencyFormatter.format(summary.totalCost)}")
            content.addText("Cost per ${FuelCalculator.distanceUnitLabel(vehicle)}: ${formatCurrencyPerDistance(summary.costPerDistance)}")
            content.addText(
                "${entryCountSummary(summary)}   Items: ${summary.maintenanceItemCount}   Services: ${summary.maintenanceServiceLogCount}",
                colorRes = R.color.text_secondary,
            )
            content.addActions(*vehicleCardActions(vehicle))
            addView(content)
        }
    }

    private fun buildSummaryCard(summary: VehicleSummary): View {
        val vehicle = summary.vehicle
        return buildCard().apply {
            val content = cardContent()
            content.addText("Summary", 18f, Typeface.BOLD)
            content.addText("Health score: ${summary.healthScore}%")
            content.addText("Current mileage: ${formatDistance(vehicle, summary.currentMileage)}")
            content.addText(formatTirePsiReminder(vehicle))
            content.addText("Maintenance: ${summary.overdueCount} overdue, ${summary.dueSoonCount} due soon, ${summary.unknownCount} needs baseline")
            content.addText("Efficiency: ${formatEfficiency(vehicle, summary.lastEfficiency)}")
            content.addText("Tracked distance: ${formatDistance(vehicle, summary.totalDistance)}")
            if (vehicle.vehicleType.usesLiquidFuel) content.addText("Fuel cost: ${currencyFormatter.format(summary.totalFuelCost)}")
            if (vehicle.vehicleType.usesBattery) content.addText("Charging cost: ${currencyFormatter.format(summary.totalChargingCost)}")
            content.addText("Maintenance cost: ${currencyFormatter.format(summary.totalMaintenanceCost)}")
            content.addText("Total cost: ${currencyFormatter.format(summary.totalCost)}")
            content.addText("Cost per ${FuelCalculator.distanceUnitLabel(vehicle)}: ${formatCurrencyPerDistance(summary.costPerDistance)}")
            content.addText("Estimated range: ${formatDistance(vehicle, summary.estimatedRange)}")
            if (vehicle.vehicleType.usesLiquidFuel) content.addText("Tank capacity: ${formatNumber(vehicle.tankCapacity, 2)} ${FuelCalculator.volumeUnitLabel(vehicle)}")
            if (vehicle.vehicleType.usesBattery) content.addText("Battery capacity: ${formatDistanceAgnostic(vehicle.batteryCapacity)} ${vehicle.energyUnit.displayLabel}")
            addView(content)
        }
    }

    private fun buildRecommendationCard(vehicle: Vehicle, state: MaintenanceItemState): View =
        buildCard().apply {
            val content = cardContent()
            content.addText("What Needs Attention Now", 18f, Typeface.BOLD)
            content.addText("${state.item.name}: ${state.status.displayLabel}")
            content.addText(formatNextDue(vehicle, state), colorRes = R.color.text_secondary)
            content.addActions("Log Service" to { showLogServiceDialog(vehicle, state.item) })
            addView(content)
        }

    private fun buildSeasonalCard(vehicle: Vehicle, fuelEntries: List<FuelEntry>): View =
        buildCard().apply {
            val comparison = FuelCalculator.buildSeasonalEfficiencyComparison(vehicle, fuelEntries)
            val content = cardContent()
            content.addText("Seasonal Efficiency", 18f, Typeface.BOLD)
            content.addText("${comparison.currentSeason}: ${formatEfficiency(vehicle, comparison.currentSeasonAverage)}")
            content.addText("Overall: ${formatEfficiency(vehicle, comparison.overallAverage)}", colorRes = R.color.text_secondary)
            addView(content)
        }

    private fun buildChartCard(title: String, unitLabel: String, points: List<ChartPoint>): View =
        buildCard().apply {
            val content = cardContent()
            content.addText(title, 18f, Typeface.BOLD, bottomMarginDp = 2)
            content.addText(
                chartPrimarySummary(unitLabel, points),
                22f,
                Typeface.BOLD,
                bottomMarginDp = 2,
            )
            content.addText(
                chartSecondarySummary(title, unitLabel, points),
                13f,
                Typeface.NORMAL,
                R.color.text_secondary,
                bottomMarginDp = if (points.size >= 2) 10 else 0,
            )

            if (points.size >= 2) {
                val chart = LineChartView(this@MainActivity).apply {
                    setChartData(
                        unitLabel = unitLabel,
                        points = points,
                        showArea = title.contains("Cost", ignoreCase = true),
                        showPointLabels = true,
                    )
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        132.dp(),
                    )
                }
                content.addView(chart)
            }

            addView(content)
        }

    private fun buildMaintenanceItemCard(vehicle: Vehicle, state: MaintenanceItemState): View =
        buildCard().apply {
            val item = state.item
            val content = cardContent()
            content.addText(item.name, 17f, Typeface.BOLD)
            content.addText("${state.category.name}   ${state.status.displayLabel}   ${item.importance.displayLabel} importance")
            content.addText(formatInterval(vehicle, item), colorRes = R.color.text_secondary)
            content.addText(formatLastService(vehicle, state), colorRes = R.color.text_secondary)
            content.addText(formatNextDue(vehicle, state), colorRes = R.color.text_secondary)
            if (item.notes.isNotBlank()) content.addText(item.notes, colorRes = R.color.text_secondary)
            content.addActions(
                "Log Service" to { showLogServiceDialog(vehicle, item) },
                "Edit" to { showMaintenanceItemDialog(vehicle, item) },
                "Delete" to { confirmDeleteMaintenanceItem(vehicle, item) },
            )
            addView(content)
        }

    private fun buildFuelEntryCard(vehicle: Vehicle, entry: FuelEntry, distance: Double?): View =
        buildCard().apply {
            val content = cardContent()
            content.addText(entry.dateTime.format(dateTimeFormatter), 17f, Typeface.BOLD)
            content.addText("Odometer: ${formatDistance(vehicle, entry.odometer)}")
            content.addText("${entryAmountLabel(entry.entryType)}: ${formatNumber(entry.fuelAmount, 3)} ${FuelCalculator.entryUnitLabel(vehicle, entry.entryType)}")
            content.addText("Price: ${currencyFormatter.format(entry.pricePerUnit)} per ${FuelCalculator.entryUnitLabel(vehicle, entry.entryType)}")
            content.addText("Total: ${currencyFormatter.format(entry.totalCost)}")
            content.addText("Distance since previous: ${formatDistance(vehicle, distance)}")
            content.addText(fullEntryText(entry), colorRes = R.color.text_secondary)
            content.addActions("Delete" to { confirmDeleteFuelEntry(vehicle, entry) })
            addView(content)
        }

    private fun buildServiceLogCard(vehicle: Vehicle, log: MaintenanceServiceLog): View =
        buildCard().apply {
            val item = data.maintenanceItems.firstOrNull { it.id == log.maintenanceItemId }
            val content = cardContent()
            content.addText(item?.name ?: "Maintenance Service", 17f, Typeface.BOLD)
            content.addText(log.dateTime.format(dateTimeFormatter))
            content.addText("Odometer: ${formatDistance(vehicle, log.odometer)}")
            content.addText("Cost: ${currencyFormatter.format(log.cost)}")
            if (log.notes.isNotBlank()) content.addText(log.notes, colorRes = R.color.text_secondary)
            content.addActions("Delete" to { confirmDeleteServiceLog(vehicle, log) })
            addView(content)
        }

    private fun showAddVehicleDialog() {
        val nameInput = createInput("Name", InputType.TYPE_CLASS_TEXT)
        val makeInput = createInput("Make", InputType.TYPE_CLASS_TEXT)
        val modelInput = createInput("Model", InputType.TYPE_CLASS_TEXT)
        val yearInput = createInput("Year", InputType.TYPE_CLASS_NUMBER)
        val mileageInput = createInput("Current mileage", decimalInputType()).apply { setText("0") }
        val frontTirePsiInput = createInput("Front tire PSI", decimalInputType())
        val rearTirePsiInput = createInput("Rear tire PSI", decimalInputType())
        val tankInput = createInput("Tank capacity", decimalInputType())
        val batteryInput = createInput("Battery capacity", decimalInputType())
        val vehicleTypes = VehicleType.entries.toList()
        val vehicleTypeSpinner = createSpinner(vehicleTypes.map { it.displayLabel })
        val distanceUnit = createChoiceControl(
            listOf(
                "Miles" to DistanceUnit.MILES,
                "Kilometers" to DistanceUnit.KILOMETERS,
            ),
        )
        val volumeUnit = createChoiceControl(
            listOf(
                "Gallons" to VolumeUnit.GALLONS,
                "Liters" to VolumeUnit.LITERS,
            ),
        )
        val tankLayout = labeledView("Tank capacity (${VolumeUnit.GALLONS.displayLabel})", tankInput)
        val batteryLayout = labeledView("Battery capacity (${EnergyUnit.KILOWATT_HOURS.displayLabel})", batteryInput)
        val volumeUnitLayout = labeledView("Fuel volume unit", volumeUnit.view)

        val form = dialogForm().apply {
            addView(labeledView("Name", nameInput))
            addView(labeledView("Make", makeInput))
            addView(labeledView("Model", modelInput))
            addView(labeledView("Year", yearInput))
            addView(labeledView("Current mileage", mileageInput))
            addView(labeledView("Front tire PSI", frontTirePsiInput))
            addView(labeledView("Rear tire PSI", rearTirePsiInput))
            addView(labeledView("Vehicle type", vehicleTypeSpinner))
            addView(tankLayout)
            addView(batteryLayout)
            addView(labeledView("Distance unit", distanceUnit.view))
            addView(volumeUnitLayout)
        }

        fun selectedVehicleType(): VehicleType = vehicleTypes[vehicleTypeSpinner.selectedItemPosition]

        fun updateVehicleTypeFields(type: VehicleType) {
            tankLayout.visibility = if (type.usesLiquidFuel) View.VISIBLE else View.GONE
            volumeUnitLayout.visibility = if (type.usesLiquidFuel) View.VISIBLE else View.GONE
            batteryLayout.visibility = if (type.usesBattery) View.VISIBLE else View.GONE
            if (type.usesLiquidFuel) {
                tankInput.hint = "Tank capacity"
            }
            if (type.usesBattery) {
                batteryInput.hint = "Battery capacity"
            }
        }

        vehicleTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateVehicleTypeFields(vehicleTypes[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        updateVehicleTypeFields(selectedVehicleType())

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Add Vehicle")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val currentMileage = parseNonNegativeDouble(mileageInput, "Current mileage") ?: return@setOnClickListener
                val parsedYear = parseOptionalYear(yearInput) ?: return@setOnClickListener
                val year = parsedYear.value
                val frontTirePsi = parsePositiveDouble(frontTirePsiInput, "Front tire PSI") ?: return@setOnClickListener
                val rearTirePsi = parsePositiveDouble(rearTirePsiInput, "Rear tire PSI") ?: return@setOnClickListener
                val vehicleType = selectedVehicleType()
                val tankCapacity = if (vehicleType.usesLiquidFuel) {
                    parsePositiveDouble(tankInput, "Tank capacity") ?: return@setOnClickListener
                } else {
                    0.0
                }
                val batteryCapacity = if (vehicleType.usesBattery) {
                    parsePositiveDouble(batteryInput, "Battery capacity") ?: return@setOnClickListener
                } else {
                    null
                }
                if (name.isBlank()) {
                    showToast("Vehicle name is required.")
                    return@setOnClickListener
                }

                val vehicle = Vehicle(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    make = makeInput.text.toString().trim(),
                    model = modelInput.text.toString().trim(),
                    year = year,
                    currentMileage = currentMileage,
                    fuelType = fuelTypeForVehicleType(vehicleType),
                    vehicleType = vehicleType,
                    tankCapacity = tankCapacity,
                    batteryCapacity = batteryCapacity,
                    recommendedFrontTirePsi = frontTirePsi,
                    recommendedRearTirePsi = rearTirePsi,
                    distanceUnit = distanceUnit.selected(),
                    volumeUnit = volumeUnit.selected(),
                    energyUnit = EnergyUnit.KILOWATT_HOURS,
                )
                val seededMaintenanceItems = MaintenanceDefaults.templatesForVehicleType(vehicleType)
                    .map { template -> template.toMaintenanceItem(vehicle.id) }
                if (persistData(
                        data.copy(
                            vehicles = data.vehicles + vehicle,
                            maintenanceItems = data.maintenanceItems + seededMaintenanceItems,
                        ),
                        "Vehicle saved.",
                    )
                ) {
                    dialog.dismiss()
                    renderMainScreen()
                }
            }
        }
        dialog.show()
    }

    private fun showAddFuelEntryDialog(vehicle: Vehicle, entryType: EnergyEntryType = EnergyEntryType.FUEL) {
        if (entryType == EnergyEntryType.FUEL && !vehicle.vehicleType.usesLiquidFuel) {
            showToast("Fuel logs are not available for this vehicle type.")
            return
        }
        if (entryType == EnergyEntryType.CHARGING && !vehicle.vehicleType.usesBattery) {
            showToast("Charging logs are not available for this vehicle type.")
            return
        }
        val now = LocalDateTime.now()
        val dateInput = createInput("Date", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalDate().format(inputDateFormatter))
        }
        val timeInput = createInput("Time", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalTime().withSecond(0).withNano(0).format(inputTimeFormatter))
        }
        val odometerInput = createInput("Odometer", decimalInputType()).apply {
            setText(formatPlainNumber(FuelCalculator.buildVehicleSummary(vehicle, data).currentMileage))
        }
        val fuelInput = createInput(entryAmountLabel(entryType), decimalInputType())
        val priceInput = createInput("Price per ${FuelCalculator.entryUnitLabel(vehicle, entryType)}", decimalInputType())
        val fullTankSwitch = SwitchMaterial(this).apply {
            text = if (entryType == EnergyEntryType.CHARGING) "Full charge" else "Full tank"
            isChecked = true
            setTextColor(color(R.color.text_primary))
        }

        val form = dialogForm().apply {
            addView(labeledView("Date (YYYY-MM-DD)", dateInput))
            addView(labeledView("Time (HH:MM)", timeInput))
            addView(labeledView("Odometer (${FuelCalculator.distanceUnitLabel(vehicle)})", odometerInput))
            addView(labeledView("${entryAmountLabel(entryType)} (${FuelCalculator.entryUnitLabel(vehicle, entryType)})", fuelInput))
            addView(labeledView("Price per ${FuelCalculator.entryUnitLabel(vehicle, entryType)}", priceInput))
            addView(fullTankSwitch)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (entryType == EnergyEntryType.CHARGING) "Add Charging" else "Add Fuel")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val dateTime = parseDateTime(dateInput, timeInput) ?: return@setOnClickListener
                val odometer = parseNonNegativeDouble(odometerInput, "Odometer") ?: return@setOnClickListener
                val fuelAmount = parsePositiveDouble(fuelInput, entryAmountLabel(entryType)) ?: return@setOnClickListener
                val pricePerUnit = parseNonNegativeDouble(priceInput, "Price per unit") ?: return@setOnClickListener

                val entry = FuelEntry(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicle.id,
                    dateTime = dateTime,
                    odometer = odometer,
                    fuelAmount = fuelAmount,
                    pricePerUnit = pricePerUnit,
                    isFullTank = fullTankSwitch.isChecked,
                    entryType = entryType,
                )
                val updated = data.copy(
                    vehicles = data.vehicles.updateVehicleMileage(vehicle.id, odometer),
                    fuelEntries = data.fuelEntries + entry,
                )
                if (persistData(updated, if (entryType == EnergyEntryType.CHARGING) "Charging entry saved." else "Fuel entry saved.")) {
                    dialog.dismiss()
                    renderVehicleDetail(vehicle.id)
                }
            }
        }
        dialog.show()
    }

    private fun showMaintenanceItemDialog(vehicle: Vehicle, existing: MaintenanceItem? = null) {
        val categories = data.maintenanceCategories
        val importances = MaintenanceImportance.entries.toList()
        val nameInput = createInput("Name", InputType.TYPE_CLASS_TEXT).apply { setText(existing?.name.orEmpty()) }
        val categorySpinner = createSpinner(
            categories.map { it.name },
            categories.indexOfFirst { it.id == existing?.categoryId }.takeIf { it >= 0 } ?: 0,
        )
        val importanceSpinner = createSpinner(
            importances.map { it.displayLabel },
            importances.indexOf(existing?.importance ?: MaintenanceImportance.MEDIUM).takeIf { it >= 0 } ?: 0,
        )
        val intervalMilesInput = createInput("Interval miles", decimalInputType()).apply {
            setText(existing?.intervalMiles?.let(::formatPlainNumber).orEmpty())
        }
        val intervalDaysInput = createInput("Interval days", InputType.TYPE_CLASS_NUMBER).apply {
            setText(existing?.intervalTimeDays?.toString().orEmpty())
        }
        val notesInput = createInput("Notes", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = 2
            setText(existing?.notes.orEmpty())
        }

        val form = dialogForm().apply {
            addView(labeledView("Name", nameInput))
            addView(labeledView("Category", categorySpinner))
            addView(labeledView("Importance", importanceSpinner))
            addView(labeledView("Interval miles", intervalMilesInput))
            addView(labeledView("Interval days", intervalDaysInput))
            addView(labeledView("Notes", notesInput))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add Maintenance Item" else "Edit Maintenance Item")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                if (name.isBlank()) {
                    showToast("Maintenance item name is required.")
                    return@setOnClickListener
                }
                val parsedIntervalMiles = parseOptionalPositiveDouble(intervalMilesInput, "Interval miles") ?: return@setOnClickListener
                val parsedIntervalDays = parseOptionalPositiveInt(intervalDaysInput, "Interval days") ?: return@setOnClickListener
                val item = MaintenanceItem(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    vehicleId = vehicle.id,
                    categoryId = categories[categorySpinner.selectedItemPosition].id,
                    name = name,
                    intervalMiles = parsedIntervalMiles.value,
                    intervalTimeDays = parsedIntervalDays.value,
                    notes = notesInput.text.toString().trim(),
                    importance = importances[importanceSpinner.selectedItemPosition],
                )
                val updatedItems = if (existing == null) {
                    data.maintenanceItems + item
                } else {
                    data.maintenanceItems.map { if (it.id == existing.id) item else it }
                }
                if (persistData(data.copy(maintenanceItems = updatedItems), "Maintenance item saved.")) {
                    dialog.dismiss()
                    renderVehicleDetail(vehicle.id)
                }
            }
        }
        dialog.show()
    }

    private fun showLogServiceDialog(vehicle: Vehicle, preselectedItem: MaintenanceItem? = null) {
        val items = data.maintenanceItems
            .filter { it.vehicleId == vehicle.id }
            .sortedBy { it.name.lowercase() }
        if (items.isEmpty()) {
            showToast("Add a maintenance item before logging service.")
            return
        }

        val now = LocalDateTime.now()
        val selectedIndex = preselectedItem?.let { selected ->
            items.indexOfFirst { it.id == selected.id }.takeIf { it >= 0 }
        } ?: 0
        val itemSpinner = createSpinner(items.map { it.name }, selectedIndex)
        val dateInput = createInput("Date", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalDate().format(inputDateFormatter))
        }
        val timeInput = createInput("Time", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalTime().withSecond(0).withNano(0).format(inputTimeFormatter))
        }
        val odometerInput = createInput("Odometer", decimalInputType()).apply {
            setText(formatPlainNumber(FuelCalculator.buildVehicleSummary(vehicle, data).currentMileage))
        }
        val costInput = createInput("Cost", decimalInputType()).apply { setText("0") }
        val notesInput = createInput("Notes", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = 2
        }

        val form = dialogForm().apply {
            addView(labeledView("Maintenance item", itemSpinner))
            addView(labeledView("Date (YYYY-MM-DD)", dateInput))
            addView(labeledView("Time (HH:MM)", timeInput))
            addView(labeledView("Odometer (${FuelCalculator.distanceUnitLabel(vehicle)})", odometerInput))
            addView(labeledView("Cost", costInput))
            addView(labeledView("Notes", notesInput))
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Log Maintenance")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val item = items[itemSpinner.selectedItemPosition]
                val dateTime = parseDateTime(dateInput, timeInput) ?: return@setOnClickListener
                val odometer = parseNonNegativeDouble(odometerInput, "Odometer") ?: return@setOnClickListener
                val cost = parseNonNegativeDouble(costInput, "Cost") ?: return@setOnClickListener
                val log = MaintenanceServiceLog(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicle.id,
                    maintenanceItemId = item.id,
                    dateTime = dateTime,
                    odometer = odometer,
                    cost = cost,
                    notes = notesInput.text.toString().trim(),
                )
                val updated = data.copy(
                    vehicles = data.vehicles.updateVehicleMileage(vehicle.id, odometer),
                    maintenanceServiceLogs = data.maintenanceServiceLogs + log,
                )
                if (persistData(updated, "Maintenance logged.")) {
                    dialog.dismiss()
                    renderVehicleDetail(vehicle.id)
                }
            }
        }
        dialog.show()
    }

    private fun showSettingsDialog() {
        val thresholdInput = createInput("Due soon threshold percent", InputType.TYPE_CLASS_NUMBER).apply {
            setText(data.settings.dueSoonThresholdPercent.toString())
        }
        val remindersSwitch = SwitchMaterial(this).apply {
            text = "Daily maintenance reminders"
            isChecked = data.settings.maintenanceRemindersEnabled
            setTextColor(color(R.color.text_primary))
        }
        val permissionText = TextView(this).apply {
            text = "Notification status: ${notificationStatusText()}"
            textSize = 14f
            setTextColor(color(R.color.text_secondary))
        }

        val form = dialogForm().apply {
            addView(labeledView("Due soon threshold percent", thresholdInput))
            addView(remindersSwitch)
            addView(permissionText)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Settings")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val threshold = parseThresholdPercent(thresholdInput) ?: return@setOnClickListener
                val remindersEnabled = remindersSwitch.isChecked
                val updated = data.copy(
                    settings = data.settings.copy(
                        dueSoonThresholdPercent = threshold,
                        maintenanceRemindersEnabled = remindersEnabled,
                    ),
                )
                if (persistData(updated, "Settings saved.")) {
                    dialog.dismiss()
                    if (remindersEnabled) requestNotificationPermissionIfNeeded()
                    selectedVehicleId?.let(::renderVehicleDetail) ?: renderMainScreen()
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteVehicle(vehicle: Vehicle) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Vehicle")
            .setMessage("Delete ${vehicle.name} and all of its fuel entries, maintenance items, and service logs?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Yes") { _, _ ->
                val itemIds = data.maintenanceItems.filter { it.vehicleId == vehicle.id }.map { it.id }.toSet()
                val updated = data.copy(
                    vehicles = data.vehicles.filterNot { it.id == vehicle.id },
                    fuelEntries = data.fuelEntries.filterNot { it.vehicleId == vehicle.id },
                    maintenanceItems = data.maintenanceItems.filterNot { it.vehicleId == vehicle.id },
                    maintenanceServiceLogs = data.maintenanceServiceLogs.filterNot {
                        it.vehicleId == vehicle.id || it.maintenanceItemId in itemIds
                    },
                )
                if (persistData(updated, "Vehicle deleted.")) renderMainScreen()
            }
            .show()
    }

    private fun confirmDeleteFuelEntry(vehicle: Vehicle, entry: FuelEntry) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Fuel Entry")
            .setMessage("Delete the fuel entry from ${entry.dateTime.format(dateFormatter)}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (persistData(data.copy(fuelEntries = data.fuelEntries.filterNot { it.id == entry.id }), "Fuel entry deleted.")) {
                    renderVehicleDetail(vehicle.id)
                }
            }
            .show()
    }

    private fun confirmDeleteMaintenanceItem(vehicle: Vehicle, item: MaintenanceItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Maintenance Item")
            .setMessage("Delete ${item.name} and its service history?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val updated = data.copy(
                    maintenanceItems = data.maintenanceItems.filterNot { it.id == item.id },
                    maintenanceServiceLogs = data.maintenanceServiceLogs.filterNot { it.maintenanceItemId == item.id },
                )
                if (persistData(updated, "Maintenance item deleted.")) renderVehicleDetail(vehicle.id)
            }
            .show()
    }

    private fun confirmDeleteServiceLog(vehicle: Vehicle, log: MaintenanceServiceLog) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Service Log")
            .setMessage("Delete the service log from ${log.dateTime.format(dateFormatter)}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val updated = data.copy(
                    maintenanceServiceLogs = data.maintenanceServiceLogs.filterNot { it.id == log.id },
                )
                if (persistData(updated, "Service log deleted.")) renderVehicleDetail(vehicle.id)
            }
            .show()
    }

    private fun confirmRestore(restoredData: FuelMathData) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Restore Backup")
            .setMessage(
                "Replace local data with ${restoredData.vehicles.size} vehicles, " +
                    "${restoredData.fuelEntries.size} fuel entries, " +
                    "${restoredData.maintenanceItems.size} maintenance items, and " +
                    "${restoredData.maintenanceServiceLogs.size} service logs?",
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Restore") { _, _ ->
                if (persistData(restoredData, "Backup restored.")) {
                    renderMainScreen()
                }
            }
            .show()
    }

    private fun persistData(updatedData: FuelMathData, successMessage: String): Boolean {
        return try {
            if (repository.saveData(updatedData)) {
                data = updatedData
                MaintenanceReminderScheduler.sync(this, data)
                showToast(successMessage)
                true
            } else {
                showError("Unable to save data. SharedPreferences commit returned false.")
                false
            }
        } catch (error: RuntimeException) {
            showError("Unable to save data.", error)
            false
        }
    }

    private fun createDocument(requestCode: Int, mimeType: String, fileName: String) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mimeType
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, requestCode)
    }

    private fun openRestoreDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_RESTORE_JSON)
    }

    @Deprecated("Deprecated by Android; retained here to avoid adding Activity Result dependencies.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != Activity.RESULT_OK) return
        val uri = resultData?.data ?: return

        when (requestCode) {
            REQUEST_BACKUP_JSON -> writeDocument(uri, repository.encodeBackup(data), "Backup saved.")
            REQUEST_RESTORE_JSON -> restoreDocument(uri)
        }
    }

    private fun writeDocument(uri: Uri, content: String, successMessage: String) {
        try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            val output = contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open document for writing.")
            output.use { it.write(bytes) }
            showToast(successMessage)
        } catch (error: IOException) {
            showError("Unable to write the selected document.", error)
        } catch (error: SecurityException) {
            showError("Android denied write access to the selected document.", error)
        }
    }

    private fun restoreDocument(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open document for reading.")
            val json = input.use { String(it.readBytes(), Charsets.UTF_8) }
            val restoredData = repository.decodeBackup(json)
            confirmRestore(restoredData)
        } catch (error: IOException) {
            showError("Unable to read the selected backup.", error)
        } catch (error: RuntimeException) {
            showError("The selected backup is invalid and was not restored.", error)
        } catch (error: SecurityException) {
            showError("Android denied read access to the selected backup.", error)
        }
    }

    private fun createScreenRoot(toolbarTitle: String, onBack: (() -> Unit)? = null): LinearLayout {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.app_background))
        }
        ViewCompat.setOnApplyWindowInsetsListener(shell) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, 0)
            insets
        }
        val toolbar = MaterialToolbar(this).apply {
            title = toolbarTitle
            setTitleTextColor(color(R.color.text_primary))
            setBackgroundColor(color(R.color.app_background))
            if (onBack != null) {
                setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                setNavigationIconTint(color(R.color.text_primary))
                setNavigationOnClickListener { onBack() }
                navigationContentDescription = "Back"
            }
        }
        shell.addView(
            toolbar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                64.dp(),
            ),
        )

        val scrollView = ScrollView(this).apply {
            isFillViewport = false
            setBackgroundColor(color(R.color.app_background))
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp(), 8.dp(), 20.dp(), 28.dp())
        }
        scrollView.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        shell.addView(
            scrollView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            ),
        )
        setContentView(shell)
        ViewCompat.requestApplyInsets(shell)
        return root
    }

    private fun buildCard(clickable: Boolean = false): MaterialCardView =
        MaterialCardView(this).apply {
            radius = 8.dp().toFloat()
            cardElevation = 0f
            strokeWidth = 1.dp()
            strokeColor = color(R.color.chart_grid)
            useCompatPadding = false
            setCardBackgroundColor(color(R.color.surface_card))
            isClickable = clickable
            isFocusable = clickable
            rippleColor = ColorStateList.valueOf(color(R.color.fuel_primary_light))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = 14.dp()
            }
        }

    private fun cardContent(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp(), 16.dp(), 18.dp(), 18.dp())
        }

    private fun LinearLayout.addSectionHeader(value: String): TextView =
        addText(
            value = value,
            sizeSp = 15f,
            style = Typeface.BOLD,
            colorRes = R.color.text_secondary,
            bottomMarginDp = 10,
            topMarginDp = 8,
        )

    private fun LinearLayout.addText(
        value: String,
        sizeSp: Float = 14f,
        style: Int = Typeface.NORMAL,
        colorRes: Int = R.color.text_primary,
        bottomMarginDp: Int = 4,
        topMarginDp: Int = 0,
    ): TextView {
        val view = TextView(this@MainActivity).apply {
            text = value
            textSize = sizeSp
            typeface = Typeface.DEFAULT_BOLD.takeIf { style == Typeface.BOLD } ?: Typeface.DEFAULT
            setTextColor(color(colorRes))
            includeFontPadding = true
            letterSpacing = 0f
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = topMarginDp.dp()
                bottomMargin = bottomMarginDp.dp()
            }
        }
        addView(view)
        return view
    }

    private fun LinearLayout.addActions(vararg actions: Pair<String, () -> Unit>) {
        val scroll = HorizontalScrollView(this@MainActivity).apply {
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        actions.forEach { (label, action) ->
            row.addView(
                MaterialButton(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    cornerRadius = 8.dp()
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    setOnClickListener { action() }
                    minHeight = 40.dp()
                    applyActionTone(label)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        rightMargin = 8.dp()
                        topMargin = 6.dp()
                        bottomMargin = 8.dp()
                    }
                },
            )
        }
        scroll.addView(row)
        addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    private fun dialogForm(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2.dp(), 8.dp(), 2.dp(), 2.dp())
        }

    private fun scrollDialogView(content: View): View =
        ScrollView(this).apply {
            addView(content)
            setPadding(16.dp(), 0, 16.dp(), 0)
        }

    private fun labeledView(label: String, child: View): View {
        if (child is EditText) {
            return TextInputLayout(this).apply {
                hint = label
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(8.dp().toFloat(), 8.dp().toFloat(), 8.dp().toFloat(), 8.dp().toFloat())
                boxStrokeColor = themeColor(com.google.android.material.R.attr.colorOutline, R.color.chart_axis)
                setBoxBackgroundColor(color(R.color.surface_card))
                addView(
                    child,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = 12.dp()
                }
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val labelView = TextView(this@MainActivity).apply {
                text = label
                textSize = 13f
                setTextColor(color(R.color.text_secondary))
            }
            addView(labelView)
            addView(child)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = 12.dp()
            }
        }
    }

    private fun createInput(hint: String, inputType: Int): TextInputEditText =
        TextInputEditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setSingleLine(inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
            setTextColor(color(R.color.text_primary))
            setHintTextColor(color(R.color.text_secondary))
            imeOptions = EditorInfo.IME_ACTION_NEXT
        }

    private fun createSpinner(labels: List<String>, selectedIndex: Int = 0): Spinner {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        return Spinner(this).apply {
            this.adapter = adapter
            if (labels.isNotEmpty()) setSelection(selectedIndex.coerceIn(0, labels.lastIndex))
        }
    }

    private fun <T> createChoiceControl(options: List<Pair<String, T>>): ChoiceControl<T> {
        require(options.isNotEmpty()) { "Choice controls require at least one option." }
        var selected = options.first().second
        val valuesById = mutableMapOf<Int, T>()
        val group = MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        fun applyChoiceStyles() {
            valuesById.keys.forEach { id ->
                val button = group.findViewById<MaterialButton>(id) ?: return@forEach
                val selectedColor = color(R.color.fuel_primary_light)
                val unselectedColor = color(R.color.surface_card)
                button.backgroundTintList = ColorStateList.valueOf(if (button.isChecked) selectedColor else unselectedColor)
                button.setTextColor(if (button.isChecked) color(R.color.fuel_primary_dark) else color(R.color.text_primary))
                button.strokeColor = ColorStateList.valueOf(color(R.color.chart_grid))
                button.strokeWidth = 1.dp()
            }
        }

        options.forEachIndexed { index, (label, value) ->
            val buttonId = View.generateViewId()
            valuesById[buttonId] = value
            group.addView(
                MaterialButton(this).apply {
                    id = buttonId
                    text = label
                    isAllCaps = false
                    isCheckable = true
                    cornerRadius = 8.dp()
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 48.dp()
                    layoutParams = LinearLayout.LayoutParams(0, 48.dp(), 1f).apply {
                        if (index > 0) leftMargin = 8.dp()
                    }
                },
            )
            if (index == 0) group.check(buttonId)
        }

        group.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                selected = valuesById.getValue(checkedId)
                applyChoiceStyles()
            }
        }
        applyChoiceStyles()

        return ChoiceControl(group) { selected }
    }

    private fun parseDateTime(dateInput: EditText, timeInput: EditText): LocalDateTime? {
        return try {
            val date = LocalDate.parse(dateInput.text.toString().trim(), inputDateFormatter)
            val time = LocalTime.parse(timeInput.text.toString().trim(), parseTimeFormatter)
            LocalDateTime.of(date, time)
        } catch (error: RuntimeException) {
            showToast("Enter a valid date and time.")
            null
        }
    }

    private fun parsePositiveDouble(input: EditText, label: String): Double? {
        val value = parseDouble(input, label) ?: return null
        if (value <= 0.0) {
            showToast("$label must be greater than 0.")
            return null
        }
        return value
    }

    private fun parseNonNegativeDouble(input: EditText, label: String): Double? {
        val value = parseDouble(input, label) ?: return null
        if (value < 0.0) {
            showToast("$label cannot be negative.")
            return null
        }
        return value
    }

    private fun parseOptionalPositiveDouble(input: EditText, label: String): OptionalValue<Double>? {
        if (input.text.toString().trim().isBlank()) return OptionalValue(null)
        return parsePositiveDouble(input, label)?.let(::OptionalValue)
    }

    private fun parseOptionalNonNegativeDouble(input: EditText, label: String): OptionalValue<Double>? {
        if (input.text.toString().trim().isBlank()) return OptionalValue(null)
        return parseNonNegativeDouble(input, label)?.let(::OptionalValue)
    }

    private fun parseOptionalPositiveInt(input: EditText, label: String): OptionalValue<Int>? {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) return OptionalValue(null)
        val value = raw.toIntOrNull()
        if (value == null || value <= 0) {
            showToast("$label must be a whole number greater than 0.")
            return null
        }
        return OptionalValue(value)
    }

    private fun parseOptionalYear(input: EditText): OptionalValue<Int>? {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) return OptionalValue(null)
        val value = raw.toIntOrNull()
        if (value == null || value < 1886) {
            showToast("Year must be 1886 or later.")
            return null
        }
        return OptionalValue(value)
    }

    private fun parseOptionalDate(input: EditText, label: String): OptionalValue<LocalDate>? {
        val raw = input.text.toString().trim()
        if (raw.isBlank()) return OptionalValue(null)
        return try {
            OptionalValue(LocalDate.parse(raw, inputDateFormatter))
        } catch (error: RuntimeException) {
            showToast("$label must use YYYY-MM-DD.")
            null
        }
    }

    private fun parseThresholdPercent(input: EditText): Int? {
        val value = input.text.toString().trim().toIntOrNull()
        if (value == null || value !in 0..100) {
            showToast("Due soon threshold must be between 0 and 100.")
            return null
        }
        return value
    }

    private fun parseDouble(input: EditText, label: String): Double? {
        val value = input.text.toString().trim().toDoubleOrNull()
        if (value == null || !value.isFinite()) {
            showToast("$label must be a valid number.")
            return null
        }
        return value
    }

    private fun decimalInputType(): Int =
        InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

    private fun List<Vehicle>.updateVehicleMileage(vehicleId: String, odometer: Double): List<Vehicle> =
        map { vehicle ->
            if (vehicle.id == vehicleId) {
                vehicle.copy(currentMileage = max(vehicle.currentMileage, odometer))
            } else {
                vehicle
            }
        }

    private fun vehicleDescriptor(vehicle: Vehicle): String =
        listOfNotNull(
            vehicle.year?.toString(),
            vehicle.make.takeIf { it.isNotBlank() },
            vehicle.model.takeIf { it.isNotBlank() },
            vehicle.vehicleType.displayLabel.takeIf { it.isNotBlank() },
        ).joinToString(" ")

    private fun vehicleDetailActions(vehicle: Vehicle): Array<Pair<String, () -> Unit>> =
        buildList {
            if (vehicle.vehicleType.usesLiquidFuel) add("Add Fuel" to { showAddFuelEntryDialog(vehicle, EnergyEntryType.FUEL) })
            if (vehicle.vehicleType.usesBattery) add("Add Charging" to { showAddFuelEntryDialog(vehicle, EnergyEntryType.CHARGING) })
            add("Add Item" to { showMaintenanceItemDialog(vehicle) })
            add("Log Service" to { showLogServiceDialog(vehicle) })
            add("Settings" to { showSettingsDialog() })
        }.toTypedArray()

    private fun vehicleCardActions(vehicle: Vehicle): Array<Pair<String, () -> Unit>> =
        buildList {
            add("Open" to { renderVehicleDetail(vehicle.id) })
            if (vehicle.vehicleType.usesLiquidFuel) add("Add Fuel" to { showAddFuelEntryDialog(vehicle, EnergyEntryType.FUEL) })
            if (vehicle.vehicleType.usesBattery) add("Add Charging" to { showAddFuelEntryDialog(vehicle, EnergyEntryType.CHARGING) })
            add("Log Service" to { showLogServiceDialog(vehicle) })
            add("Delete" to { confirmDeleteVehicle(vehicle) })
        }.toTypedArray()

    private fun fuelTypeForVehicleType(vehicleType: VehicleType): FuelType =
        when (vehicleType) {
            VehicleType.GASOLINE,
            VehicleType.HYBRID,
            VehicleType.PLUG_IN_HYBRID,
            VehicleType.MOTORCYCLE,
            -> FuelType.GASOLINE
            VehicleType.DIESEL -> FuelType.DIESEL
            VehicleType.EV -> FuelType.ELECTRIC
            VehicleType.OTHER -> FuelType.OTHER
        }

    private fun entryCountSummary(summary: VehicleSummary): String =
        when {
            summary.vehicle.vehicleType.usesLiquidFuel && summary.vehicle.vehicleType.usesBattery ->
                "Fuel entries: ${summary.fuelEntryCount}   Charging entries: ${summary.chargingEntryCount}"
            summary.vehicle.vehicleType.usesBattery -> "Charging entries: ${summary.chargingEntryCount}"
            else -> "Fuel entries: ${summary.fuelEntryCount}"
        }

    private fun energyCostTitle(vehicle: Vehicle): String =
        when {
            vehicle.vehicleType.usesLiquidFuel && vehicle.vehicleType.usesBattery -> "Energy Cost"
            vehicle.vehicleType.usesBattery -> "Charging Cost"
            else -> "Fuel Cost"
        }

    private fun entryAmountLabel(entryType: EnergyEntryType): String =
        if (entryType == EnergyEntryType.CHARGING) "Energy added" else "Fuel amount"

    private fun fullEntryText(entry: FuelEntry): String =
        when (entry.entryType) {
            EnergyEntryType.CHARGING -> if (entry.isFullTank) "Full charge" else "Partial charge"
            EnergyEntryType.FUEL -> if (entry.isFullTank) "Full tank" else "Partial fill"
        }

    private fun formatRecommendation(state: MaintenanceItemState?): String =
        state?.let {
            if (it.status == MaintenanceStatus.UNKNOWN) {
                "Enter baseline for ${it.item.name}"
            } else {
                "${it.item.name} (${it.status.displayLabel})"
            }
        } ?: "--"

    private fun formatInterval(vehicle: Vehicle, item: MaintenanceItem): String {
        val mileage = item.intervalMiles?.let { "${formatDistance(vehicle, it)} interval" }
        val days = item.intervalTimeDays?.let { "$it day interval" }
        return listOfNotNull(mileage, days).joinToString(" / ").ifBlank { "No interval set" }
    }

    private fun formatTirePsiReminder(vehicle: Vehicle): String {
        val front = vehicle.recommendedFrontTirePsi?.let { formatNumber(it, 1) } ?: "--"
        val rear = vehicle.recommendedRearTirePsi?.let { formatNumber(it, 1) } ?: "--"
        return "Tire recommended PSI: front $front | rear $rear"
    }

    private fun formatLastService(vehicle: Vehicle, state: MaintenanceItemState): String {
        val log = state.lastServiceLog ?: return "No maintenance logs, please record last maintenance"
        val mileage = formatDistance(vehicle, log.odometer)
        val date = log.dateTime.toLocalDate().format(dateFormatter)
        val cost = currencyFormatter.format(log.cost)
        return "Last service: $mileage   $date   $cost"
    }

    private fun MaintenanceTemplateDefinition.toMaintenanceItem(vehicleId: String): MaintenanceItem =
        MaintenanceItem(
            id = UUID.randomUUID().toString(),
            vehicleId = vehicleId,
            categoryId = categoryId,
            name = name,
            intervalMiles = intervalMiles,
            intervalTimeDays = intervalTimeDays,
            importance = importance,
        )

    private fun formatNextDue(vehicle: Vehicle, state: MaintenanceItemState): String {
        if (state.status == MaintenanceStatus.UNKNOWN) {
            return "Needs baseline: log the last known service before due status can be calculated"
        }
        val mileage = state.nextDueMileage?.let { "at ${formatDistance(vehicle, it)}" }
        val date = state.nextDueDate?.let { "by ${it.format(dateFormatter)}" }
        val remaining = listOfNotNull(
            state.milesRemaining?.let { "${formatNumber(it, 1)} ${FuelCalculator.distanceUnitLabel(vehicle)} remaining" },
            state.daysRemaining?.let { "$it days remaining" },
        ).joinToString("   ")
        val due = listOfNotNull(mileage, date).joinToString(" or ")
        return when {
            due.isBlank() -> "Next due: no interval set"
            remaining.isBlank() -> "Next due: $due"
            else -> "Next due: $due   $remaining"
        }
    }

    private fun formatEfficiency(vehicle: Vehicle, value: Double?): String =
        value?.let { "${formatNumber(it, 2)} ${FuelCalculator.efficiencyUnitLabel(vehicle)}" } ?: "--"

    private fun formatDistance(vehicle: Vehicle, value: Double?): String =
        value?.let { "${formatNumber(it, 1)} ${FuelCalculator.distanceUnitLabel(vehicle)}" } ?: "--"

    private fun formatCurrencyPerDistance(value: Double?): String =
        value?.let { currencyFormatter.format(it) } ?: "--"

    private fun formatDistanceAgnostic(value: Double?): String =
        value?.let { formatNumber(it, 1) } ?: "--"

    private fun chartPrimarySummary(unitLabel: String, points: List<ChartPoint>): String {
        val value = points.lastOrNull()?.value ?: return "No data yet"
        return if (unitLabel.equals("USD", ignoreCase = true)) {
            currencyFormatter.format(value)
        } else {
            "${formatNumber(value, if (value >= 100.0) 0 else 1)} $unitLabel".trim()
        }
    }

    private fun chartSecondarySummary(title: String, unitLabel: String, points: List<ChartPoint>): String {
        if (points.isEmpty()) return "No recorded points yet"
        if (points.size == 1) {
            return when {
                title.contains("Maintenance", ignoreCase = true) -> "1 service entry"
                title.contains("Efficiency", ignoreCase = true) -> "1 recorded interval"
                else -> "1 recorded point"
            }
        }

        val delta = points.last().value - points.first().value
        val countSummary = when {
            title.contains("Fuel", ignoreCase = true) || title.contains("Charging", ignoreCase = true) || title.contains("Total Cost", ignoreCase = true) ->
                "${points.size} logged updates"
            title.contains("Maintenance", ignoreCase = true) -> "${points.size} service entries"
            else -> "${points.size} plotted points"
        }
        val deltaSummary = if (unitLabel.equals("USD", ignoreCase = true)) {
            "${if (delta >= 0) "+" else "-"}${currencyFormatter.format(kotlin.math.abs(delta))} since first entry"
        } else {
            "${if (delta >= 0) "+" else "-"}${formatNumber(kotlin.math.abs(delta), 1)} $unitLabel since first entry"
        }
        return "$countSummary   $deltaSummary"
    }

    private fun formatNumber(value: Double, decimals: Int): String =
        "%.${decimals}f".format(Locale.US, value)

    private fun formatPlainNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_NOTIFICATIONS) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            MaintenanceReminderScheduler.sync(this, data)
            showToast("Maintenance reminders enabled.")
        } else {
            showToast("Notification permission was denied. In-app maintenance alerts remain available.")
        }
    }

    private fun notificationStatusText(): String =
        when {
            !data.settings.maintenanceRemindersEnabled -> "Off"
            MaintenanceReminderScheduler.notificationsAllowed(this) -> "On"
            else -> "Permission needed"
        }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String, error: Throwable? = null) {
        val detail = error?.message?.takeIf { it.isNotBlank() }
        MaterialAlertDialogBuilder(this)
            .setTitle("Action Failed")
            .setMessage(if (detail == null) message else "$message\n\nDetail: $detail")
            .setPositiveButton("OK", null)
            .show()
    }

    private data class ChoiceControl<T>(
        val view: View,
        val selected: () -> T,
    )

    private data class OptionalValue<T>(
        val value: T?,
    )

    private fun MaterialButton.applyActionTone(label: String) {
        val normalized = label.lowercase(Locale.US)
        when {
            normalized.contains("delete") -> {
                backgroundTintList = ColorStateList.valueOf(
                    themeColor(com.google.android.material.R.attr.colorErrorContainer, R.color.surface_card_high),
                )
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnErrorContainer, R.color.text_primary))
            }
            normalized.startsWith("add") || normalized.startsWith("log") -> {
                backgroundTintList = ColorStateList.valueOf(
                    themeColor(com.google.android.material.R.attr.colorPrimaryContainer, R.color.fuel_primary_light),
                )
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer, R.color.fuel_primary_dark))
            }
            else -> {
                backgroundTintList = ColorStateList.valueOf(
                    themeColor(com.google.android.material.R.attr.colorSecondaryContainer, R.color.fuel_secondary_light),
                )
                setTextColor(themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer, R.color.fuel_secondary_dark))
            }
        }
    }

    private fun color(resId: Int): Int =
        when (resId) {
            R.color.app_background -> themeColor(com.google.android.material.R.attr.colorSurface, resId)
            R.color.surface_card -> themeColor(com.google.android.material.R.attr.colorSurfaceContainer, resId)
            R.color.surface_card_high -> themeColor(com.google.android.material.R.attr.colorSurfaceContainerHigh, resId)
            R.color.text_primary -> themeColor(com.google.android.material.R.attr.colorOnSurface, resId)
            R.color.text_secondary -> themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, resId)
            R.color.fuel_primary -> themeColor(androidx.appcompat.R.attr.colorPrimary, resId)
            R.color.fuel_primary_dark -> themeColor(com.google.android.material.R.attr.colorOnPrimaryContainer, resId)
            R.color.fuel_primary_light -> themeColor(com.google.android.material.R.attr.colorPrimaryContainer, resId)
            R.color.fuel_secondary -> themeColor(com.google.android.material.R.attr.colorSecondary, resId)
            R.color.fuel_secondary_dark -> themeColor(com.google.android.material.R.attr.colorOnSecondaryContainer, resId)
            R.color.fuel_secondary_light -> themeColor(com.google.android.material.R.attr.colorSecondaryContainer, resId)
            R.color.chart_grid -> themeColor(com.google.android.material.R.attr.colorOutlineVariant, resId)
            R.color.chart_axis -> themeColor(com.google.android.material.R.attr.colorOutline, resId)
            else -> ContextCompat.getColor(this, resId)
        }

    private fun themeColor(attr: Int, fallbackResId: Int): Int =
        MaterialColors.getColor(this, attr, ContextCompat.getColor(this, fallbackResId))

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_BACKUP_JSON = 1002
        private const val REQUEST_RESTORE_JSON = 1003
        private const val REQUEST_NOTIFICATIONS = 1004
    }
}
