package com.ncr.app.excel

import com.ncr.app.data.NcrModel
import com.ncr.app.data.NcrRepository
import org.apache.poi.ss.usermodel.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDate

object ExcelExporter {

    private fun replaceInSheet(sheet: Sheet, replacements: Map<String, String>) {
        for (rowNum in 0..sheet.lastRowNum) {
            val row = sheet.getRow(rowNum) ?: continue
            for (colNum in 0 until row.lastCellNum) {
                val cell = row.getCell(colNum)
                if (cell != null && cell.cellType == CellType.STRING) {
                    var text = cell.stringCellValue
                    var changed = false
                    for ((key, value) in replacements) {
                        if (text.contains(key)) {
                            text = text.replace(key, value)
                            changed = true
                        }
                    }
                    if (changed) {
                        cell.setCellValue(text)
                    }
                }
            }
        }
    }

    fun exportNcrForm(ncr: NcrModel, destFile: File) {
        val ncrNo = ncr.ncr ?: "Unknown"
        val baseDir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile } ?: File(System.getProperty("user.dir"))
        val templateFile = File(baseDir, "templates/NSF-DIF-001 NCR-Form Rev 01.xlsx")
        if (!templateFile.exists()) throw Exception("Template not found: NSF-DIF-001 NCR-Form Rev 01.xlsx")

        FileInputStream(templateFile).use { fis ->
            val wb = WorkbookFactory.create(fis)
            val sheet = wb.getSheetAt(0) // Assuming first sheet
            val deptName = NcrRepository.getDeptMap()[ncr.responsibleDept] ?: ""

            val replacements = mapOf(
                "\${dateissue}" to (ncr.date?.toString() ?: ""),
                "\${ncr}" to (ncr.ncr ?: ""),
                "\${c}" to if (ncr.customerComplain) "✔" else "",
                "\${m}" to if (ncr.material) "✔" else "",
                "\${s}" to if (ncr.safetyIssue) "✔" else "",
                "\${p}" to if (ncr.product) "✔" else "",
                "\${e}" to if (ncr.environmentalIssue) "✔" else "",
                "\${q}" to if (ncr.qualityManagementIssue) "✔" else "",
                "\${Descripe}" to (ncr.descriptionOfNonConformity ?: ""),
                "\${ref}" to (ncr.reference ?: ""),
                "\${by}" to (ncr.ncrRaisedBy ?: ""),
                "\${disposition}" to (ncr.recommendedDisposition ?: ""),
                "\${suggested}" to (ncr.suggestedBy ?: ""),
                "\${disposdate}" to (ncr.dispositionDate?.toString() ?: ""),
                "\${completiondate}" to (ncr.targetCompletionDate?.toString() ?: "")
            )

            replaceInSheet(sheet, replacements)
            FileOutputStream(destFile).use { wb.write(it) }
        }
    }

    fun exportCarForm(ncr: NcrModel, destFile: File) {
        val ncrNo = ncr.ncr ?: "Unknown"
        val baseDir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile } ?: File(System.getProperty("user.dir"))
        val templateFile = File(baseDir, "templates/NSF-DIF-002 CAR Form Rev.03.xlsx")
        if (!templateFile.exists()) throw Exception("Template not found: NSF-DIF-002 CAR Form Rev.03.xlsx")

        FileInputStream(templateFile).use { fis ->
            val wb = WorkbookFactory.create(fis)
            val sheet = wb.getSheetAt(0)

            val replacements = mapOf(
                "\${date}" to (ncr.date?.toString() ?: ""),
                "\${ncr}" to (ncr.carNo ?: ""),
                "\${description}" to (ncr.descriptionOfNonConformity ?: ""),
                "\${Ref}" to (ncr.reference ?: ""),
                "\${method}" to (ncr.methodUsed ?: ""),
                "\${rootcause}" to (ncr.rootCause ?: ""),
                "\${agreed}" to (ncr.correctiveActionAgreed ?: "")
            )

            replaceInSheet(sheet, replacements)
            FileOutputStream(destFile).use { wb.write(it) }
        }
    }

    fun exportRegister(ncrList: List<NcrModel>, destFile: File, isCar: Boolean = false) {
        val templateName = if (isCar) "NSF-DIF-004 CAR Register Rev 0.xlsx" else "NSF-DIF-003 NCR register Rev 2.xlsx"
        val baseDir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile } ?: File(System.getProperty("user.dir"))
        val templateFile = File(File(baseDir, "templates"), templateName)
        if (!templateFile.exists()) throw Exception("Template not found: $templateName")

        FileInputStream(templateFile).use { fis ->
            val wb = WorkbookFactory.create(fis)
            val sheet = wb.getSheetAt(0)
            val deptMap = NcrRepository.getDeptMap()

            // 1. Replace header variables
            val pendingCount = ncrList.count { it.actualCloseDate == null }.toString()
            val totalCount = ncrList.size.toString()
            
            val years = ncrList.mapNotNull { it.date?.year }.distinct().sorted()
            val yearStr = when {
                years.isEmpty() -> LocalDate.now().year.toString()
                years.size == 1 -> years.first().toString()
                else -> "${years.first()}-${years.last()}"
            }
            val date = LocalDate.now().toString()

            val headerReplacements = mapOf(
                "\${year}" to yearStr,
                "\${date}" to date,
                "\${totalncr}" to totalCount,
                "\${pendingncr}" to pendingCount
            )
            replaceInSheet(sheet, headerReplacements)

            // 2. Find template row
            var templateRowNum = -1
            var templateRow: Row? = null
            for (i in 0..sheet.lastRowNum) {
                val r = sheet.getRow(i) ?: continue
                for (j in 0 until r.lastCellNum) {
                    val c = r.getCell(j)
                    if (c != null && c.cellType == CellType.STRING) {
                        if (c.stringCellValue.contains(if (isCar) "\${car}" else "\${ncr}")) {
                            templateRowNum = i
                            templateRow = r
                            break
                        }
                    }
                }
                if (templateRowNum != -1) break
            }

            if (templateRowNum != -1 && templateRow != null) {
                // Save original template string values
                val originalPlaceholders = arrayOfNulls<String>(templateRow.lastCellNum.toInt())
                for (i in 0 until templateRow.lastCellNum) {
                    val cell = templateRow.getCell(i)
                    if (cell != null && cell.cellType == CellType.STRING) {
                        originalPlaceholders[i] = cell.stringCellValue
                    }
                }

                val sortedList =
                    ncrList.sortedWith(compareByDescending<NcrModel> { it.date }.thenByDescending { it.id })

                // If there are multiple items, we need to shift rows down to make space
                if (sortedList.size > 1) {
                    sheet.shiftRows(
                        templateRowNum + 1,
                        sheet.lastRowNum + sortedList.size,
                        sortedList.size - 1,
                        true,
                        true
                    )
                }

                for ((index, ncr) in sortedList.withIndex()) {
                    val currentRowNum = templateRowNum + index
                    val row = if (index == 0) templateRow else sheet.createRow(currentRowNum)

                    if (index > 0) {
                        // Copy cell styles and formulas from templateRow
                        row.height = templateRow.height
                        for (i in 0 until templateRow.lastCellNum) {
                            val oldCell = templateRow.getCell(i)
                            if (oldCell != null) {
                                val newCell = row.createCell(i, oldCell.cellType)
                                newCell.cellStyle = oldCell.cellStyle
                                when (oldCell.cellType) {
                                    CellType.STRING -> newCell.setCellValue(oldCell.stringCellValue)
                                    CellType.NUMERIC -> newCell.setCellValue(oldCell.numericCellValue)
                                    CellType.BOOLEAN -> newCell.setCellValue(oldCell.booleanCellValue)
                                    CellType.FORMULA -> newCell.cellFormula = oldCell.cellFormula
                                    else -> {}
                                }
                            }
                        }

                        // Copy merged regions for this row
                        val mergedRegionsToCopy = mutableListOf<org.apache.poi.ss.util.CellRangeAddress>()
                        for (i in 0 until sheet.numMergedRegions) {
                            val region = sheet.getMergedRegion(i)
                            if (region.firstRow == templateRowNum && region.lastRow == templateRowNum) {
                                mergedRegionsToCopy.add(
                                    org.apache.poi.ss.util.CellRangeAddress(
                                        currentRowNum, currentRowNum, region.firstColumn, region.lastColumn
                                    )
                                )
                            }
                        }
                        for (region in mergedRegionsToCopy) {
                            sheet.addMergedRegion(region)
                        }
                    }

                    val deptName = deptMap[ncr.responsibleDept] ?: ""
                    val rowReplacements = if (isCar) {
                        mapOf(
                            "\${car}" to (ncr.carNo ?: ""),
                            "\${description}" to (ncr.descriptionOfNonConformity ?: ""),
                            "\${ncr}" to (ncr.ncr ?: ""),
                            "\${respDept}" to deptName,
                            "\${dateissue}" to (ncr.date?.toString() ?: ""),
                            "\${target}" to (ncr.targetCompletionDate?.toString() ?: "")
                        )
                    } else {
                        mapOf(
                            "\${ncr}" to (ncr.ncr ?: ""),
                            "\${description}" to (ncr.descriptionOfNonConformity ?: ""),
                            "\${ref}" to (ncr.reference ?: ""),
                            "\${raisedby}" to (ncr.ncrRaisedBy ?: ""),
                            "\${car}" to (ncr.carNo ?: ""),
                            "\${resbDept}" to deptName,
                            "\${dateissue}" to (ncr.date?.toString() ?: ""),
                            "\${disposition}" to (ncr.recommendedDisposition ?: ""),
                            "\${target}" to (ncr.targetCompletionDate?.toString() ?: ""),
                            "\${actual}" to (ncr.actualCloseDate?.toString() ?: "")
                        )
                    }

                    // Apply replacements for this row using original placeholders
                    for (i in 0 until row.lastCellNum) {
                        val cell = row.getCell(i)
                        val originalText = if (i < originalPlaceholders.size) originalPlaceholders[i] else null

                        if (cell != null && originalText != null) {
                            var text: String = originalText
                            var changed = false
                            for ((key, value) in rowReplacements) {
                                if (text.contains(key)) {
                                    text = text.replace(key, value)
                                    changed = true
                                }
                            }
                            if (changed || index > 0) {
                                cell.setCellValue(text)
                            }
                        }
                    }
                }
            }

            FileOutputStream(destFile).use { wb.write(it) }
        }
    }
}
