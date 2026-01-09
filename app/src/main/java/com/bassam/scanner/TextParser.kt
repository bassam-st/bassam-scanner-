package com.bassam.scanner

object TextParser {

    // HS: 8 or 10 digits
    private val hsRegex = Regex("\\b(\\d{8}|\\d{10})\\b")

    fun parse(text: String): Pair<String, String> {
        val matches = hsRegex.findAll(text).map { it.value }.toList()

        // Prefer 8 digits if multiple found
        val hsCode = matches.firstOrNull { it.length == 8 }
            ?: matches.firstOrNull()
            ?: "N/A"

        val itemName = extractItemName(text)

        return Pair(hsCode, itemName)
    }

    private fun extractItemName(text: String): String {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Look for "تسمية السلعة" (or similar) and take near line
        for (i in lines.indices) {
            val line = lines[i]
            if (line.contains("تسمية", ignoreCase = true) && i + 1 < lines.size) {
                val candidate = lines[i + 1]
                if (candidate.length >= 3) return candidate
            }
            if (line.contains("التسمية", ignoreCase = true) && i + 1 < lines.size) {
                val candidate = lines[i + 1]
                if (candidate.length >= 3) return candidate
            }
        }

        // Fallback: choose longest Arabic-ish segment (10+ chars) that's not just numbers
        val candidates = lines.filter { l ->
            l.length >= 10 && l.any { it in 'ء'..'ي' } && !l.all { it.isDigit() }
        }

        return candidates.maxByOrNull { it.length } ?: "Unknown"
    }
}
