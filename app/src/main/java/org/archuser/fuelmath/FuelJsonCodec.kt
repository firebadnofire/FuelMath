package org.archuser.fuelmath

import java.time.LocalDateTime
import java.util.LinkedHashMap

object FuelJsonCodec {
    fun encode(data: FuelMathData): String = buildString {
        appendObjectStart()
        appendField("schemaVersion", data.schemaVersion)
        appendComma()
        appendArrayField("vehicles", data.vehicles) { vehicle ->
            appendObjectStart()
            appendField("id", vehicle.id)
            appendComma()
            appendField("name", vehicle.name)
            appendComma()
            appendField("tankCapacity", vehicle.tankCapacity)
            appendComma()
            appendField("distanceUnit", vehicle.distanceUnit.storageValue)
            appendComma()
            appendField("volumeUnit", vehicle.volumeUnit.storageValue)
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
            appendObjectEnd()
        }
        appendComma()
        appendArrayField("maintenanceEntries", data.maintenanceEntries) { entry ->
            appendObjectStart()
            appendField("id", entry.id)
            appendComma()
            appendField("vehicleId", entry.vehicleId)
            appendComma()
            appendField("dateTime", entry.dateTime.toString())
            appendComma()
            appendField("odometer", entry.odometer)
            appendComma()
            appendField("type", entry.type)
            appendComma()
            appendField("cost", entry.cost)
            appendComma()
            appendField("notes", entry.notes)
            appendObjectEnd()
        }
        appendObjectEnd()
    }

    fun decode(json: String): FuelMathData {
        val root = SimpleJsonParser(json).parseRoot().asObject("root")
        val schemaVersion = root.requiredNumber("schemaVersion").toInt()
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported backup schema version: $schemaVersion"
        }

        val vehicles = root.requiredList("vehicles").map { value ->
            val item = value.asObject("vehicle")
            Vehicle(
                id = item.requiredString("id"),
                name = item.requiredString("name"),
                tankCapacity = item.requiredNumber("tankCapacity"),
                distanceUnit = DistanceUnit.fromStorage(item.requiredString("distanceUnit")),
                volumeUnit = VolumeUnit.fromStorage(item.requiredString("volumeUnit")),
            )
        }

        val fuelEntries = root.requiredList("fuelEntries").map { value ->
            val item = value.asObject("fuel entry")
            FuelEntry(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                dateTime = LocalDateTime.parse(item.requiredString("dateTime")),
                odometer = item.requiredNumber("odometer"),
                fuelAmount = item.requiredNumber("fuelAmount"),
                pricePerUnit = item.requiredNumber("pricePerUnit"),
                isFullTank = item.requiredBoolean("isFullTank"),
            )
        }

        val maintenanceEntries = root.requiredList("maintenanceEntries").map { value ->
            val item = value.asObject("maintenance entry")
            MaintenanceEntry(
                id = item.requiredString("id"),
                vehicleId = item.requiredString("vehicleId"),
                dateTime = LocalDateTime.parse(item.requiredString("dateTime")),
                odometer = item.requiredNumber("odometer"),
                type = item.requiredString("type"),
                cost = item.requiredNumber("cost"),
                notes = item.requiredString("notes"),
            )
        }

        validateReferences(vehicles, fuelEntries, maintenanceEntries)
        return FuelMathData(
            schemaVersion = schemaVersion,
            vehicles = vehicles,
            fuelEntries = fuelEntries,
            maintenanceEntries = maintenanceEntries,
        )
    }

    private fun validateReferences(
        vehicles: List<Vehicle>,
        fuelEntries: List<FuelEntry>,
        maintenanceEntries: List<MaintenanceEntry>,
    ) {
        val vehicleIds = vehicles.map { it.id }.toSet()
        require(vehicles.all { it.id.isNotBlank() && it.name.isNotBlank() }) {
            "Vehicle id and name are required"
        }
        require(vehicles.all { it.tankCapacity.isFinite() && it.tankCapacity > 0.0 }) {
            "Vehicle tank capacities must be positive"
        }
        require(fuelEntries.all { it.vehicleId in vehicleIds }) {
            "Backup contains a fuel entry for an unknown vehicle"
        }
        require(maintenanceEntries.all { it.vehicleId in vehicleIds }) {
            "Backup contains a maintenance entry for an unknown vehicle"
        }
    }

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

private fun Any?.asObject(label: String): Map<String, Any?> =
    this as? Map<String, Any?> ?: throw IllegalArgumentException("Expected $label to be an object")

private fun Map<String, Any?>.requiredString(name: String): String =
    this[name] as? String ?: throw IllegalArgumentException("Expected '$name' to be a string")

private fun Map<String, Any?>.requiredNumber(name: String): Double =
    (this[name] as? Number)?.toDouble() ?: throw IllegalArgumentException("Expected '$name' to be a number")

private fun Map<String, Any?>.requiredBoolean(name: String): Boolean =
    this[name] as? Boolean ?: throw IllegalArgumentException("Expected '$name' to be a boolean")

private fun Map<String, Any?>.requiredList(name: String): List<Any?> =
    this[name] as? List<Any?> ?: throw IllegalArgumentException("Expected '$name' to be an array")
