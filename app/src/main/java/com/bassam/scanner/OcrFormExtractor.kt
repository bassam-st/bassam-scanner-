package com.bassam.scanner

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 * Extract multiple items from a single customs draft page.
 *
 * Assumption (as you confirmed): items are stacked vertically.
 * Field 31 -> item name (take LAST meaningful line inside field 31 area)
 * Field 33 -> HS code (under "البند التعريفي")
 */
object OcrFormExtractor {

    data class ParsedItem(
        val hsCode: String,
        val itemName: String
    )

    private val hsRegex = Regex("\\b(\\d{8}|\\d{10})\\b")

    fun extractItems(result: Text): List<ParsedItem> {
        val field31Lines = findAllLines(result) { line ->
            Regex("(^|\\s)31(\\s|$|[:.،])").containsMatchIn(line.text.normalize())
        }.sortedBy { it.boundingBox?.top ?: 0 }

        if (field31Lines.isEmpty()) {
            // Fallback: try extract one item from whole page
            val hs = extractHsInRect(result, null) ?: extractHsFallback(result.text ?: "")
            val name = extractAnyItemName(result) ?: "Unknown"
            return if (hs != "N/A" || name != "Unknown") listOf(ParsedItem(hs, name)) else emptyList()
        }

        val allLines = getAllLines(result).sortedBy { it.boundingBox?.top ?: 0 }

        val items = mutableListOf<ParsedItem>()

        for (i in field31Lines.indices) {
            val f31 = field31Lines[i]
            val f31Box = f31.boundingBox ?: continue

            // Define segment for this item: from this 31 top to next 31 top (or end)
            val segTop = (f31Box.top - 80).coerceAtLeast(0)
            val segBottom = if (i + 1 < field31Lines.size) {
                val nextBox = field31Lines[i + 1].boundingBox
                ((nextBox?.top ?: (f31Box.bottom + 1200)) - 40).coerceAtLeast(f31Box.bottom + 300)
            } else {
                f31Box.bottom + 1600
            }

            // Segment rect (wide enough)
            val segmentRect = Rect(
                (f31Box.left - 500).coerceAtLeast(0),
                segTop,
                f31Box.right + 2500,
                segBottom
            )

            // 1) Extract item name from field 31 area inside this segment:
            val name = extractNameFromField31Area(allLines, f31Box, segmentRect) ?: "Unknown"

            // 2) Extract HS from field 33 inside the same segment:
            val hs = extractHsFromField33InSegment(result, segmentRect) ?: "N/A"

            // Only add if we got something meaningful
            if (!(hs == "N/A" && name == "Unknown")) {
                items.add(ParsedItem(hs, name))
            }
        }

        // Remove duplicates if OCR repeats
        return items.distinctBy { "${it.hsCode}|${it.itemName}" }
    }

    private fun extractNameFromField31Area(
        allLines: List<Text.Line>,
        field31Box: Rect,
        segmentRect: Rect
    ): String? {
        // ROI below field 31 label until segment bottom
        val roi = Rect(
            (field31Box.left - 150).coerceAtLeast(0),
            (field31Box.bottom + 5).coerceAtLeast(0),
            field31Box.right + 2500,
            segmentRect.bottom
        )

        val inside = allLines
            .filter { line ->
                val b = line.boundingBox ?: return@filter false
                Rect.intersects(roi, b) && b.top >= roi.top && b.top <= roi.bottom
            }
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .filter { isMeaningfulArabicName(it) }

        return inside.lastOrNull()
    }

    private fun extractHsFromField33InSegment(result: Text, segmentRect: Rect): String? {
        // Find anchor "البند التعريفي" inside this segment
        val anchor = findFirstLine(result) { line ->
            val b = line.boundingBox ?: return@findFirstLine false
            if (!Rect.intersects(segmentRect, b)) return@findFirstLine false

            val s = line.text.normalize()
            s.contains("البند") && (s.contains("التعريفي") || s.contains("التعريف") || s.contains("التعريفة"))
        } ?: return extractHsInRect(result, segmentRect)

        val aBox = anchor.boundingBox ?: return extractHsInRect(result, segmentRect)

        // Search numbers under anchor within segment
        val searchRect = Rect(
            (segmentRect.left).coerceAtLeast(0),
            (aBox.bottom + 5).coerceAtLeast(0),
            segmentRect.right,
            (aBox.bottom + 450).coerceAtMost(segmentRect.bottom)
        )

        val nums = mutableListOf<String>()
        forEachLine(result) { line ->
            val b = line.boundingBox ?: return@forEachLine
            if (!Rect.intersects(searchRect, b)) return@forEachLine
            hsRegex.findAll(line.text).forEach { nums.add(it.value) }
        }

        if (nums.isEmpty()) return extractHsInRect(result, segmentRect)
        return nums.firstOrNull { it.length == 8 } ?: nums.firstOrNull()
    }

    private fun extractHsInRect(result: Text, rect: Rect?): String? {
        val nums = mutableListOf<String>()
        forEachLine(result) { line ->
            val b = line.boundingBox ?: return@forEachLine
            if (rect != null && !Rect.intersects(rect, b)) return@forEachLine
            hsRegex.findAll(line.text).forEach { nums.add(it.value) }
        }
        if (nums.isEmpty()) return null
        return nums.firstOrNull { it.length == 8 } ?: nums.firstOrNull()
    }

    private fun extractHsFallback(raw: String): String {
        val matches = hsRegex.findAll(raw).map { it.value }.toList()
        return matches.firstOrNull { it.length == 8 }
            ?: matches.firstOrNull()
            ?: "N/A"
    }

    private fun extractAnyItemName(result: Text): String? {
        val lines = getAllLines(result)
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .filter { isMeaningfulArabicName(it) }

        return lines.maxByOrNull { it.length }
    }

    private fun isMeaningfulArabicName(s: String): Boolean {
        val t = s.normalize()
        if (t.length < 4) return false

        val arabicCount = t.count { it in 'ء'..'ي' }
        if (arabicCount < 3) return false

        val digitCount = t.count { it.isDigit() }
        if (digitCount > t.length / 2) return false

        val bad = listOf("البند", "الرقم", "قيمة", "وزن", "عدد", "مستند", "النقل", "الوضع", "منشأ")
        if (bad.any { t.contains(it) }) return false

        return true
    }

    private fun forEachLine(result: Text, fn: (Text.Line) -> Unit) {
        result.textBlocks.forEach { block ->
            block.lines.forEach { line -> fn(line) }
        }
    }

    private fun getAllLines(result: Text): List<Text.Line> {
        val out = mutableListOf<Text.Line>()
        forEachLine(result) { out.add(it) }
        return out
    }

    private fun findAllLines(result: Text, predicate: (Text.Line) -> Boolean): List<Text.Line> {
        val out = mutableListOf<Text.Line>()
        forEachLine(result) { line ->
            if (predicate(line)) out.add(line)
        }
        return out
    }

    private fun findFirstLine(result: Text, predicate: (Text.Line) -> Boolean): Text.Line? {
        var found: Text.Line? = null
        forEachLine(result) { line ->
            if (found == null && predicate(line)) found = line
        }
        return found
    }

    private fun String.normalize(): String {
        return this
            .replace('\u00A0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .trim()
    }
}
