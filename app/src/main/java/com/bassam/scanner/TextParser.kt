package com.bassam.scanner

object TextParser {

    data class Parsed(val hsCode: String?, val itemName: String?)

    fun parse(raw: String): Parsed {
        val cleaned = raw.replace("\u200F", " ").replace("\u200E", " ")
        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }

        val hs = findHsCode(lines)
        val name = findItemName(lines, hs)

        return Parsed(hsCode = hs, itemName = name)
    }

    private fun findHsCode(lines: List<String>): String? {
        val joined = lines.joinToString(" ")
        // اليمن غالباً 8 أرقام
        return Regex("""\b\d{8}\b""").find(joined)?.value
    }

    private fun findItemName(lines: List<String>, hs: String?): String? {
        // 1) بعد "تسمية السلعة" أو "التسمية التجارية"
        val idx = lines.indexOfFirst { it.contains("تسمية السلعة") || it.contains("التسمية التجارية") }
        if (idx >= 0) {
            val same = lines[idx]
            val afterColon = same.split(":", "：").getOrNull(1)?.trim()
            if (!afterColon.isNullOrBlank()) return normalize(afterColon)

            val next = lines.getOrNull(idx + 1)
            if (!next.isNullOrBlank()) return normalize(next)
        }

        // 2) قريب من سطر HS إن وجد
        if (!hs.isNullOrBlank()) {
            val hsIndex = lines.indexOfFirst { it.contains(hs) }
            if (hsIndex >= 0) {
                val window = (hsIndex..minOf(hsIndex + 6, lines.lastIndex)).map { lines[it] }
                val candidate = window
                    .filter { it.any { ch -> ch in '\u0600'..'\u06FF' } }
                    .filter { it.length >= 8 }
                    .maxByOrNull { it.length }
                if (!candidate.isNullOrBlank()) return normalize(candidate)
            }
        }

        // 3) fallback: أول سطر عربي طويل
        return lines.firstOrNull { it.any { ch -> ch in '\u0600'..'\u06FF' } && it.length >= 10 }
            ?.let { normalize(it) }
    }

    private fun normalize(s: String): String =
        s.replace(Regex("""\s+"""), " ").trim()
}
