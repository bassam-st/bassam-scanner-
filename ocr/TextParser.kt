package com.bassam.scanner.ocr

data class ParsedResult(
  val hsCode: String?,
  val itemName: String?
)

object TextParser {

  // يستخرج بند جمركي 8 أو 10 أرقام (مثل 64039900 أو 39269090 أو 32141000)
  private val hsRegex = Regex("""\b(\d{8}|\d{10})\b""")

  fun parse(raw: String): ParsedResult {
    val text = raw.replace("\n", " ").replace(Regex("\\s+"), " ").trim()

    val hs = hsRegex.find(text)?.groupValues?.getOrNull(1)

    // محاولة استخراج اسم الصنف: نبحث عن كلمة "تسمية السلعة" أو نأخذ سطر طويل عربي
    val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }

    var name: String? = null

    // 1) أفضلية: سطر بعد "تسمية السلعة"
    val idx = lines.indexOfFirst { it.contains("تسمية السلعة") }
    if (idx >= 0 && idx + 1 < lines.size) {
      val candidate = lines[idx + 1]
      if (candidate.length >= 6) name = candidate
    }

    // 2) بديل: أطول سطر عربي مفهوم (ليس أرقام فقط)
    if (name == null) {
      val candidate = lines
        .filter { it.length >= 8 }
        .filterNot { it.matches(Regex("""[\d\W]+""")) }
        .maxByOrNull { it.length }
      name = candidate
    }

    // تنظيف الاسم
    name = name?.replace(Regex("\\s+"), " ")?.trim()

    return ParsedResult(hsCode = hs, itemName = name)
  }
}
