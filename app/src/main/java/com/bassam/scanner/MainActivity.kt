package com.bassam.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bassam.scanner.data.AppDatabase
import com.bassam.scanner.data.ScanItem
import com.bassam.scanner.ui.ScanAdapter
import com.bassam.scanner.util.Exporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val scope = MainScope()
    private val db by lazy { AppDatabase.get(this) }
    private val adapter = ScanAdapter()

    private lateinit var tvLive: TextView
    private lateinit var etHs: EditText
    private lateinit var etName: EditText
    private lateinit var btnSave: Button
    private lateinit var btnSearch: Button
    private lateinit var btnExport: Button
    private lateinit var btnClear: Button
    private lateinit var recycler: RecyclerView

    private val analyzer = OcrAnalyzer { result ->
        runOnUiThread {
            tvLive.text = "البند: ${result.hsCode ?: "-"}\nالصنف: ${result.itemName ?: "-"}\n\n${result.rawText.take(500)}"

            // تعبئة تلقائية (تستطيع تعديلها قبل الحفظ)
            if (!result.hsCode.isNullOrBlank() && etHs.text.isNullOrBlank()) etHs.setText(result.hsCode)
            if (!result.itemName.isNullOrBlank() && etName.text.isNullOrBlank()) etName.setText(result.itemName)
        }
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else tvLive.text = "لا يمكن تشغيل السكانر بدون صلاحية الكاميرا."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvLive = findViewById(R.id.tvLive)
        etHs = findViewById(R.id.etHs)
        etName = findViewById(R.id.etName)
        btnSave = findViewById(R.id.btnSave)
        btnSearch = findViewById(R.id.btnSearch)
        btnExport = findViewById(R.id.btnExport)
        btnClear = findViewById(R.id.btnClear)
        recycler = findViewById(R.id.recycler)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        btnSave.setOnClickListener { saveCurrent() }
        btnSearch.setOnClickListener { showSearchDialog() }
        btnExport.setOnClickListener { exportAll() }
        btnClear.setOnClickListener { confirmClearAll() }

        loadLatest()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { p ->
                val pv = findViewById<androidx.camera.view.PreviewView>(R.id.previewView)
                p.setSurfaceProvider(pv.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(ContextCompat.getMainExecutor(this), analyzer) }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

        }, ContextCompat.getMainExecutor(this))
    }

    private fun saveCurrent() {
        val hs = etHs.text?.toString()?.trim().orEmpty()
        val name = etName.text?.toString()?.trim().orEmpty()
        val raw = analyzer.lastRawText ?: ""

        if (hs.length != 8 || hs.any { !it.isDigit() }) {
            tvLive.text = "خطأ: البند يجب أن يكون 8 أرقام (مثال: 64039900)."
            return
        }
        if (name.isBlank()) {
            tvLive.text = "اكتب اسم الصنف قبل الحفظ."
            return
        }

        scope.launch {
            withContext(Dispatchers.IO) {
                db.scanDao().insert(
                    ScanItem(
                        hsCode = hs,
                        name = name,
                        rawText = raw
                    )
                )
            }
            tvLive.text = "تم الحفظ: $hs — $name"
            etHs.setText("")
            etName.setText("")
            loadLatest()
        }
    }

    private fun loadLatest() {
        scope.launch {
            val list = withContext(Dispatchers.IO) { db.scanDao().latest(50) }
            adapter.submit(list)
        }
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "اكتب بند (8 أرقام) أو جزء من اسم الصنف"
        }

        AlertDialog.Builder(this)
            .setTitle("بحث داخل المحفوظات")
            .setView(input)
            .setPositiveButton("بحث") { _, _ ->
                val q = input.text?.toString()?.trim().orEmpty()
                doSearch(q)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun doSearch(q: String) {
        if (q.isBlank()) {
            loadLatest()
            return
        }
        scope.launch {
            val list = withContext(Dispatchers.IO) { db.scanDao().search("%$q%") }
            adapter.submit(list)
            tvLive.text = if (list.isEmpty()) "لا توجد نتائج لـ: $q" else "نتائج البحث: ${list.size}"
        }
    }

    private fun exportAll() {
        scope.launch {
            val all = withContext(Dispatchers.IO) { db.scanDao().latest(5000) } // تصدير حتى 5000
            val (csvPath, jsonPath) = Exporter.export(this@MainActivity, all)
            tvLive.text = "تم التصدير:\nCSV: $csvPath\nJSON: $jsonPath"
        }
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(this)
            .setTitle("تأكيد")
            .setMessage("هل تريد مسح كل المحفوظات نهائيًا؟")
            .setPositiveButton("نعم") { _, _ -> clearAll() }
            .setNegativeButton("لا", null)
            .show()
    }

    private fun clearAll() {
        scope.launch {
            withContext(Dispatchers.IO) { db.scanDao().clearAll() }
            tvLive.text = "تم مسح كل المحفوظات."
            loadLatest()
        }
    }
}
