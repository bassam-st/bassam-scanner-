package com.bassam.scanner.util

import android.content.Context
import android.os.Environment
import com.bassam.scanner.data.ScanEntry
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset

object CsvExporter {

    fun exportToDownloads(context: Context, items: List<ScanEntry>): File {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()

        val file = File(dir, "bassam_scanner_${System.currentTimeMillis()}.csv")

        // CSV بسيط: name,hsCode,createdAt
        val sb = StringBuilder()
        sb.append("name,hsCode,createdAt\n")
        for (i in items) {
            sb.append(escape(i.name)).append(",")
            sb.append(escape(i.hsCode)).append(",")
            sb.append(i.createdAt).append("\n")
        }

        FileOutputStream(file).use { out ->
            out.write(sb.toString().toByteArray(Charset.forName("UTF-8")))
        }
        return file
    }

    private fun escape(s: String): String {
        val x = s.replace("\"", "\"\"")
        return "\"$x\""
    }
}
