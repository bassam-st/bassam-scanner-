package com.bassam.scanner

import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Emits structured OCR:
 * - hsCode: extracted near field 33 (البند التعريفي)
 * - itemName: extracted from field 31 (last Arabic-ish line)
 * - rawText: full OCR text
 * - shouldAutoSave: true when (hsCode,itemName) stayed stable for a short window
 */
class OcrAnalyzer(
    private val onFrame: (OcrFrame) -> Unit,
    private val stableFramesNeeded: Int = 8,       // increase to make it calmer
    private val minEmitIntervalMs: Long = 120L      // throttle UI updates
) : ImageAnalysis.Analyzer {

    data class OcrFrame(
        val rawText: String,
        val hsCode: String,
        val itemName: String,
        val shouldAutoSave: Boolean
    )

    // Arabic recognizer (best for Arabic customs forms)
    private val recognizer = TextRecognition.getClient(
        ArabicTextRecognizerOptions.Builder().build()
    )

    private val busy = AtomicBoolean(false)

    // Stability control
    private var lastKey: String = ""
    private var stableCount: Int = 0
    private var lastEmitAt: Long = 0L
    private var lastAutoSaveKey: String = ""

    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }

        val mediaImage = image.image
        if (mediaImage == null) {
            busy.set(false)
            image.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

        recognizer.process(inputImage)
            .addOnSuccessListener { result ->
                val raw = result.text ?: ""
                val hs = extractHsFromField33(result) ?: extractHsFallback(raw)
                val name = extractItemNameFromField31(result) ?: "Unknown"

                val key = "${hs}|${name}"
                if (key == lastKey) stableCount++ else {
                    lastKey = key
                    stableCount = 1
                }

                val now = System.currentTimeMillis()
                if (now - lastEmitAt >= minEmitIntervalMs) {
                    lastEmitAt = now

                    val stable = stableCount >= stableFramesNeeded
                    val shouldAutoSave = stable && key != lastAutoSaveKey && hs != "N/A" && name != "Unknown"

                    if (shouldAutoSave) {
                        // prevent repeated autosaves for same detected row
                        lastAutoSaveKey = key
                    }

                    onFrame(
                        OcrFrame(
                            rawText = raw,
                            hsCode = hs,
                            itemName = name,
                            shouldAutoSave = shouldAutoSave
                        )
                    )
                }
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }

    // -------------------- Field 33: HS extraction --------------------

    private val hsRegex = Regex("\\b(\\d{8}|\\d{10})\\b")

    private fun extractHsFromField33(result: Text): String? {
        // 1) Find anchor line containing "البند التعريفي" or close variants
        val anchor = findLine(result) { l ->
            val s = l.text.normalize()
            s.contains("البند") && (s.contains("التعريفي") || s.contains("التعريف") || s.contains("التعريفة"))
        } ?: return null

        val aBox = anchor.boundingBox ?: return null

        // 2) Search numbers "below" the anchor (same column-ish)
        val searchRect = Rect(
            aBox.left - 80,
            aBox.bottom + 5,
            aBox.right + 900,
            aBox.bottom + 450
        )

        val candidates = mutableListOf<String>()

        forEachLine(result) { line ->
            val box = line.boundingBox ?: return@forEachLine
            if (!Rect.intersects(searchRect, box)) return@forEachLine

            hsRegex.findAll(line.text).forEach { m ->
                candidates.add(m.value)
            }
        }

        if (candidates.isEmpty()) return null

        // Prefer 8 digits
        return candidates.firstOrNull { it.length == 8 } ?: candidates.firstOrNull()
    }

    private fun extractHsFallback(raw: String): String {
        val matches = hsRegex.findAll(raw).map { it.value }.toList()
        return matches.firstOrNull { it.length == 8 }
            ?: matches.firstOrNull()
            ?: "N/A"
    }

    // -------------------- Field 31: Item name extraction --------------------

    private fun extractItemNameFromField31(result: Text): String? {
        // Strategy:
        // - Find a "31" line (field number)
        // - Create a vertical ROI below it until next field header (32/33/34...) if found
        // - Return LAST meaningful Arabic-ish line inside ROI

        val field31Line = findLine(result) { l ->
            // standalone 31
            Regex("(^|\\s)31(\\s|$|[:.،])").containsMatchIn(l.text.normalize())
        } ?: return null

        val b31 = field31Line.boundingBox ?: return null

        // Find the next field header line (32..40) that is BELOW field 31 to stop the region
        val stopper = findNearestBelow(result, b31) { l ->
            Regex("(^|\\s)(3[2-9]|40)(\\s|$|[:.،])").containsMatchIn(l.text.normalize())
        }

        val bottomY = stopper?.boundingBox?.top ?: (b31.bottom + 650)

        val roi = Rect(
            b31.left - 150,
            b31.bottom + 5,
            b31.right + 1200,
            bottomY
        )

        val linesInside = mutableListOf<Text.Line>()

        forEachLine(result) { line ->
            val box = line.boundingBox ?: return@forEachLine
            if (box.top >= roi.top && box.bottom <= roi.bottom && Rect.intersects(roi, box)) {
                linesInside.add(line)
            }
        }

        if (linesInside.isEmpty()) return null

        // Sort by vertical position then pick the LAST meaningful Arabic line
        linesInside.sortBy { it.boundingBox?.top ?: 0 }

        val meaningful = linesInside
            .map { it.text.trim() }
            .filter { it.isNotBlank() }
            .filter { isMeaningfulArabicName(it) }

        return meaningful.lastOrNull()
    }

    private fun isMeaningfulArabicName(s: String): Boolean {
        val t = s.normalize()
        val arabicCount = t.count { it in 'ء'..'ي' }
        if (arabicCount < 3) return false

        val digitCount = t.count { it.isDigit() }
        if (digitCount > t.length / 2) return false

        // Avoid obvious labels/fields
        val bad = listOf("البند", "الرقم", "قيمة", "وزن", "مستند", "النقل", "الوضع", "منشأ", "وحدات", "إضافية")
        if (bad.any { t.contains(it) }) return false

        return true
    }

    // -------------------- Helpers: iterate / search lines --------------------

    private fun forEachLine(result: Text, fn: (Text.Line) -> Unit) {
        result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                fn(line)
            }
        }
    }

    private fun findLine(result: Text, predicate: (Text.Line) -> Boolean): Text.Line? {
        var found: Text.Line? = null
        forEachLine(result) { line ->
            if (found == null && predicate(line)) found = line
        }
        return found
    }

    private fun findNearestBelow(
        result: Text,
        ref: Rect,
        predicate: (Text.Line) -> Boolean
    ): Text.Line? {
        var best: Text.Line? = null
        var bestDy = Int.MAX_VALUE

        forEachLine(result) { line ->
            val box = line.boundingBox ?: return@forEachLine
            if (box.top <= ref.bottom) return@forEachLine
            if (!predicate(line)) return@forEachLine

            val dy = box.top - ref.bottom
            if (dy in 1 until bestDy) {
                bestDy = dy
                best = line
            }
        }
        return best
    }

    private fun String.normalize(): String {
        return this
            .replace('\u00A0', ' ')
            .replace(Regex("[\\t ]+"), " ")
            .trim()
    }
}
