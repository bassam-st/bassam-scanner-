package com.bassam.scanner.ocr

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicLong

class OcrAnalyzer(
    private val onDetected: (name: String, hsCode: String) -> Unit
) : ImageAnalysis.Analyzer {

    // التخفيف: لا نحلل كل الفريمات حتى لا يثقل
    private val lastRun = AtomicLong(0L)
    private val minIntervalMs = 700L

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val hsRegex = Regex("""\b\d{8}\b""")

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastRun.get() < minIntervalMs) {
            imageProxy.close()
            return
        }
        lastRun.set(now)

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val img = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(img)
            .addOnSuccessListener { visionText ->
                // نجمع الأسطر
                val lines = mutableListOf<String>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val t = line.text.trim()
                        if (t.isNotEmpty()) lines.add(t)
                    }
                }

                // نبحث عن أول بند 8 أرقام
                var foundCode: String? = null
                var foundLineIndex = -1

                for (i in lines.indices) {
                    val m = hsRegex.find(lines[i])
                    if (m != null) {
                        foundCode = m.value
                        foundLineIndex = i
                        break
                    }
                }

                if (foundCode != null) {
                    // نحاول نأخذ اسم الصنف: نفس السطر بعد حذف البند، أو أقرب سطر قبله
                    val current = lines[foundLineIndex]
                    val nameFromSameLine = current.replace(foundCode!!, "").trim()

                    val name = when {
                        nameFromSameLine.length >= 3 -> nameFromSameLine
                        foundLineIndex > 0 -> lines[foundLineIndex - 1]
                        else -> ""
                    }

                    onDetected(name, foundCode!!)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }
}
