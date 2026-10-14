package com.ncr.app.pdf

import com.ncr.app.data.NcrModel
import com.ncr.app.excel.ExcelExporter
import java.io.File
import java.util.UUID

object PdfExporter {

    private fun convertExcelToPdf(excelFile: File, pdfFile: File) {
        // Use PowerShell COM object to silently convert Excel to PDF
        // Single quotes are used for file paths in PS to prevent variable expansion
        val script = """
            ${'$'}excel = New-Object -ComObject Excel.Application
            ${'$'}excel.Visible = ${'$'}false
            ${'$'}excel.DisplayAlerts = ${'$'}false
            try {
                ${'$'}workbook = ${'$'}excel.Workbooks.Open('${excelFile.absolutePath}')
                ${'$'}workbook.ExportAsFixedFormat(0, '${pdfFile.absolutePath}')
                ${'$'}workbook.Close(${'$'}false)
            } finally {
                ${'$'}excel.Quit()
                [System.Runtime.Interopservices.Marshal]::ReleaseComObject(${'$'}excel) | Out-Null
            }
        """.trimIndent()

        val scriptFile = File.createTempFile("convert_${UUID.randomUUID()}", ".ps1")
        scriptFile.writeText(script)

        try {
            val process = ProcessBuilder("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", scriptFile.absolutePath)
                .start()
            process.waitFor()
        } finally {
            scriptFile.delete()
        }
    }

    fun exportNcrForm(ncr: NcrModel, destPath: String) {
        val destFile = File(destPath)
        val tempExcel = File.createTempFile("temp_ncr_${ncr.ncr?.replace("/", "_")}", ".xlsx")
        try {
            ExcelExporter.exportNcrForm(ncr, tempExcel)
            convertExcelToPdf(tempExcel, destFile)
        } finally {
            tempExcel.delete()
        }
    }

    fun exportCarForm(ncr: NcrModel, destPath: String) {
        val destFile = File(destPath)
        val tempExcel = File.createTempFile("temp_car_${ncr.ncr?.replace("/", "_")}", ".xlsx")
        try {
            ExcelExporter.exportCarForm(ncr, tempExcel)
            convertExcelToPdf(tempExcel, destFile)
        } finally {
            tempExcel.delete()
        }
    }

    fun exportRegister(ncrList: List<NcrModel>, destPath: String, isCar: Boolean = false) {
        val destFile = File(destPath)
        val tempExcel = File.createTempFile("temp_register", ".xlsx")
        try {
            ExcelExporter.exportRegister(ncrList, tempExcel, isCar)
            convertExcelToPdf(tempExcel, destFile)
        } finally {
            tempExcel.delete()
        }
    }
}
