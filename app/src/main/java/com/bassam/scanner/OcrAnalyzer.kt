package com.bassam.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.bassam.scanner.model.ScanResult
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions

class OcrAnalyzer(
    private val onResult: (ScanResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(ArabicTextRecognizerOptions.Builder().build())

    @Volatile var lastRawText: String? = null
    private var lastBestHs: String? = null
    private var lastBestName: String? = null

    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val raw = visionText.text ?: ""
                lastRawText = raw

                val parsed = TextParser.parse(raw)
                if (!parsed.hsCode.isNullOrBlank()) lastBestHs = parsed.hsCode
                if (!parsed.itemName.isNullOrBlank()) lastBestName = parsed.itemName

                onResult(
                    ScanResult(
                        hsCode = lastBestHs,
                        itemName = lastBestName,
                        rawText = raw
                    )
                )
            }
            .addOnCompleteListener { imageProxy.close() }
    }
}
