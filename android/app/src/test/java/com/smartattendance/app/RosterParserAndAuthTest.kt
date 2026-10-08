package com.smartattendance.app

import com.smartattendance.app.core.network.SupabaseAttendanceService
import com.smartattendance.app.core.roster.RosterFileParser
import org.junit.Assert.*
import org.junit.Test

class RosterParserAndAuthTest {

    @Test
    fun testPlainTextRosterParsing() {
        val sampleCsv = """
            Roll Number, Name, Email, Semester
            26DSAI001, Rahul Kumar, rahul@student.iiitnr.edu.in, 1
            26DSAI002, Aman Singh, aman@student.iiitnr.edu.in, 1
            26DSAI003, Priya Sharma, priya@student.iiitnr.edu.in, 1
        """.trimIndent()

        val parsed = RosterFileParser.parseFromPlainText(sampleCsv)
        assertEquals(3, parsed.size)
        assertEquals("26DSAI001", parsed[0].rollNumber)
        assertEquals("Rahul Kumar", parsed[0].name)
        assertEquals("rahul@student.iiitnr.edu.in", parsed[0].email)

        assertEquals("26DSAI002", parsed[1].rollNumber)
        assertEquals("Aman Singh", parsed[1].name)
    }

    @Test
    fun testTabSeparatedRosterParsing() {
        val tabList = "263200113\tNihal Kumar\tnihal@student.iiitnr.edu.in\n263200114\tAnanya Mishra\tananya@student.iiitnr.edu.in"
        val parsed = RosterFileParser.parseFromPlainText(tabList)
        assertEquals(2, parsed.size)
        assertEquals("263200113", parsed[0].rollNumber)
        assertEquals("Nihal Kumar", parsed[0].name)
        assertEquals("263200114", parsed[1].rollNumber)
        assertEquals("Ananya Mishra", parsed[1].name)
    }

    @Test
    fun testPasswordHashingStability() {
        val hash1 = SupabaseAttendanceService.hashPassword("password123")
        val hash2 = SupabaseAttendanceService.hashPassword("password123")
        val diffHash = SupabaseAttendanceService.hashPassword("different456")

        assertNotNull(hash1)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, diffHash)
        assertEquals(64, hash1.length) // SHA-256 hex string length
    }
}
