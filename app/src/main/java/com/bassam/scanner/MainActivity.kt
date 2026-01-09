package com.bassam.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bassam.scanner.data.AppDatabase
import com.bassam.scanner.data.ScanEntity
import com.bassam.scanner.databinding.ActivityMainBinding
import com.bassam.scanner.ui.ScanAdapter
import com.bassam.scanner.util.CsvExporter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.arabic.ArabicTextRecognizerOptions
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var db: AppDatabase
    private lateinit var adapter: ScanAdapter

    private var lastItems: List<OcrFormExtractor.ParsedItem> = emptyList()
    private var lastRawText: String = ""
    private var lastSavedKey: String = ""

    private val recognizer = TextRecognition.getClient(
        ArabicTextRecognizerOptions.Builder().build()
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        processImageUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)

        adapter = ScanAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        loadData()

        binding.btnSave.setOnClickListener { saveCurrent(manual = true) }
        binding.btnImport.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnExport.setOnClickListener { CsvExporter.export(this, db.scanDao().getAllSync()) }
        binding.btnClear.setOnClickListener {
            db.scanDao().deleteAll()
            loadData()
            lastSavedKey = ""
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(
                cameraExecutor,
                OcrAnalyzer(
                    onFrame = { frame ->
                        runOnUiThread {
                            lastRawText = frame.rawText
                            lastItems = frame.items

                            binding.txtLive.text = formatItems(frame.items)

                            if (frame.shouldAutoSave) {
                                saveItems(frame.items, frame.rawText, manual = false)
                            }
                        }
                    },
                    stableFramesNeeded = 8,
                    minEmitIntervalMs = 150L
                )
            )

            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun formatItems(items: List<OcrFormExtractor.ParsedItem>): String {
        if (items.isEmpty()) return "وجّه الكاميرا على المسودة...\nHS: ...\nItem: ..."

        return buildString {
            items.forEachIndexed { idx, it ->
                append("${idx + 1}) HS: ${it.hsCode}\n")
                append("   Item: ${it.itemName}\n")
            }
        }.trim()
    }

    private fun saveCurrent(manual: Boolean) {
        if (lastItems.isEmpty()) {
            Toast.makeText(this, "No items detected yet", Toast.LENGTH_SHORT).show()
            return
        }
        saveItems(lastItems, lastRawText, manual = manual)
    }

    private fun saveItems(items: List<OcrFormExtractor.ParsedItem>, rawText: String, manual: Boolean) {
        // Filter meaningful rows
        val good = items.filter { it.hsCode != "N/A" && it.itemName != "Unknown" }
        if (good.isEmpty()) {
            if (manual) Toast.makeText(this, "Not detected yet", Toast.LENGTH_SHORT).show()
            return
        }

        val key = good.joinToString("|") { "${it.hsCode}:${it.itemName}" }
        if (key == lastSavedKey) return
        lastSavedKey = key

        cameraExecutor.execute {
            good.forEach { it ->
                val entity = ScanEntity(
                    hsCode = it.hsCode,
                    itemName = it.itemName,
                    rawText = rawText,
                    createdAt = System.currentTimeMillis()
                )
                db.scanDao().insert(entity)
            }

            runOnUiThread {
                loadData()
                val msg = if (manual) "Saved ${good.size} item(s)" else "Auto-saved ${good.size} item(s)"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun processImageUri(uri: Uri) {
        try {
            val image = InputImage.fromFilePath(this, uri)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val items = OcrFormExtractor.extractItems(result)
                    lastRawText = result.text ?: ""
                    lastItems = items
                    binding.txtLive.text = formatItems(items)

                    if (items.isNotEmpty()) {
                        // احفظ يدويًا/تلقائيًا؟ هنا نخليه حفظ يدوي بالزر، لكن تستطيع تغييره لو تريد.
                        Toast.makeText(this, "Image processed: ${items.size} item(s)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "No items found in image", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to read image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid image", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        adapter.submitList(db.scanDao().getAllSync())
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
