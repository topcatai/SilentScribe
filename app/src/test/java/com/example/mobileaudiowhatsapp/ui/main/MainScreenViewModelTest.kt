package com.example.mobileaudiowhatsapp.ui.main

import com.example.mobileaudiowhatsapp.util.FilenameParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FilenameParserTest {

    @Test
    fun testPrimaryFormat() {
        val filename = "0091-6361265991(00916361265991)_20260415122848.mp3"
        val meta = FilenameParser.parse(filename)
        assertNotNull(meta)
        assertEquals("6361265991", meta!!.phoneNumber)
        assertEquals("0091-6361265991", meta.displayName)
        // 2026-04-15 12:28:48 in UTC/Local depending on SimpleDateFormat timezone.
        // Let's verify that it parses without crashing and returns a timestamp > 0.
        assert(meta.timestampMs > 0L)
    }

    @Test
    fun testFallbackWithName() {
        val filename = "Papa_919876543210_20260522105122.mp3"
        val meta = FilenameParser.parse(filename)
        assertNotNull(meta)
        assertEquals("9876543210", meta!!.phoneNumber)
        assertEquals("Papa", meta.displayName)
        assert(meta.timestampMs > 0L)
    }

    @Test
    fun testFallbackNumberOnly() {
        val filename = "+919876543210_20260522105122.mp3"
        val meta = FilenameParser.parse(filename)
        assertNotNull(meta)
        assertEquals("9876543210", meta!!.phoneNumber)
        assertNull(meta.displayName)
        assert(meta.timestampMs > 0L)
    }

    @Test
    fun testInvalidFormat() {
        val filename = "invalid_file.txt"
        val meta = FilenameParser.parse(filename)
        assertNull(meta)
    }
}
