package com.smartattendance.app.core.roster

import android.content.Context
import android.net.Uri
import android.util.Log
import com.smartattendance.app.core.network.ParsedRosterStudent
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object RosterFileParser {
    private const val TAG = "RosterFileParser"

    // Regular expressions for intelligent fallback matching
    private val ROLL_REGEX = Regex("(?i)\\b(2[0-9][A-Z0-9]{4,10}|[0-9]{6,12}|EMP-[A-Z0-9-]+)\\b")
    private val EMAIL_REGEX = Regex("(?i)[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\\.[a-zA-Z0-9-.]+")

    /**
     * Parses an input stream from a URI based on its file extension or content.
     */
    fun parseRosterUri(context: Context, uri: Uri, fileName: String?): List<ParsedRosterStudent> {
        val name = fileName ?: uri.lastPathSegment ?: "roster.csv"
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseRosterStream(stream, name)
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read roster from URI $uri", e)
            emptyList()
        }
    }

    /**
     * Parses from a stream based on file extension.
     */
    fun parseRosterStream(stream: InputStream, fileName: String): List<ParsedRosterStudent> {
        return when {
            fileName.endsWith(".xlsx", ignoreCase = true) -> parseXlsxStream(stream)
            fileName.endsWith(".csv", ignoreCase = true) ||
            fileName.endsWith(".tsv", ignoreCase = true) ||
            fileName.endsWith(".txt", ignoreCase = true) -> parseDelimitedStream(stream)
            fileName.endsWith(".pdf", ignoreCase = true) -> parsePdfStream(stream)
            else -> {
                // Try CSV/text parsing by default
                parseDelimitedStream(stream)
            }
        }
    }

    /**
     * Parses XLSX format directly via ZipInputStream and XmlPullParser.
     * Extracts shared strings, then row cells from sheet1.xml.
     */
    fun parseXlsxStream(stream: InputStream): List<ParsedRosterStudent> {
        val sharedStrings = mutableListOf<String>()
        val sheetRows = mutableListOf<List<String>>()

        try {
            // First pass: extract sharedStrings and sheet1 data into byte arrays
            var sharedStringsBytes: ByteArray? = null
            var sheetBytes: ByteArray? = null

            val zip = ZipInputStream(stream)
            var entry = zip.nextEntry
            while (entry != null) {
                val entryName = entry.name.lowercase()
                if (entryName.contains("sharedstrings.xml")) {
                    sharedStringsBytes = zip.readBytes()
                } else if (entryName.contains("worksheets/sheet1.xml") ||
                    (sheetBytes == null && entryName.contains("worksheets/sheet") && entryName.endsWith(".xml"))) {
                    sheetBytes = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }

            // 1. Parse sharedStrings if present
            if (sharedStringsBytes != null) {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(sharedStringsBytes.inputStream(), "UTF-8")
                var eventType = parser.eventType
                var inTextTag = false
                val currentString = StringBuilder()

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            if (parser.name == "t") {
                                inTextTag = true
                                currentString.setLength(0)
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inTextTag) {
                                currentString.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            if (parser.name == "t") {
                                inTextTag = false
                                sharedStrings.add(currentString.toString())
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            // 2. Parse sheet rows and cell values
            if (sheetBytes != null) {
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(sheetBytes.inputStream(), "UTF-8")
                var eventType = parser.eventType

                var currentRow = mutableListOf<String>()
                var cellType: String? = null
                var cellValue = StringBuilder()
                var inValueTag = false

                while (eventType != XmlPullParser.END_DOCUMENT) {
                    when (eventType) {
                        XmlPullParser.START_TAG -> {
                            when (parser.name) {
                                "row" -> {
                                    currentRow = mutableListOf()
                                }
                                "c" -> {
                                    cellType = parser.getAttributeValue(null, "t")
                                    cellValue.setLength(0)
                                }
                                "v", "t" -> {
                                    inValueTag = true
                                }
                            }
                        }
                        XmlPullParser.TEXT -> {
                            if (inValueTag) {
                                cellValue.append(parser.text)
                            }
                        }
                        XmlPullParser.END_TAG -> {
                            when (parser.name) {
                                "v", "t" -> {
                                    inValueTag = false
                                }
                                "c" -> {
                                    val rawVal = cellValue.toString().trim()
                                    val resolvedVal = if (cellType == "s") {
                                        val idx = rawVal.toIntOrNull()
                                        if (idx != null && idx >= 0 && idx < sharedStrings.size) {
                                            sharedStrings[idx]
                                        } else {
                                            rawVal
                                        }
                                    } else {
                                        rawVal
                                    }
                                    currentRow.add(resolvedVal)
                                    cellType = null
                                }
                                "row" -> {
                                    if (currentRow.any { it.isNotBlank() }) {
                                        sheetRows.add(currentRow.toList())
                                    }
                                }
                            }
                        }
                    }
                    eventType = parser.next()
                }
            }

            return extractStudentsFromTable(sheetRows)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XLSX stream", e)
            return emptyList()
        }
    }

    /**
     * Parses CSV, TSV, or TXT lines.
     */
    fun parseDelimitedStream(stream: InputStream): List<ParsedRosterStudent> {
        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val rawText = reader.readText()
        return parseFromPlainText(rawText)
    }

    /**
     * Intelligent text / CSV parsing from raw string (supports copy-pasting from PDF/Excel).
     */
    fun parseFromPlainText(rawText: String): List<ParsedRosterStudent> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()

        // Detect delimiter: comma, tab, semicolon, or multi-space
        val table = mutableListOf<List<String>>()
        for (line in lines) {
            val delimiter = when {
                line.contains("\t") -> "\t"
                line.contains(",") -> ","
                line.contains(";") -> ";"
                line.contains("|") -> "|"
                else -> null
            }

            val cells = if (delimiter != null) {
                line.split(delimiter).map { it.trim().trim('"', '\'') }
            } else {
                // Split by 2 or more spaces
                line.split(Regex("\\s{2,}")).map { it.trim() }
            }
            if (cells.any { it.isNotBlank() }) {
                table.add(cells)
            }
        }

        val result = extractStudentsFromTable(table)
        if (result.isNotEmpty()) return result

        // Fallback: Line-by-line regex scanning
        return extractStudentsByRegex(lines)
    }

    /**
     * Extracts students from a PDF stream by scanning text lines and tokens.
     */
    fun parsePdfStream(stream: InputStream): List<ParsedRosterStudent> {
        return try {
            val reader = BufferedReader(InputStreamReader(stream, Charsets.ISO_8859_1))
            val content = reader.readText()
            // Search text for roll numbers and names
            extractStudentsByRegex(content.lines())
        } catch (e: Exception) {
            Log.e(TAG, "Error in parsePdfStream", e)
            emptyList()
        }
    }

    /**
     * Maps a 2D table of cells into a list of ParsedRosterStudent by detecting columns.
     */
    private fun extractStudentsFromTable(rows: List<List<String>>): List<ParsedRosterStudent> {
        if (rows.isEmpty()) return emptyList()

        // 1. Look for header row
        var headerIndex = -1
        var rollCol = -1
        var nameCol = -1
        var emailCol = -1

        for (i in 0 until minOf(5, rows.size)) {
            val row = rows[i]
            for (j in row.indices) {
                val cell = row[j].lowercase()
                if (rollCol == -1 && (cell.contains("roll") || cell.contains("enroll") || cell.contains("reg") || cell == "id" || cell == "student id")) {
                    rollCol = j
                }
                if (nameCol == -1 && (cell.contains("name") || cell.contains("student name") || cell.contains("candidate"))) {
                    nameCol = j
                }
                if (emailCol == -1 && (cell.contains("email") || cell.contains("mail"))) {
                    emailCol = j
                }
            }
            if (rollCol != -1 && nameCol != -1) {
                headerIndex = i
                break
            }
        }

        // If headers weren't found, check if first row itself looks like data or headers
        val startRow = if (headerIndex != -1) headerIndex + 1 else 0
        if (rollCol == -1 || nameCol == -1) {
            // Heuristic detection on the first data row
            val sampleRow = rows.getOrNull(startRow) ?: return emptyList()
            for (j in sampleRow.indices) {
                val cell = sampleRow[j].trim()
                if (rollCol == -1 && ROLL_REGEX.matches(cell)) {
                    rollCol = j
                } else if (emailCol == -1 && EMAIL_REGEX.matches(cell)) {
                    emailCol = j
                } else if (nameCol == -1 && cell.length > 2 && cell.all { it.isLetter() || it.isWhitespace() || it == '.' }) {
                    nameCol = j
                }
            }
        }

        if (rollCol == -1) {
            // Cannot reliably identify roll numbers column
            return emptyList()
        }

        val students = mutableListOf<ParsedRosterStudent>()
        val seenRolls = mutableSetOf<String>()

        for (i in startRow until rows.size) {
            val row = rows[i]
            if (rollCol >= row.size) continue

            val rawRoll = row[rollCol].trim()
            if (rawRoll.isBlank() || rawRoll.equals("roll", ignoreCase = true) || rawRoll.equals("roll no", ignoreCase = true)) {
                continue
            }

            val cleanRoll = rawRoll.uppercase()
            if (seenRolls.contains(cleanRoll)) continue

            val rawName = if (nameCol != -1 && nameCol < row.size) row[nameCol].trim() else "Student $cleanRoll"
            val cleanName = if (rawName.isBlank()) "Student $cleanRoll" else rawName

            val rawEmail = if (emailCol != -1 && emailCol < row.size) row[emailCol].trim() else ""
            val cleanEmail = if (rawEmail.isNotBlank()) {
                rawEmail
            } else {
                "${cleanRoll.lowercase().replace("-", "_")}@student.iiitnr.edu.in"
            }

            students.add(
                ParsedRosterStudent(
                    rollNumber = cleanRoll,
                    name = cleanName,
                    email = cleanEmail,
                    semester = 1,
                    program = "B.Tech DSAI"
                )
            )
            seenRolls.add(cleanRoll)
        }

        return students
    }

    /**
     * Fallback extractor using regex across free-form text lines.
     */
    private fun extractStudentsByRegex(lines: List<String>): List<ParsedRosterStudent> {
        val students = mutableListOf<ParsedRosterStudent>()
        val seenRolls = mutableSetOf<String>()

        for (line in lines) {
            val rollMatch = ROLL_REGEX.find(line) ?: continue
            val roll = rollMatch.value.uppercase()
            if (seenRolls.contains(roll)) continue

            val emailMatch = EMAIL_REGEX.find(line)
            val email = emailMatch?.value ?: "${roll.lowercase().replace("-", "_")}@student.iiitnr.edu.in"

            // Name is remaining text after removing roll and email
            var remaining = line.replace(rollMatch.value, "").trim()
            if (emailMatch != null) {
                remaining = remaining.replace(emailMatch.value, "").trim()
            }
            // Strip punctuation and numbers
            val candidateName = remaining.replace(Regex("[,;|\t\\[\\](){}]"), " ")
                .replace(Regex("^\\s*\\d+\\s*[.-]?\\s*"), "") // strip leading serial number e.g. "1. "
                .trim()
                .replace(Regex("\\s+"), " ")

            val name = if (candidateName.length >= 2) candidateName else "Student $roll"

            students.add(
                ParsedRosterStudent(
                    rollNumber = roll,
                    name = name,
                    email = email
                )
            )
            seenRolls.add(roll)
        }

        return students
    }
}
