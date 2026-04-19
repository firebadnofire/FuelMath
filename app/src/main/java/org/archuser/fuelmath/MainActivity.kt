package org.archuser.fuelmath

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import java.io.IOException
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

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
        val root = createScreenRoot()

        root.addText("Fuel Math", 28f, Typeface.BOLD)
        root.addText("Vehicles", 18f, Typeface.BOLD, R.color.text_secondary)
        root.addActions(
            "Add Vehicle" to { showAddVehicleDialog() },
            "Export CSV" to { createDocument(REQUEST_EXPORT_CSV, "text/csv", "fuel-math-export.csv") },
            "Backup" to { createDocument(REQUEST_BACKUP_JSON, "application/json", "fuel-math-backup.json") },
            "Restore" to { openRestoreDocument() },
        )

        if (data.vehicles.isEmpty()) {
            root.addText("No vehicles yet.", 16f, Typeface.NORMAL, R.color.text_secondary)
            return
        }

        data.vehicles.sortedBy { it.name.lowercase() }.forEach { vehicle ->
            val summary = FuelCalculator.buildVehicleSummary(vehicle, data.fuelEntries, data.maintenanceEntries)
            root.addView(buildVehicleCard(summary))
        }
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
        val root = createScreenRoot()
        val fuelEntries = FuelCalculator.entriesForVehicle(data.fuelEntries, vehicle.id)
        val maintenanceEntries = FuelCalculator.maintenanceForVehicle(data.maintenanceEntries, vehicle.id)
        val summary = FuelCalculator.buildVehicleSummary(vehicle, data.fuelEntries, data.maintenanceEntries)

        root.addText(vehicle.name, 26f, Typeface.BOLD)
        root.addActions(
            "Back" to { renderMainScreen() },
            "Add Entry" to { showAddFuelEntryDialog(vehicle) },
            "Add Maintenance" to { showAddMaintenanceDialog(vehicle) },
        )

        root.addView(buildSummaryCard(summary))

        root.addText("Charts", 18f, Typeface.BOLD, R.color.text_secondary)
        root.addView(
            buildChartCard(
                title = "Efficiency",
                unitLabel = FuelCalculator.efficiencyUnitLabel(vehicle),
                points = FuelCalculator.buildEfficiencyChart(vehicle, fuelEntries),
            ),
        )
        root.addView(
            buildChartCard(
                title = "Fuel Cost",
                unitLabel = currencyFormatter.currency?.currencyCode.orEmpty(),
                points = FuelCalculator.buildCostOverTime(fuelEntries),
            ),
        )
        root.addView(
            buildChartCard(
                title = "Fuel Price",
                unitLabel = currencyFormatter.currency?.currencyCode.orEmpty(),
                points = FuelCalculator.buildFuelPriceTrend(fuelEntries),
            ),
        )

        root.addText("Fuel Log", 18f, Typeface.BOLD, R.color.text_secondary)
        if (fuelEntries.isEmpty()) {
            root.addText("No fuel entries yet.", 16f, Typeface.NORMAL, R.color.text_secondary)
        } else {
            val distances = FuelCalculator.calculateEntryDistances(fuelEntries)
                .associate { it.entry.id to it.distanceFromPrevious }
            fuelEntries.sortedWith(compareByDescending<FuelEntry> { it.dateTime }.thenByDescending { it.odometer })
                .forEach { entry ->
                    root.addView(buildFuelEntryCard(vehicle, entry, distances[entry.id]))
                }
        }

        root.addText("Maintenance", 18f, Typeface.BOLD, R.color.text_secondary)
        if (maintenanceEntries.isEmpty()) {
            root.addText("No maintenance entries yet.", 16f, Typeface.NORMAL, R.color.text_secondary)
        } else {
            maintenanceEntries.sortedWith(compareByDescending<MaintenanceEntry> { it.dateTime }.thenByDescending { it.odometer })
                .forEach { entry ->
                    root.addView(buildMaintenanceCard(vehicle, entry))
                }
        }
    }

    private fun buildVehicleCard(summary: VehicleSummary): View {
        val vehicle = summary.vehicle
        return buildCard(clickable = true).apply {
            setOnClickListener { renderVehicleDetail(vehicle.id) }
            val content = cardContent()
            content.addText(vehicle.name, 20f, Typeface.BOLD)
            content.addText("Efficiency: ${formatEfficiency(vehicle, summary.lastEfficiency)}")
            content.addText("Total fuel cost: ${currencyFormatter.format(summary.totalFuelCost)}")
            content.addText("Cost per ${FuelCalculator.distanceUnitLabel(vehicle)}: ${formatCurrencyPerDistance(summary.costPerDistance)}")
            content.addText("Last fill-up: ${summary.lastFillUpDate?.format(dateFormatter) ?: "--"}")
            content.addText("Estimated range: ${formatDistance(vehicle, summary.estimatedRange)}")
            content.addText("Fuel entries: ${summary.fuelEntryCount}   Maintenance: ${summary.maintenanceEntryCount}", colorRes = R.color.text_secondary)
            content.addActions(
                "Open" to { renderVehicleDetail(vehicle.id) },
                "Delete" to { confirmDeleteVehicle(vehicle) },
            )
            addView(content)
        }
    }

    private fun buildSummaryCard(summary: VehicleSummary): View {
        val vehicle = summary.vehicle
        return buildCard().apply {
            val content = cardContent()
            content.addText("Summary", 18f, Typeface.BOLD)
            content.addText("Efficiency: ${formatEfficiency(vehicle, summary.lastEfficiency)}")
            content.addText("Total distance: ${formatDistance(vehicle, summary.totalDistance)}")
            content.addText("Total fuel cost: ${currencyFormatter.format(summary.totalFuelCost)}")
            content.addText("Cost per ${FuelCalculator.distanceUnitLabel(vehicle)}: ${formatCurrencyPerDistance(summary.costPerDistance)}")
            content.addText("Estimated range: ${formatDistance(vehicle, summary.estimatedRange)}")
            content.addText("Tank capacity: ${formatNumber(vehicle.tankCapacity, 2)} ${FuelCalculator.volumeUnitLabel(vehicle)}")
            addView(content)
        }
    }

    private fun buildChartCard(title: String, unitLabel: String, points: List<ChartPoint>): View =
        buildCard().apply {
            val chart = LineChartView(this@MainActivity).apply {
                setChartData(title, unitLabel, points)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    180.dp(),
                )
            }
            addView(chart)
        }

    private fun buildFuelEntryCard(vehicle: Vehicle, entry: FuelEntry, distance: Double?): View =
        buildCard().apply {
            val content = cardContent()
            content.addText(entry.dateTime.format(dateTimeFormatter), 17f, Typeface.BOLD)
            content.addText("Odometer: ${formatDistance(vehicle, entry.odometer)}")
            content.addText("Fuel: ${formatNumber(entry.fuelAmount, 3)} ${FuelCalculator.volumeUnitLabel(vehicle)}")
            content.addText("Price: ${currencyFormatter.format(entry.pricePerUnit)} per ${FuelCalculator.volumeUnitLabel(vehicle)}")
            content.addText("Total: ${currencyFormatter.format(entry.totalCost)}")
            content.addText("Distance since previous: ${formatDistance(vehicle, distance)}")
            content.addText(if (entry.isFullTank) "Full tank" else "Partial fill", colorRes = R.color.text_secondary)
            content.addActions("Delete" to { confirmDeleteFuelEntry(vehicle, entry) })
            addView(content)
        }

    private fun buildMaintenanceCard(vehicle: Vehicle, entry: MaintenanceEntry): View =
        buildCard().apply {
            val content = cardContent()
            content.addText(entry.type, 17f, Typeface.BOLD)
            content.addText(entry.dateTime.format(dateTimeFormatter))
            content.addText("Odometer: ${formatDistance(vehicle, entry.odometer)}")
            content.addText("Cost: ${currencyFormatter.format(entry.cost)}")
            if (entry.notes.isNotBlank()) {
                content.addText(entry.notes, colorRes = R.color.text_secondary)
            }
            content.addActions("Delete" to { confirmDeleteMaintenance(vehicle, entry) })
            addView(content)
        }

    private fun showAddVehicleDialog() {
        val nameInput = createInput("Name", InputType.TYPE_CLASS_TEXT)
        val tankInput = createInput("Tank capacity", decimalInputType())
        val distanceSpinner = createSpinner(listOf("Miles (mi)", "Kilometers (km)"))
        val volumeSpinner = createSpinner(listOf("Gallons (gal)", "Liters (L)"))

        val form = dialogForm().apply {
            addView(labeledView("Name", nameInput))
            addView(labeledView("Tank capacity", tankInput))
            addView(labeledView("Distance unit", distanceSpinner))
            addView(labeledView("Volume unit", volumeSpinner))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Vehicle")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val tankCapacity = parsePositiveDouble(tankInput, "Tank capacity") ?: return@setOnClickListener
                if (name.isBlank()) {
                    showToast("Vehicle name is required.")
                    return@setOnClickListener
                }

                val vehicle = Vehicle(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    tankCapacity = tankCapacity,
                    distanceUnit = if (distanceSpinner.selectedItemPosition == 0) DistanceUnit.MILES else DistanceUnit.KILOMETERS,
                    volumeUnit = if (volumeSpinner.selectedItemPosition == 0) VolumeUnit.GALLONS else VolumeUnit.LITERS,
                )
                if (persistData(data.copy(vehicles = data.vehicles + vehicle), "Vehicle saved.")) {
                    dialog.dismiss()
                    renderMainScreen()
                }
            }
        }
        dialog.show()
    }

    private fun showAddFuelEntryDialog(vehicle: Vehicle) {
        val now = LocalDateTime.now()
        val dateInput = createInput("Date", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalDate().format(inputDateFormatter))
        }
        val timeInput = createInput("Time", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalTime().withSecond(0).withNano(0).format(inputTimeFormatter))
        }
        val odometerInput = createInput("Odometer", decimalInputType())
        val fuelInput = createInput("Fuel amount", decimalInputType())
        val priceInput = createInput("Price per ${FuelCalculator.volumeUnitLabel(vehicle)}", decimalInputType())
        val fullTankSwitch = SwitchCompat(this).apply {
            text = "Full tank"
            isChecked = true
            setTextColor(color(R.color.text_primary))
        }

        val form = dialogForm().apply {
            addView(labeledView("Date (YYYY-MM-DD)", dateInput))
            addView(labeledView("Time (HH:MM)", timeInput))
            addView(labeledView("Odometer (${FuelCalculator.distanceUnitLabel(vehicle)})", odometerInput))
            addView(labeledView("Fuel amount (${FuelCalculator.volumeUnitLabel(vehicle)})", fuelInput))
            addView(labeledView("Price per ${FuelCalculator.volumeUnitLabel(vehicle)}", priceInput))
            addView(fullTankSwitch)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Fuel Entry")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val dateTime = parseDateTime(dateInput, timeInput) ?: return@setOnClickListener
                val odometer = parseNonNegativeDouble(odometerInput, "Odometer") ?: return@setOnClickListener
                val fuelAmount = parsePositiveDouble(fuelInput, "Fuel amount") ?: return@setOnClickListener
                val pricePerUnit = parseNonNegativeDouble(priceInput, "Price per unit") ?: return@setOnClickListener

                val entry = FuelEntry(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicle.id,
                    dateTime = dateTime,
                    odometer = odometer,
                    fuelAmount = fuelAmount,
                    pricePerUnit = pricePerUnit,
                    isFullTank = fullTankSwitch.isChecked,
                )
                if (persistData(data.copy(fuelEntries = data.fuelEntries + entry), "Fuel entry saved.")) {
                    dialog.dismiss()
                    renderVehicleDetail(vehicle.id)
                }
            }
        }
        dialog.show()
    }

    private fun showAddMaintenanceDialog(vehicle: Vehicle) {
        val now = LocalDateTime.now()
        val dateInput = createInput("Date", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalDate().format(inputDateFormatter))
        }
        val timeInput = createInput("Time", InputType.TYPE_CLASS_DATETIME).apply {
            setText(now.toLocalTime().withSecond(0).withNano(0).format(inputTimeFormatter))
        }
        val odometerInput = createInput("Odometer", decimalInputType())
        val typeInput = createInput("Type", InputType.TYPE_CLASS_TEXT)
        val costInput = createInput("Cost", decimalInputType()).apply {
            setText("0")
        }
        val notesInput = createInput("Notes", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = 2
        }

        val form = dialogForm().apply {
            addView(labeledView("Date (YYYY-MM-DD)", dateInput))
            addView(labeledView("Time (HH:MM)", timeInput))
            addView(labeledView("Odometer (${FuelCalculator.distanceUnitLabel(vehicle)})", odometerInput))
            addView(labeledView("Type", typeInput))
            addView(labeledView("Cost", costInput))
            addView(labeledView("Notes", notesInput))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Maintenance")
            .setView(scrollDialogView(form))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val dateTime = parseDateTime(dateInput, timeInput) ?: return@setOnClickListener
                val odometer = parseNonNegativeDouble(odometerInput, "Odometer") ?: return@setOnClickListener
                val type = typeInput.text.toString().trim()
                val cost = parseNonNegativeDouble(costInput, "Cost") ?: return@setOnClickListener
                if (type.isBlank()) {
                    showToast("Maintenance type is required.")
                    return@setOnClickListener
                }

                val entry = MaintenanceEntry(
                    id = UUID.randomUUID().toString(),
                    vehicleId = vehicle.id,
                    dateTime = dateTime,
                    odometer = odometer,
                    type = type,
                    cost = cost,
                    notes = notesInput.text.toString().trim(),
                )
                if (persistData(data.copy(maintenanceEntries = data.maintenanceEntries + entry), "Maintenance saved.")) {
                    dialog.dismiss()
                    renderVehicleDetail(vehicle.id)
                }
            }
        }
        dialog.show()
    }

    private fun confirmDeleteVehicle(vehicle: Vehicle) {
        AlertDialog.Builder(this)
            .setTitle("Delete Vehicle")
            .setMessage("Delete ${vehicle.name} and all of its fuel and maintenance entries?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                val updated = data.copy(
                    vehicles = data.vehicles.filterNot { it.id == vehicle.id },
                    fuelEntries = data.fuelEntries.filterNot { it.vehicleId == vehicle.id },
                    maintenanceEntries = data.maintenanceEntries.filterNot { it.vehicleId == vehicle.id },
                )
                if (persistData(updated, "Vehicle deleted.")) renderMainScreen()
            }
            .show()
    }

    private fun confirmDeleteFuelEntry(vehicle: Vehicle, entry: FuelEntry) {
        AlertDialog.Builder(this)
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

    private fun confirmDeleteMaintenance(vehicle: Vehicle, entry: MaintenanceEntry) {
        AlertDialog.Builder(this)
            .setTitle("Delete Maintenance")
            .setMessage("Delete ${entry.type} from ${entry.dateTime.format(dateFormatter)}?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                if (persistData(data.copy(maintenanceEntries = data.maintenanceEntries.filterNot { it.id == entry.id }), "Maintenance deleted.")) {
                    renderVehicleDetail(vehicle.id)
                }
            }
            .show()
    }

    private fun confirmRestore(restoredData: FuelMathData) {
        AlertDialog.Builder(this)
            .setTitle("Restore Backup")
            .setMessage(
                "Replace local data with ${restoredData.vehicles.size} vehicles, " +
                    "${restoredData.fuelEntries.size} fuel entries, and " +
                    "${restoredData.maintenanceEntries.size} maintenance entries?",
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
            REQUEST_EXPORT_CSV -> writeDocument(uri, FuelCalculator.buildExportCsv(data), "CSV export saved.")
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

    private fun createScreenRoot(): LinearLayout {
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(color(R.color.app_background))
            isFillViewport = false
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp(), 16.dp(), 16.dp(), 24.dp())
        }
        scrollView.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        setContentView(scrollView)
        return root
    }

    private fun buildCard(clickable: Boolean = false): MaterialCardView =
        MaterialCardView(this).apply {
            radius = 8.dp().toFloat()
            cardElevation = 2.dp().toFloat()
            useCompatPadding = true
            setCardBackgroundColor(color(R.color.surface_card))
            isClickable = clickable
            isFocusable = clickable
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = 12.dp()
            }
        }

    private fun cardContent(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 12.dp(), 14.dp(), 14.dp())
        }

    private fun LinearLayout.addText(
        value: String,
        sizeSp: Float = 14f,
        style: Int = Typeface.NORMAL,
        colorRes: Int = R.color.text_primary,
    ): TextView {
        val view = TextView(this@MainActivity).apply {
            text = value
            textSize = sizeSp
            typeface = Typeface.DEFAULT_BOLD.takeIf { style == Typeface.BOLD } ?: Typeface.DEFAULT
            setTextColor(color(colorRes))
            includeFontPadding = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                bottomMargin = 4.dp()
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
                Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    setOnClickListener { action() }
                    minHeight = 44.dp()
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
            setPadding(4.dp(), 4.dp(), 4.dp(), 4.dp())
        }

    private fun scrollDialogView(content: View): View =
        ScrollView(this).apply {
            addView(content)
            setPadding(16.dp(), 0, 16.dp(), 0)
        }

    private fun labeledView(label: String, child: View): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val labelView = TextView(this@MainActivity).apply {
                text = label
                textSize = 13f
                setTextColor(color(R.color.text_secondary))
            }
            addView(labelView)
            addView(
                child,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    bottomMargin = 10.dp()
                },
            )
        }

    private fun createInput(hint: String, inputType: Int): EditText =
        EditText(this).apply {
            this.hint = hint
            this.inputType = inputType
            setSingleLine(inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
        }

    private fun createSpinner(labels: List<String>): Spinner =
        Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                labels,
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
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

    private fun formatEfficiency(vehicle: Vehicle, value: Double?): String =
        value?.let { "${formatNumber(it, 2)} ${FuelCalculator.efficiencyUnitLabel(vehicle)}" } ?: "--"

    private fun formatDistance(vehicle: Vehicle, value: Double?): String =
        value?.let { "${formatNumber(it, 1)} ${FuelCalculator.distanceUnitLabel(vehicle)}" } ?: "--"

    private fun formatCurrencyPerDistance(value: Double?): String =
        value?.let { currencyFormatter.format(it) } ?: "--"

    private fun formatNumber(value: Double, decimals: Int): String =
        "%.${decimals}f".format(Locale.US, value)

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showError(message: String, error: Throwable? = null) {
        val detail = error?.message?.takeIf { it.isNotBlank() }
        AlertDialog.Builder(this)
            .setTitle("Action Failed")
            .setMessage(if (detail == null) message else "$message\n\nDetail: $detail")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun color(resId: Int): Int = ContextCompat.getColor(this, resId)

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_EXPORT_CSV = 1001
        private const val REQUEST_BACKUP_JSON = 1002
        private const val REQUEST_RESTORE_JSON = 1003
    }
}
