package com.bassam.scanner.util

import android.content.Context
import com.bassam.scanner.data.ScanItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Exporter {

    // يكتب داخل: Android/data/<package>/files/exports/
    fun export(context: Context, items: List<ScanItem>): Pair<String, String> {
        val dir = File(context.getExternalFilesDir(null), "exports")
        if (!dir.exists()) dir.mkdirs()

        val csv = File(dir, "bassam_scans.csv")
        csv.writeText(buildCsv(items), Charsets.UTF_8)

        val json = File(dir, "bassam_scans.json")
        json.writeText(buildJson(items), Charsets.UTF_8)

        return csv.absolutePath to json.absolutePath
    }

    private fun esc(s: String): String {
        val x = s.replace("\"", "\"\"")
        return "\"$x\""
    }

    private fun buildCsv(items: List<ScanItem>): String {
        val sb = StringBuilder()
        sb.append("id,hsCode,name,createdAt,rawText\n")
        for (it in items) {
            sb.append(it.id).append(",")
            sb.append(esc(it.hsCode)).append(",")
            sb.append(esc(it.name)).append(",")
            sb.append(it.createdAt).append(",")
            sb.append(esc(it.rawText.take(1500))) // قص لمنع ملف ضخم
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun buildJson(items: List<ScanItem>): String {
        val arr = JSONArray()
        for (it in items) {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("hsCode", it.hsCode)
            o.put("name", it.name)
            o.put("createdAt", it.createdAt)
            o.put("rawText", it.rawText)
            arr.put(o)
        }
        val root = JSONObject()
        root.put("items", arr)
        return root.toString(2)
    }
}
