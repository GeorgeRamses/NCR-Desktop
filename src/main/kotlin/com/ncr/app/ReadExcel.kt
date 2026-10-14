package com.ncr.app

import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File
import java.io.FileInputStream

fun main() {
    val templates = listOf(
        "NCR-Form Rev 01.xlsx",
        "NSF-DIF 004 CAR Register Rev 0.xlsx",
        "NSF-DIF-002 Rev.03 CAR Form.xlsx",
        "NSF-DIF-003 NCR register Rev 2.xlsx"
    )
    
    for (template in templates) {
        val file = File("templates/$template")
        if (!file.exists()) {
            println("Missing: $template")
            continue
        }
        println("\n--- $template ---")
        try {
            FileInputStream(file).use { fis ->
                val wb = WorkbookFactory.create(fis)
                for (i in 0 until wb.numberOfSheets) {
                    val sheet = wb.getSheetAt(i)
                    println("Sheet: ${sheet.sheetName}")
                    for (rowNum in 0..100) {
                        val row = sheet.getRow(rowNum) ?: continue
                        for (colNum in 0..50) {
                            val cell = row.getCell(colNum)
                            if (cell != null && cell.cellType == CellType.STRING) {
                                val value = cell.stringCellValue
                                if (value.contains("\${")) {
                                    println("Found placeholder in [" + sheet.sheetName + "] row " + rowNum + " col " + colNum + ": " + value)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            println("Error reading $template: ${e.message}")
        }
    }
}
