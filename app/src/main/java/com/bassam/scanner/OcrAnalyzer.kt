package com.bassam.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class OcrAnalyzer(
    private val onFrame: (OcrFrame) -> Unit,
    private val stableFramesNeeded: Int = 8,
    private val minEmitIntervalMs: Long = 150L
) : ImageAnalysis.Analyzer {

    data class OcrFrame(
        val rawText: String,
        val items: List<OcrFormExtractor.ParsedItem>,
        val shouldAutoSave: Boolean
    )

    private val recognizer = TextRecognition.getClient(
        ArabicTextRecognizerOptions.Builder().build()
    )

    private val busy = AtomicBoolean(false)

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

        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)

        recognizer.process(input)
            .addOnSuccessListener { result ->
                val raw = result.text ?: ""
                val items = OcrFormExtractor.extractItems(result)

                val key = items.joinToString("|") { "${it.hsCode}:${it.itemName}" }
                if (key == lastKey) stableCount++ else {
                    lastKey = key
                    stableCount = 1
                }

                val now = System.currentTimeMillis()
                if (now - lastEmitAt >= minEmitIntervalMs) {
                    lastEmitAt = now

                    val stable = stableCount >= stableFramesNeeded
                    val hasGood = items.any { it.hsCode != "N/A" && it.itemName != "Unknown" }
                    val shouldAutoSave = stable && hasGood && key.isNotBlank() && key != lastAutoSaveKey

                    if (shouldAutoSave) {
                        lastAutoSaveKey = key
                    }

                    onFrame(OcrFrame(rawText = raw, items = items, shouldAutoSave = shouldAutoSave))
                }
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }
}
