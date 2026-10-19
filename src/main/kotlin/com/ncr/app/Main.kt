package com.ncr.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ncr.app.data.BulkOperationState
import com.ncr.app.data.DatabaseFactory
import com.ncr.app.data.NcrRepository
import com.ncr.app.data.NcrStatus
import com.ncr.app.pdf.PdfExporter
import com.ncr.app.excel.ExcelExporter
import com.ncr.app.ui.DashboardScreen
import com.ncr.app.ui.NcrDataEntryScreen
import com.ncr.app.ui.SettingsScreen
import com.ncr.app.email.NotificationScheduler
import java.io.File
import java.awt.Desktop
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.lazy.rememberLazyListState
import com.ncr.app.data.NcrModel
import com.ncr.app.data.NcrFilter
import java.time.LocalDate
sealed class Screen {
    object Dashboard : Screen()
    data class NcrDataEntry(val ncrId: Int?) : Screen()
    object Settings : Screen()
}

/**
 * Version shown in the window title. Uses the generated BuildConfig when the Gradle build provides one,
 * otherwise the title simply has no version (so the project compiles either way).
 */
private val appVersion: String? = runCatching {
    val cls = Class.forName("com.ncr.app.BuildConfig")
    val direct = runCatching { cls.getField("APP_VERSION").get(null) }.getOrNull()
    (direct ?: cls.getMethod("getAPP_VERSION").invoke(cls.getField("INSTANCE").get(null))) as String
}.getOrNull()

fun main() = application {
    // remember{} = run ONCE. Code placed directly in application{} re-runs on every recomposition
    // (e.g. each keystroke in the search box), which started duplicate schedulers.
    remember {
        DatabaseFactory.init()
        NotificationScheduler.start()
    }

    // Ensure templates exist (Excel templates should already exist)
    val baseDir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile } ?: File(System.getProperty("user.dir"))
    val templatesDir = File(baseDir, "templates")

    val userHome = System.getProperty("user.home")
    val printsDir = File(userHome, "Documents/NCR_Prints").apply { mkdirs() }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }

    // Persistent filter states hoisted from DashboardScreen
    var searchQuery by remember { mutableStateOf("") }
    var selectedProject by remember { mutableStateOf<String?>(null) }
    var dateFromText by remember { mutableStateOf("") }
    var dateToText by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf(NcrStatus.ALL) }
    var selectedDept by remember { mutableStateOf<Int?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showArchived by remember { mutableStateOf(false) }

    var ncrList by remember { mutableStateOf(listOf<NcrModel>()) }
    val dashboardListState = rememberLazyListState()
    var bulkOperationState by remember { mutableStateOf<BulkOperationState>(BulkOperationState.Idle) }
    val bulkScope = rememberCoroutineScope()

    val currentFilter = NcrFilter(
        searchQuery = searchQuery,
        projectNo = selectedProject,
        dateFrom = try { if (dateFromText.isNotBlank()) LocalDate.parse(dateFromText) else null } catch (e: Exception) { null },
        dateTo = try { if (dateToText.isNotBlank()) LocalDate.parse(dateToText) else null } catch (e: Exception) { null },
        status = selectedStatus,
        deptId = selectedDept,
        isArchived = showArchived
    )

    LaunchedEffect(currentFilter, currentScreen) {
        if (currentScreen is Screen.Dashboard) {
            ncrList = NcrRepository.searchNcrFiltered(currentFilter)
        }
    }

    Window(
        onCloseRequest = {
            NotificationScheduler.stop()
            exitApplication()
        },
        title = "NCR Management System" + (appVersion?.let { " - v$it" } ?: ""),
        icon = painterResource("icon.png"),
        state = rememberWindowState(placement = WindowPlacement.Maximized)
    ) {
        MaterialTheme {
            if (errorMessage != null) {
                AlertDialog(
                    onDismissRequest = { errorMessage = null },
                    title = { Text("Error") },
                    text = { Text("Failed to generate file. It might be open in another application.\n\nDetails: $errorMessage") },
                    confirmButton = {
                        Button(onClick = { errorMessage = null }) {
                            Text("OK")
                        }
                    }
                )
            }

            when (val screen = currentScreen) {
                is Screen.Dashboard -> DashboardScreen(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    selectedProject = selectedProject,
                    onSelectedProjectChange = { selectedProject = it },
                    dateFromText = dateFromText,
                    onDateFromTextChange = { dateFromText = it },
                    dateToText = dateToText,
                    onDateToTextChange = { dateToText = it },
                    selectedStatus = selectedStatus,
                    onSelectedStatusChange = { selectedStatus = it },
                    selectedDept = selectedDept,
                    onSelectedDeptChange = { selectedDept = it },
                    onNewNcrClick = { currentScreen = Screen.NcrDataEntry(null) },
                    onSettingsClick = { currentScreen = Screen.Settings },
                    onEditNcrClick = { id -> currentScreen = Screen.NcrDataEntry(id) },
                    onPrintNcrPdfClick = { id ->
                        try {
                            val ncr = NcrRepository.getNcrById(id)
                            if (ncr != null) {
                                val safeNcrNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                val file = File(printsDir, "NCR_$safeNcrNo.pdf")
                                PdfExporter.exportNcrForm(ncr, file.absolutePath)
                                if (Desktop.isDesktopSupported() && file.exists()) {
                                    Desktop.getDesktop().open(file)
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onPrintCarPdfClick = { id ->
                        try {
                            val ncr = NcrRepository.getNcrById(id)
                            if (ncr != null) {
                                val safeNcrNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                val file = File(printsDir, "CAR_$safeNcrNo.pdf")
                                PdfExporter.exportCarForm(ncr, file.absolutePath)
                                if (Desktop.isDesktopSupported() && file.exists()) {
                                    Desktop.getDesktop().open(file)
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onExportRegisterPdfClick = { filteredList ->
                        try {
                            val file = File(printsDir, "NCR_Register.pdf")
                            PdfExporter.exportRegister(filteredList, file.absolutePath)
                            if (Desktop.isDesktopSupported() && file.exists()) {
                                Desktop.getDesktop().open(file)
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onExportRegisterExcelClick = { filteredList ->
                        try {
                            val file = File(printsDir, "NCR_Register.xlsx")
                            ExcelExporter.exportRegister(filteredList, file, isCar = false)
                            if (Desktop.isDesktopSupported() && file.exists()) {
                                Desktop.getDesktop().open(file)
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onExportCarRegisterPdfClick = { filteredList ->
                        try {
                            val file = File(printsDir, "CAR_Register.pdf")
                            PdfExporter.exportRegister(filteredList, file.absolutePath, isCar = true)
                            if (Desktop.isDesktopSupported() && file.exists()) {
                                Desktop.getDesktop().open(file)
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onExportCarRegisterExcelClick = { filteredList ->
                        try {
                            val file = File(printsDir, "CAR_Register.xlsx")
                            ExcelExporter.exportRegister(filteredList, file, isCar = true)
                            if (Desktop.isDesktopSupported() && file.exists()) {
                                Desktop.getDesktop().open(file)
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onPrintNcrExcelClick = { id ->
                        try {
                            val ncr = NcrRepository.getNcrById(id)
                            if (ncr != null) {
                                val safeNcrNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                val file = File(printsDir, "NCR_$safeNcrNo.xlsx")
                                ExcelExporter.exportNcrForm(ncr, file)
                                if (Desktop.isDesktopSupported() && file.exists()) {
                                    Desktop.getDesktop().open(file)
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onPrintCarExcelClick = { id ->
                        try {
                            val ncr = NcrRepository.getNcrById(id)
                            if (ncr != null) {
                                val safeNcrNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                val file = File(printsDir, "CAR_$safeNcrNo.xlsx")
                                ExcelExporter.exportCarForm(ncr, file)
                                if (Desktop.isDesktopSupported() && file.exists()) {
                                    Desktop.getDesktop().open(file)
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onOpenScannedNcrClick = { id ->
                        try {
                            val ncr = NcrRepository.getNcrById(id)
                            if (ncr != null && !ncr.ncr.isNullOrEmpty()) {
                                val safeNcrNo = ncr.ncr.replace("/", "-")
                                val scanFolder = File(baseDir, "NCR Scan")
                                val scanFile = File(scanFolder, "$safeNcrNo.pdf")
                                if (scanFile.exists()) {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(scanFile)
                                    }
                                } else {
                                    errorMessage = "Scanned NCR not found: ${scanFile.absolutePath}"
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred"
                        }
                    },
                    onArchiveNcrClick = { id, archive ->
                        try {
                            NcrRepository.archiveNcr(id, archive)
                            ncrList = NcrRepository.searchNcrFiltered(currentFilter) // refresh list
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "An unknown error occurred while archiving"
                        }
                    },
                    showArchived = showArchived,
                    onShowArchivedChange = { showArchived = it },
                    ncrList = ncrList,
                    listState = dashboardListState,
                    bulkOperationState = bulkOperationState,
                    onBulkNcrPdf = { list ->
                        BulkActionExecutor.executeBulk(
                            scope = bulkScope,
                            ncrList = list,
                            operationName = "Generating NCR PDFs",
                            outputDir = printsDir,
                            state = mutableStateOf(bulkOperationState).also {
                                // We need to use the actual state reference
                            }.let {
                                // Create a proper MutableState wrapper
                                object : MutableState<BulkOperationState> {
                                    override var value: BulkOperationState
                                        get() = bulkOperationState
                                        set(v) { bulkOperationState = v }
                                    override fun component1() = value
                                    override fun component2(): (BulkOperationState) -> Unit = { value = it }
                                }
                            },
                            fileNameProvider = { ncr ->
                                val safeNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                "NCR_$safeNo.pdf"
                            },
                            action = { ncr, destFile ->
                                PdfExporter.exportNcrForm(ncr, destFile.absolutePath)
                            }
                        )
                    },
                    onBulkCarPdf = { list ->
                        BulkActionExecutor.executeBulk(
                            scope = bulkScope,
                            ncrList = list,
                            operationName = "Generating CAR PDFs",
                            outputDir = printsDir,
                            state = object : MutableState<BulkOperationState> {
                                override var value: BulkOperationState
                                    get() = bulkOperationState
                                    set(v) { bulkOperationState = v }
                                override fun component1() = value
                                override fun component2(): (BulkOperationState) -> Unit = { value = it }
                            },
                            fileNameProvider = { ncr ->
                                val safeNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                "CAR_$safeNo.pdf"
                            },
                            action = { ncr, destFile ->
                                PdfExporter.exportCarForm(ncr, destFile.absolutePath)
                            }
                        )
                    },
                    onBulkNcrExcel = { list ->
                        BulkActionExecutor.executeBulk(
                            scope = bulkScope,
                            ncrList = list,
                            operationName = "Generating NCR Excel Forms",
                            outputDir = printsDir,
                            state = object : MutableState<BulkOperationState> {
                                override var value: BulkOperationState
                                    get() = bulkOperationState
                                    set(v) { bulkOperationState = v }
                                override fun component1() = value
                                override fun component2(): (BulkOperationState) -> Unit = { value = it }
                            },
                            fileNameProvider = { ncr ->
                                val safeNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                "NCR_$safeNo.xlsx"
                            },
                            action = { ncr, destFile ->
                                ExcelExporter.exportNcrForm(ncr, destFile)
                            }
                        )
                    },
                    onBulkCarExcel = { list ->
                        BulkActionExecutor.executeBulk(
                            scope = bulkScope,
                            ncrList = list,
                            operationName = "Generating CAR Excel Forms",
                            outputDir = printsDir,
                            state = object : MutableState<BulkOperationState> {
                                override var value: BulkOperationState
                                    get() = bulkOperationState
                                    set(v) { bulkOperationState = v }
                                override fun component1() = value
                                override fun component2(): (BulkOperationState) -> Unit = { value = it }
                            },
                            fileNameProvider = { ncr ->
                                val safeNo = ncr.ncr?.replace("/", "_") ?: "Unknown"
                                "CAR_$safeNo.xlsx"
                            },
                            action = { ncr, destFile ->
                                ExcelExporter.exportCarForm(ncr, destFile)
                            }
                        )
                    },
                    onBulkDismiss = { bulkOperationState = BulkOperationState.Idle },
                    onBulkOpenFolder = {
                        try {
                            if (Desktop.isDesktopSupported() && printsDir.exists()) {
                                Desktop.getDesktop().open(printsDir)
                            }
                        } catch (_: Exception) { }
                    }
                )
                is Screen.NcrDataEntry -> NcrDataEntryScreen(
                    ncrId = screen.ncrId,
                    onBack = { currentScreen = Screen.Dashboard }
                )
                is Screen.Settings -> SettingsScreen(
                    onBack = { currentScreen = Screen.Dashboard }
                )
            }
        }
    }
}