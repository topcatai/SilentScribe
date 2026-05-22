package com.example.mobileaudiowhatsapp.util

data class ParsedMetadata(
    val phoneNumber: String,   // Normalised 10-digit
    val displayName: String?,  // Contact name or formatted prefix, null if absent
    val timestampMs: Long
)

object FilenameParser {

    // Primary: anything(CleanNumber)_YYYYMMDDHHMMSS.ext
    private val PRIMARY = Regex(
        """^(.+)\(([^)]+)\)_(\d{12,14})\.(mp3|m4a|wav|aac|ogg)$""",
        RegexOption.IGNORE_CASE
    )

    // Fallback: optional_name_PhoneNumber_YYYYMMDDHHMMSS.ext
    private val FALLBACK = Regex(
        """^(?:(.+)_)?(\+?\d{10,13})_(\d{12,14})\.(mp3|m4a|wav|aac|ogg)$""",
        RegexOption.IGNORE_CASE
    )

    fun parse(filename: String): ParsedMetadata? {
        PRIMARY.matchEntire(filename)?.let { m ->
            val raw  = m.groups[2]?.value?.trim() ?: return null
            val ts   = m.groups[3]?.value         ?: return null
            return ParsedMetadata(normalise(raw), m.groups[1]?.value?.trim(), parseTs(ts))
        }
        FALLBACK.matchEntire(filename)?.let { m ->
            val raw  = m.groups[2]?.value ?: return null
            val ts   = m.groups[3]?.value ?: return null
            return ParsedMetadata(normalise(raw), m.groups[1]?.value?.trim(), parseTs(ts))
        }
        return null
    }

    // Strip 0091 / +91 / 91 prefix → 10-digit domestic number
    private fun normalise(raw: String): String {
        val d = raw.filter { it.isDigit() }
        return when {
            d.length == 12 && d.startsWith("91")   -> d.drop(2)
            d.length == 13 && d.startsWith("091")  -> d.drop(3)
            d.length == 14 && d.startsWith("0091") -> d.drop(4)
            else -> d
        }
    }

    // Handles 14-digit (YYYYMMDDHHMMSS) and 12-digit (YYYYMMDDHHMM → append "00")
    private fun parseTs(s: String): Long {
        val padded = if (s.length == 12) "${s}00" else s
        return runCatching {
            java.text.SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.US).parse(padded)?.time ?: 0L
        }.getOrDefault(0L)
    }
}
