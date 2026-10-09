package com.smartattendance.app.core.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.smartattendance.app.core.engine.TimetableEngine
import com.smartattendance.app.ui.teacher.ClassScheduleStatus
import com.smartattendance.app.ui.teacher.TeacherClassItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

object GeminiTimetableParser {

    private val DEFAULT_API_KEY: String by lazy {
        try {
            String(android.util.Base64.decode("QVEuQWI4Uk42S1lac0ZQU3hLWnZ1SGJ0QkdzTUpsTUNncWNPM2M2ay1KQlVxQlpibGU0U2c=", android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
        } catch (_: Exception) {
            ""
        }
    }
    private const val PREFS_KEY_NAME = "gemini_api_key_override"
    private const val MODEL_NAME = "gemini-3.6-flash"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    fun getApiKey(context: Context?): String {
        if (context == null) return DEFAULT_API_KEY
        val prefs = context.getSharedPreferences("smart_attendance_prefs", Context.MODE_PRIVATE)
        return prefs.getString(PREFS_KEY_NAME, DEFAULT_API_KEY)?.ifBlank { DEFAULT_API_KEY } ?: DEFAULT_API_KEY
    }

    fun setApiKey(context: Context, newKey: String) {
        val prefs = context.getSharedPreferences("smart_attendance_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString(PREFS_KEY_NAME, newKey.trim()).apply()
    }

    data class ParsedLecture(
        val subjectName: String,
        val subjectCode: String,
        val program: String,
        val room: String,
        val dayOfWeek: Int, // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
        val startTime: String, // HH:mm:ss
        val endTime: String   // HH:mm:ss
    ) {
        fun toTeacherClassItem(): TeacherClassItem {
            val cid = UUID.randomUUID().toString()
            val cleanCode = subjectCode.filter { it.isLetterOrDigit() }.uppercase(Locale.US).take(4).ifEmpty { "SUBJ" }
            val joinCode = "$cleanCode-${kotlin.math.abs(cid.hashCode() % 9000 + 1000)}"
            val dayName = when (dayOfWeek) {
                1 -> "Monday"
                2 -> "Tuesday"
                3 -> "Wednesday"
                4 -> "Thursday"
                5 -> "Friday"
                6 -> "Saturday"
                7 -> "Sunday"
                else -> "Monday"
            }
            val formattedSlot = "$dayName, ${TimetableEngine.formatDisplaySlot(startTime, endTime)}"

            return TeacherClassItem(
                id = cid,
                subjectName = subjectName.trim(),
                subjectCode = subjectCode.trim(),
                program = program.trim(),
                room = room.trim(),
                timeSlot = formattedSlot,
                enrolledStudents = 0,
                isReadyToStart = true,
                status = ClassScheduleStatus.SCHEDULED,
                joinCode = joinCode,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime
            )
        }
    }

    suspend fun parseFromUri(context: Context, imageUri: Uri): Result<List<ParsedLecture>> = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeSampledBitmapFromUri(context, imageUri, reqWidth = 1600, reqHeight = 1600)
                ?: return@withContext Result.failure(Exception("Could not load image from device"))

            parseFromBitmap(context, bitmap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseFromBitmap(context: Context, bitmap: Bitmap): Result<List<ParsedLecture>> = withContext(Dispatchers.IO) {
        try {
            val stream = ByteArrayOutputStream()
            // Compress to JPEG with quality 85 for fast upload & crisp OCR
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            val byteArray = stream.toByteArray()
            val base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP)

            val prompt = buildExtractionPrompt()

            val requestJson = JSONObject().apply {
                val contentsArr = JSONArray()
                val contentObj = JSONObject()
                val partsArr = JSONArray()

                // Text instruction
                partsArr.put(JSONObject().apply {
                    put("text", prompt)
                })

                // Image payload
                partsArr.put(JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    }
                    put("inline_data", inlineData)
                })

                contentObj.put("parts", partsArr)
                contentsArr.put(contentObj)
                put("contents", contentsArr)
            }

            executeGeminiRequest(context, requestJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun parseFromText(context: Context, rawText: String): Result<List<ParsedLecture>> = withContext(Dispatchers.IO) {
        try {
            val prompt = buildExtractionPrompt() + "\n\nTimetable Content:\n$rawText"

            val requestJson = JSONObject().apply {
                val contentsArr = JSONArray()
                val contentObj = JSONObject()
                val partsArr = JSONArray()

                partsArr.put(JSONObject().apply {
                    put("text", prompt)
                })

                contentObj.put("parts", partsArr)
                contentsArr.put(contentObj)
                put("contents", contentsArr)
            }

            executeGeminiRequest(context, requestJson)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun executeGeminiRequest(context: Context, requestJson: JSONObject): Result<List<ParsedLecture>> {
        val apiKey = getApiKey(context)
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        val body = requestJson.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            return Result.failure(Exception("Gemini API Error (${response.code}): $responseBody"))
        }

        val jsonResponse = JSONObject(responseBody)
        val candidates = jsonResponse.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            return Result.failure(Exception("No timetable response generated by Gemini AI."))
        }

        val candidate = candidates.getJSONObject(0)
        val content = candidate.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            return Result.failure(Exception("Empty AI content returned."))
        }

        val textPart = parts.getJSONObject(0).optString("text", "")
        val rawLectures = extractLecturesFromJsonText(textPart)
        val mergedLectures = mergeConsecutiveLectures(rawLectures)

        return if (mergedLectures.isNotEmpty()) {
            Result.success(mergedLectures)
        } else {
            Result.failure(Exception("Could not extract any scheduled lectures from timetable. Response: $textPart"))
        }
    }

    private fun buildExtractionPrompt(): String {
        return """
            You are an academic timetable analyzer AI.
            Analyze the provided timetable image or schedule and extract all lecture/lab classes.

            CRITICAL CONSECUTIVE / CONTINUOUS SLOTS MERGE RULE:
            If the same subject has consecutive, back-to-back, or continuous periods/slots on the same day (for example, Period 1 from 10:00 to 11:00 and Period 2 from 11:00 to 12:00, or a 2-hour / 3-hour practical lab):
            You MUST automatically MERGE them into ONE single continuous lecture slot with the overall start time of the first period and the final end time of the last period (e.g. startTime: "10:00:00", endTime: "12:00:00").
            DO NOT return separate entries for continuous periods of the same course. Merge them together into one slot.

            For each lecture, extract:
            - subjectName: Full subject or course name (e.g., "Computer Networks", "Database Management", "Machine Learning Lab").
            - subjectCode: Subject code (e.g., "CS301", "DS-502", "IT204"). If not explicitly mentioned, generate an appropriate 4-6 char code.
            - program: Academic program/branch/semester (e.g., "B.Tech CSE - Sem 5", "B.Tech DSAI"). Default to "B.Tech CSE" if not visible.
            - room: Classroom/Lab/Hall (e.g., "Room A-302", "Lab 2", "LT-1"). Default to "Room 101" if not specified.
            - dayOfWeek: Integer representing the day of the week:
                1 = Monday
                2 = Tuesday
                3 = Wednesday
                4 = Thursday
                5 = Friday
                6 = Saturday
                7 = Sunday
            - startTime: Start time in 24-hour ISO format "HH:mm:ss" (e.g., "09:00:00", "10:30:00", "14:00:00").
            - endTime: End time in 24-hour ISO format "HH:mm:ss" (e.g., "10:00:00", "11:30:00", "15:00:00").

            IMPORTANT INSTRUCTION:
            Return ONLY a raw valid JSON array of lecture objects.
            Do not enclose in markdown ticks, do not include comments, no conversational text.
            Example:
            [
              {
                "subjectName": "Operating Systems",
                "subjectCode": "CS302",
                "program": "B.Tech CSE - Sem 5",
                "room": "Room A-302",
                "dayOfWeek": 1,
                "startTime": "10:00:00",
                "endTime": "12:00:00"
              }
            ]
        """.trimIndent()
    }

    private fun extractLecturesFromJsonText(rawText: String): List<ParsedLecture> {
        val cleaned = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val jsonArray: JSONArray = try {
            if (cleaned.startsWith("[")) {
                JSONArray(cleaned)
            } else if (cleaned.startsWith("{")) {
                val obj = JSONObject(cleaned)
                obj.optJSONArray("lectures")
                    ?: obj.optJSONArray("classes")
                    ?: obj.optJSONArray("timetable")
                    ?: obj.optJSONArray("schedule")
                    ?: JSONArray()
            } else {
                val start = cleaned.indexOf('[')
                val end = cleaned.lastIndexOf(']')
                if (start in 0 until end) {
                    JSONArray(cleaned.substring(start, end + 1))
                } else {
                    JSONArray()
                }
            }
        } catch (e: Exception) {
            JSONArray()
        }

        val result = mutableListOf<ParsedLecture>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.optJSONObject(i) ?: continue
            val name = item.optString("subjectName").ifBlank { item.optString("name", "Lecture") }
            val code = item.optString("subjectCode").ifBlank { item.optString("code", "SUB101") }
            val program = item.optString("program").ifBlank { "B.Tech CSE" }
            val room = item.optString("room").ifBlank { "Room 101" }

            val rawDay = item.opt("dayOfWeek")
            val dayOfWeek = parseDayOfWeek(rawDay)

            val startTime = normalizeIsoTime(item.optString("startTime", "10:00:00"))
            val endTime = normalizeIsoTime(item.optString("endTime", "11:00:00"))

            result.add(
                ParsedLecture(
                    subjectName = name,
                    subjectCode = code,
                    program = program,
                    room = room,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
        return result
    }

    /**
     * Merge consecutive / back-to-back lecture slots of the same subject on the same day.
     * E.g. 10:00 - 11:00 and 11:00 - 12:00 of CS302 merges into 10:00 - 12:00.
     */
    fun mergeConsecutiveLectures(lectures: List<ParsedLecture>): List<ParsedLecture> {
        if (lectures.size <= 1) return lectures

        val result = mutableListOf<ParsedLecture>()
        val groupedByDay = lectures.groupBy { it.dayOfWeek }

        for ((_, dayLectures) in groupedByDay) {
            val sorted = dayLectures.sortedBy { TimetableEngine.parseTimeToMinutes(it.startTime) }
            val mergedDay = mutableListOf<ParsedLecture>()

            for (curr in sorted) {
                if (mergedDay.isEmpty()) {
                    mergedDay.add(curr)
                    continue
                }

                val prev = mergedDay.last()
                val sameSubject = isSameSubject(prev.subjectCode, prev.subjectName, curr.subjectCode, curr.subjectName)
                val prevEndMin = TimetableEngine.parseTimeToMinutes(prev.endTime)
                val currStartMin = TimetableEngine.parseTimeToMinutes(curr.startTime)
                val currEndMin = TimetableEngine.parseTimeToMinutes(curr.endTime)

                // If same subject, and consecutive (starts within 15 min of prev end and ends after prev)
                if (sameSubject && currStartMin <= (prevEndMin + 15) && currEndMin > prevEndMin) {
                    val merged = prev.copy(
                        endTime = curr.endTime,
                        room = if (prev.room.isNotBlank() && prev.room != "Room 101") prev.room else curr.room,
                        program = if (prev.program.isNotBlank()) prev.program else curr.program
                    )
                    mergedDay[mergedDay.lastIndex] = merged
                } else {
                    mergedDay.add(curr)
                }
            }
            result.addAll(mergedDay)
        }

        return result.sortedWith(compareBy({ it.dayOfWeek }, { TimetableEngine.parseTimeToMinutes(it.startTime) }))
    }

    fun isSameSubject(codeA: String, nameA: String, codeB: String, nameB: String): Boolean {
        val cleanCodeA = codeA.trim().uppercase(Locale.US).filter { it.isLetterOrDigit() }
        val cleanCodeB = codeB.trim().uppercase(Locale.US).filter { it.isLetterOrDigit() }
        if (cleanCodeA.isNotEmpty() && cleanCodeB.isNotEmpty() && cleanCodeA == cleanCodeB) {
            return true
        }

        val cleanNameA = nameA.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        val cleanNameB = nameB.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        if (cleanNameA.isNotEmpty() && cleanNameB.isNotEmpty()) {
            if (cleanNameA == cleanNameB) return true
            if (cleanNameA.contains(cleanNameB) || cleanNameB.contains(cleanNameA)) return true
        }
        return false
    }

    private fun parseDayOfWeek(raw: Any?): Int {
        if (raw is Number) {
            val n = raw.toInt()
            if (n in 1..7) return n
        }
        val s = raw?.toString()?.trim()?.uppercase(Locale.US) ?: return 1
        return when {
            s.startsWith("MON") -> 1
            s.startsWith("TUE") -> 2
            s.startsWith("WED") -> 3
            s.startsWith("THU") -> 4
            s.startsWith("FRI") -> 5
            s.startsWith("SAT") -> 6
            s.startsWith("SUN") -> 7
            s == "1" -> 1
            s == "2" -> 2
            s == "3" -> 3
            s == "4" -> 4
            s == "5" -> 5
            s == "6" -> 6
            s == "7" -> 7
            else -> 1
        }
    }

    private fun normalizeIsoTime(raw: String): String {
        val clean = raw.trim().uppercase(Locale.US)
        if (clean.contains("AM") || clean.contains("PM")) {
            val isPm = clean.contains("PM")
            val isAm = clean.contains("AM")
            val timePart = clean.replace("AM", "").replace("PM", "").trim()
            val parts = timePart.split(":")
            var hour = parts.getOrNull(0)?.toIntOrNull() ?: 10
            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if (isPm && hour < 12) hour += 12
            if (isAm && hour == 12) hour = 0
            return String.format(Locale.US, "%02d:%02d:00", hour, min)
        }

        val parts = clean.split(":")
        if (parts.size >= 2) {
            val hour = parts[0].toIntOrNull() ?: 10
            val min = parts[1].toIntOrNull() ?: 0
            return String.format(Locale.US, "%02d:%02d:00", hour, min)
        }
        return "10:00:00"
    }

    private fun decodeSampledBitmapFromUri(context: Context, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        var input: InputStream? = null
        return try {
            input = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            input = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input, null, options)
        } catch (e: Exception) {
            null
        } finally {
            input?.close()
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
