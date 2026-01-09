package com.bassam.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var db: AppDatabase
    private lateinit var adapter: ScanAdapter

    // آخر إطار OCR مُعالج (فيه HS + الاسم + النص الكامل)
    private var lastFrame: OcrAnalyzer.OcrFrame? = null

    // لمنع تكرار الحفظ التلقائي لنفس السطر
    private var lastAutoSavedKey: String = ""

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
        binding.btnExport.setOnClickListener {
            // تصدير من قاعدة البيانات
            CsvExporter.export(this, db.scanDao().getAllSync())
        }
        binding.btnClear.setOnClickListener {
            db.scanDao().deleteAll()
            loadData()
            lastAutoSavedKey = ""
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

            // Analyzer الجديد: يرجع OcrFrame بدل String
            analysis.setAnalyzer(
                cameraExecutor,
                OcrAnalyzer(
                    onFrame = { frame ->
                        runOnUiThread {
                            lastFrame = frame

                            // عرض مبسط وثابت (بدون هزّات النص الطويل)
                            binding.txtLive.text = buildString {
                                append("HS: ").append(frame.hsCode).append("\n")
                                append("Item: ").append(frame.itemName)
                            }

                            // حفظ تلقائي عند الثبات
                            if (frame.shouldAutoSave) {
                                val key = "${frame.hsCode}|${frame.itemName}"
                                if (key != lastAutoSavedKey) {
                                    lastAutoSavedKey = key
                                    saveFrame(frame, toast = true, manual = false)
                                }
                            }
                        }
                    },
                    stableFramesNeeded = 8,     // لو تريد ثبات أكثر زِدها (مثلاً 12)
                    minEmitIntervalMs = 120L    // لو تريد تقليل التحديثات زِدها (مثلاً 200)
                )
            )

            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * حفظ يدوي عند ضغط الزر، أو حفظ تلقائي عند ثبات القراءة
     */
    private fun saveCurrent(manual: Boolean) {
        val frame = lastFrame
        if (frame == null) {
            Toast.makeText(this, "No OCR yet", Toast.LENGTH_SHORT).show()
            return
        }
        saveFrame(frame, toast = true, manual = manual)
    }

    private fun saveFrame(frame: OcrAnalyzer.OcrFrame, toast: Boolean, manual: Boolean) {
        // حماية بسيطة: لا تحفظ إذا لم يتعرف
        if (frame.hsCode == "N/A" || frame.itemName == "Unknown") {
            if (manual) Toast.makeText(this, "Not detected yet", Toast.LENGTH_SHORT).show()
            return
        }

        val entity = ScanEntity(
            hsCode = frame.hsCode,
            itemName = frame.itemName,
            rawText = frame.rawText,
            createdAt = System.currentTimeMillis()
        )

        // الأفضل إدخال DB خارج الـ UI Thread
        cameraExecutor.execute {
            db.scanDao().insert(entity)
            runOnUiThread {
                if (toast) {
                    val msg = if (manual) "Saved (manual): ${frame.hsCode}" else "Auto-saved: ${frame.hsCode}"
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
                loadData()
            }
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
