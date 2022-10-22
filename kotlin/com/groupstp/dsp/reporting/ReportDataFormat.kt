package com.groupstp.dsp.reporting

/**
 * Формат потока данных в отчетной подсистеме.
 */
enum class ReportDataFormat(
    vararg fileExts: String
) {
    XLSX("xlsx", "xls"),
    HTML("html", "htm"),
    PDF("pdf");

    // Наиболее репрезентативное расширение файла для формата
    val fileExt = if (fileExts.isNotEmpty()) fileExts[0] else null
    // Все возможные расширения файлов для формата
    val fileExts = fileExts.toList()

    companion object {
        fun fromFileExt(fileExt: String): ReportDataFormat? {
            return values().find { it.fileExts.indexOf(fileExt) >= 0 }
        }

        fun fromFileName(fileName: String): ReportDataFormat? {
            return fromFileExt(fileName.substringAfterLast(".", ""))
        }
    }
}
