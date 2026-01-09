package com.bassam.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class OcrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)

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
                onText(result.text ?: "")
            }
            .addOnCompleteListener {
                busy.set(false)
                image.close()
            }
    }
}
