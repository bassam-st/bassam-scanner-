package com.bassam.scanner

object TextParser {

    // HS: 8 or 10 digits (prefer 8)
    private val hsRegex = Regex("\\b(\\d{8}|\\d{10})\\b")

    // Common Arabic labels/anchors in customs forms
    private val hsAnchors = listOf(
        "البند التعريفي",
        "البند التعرفة",
        "البند التعريفة",
        "بند التعريفة",
        "بند التعريفي",
        "تعريفي",
        "التعريفي"
    )

    fun parse(text: String): Pair<String, String> {
        val normalized = normalize(text)

        val hsCode = extractHsByAnchor(normalized)
            ?: extractHsFallback(normalized)

        val itemName = extractItemNameByField31(normalized)
            ?: extractItemNameFallback(normalized)

        return Pair(hsCode ?: "N/A", itemName ?: "Unknown")
    }

    /**
     * Try to extract HS code near "البند التعريفي" first.
     * Strategy:
     * - Find anchor line, then scan next 1-3 lines for first HS number (prefer 8 digits).
     * - If not found, scan same line after the anchor.
     */
    private fun extractHsByAnchor(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        fun pickPrefer8(nums: List<String>): String? {
            return nums.firstOrNull { it.length == 8 } ?: nums.firstOrNull()
        }

        for (i in lines.indices) {
            val line = lines[i]
            if (hsAnchors.any { a -> line.contains(a) }) {
                // 1) scan same line
                val sameLineNums = hsRegex.findAll(line).map { it.value }.toList()
                pickPrefer8(sameLineNums)?.let { return it }

                // 2) scan next few lines (under the header)
                val windowEnd = minOf(lines.size - 1, i + 3)
                val windowText = buildString {
                    for (j in (i + 1)..windowEnd) append(lines[j]).append('\n')
                }
                val nums = hsRegex.findAll(windowText).map { it.value }.toList()
                pickPrefer8(nums)?.let { return it }
            }
        }
        return null
    }

    /**
     * Fallback: pick first plausible HS from whole text (prefer 8 digits).
     * Also tries to avoid picking obvious non-HS numbers by simple heuristics.
     */
    private fun extractHsFallback(text: String): String? {
        val matches = hsRegex.findAll(text).map { it.value }.toList()
        if (matches.isEmpty()) return null

        // Prefer 8 digits
        val prefer8 = matches.firstOrNull { it.length == 8 }
        if (prefer8 != null) return prefer8

        return matches.firstOrNull()
    }

    /**
     * Try to extract item name from field 31:
     * - Find a line that contains "31" as a standalone field number or appears as "31 اسم"
     * - Then collect subsequent lines until next field number (e.g., 32, 33, 34...) shows up
     * - Return the LAST meaningful Arabic text line (as per your requirement)
     *
     * NOTE: Without bounding boxes, this is best-effort only.
     */
    private fun extractItemNameByField31(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Detect a line that strongly indicates field 31
        fun isField31Line(l: String): Boolean {
            // standalone "31" or "31 :" or "31." etc
            if (Regex("(^|\\s)31(\\s|$|[:.،])").containsMatchIn(l)) return true
            // sometimes appears like "31 - ..." or "31 ..."
            if (Regex("^31\\b").containsMatchIn(l)) return true
            return false
        }

        // Detect next field header (32..50) to stop
        fun isNextFieldHeader(l: String): Boolean {
            return Regex("(^|\\s)(3[2-9]|4\\d|50)(\\s|$|[:.،])").containsMatchIn(l)
        }

        val startIdx = lines.indexOfFirst { isField31Line(it) }
        if (startIdx == -1) return null

        // Collect lines after 31 until next field header
        val collected = mutableListOf<String>()
        for (i in (startIdx + 1) until lines.size) {
            val l = lines[i]
            if (isNextFieldHeader(l)) break
            collected.add(l)
        }

        if (collected.isEmpty()) return null

        // Return last meaningful Arabic-ish line
        val meaningful = collected
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { isMeaningfulNameLine(it) }

        return meaningful.lastOrNull()
    }

    private fun extractItemNameFallback(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }

        // Old approach improved: prefer Arabic lines that are not mostly digits/symbols
        val candidates = lines.filter { l ->
            isMeaningfulNameLine(l)
        }

        // Pick the "best" candidate: longest Arabic line
        return candidates.maxByOrNull { it.count { ch -> ch in 'ء'..'ي' } + it.length }
    }

    private fun isMeaningfulNameLine(line: String): Boolean {
        if (line.length < 4) return false

        // Must contain some Arabic letters
        val arabicCount = line.count { it in 'ء'..'ي' }
        if (arabicCount < 3) return false

        // Avoid lines that are mostly digits/symbols
        val digitCount = line.count { it.isDigit() }
        if (digitCount > line.length / 2) return false

        // Avoid generic labels
        val bad = listOf("وزن", "قيمة", "عدد", "مستند", "النقل", "المنشأ", "الوضع", "رسوم", "ضريبة")
        if (bad.any { b -> line.contains(b) }) return false

        return true
    }

    /**
     * Normalize OCR text: unify Arabic digits/spacing, remove duplicate spaces.
     */
    private fun normalize(text: String): String {
        // Keep it simple (do not over-normalize and lose structure)
        return text
            .replace('\u00A0', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\r\\n?"), "\n")
            .trim()
    }
}
