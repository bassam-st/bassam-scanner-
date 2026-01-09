package com.bassam.scanner.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.bassam.scanner.data.ScanEntity
import java.io.File

object CsvExporter {

    fun export(context: Context, list: List<ScanEntity>) {
        val csv = buildString {
            append("id,hsCode,itemName,createdAt\n")
            list.forEach {
                append("${it.id},${escape(it.hsCode)},${escape(it.itemName)},${it.createdAt}\n")
            }
        }

        val file = File(context.cacheDir, "scans.csv")
        file.writeText(csv, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            file
        )

        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(share, "Share CSV"))
    }

    private fun escape(s: String): String {
        val needsQuotes = s.contains(",") || s.contains("\n") || s.contains("\"")
        val v = s.replace("\"", "\"\"")
        return if (needsQuotes) "\"$v\"" else v
    }
}
