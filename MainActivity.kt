package com.bassam.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bassam.scanner.data.AppDatabase
import com.bassam.scanner.data.ScanEntry
import com.bassam.scanner.ocr.OcrAnalyzer
import com.bassam.scanner.ui.EntryAdapter
import com.bassam.scanner.util.CsvExporter
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var previewView: PreviewView
    private lateinit var txtDetected: TextView
    private lateinit var btnSave: Button
    private lateinit var btnExport: Button
    private lateinit var recycler: RecyclerView

    private lateinit var db: AppDatabase
    private lateinit var adapter: EntryAdapter

    // آخر نتيجة ملتقطة (اسم + بند)
    @Volatile private var lastName: String? = null
    @Volatile private var lastCode: String? = null

    companion object {
        private const val REQ_CAMERA = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        txtDetected = findViewById(R.id.txtDetected)
        btnSave = findViewById(R.id.btnSave)
        btnExport = findViewById(R.id.btnExport)
        recycler = findViewById(R.id.recycler)

        db = AppDatabase.get(this)

        adapter = EntryAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        refreshList()

        btnSave.setOnClickListener {
            val n = lastName?.trim().orEmpty()
            val c = lastCode?.trim().orEmpty()
            if (c.isNotEmpty()) {
                val entry = ScanEntry(
                    name = if (n.isBlank()) "غير معروف" else n,
                    hsCode = c
                )
                Thread {
                    db.scanEntryDao().insert(entry)
                    runOnUiThread { refreshList() }
                }.start()
            }
        }

        btnExport.setOnClickListener {
            Thread {
                val all = db.scanEntryDao().getAll()
                val file = CsvExporter.exportToDownloads(this, all)
                runOnUiThread {
                    txtDetected.text = "تم التصدير: ${file.name}"
                }
            }.start()
        }

        if (hasCameraPermission()) startCamera() else requestCameraPermission()
    }

    private fun refreshList() {
        Thread {
            val items = db.scanEntryDao().getAll()
            runOnUiThread { adapter.submit(items) }
        }.start()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            txtDetected.text = "صلاحية الكاميرا مطلوبة للعمل."
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = OcrAnalyzer { name, code ->
                lastName = name
                lastCode = code
                runOnUiThread {
                    txtDetected.text = "الصنف: ${name.ifBlank { "غير معروف" }} | البند: $code"
                }
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)

        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
