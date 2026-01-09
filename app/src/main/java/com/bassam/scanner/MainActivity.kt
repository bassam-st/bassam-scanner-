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

    private var lastText: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.get(this)

        adapter = ScanAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        loadData()

        binding.btnSave.setOnClickListener { saveCurrent() }
        binding.btnExport.setOnClickListener { CsvExporter.export(this, db.scanDao().getAllSync()) }
        binding.btnClear.setOnClickListener {
            db.scanDao().deleteAll()
            loadData()
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show()
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 10)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
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

            analysis.setAnalyzer(cameraExecutor, OcrAnalyzer { text ->
                runOnUiThread {
                    lastText = text
                    binding.txtLive.text = text.ifBlank { "..." }
                }
            })

            val cameraSelector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, analysis)

        }, ContextCompat.getMainExecutor(this))
    }

    private fun saveCurrent() {
        val (hs, name) = TextParser.parse(lastText)
        val entity = ScanEntity(
            hsCode = hs,
            itemName = name,
            rawText = lastText,
            createdAt = System.currentTimeMillis()
        )
        db.scanDao().insert(entity)
        Toast.makeText(this, "Saved: $hs", Toast.LENGTH_SHORT).show()
        loadData()
    }

    private fun loadData() {
        adapter.submitList(db.scanDao().getAllSync())
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
