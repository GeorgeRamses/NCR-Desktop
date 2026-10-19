package com.ncr.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncr.app.data.BulkOperationState
import com.ncr.app.data.NcrFilter
import com.ncr.app.data.NcrModel
import com.ncr.app.data.NcrRepository
import com.ncr.app.data.NcrStatus
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedProject: String?,
    onSelectedProjectChange: (String?) -> Unit,
    dateFromText: String,
    onDateFromTextChange: (String) -> Unit,
    dateToText: String,
    onDateToTextChange: (String) -> Unit,
    selectedStatus: NcrStatus,
    onSelectedStatusChange: (NcrStatus) -> Unit,
    selectedDept: Int?,
    onSelectedDeptChange: (Int?) -> Unit,
    onNewNcrClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditNcrClick: (Int) -> Unit,
    onPrintNcrPdfClick: (Int) -> Unit,
    onPrintCarPdfClick: (Int) -> Unit,
    onExportRegisterPdfClick: (List<NcrModel>) -> Unit,
    onExportRegisterExcelClick: (List<NcrModel>) -> Unit,
    onExportCarRegisterPdfClick: (List<NcrModel>) -> Unit,
    onExportCarRegisterExcelClick: (List<NcrModel>) -> Unit,
    onPrintNcrExcelClick: (Int) -> Unit,
    onPrintCarExcelClick: (Int) -> Unit,
    onOpenScannedNcrClick: (Int) -> Unit,
    onArchiveNcrClick: (Int, Boolean) -> Unit,
    showArchived: Boolean,
    onShowArchivedChange: (Boolean) -> Unit,
    ncrList: List<NcrModel>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    // Bulk operation state and callbacks
    bulkOperationState: BulkOperationState = BulkOperationState.Idle,
    onBulkNcrPdf: (List<NcrModel>) -> Unit = {},
    onBulkCarPdf: (List<NcrModel>) -> Unit = {},
    onBulkNcrExcel: (List<NcrModel>) -> Unit = {},
    onBulkCarExcel: (List<NcrModel>) -> Unit = {},
    onBulkDismiss: () -> Unit = {},
    onBulkOpenFolder: () -> Unit = {}
) {
    var projectList by remember { mutableStateOf(listOf<String>()) }
    // Dropdown expanded states
    var projectDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }

    var deptList by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
    var deptDropdownExpanded by remember { mutableStateOf(false) }

    // Selected row tracking for single-click highlight
    var selectedNcrId by remember { mutableStateOf<Int?>(null) }

    // Parse dates safely
    val dateFrom = try { if (dateFromText.isNotBlank()) LocalDate.parse(dateFromText) else null } catch (e: Exception) { null }
    val dateTo = try { if (dateToText.isNotBlank()) LocalDate.parse(dateToText) else null } catch (e: Exception) { null }

    // Load projects and depts once
    LaunchedEffect(Unit) {
        projectList = NcrRepository.getDistinctProjects()
        deptList = NcrRepository.getAllDepts()
    }

    val deptMap = remember(deptList) { deptList.toMap() }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewNcrClick,
                icon = { Icon(Icons.Default.Add, contentDescription = "New NCR") },
                text = { Text("Create NCR") }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("NCR Management Dashboard", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onSettingsClick) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
            Spacer(Modifier.height(20.dp))

            TabRow(selectedTabIndex = if (showArchived) 1 else 0, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Tab(selected = !showArchived, onClick = { onShowArchivedChange(false) }, text = { Text("Active NCRs") })
                Tab(selected = showArchived, onClick = { onShowArchivedChange(true) }, text = { Text("Archived NCRs") })
            }

            // === Filter Panel ===
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    // Row 1: Search + Project + Dept + Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Search
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = onSearchQueryChange,
                            label = { Text("Search NCR No / Job No") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )

                        // Project Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = projectDropdownExpanded,
                                onExpandedChange = { projectDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedProject ?: "All Projects",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Project") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = projectDropdownExpanded,
                                    onDismissRequest = { projectDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Projects") },
                                        onClick = {
                                            onSelectedProjectChange(null)
                                            projectDropdownExpanded = false
                                        }
                                    )
                                    projectList.forEach { project ->
                                        DropdownMenuItem(
                                            text = { Text(project) },
                                            onClick = {
                                                onSelectedProjectChange(project)
                                                projectDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Dept Dropdown
                        val currentDeptName = deptList.find { it.first == selectedDept }?.second ?: "All Depts"
                        Box(modifier = Modifier.weight(1.2f)) {
                            ExposedDropdownMenuBox(
                                expanded = deptDropdownExpanded,
                                onExpandedChange = { deptDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = currentDeptName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Department") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = deptDropdownExpanded,
                                    onDismissRequest = { deptDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Depts") },
                                        onClick = {
                                            onSelectedDeptChange(null)
                                            deptDropdownExpanded = false
                                        }
                                    )
                                    deptList.forEach { dept ->
                                        DropdownMenuItem(
                                            text = { Text(dept.second) },
                                            onClick = {
                                                onSelectedDeptChange(dept.first)
                                                deptDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Status Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = statusDropdownExpanded,
                                onExpandedChange = { statusDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = when (selectedStatus) {
                                        NcrStatus.ALL -> "All"
                                        NcrStatus.OPEN -> "Open NCR"
                                        NcrStatus.CLOSED -> "Closed NCR"
                                        NcrStatus.OVERDUE -> "Overdue NCR"
                                        NcrStatus.UPCOMING -> "Upcoming NCR"
                                    },
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Status") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = statusDropdownExpanded,
                                    onDismissRequest = { statusDropdownExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All") },
                                        onClick = { onSelectedStatusChange(NcrStatus.ALL); statusDropdownExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open NCR") },
                                        onClick = { onSelectedStatusChange(NcrStatus.OPEN); statusDropdownExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Closed NCR") },
                                        onClick = { onSelectedStatusChange(NcrStatus.CLOSED); statusDropdownExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Overdue NCR") },
                                        onClick = { onSelectedStatusChange(NcrStatus.OVERDUE); statusDropdownExpanded = false }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Upcoming NCR") },
                                        onClick = { onSelectedStatusChange(NcrStatus.UPCOMING); statusDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Date range + Clear + Export buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DatePickerField(
                            label = "Date From",
                            value = dateFrom,
                            onValueChange = { onDateFromTextChange(it?.toString() ?: "") },
                            modifier = Modifier.weight(1.2f)
                        )
                        DatePickerField(
                            label = "Date To",
                            value = dateTo,
                            onValueChange = { onDateToTextChange(it?.toString() ?: "") },
                            modifier = Modifier.weight(1.2f)
                        )

                        // Clear Filters
                        OutlinedButton(
                            onClick = {
                                onSearchQueryChange("")
                                onSelectedProjectChange(null)
                                onSelectedDeptChange(null)
                                onDateFromTextChange("")
                                onDateToTextChange("")
                                onSelectedStatusChange(NcrStatus.ALL)
                            },
                            modifier = Modifier.height(56.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear")
                        }
                    }

                    // Row 3: Export buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Export PDF
                        Button(
                            onClick = { onExportRegisterPdfClick(ncrList) },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("NCR Register (PDF)")
                        }

                        // Export Excel
                        Button(
                            onClick = { onExportRegisterExcelClick(ncrList) },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)) // Green for Excel
                        ) {
                            Text("NCR Register (Excel)")
                        }

                        // Export CAR PDF
                        Button(
                            onClick = { onExportCarRegisterPdfClick(ncrList) },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("CAR Register (PDF)")
                        }

                        // Export CAR Excel
                        Button(
                            onClick = { onExportCarRegisterExcelClick(ncrList) },
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C)) // Teal for CAR Excel
                        ) {
                            Text("CAR Register (Excel)")
                        }
                    }

                    // Result count
                    Text(
                        "${ncrList.size} record(s) found",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // === Bulk Operation Progress Dialog ===
            if (bulkOperationState is BulkOperationState.Running) {
                val runState = bulkOperationState as BulkOperationState.Running
                val animatedProgress by animateFloatAsState(
                    targetValue = runState.progress,
                    label = "bulkProgress"
                )
                AlertDialog(
                    onDismissRequest = { /* Non-dismissable while running */ },
                    title = {
                        Text(
                            runState.operationName,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                "Processing ${runState.currentItem}...  (${runState.completed}/${runState.total})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (runState.failures.isNotEmpty()) {
                                Text(
                                    "${runState.failures.size} failure(s) so far",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { runState.onCancel() }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // === Bulk Operation Completion Dialog ===
            if (bulkOperationState is BulkOperationState.Completed) {
                val doneState = bulkOperationState as BulkOperationState.Completed
                AlertDialog(
                    onDismissRequest = onBulkDismiss,
                    title = {
                        Text(
                            "${doneState.operationName} — Complete",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Success summary
                            Text(
                                "✅  ${doneState.successCount} / ${doneState.total} files generated successfully.",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            if (doneState.failures.isNotEmpty()) {
                                HorizontalDivider()
                                Text(
                                    "❌  ${doneState.failures.size} failure(s):",
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                doneState.failures.forEach { failure ->
                                    Text(
                                        "• ${failure.ncrNo}: ${failure.error}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                onBulkOpenFolder()
                            }) {
                                Text("Open Folder")
                            }
                            Button(onClick = onBulkDismiss) {
                                Text("OK")
                            }
                        }
                    }
                )
            }

            // BoxWithConstraints for the table area only
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val hScrollState = rememberScrollState()
                val minContentWidth = 1800.dp // Increased min width to fit all action buttons
                val contentWidth = maxOf(maxWidth, minContentWidth)
                val isBulkRunning = bulkOperationState is BulkOperationState.Running

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize().horizontalScroll(hScrollState)) {

                        // === Bulk Actions Bar ===
                        Row(
                            modifier = Modifier
                                .width(contentWidth)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Bulk Actions",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "(${ncrList.size} records)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.width(8.dp))

                            // --- NCR PDF ---
                            FilledTonalButton(
                                onClick = { onBulkNcrPdf(ncrList) },
                                enabled = ncrList.isNotEmpty() && !isBulkRunning,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text("All NCR PDF (${ncrList.size})", fontSize = 11.sp)
                            }

                            // --- CAR PDF ---
                            FilledTonalButton(
                                onClick = { onBulkCarPdf(ncrList) },
                                enabled = ncrList.isNotEmpty() && !isBulkRunning,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Text("All CAR PDF (${ncrList.size})", fontSize = 11.sp)
                            }

                            // --- NCR Excel ---
                            FilledTonalButton(
                                onClick = { onBulkNcrExcel(ncrList) },
                                enabled = ncrList.isNotEmpty() && !isBulkRunning,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFC8E6C9),
                                    contentColor = Color(0xFF1B5E20)
                                )
                            ) {
                                Text("All NCR Excel (${ncrList.size})", fontSize = 11.sp)
                            }

                            // --- CAR Excel ---
                            FilledTonalButton(
                                onClick = { onBulkCarExcel(ncrList) },
                                enabled = ncrList.isNotEmpty() && !isBulkRunning,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color(0xFFA5D6A7),
                                    contentColor = Color(0xFF1B5E20)
                                )
                            ) {
                                Text("All CAR Excel (${ncrList.size})", fontSize = 11.sp)
                            }
                        }

                        // === Table Header ===
                        Row(
                            modifier = Modifier.width(contentWidth).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("NCR No", modifier = Modifier.weight(1.0f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Job No", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Date", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Resp. Dept", modifier = Modifier.weight(1.0f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Description", modifier = Modifier.weight(1.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Completion Date", modifier = Modifier.weight(1.0f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Status", modifier = Modifier.weight(0.6f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Actions", modifier = Modifier.weight(3.8f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)

                        // === Table Rows ===
                        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
                                items(items = ncrList, key = { it.id ?: it.hashCode() }) { ncr ->
                                    val isClosed = ncr.actualCloseDate != null
                                    val isOverdue = !isClosed && ncr.targetCompletionDate != null && ncr.targetCompletionDate.isBefore(LocalDate.now())
                                    val isSelected = selectedNcrId == ncr.id
                                    val rowBackgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else if (isOverdue) Color(0xFFFFF3E0)
                                    else Color.Transparent

                                    Row(
                                        modifier = Modifier
                                            .width(contentWidth)
                                            .background(rowBackgroundColor)
                                            .pointerInput(ncr.id) {
                                                detectTapGestures(
                                                    onTap = {
                                                        selectedNcrId = ncr.id
                                                    },
                                                    onDoubleTap = {
                                                        ncr.id?.let { onEditNcrClick(it) }
                                                    }
                                                )
                                            }
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(ncr.ncr ?: "N/A", modifier = Modifier.weight(1.0f), fontSize = 13.sp)
                                        Text(ncr.jobNo ?: "N/A", modifier = Modifier.weight(0.8f), fontSize = 13.sp)
                                        Text(ncr.date?.toString() ?: "N/A", modifier = Modifier.weight(0.8f), fontSize = 13.sp)
                                        Text(deptMap[ncr.responsibleDept] ?: "N/A", modifier = Modifier.weight(1.0f), fontSize = 13.sp)
                                        Text(
                                            text = ncr.descriptionOfNonConformity?.take(30)?.let { if (it.length == 30) "$it..." else it } ?: "",
                                            modifier = Modifier.weight(1.8f),
                                            fontSize = 13.sp
                                        )
                                        Text(ncr.targetCompletionDate?.toString() ?: "N/A", modifier = Modifier.weight(1.0f), fontSize = 13.sp)

                                        // Status badge (NCR is closed if actualCloseDate has a value)
                                        Box(modifier = Modifier.weight(0.6f)) {
                                            Text(
                                                text = if (isClosed) "Closed" else "Open",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isClosed) Color(0xFF4CAF50) else Color(0xFFF44336))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }

                                        // Actions
                                        Row(
                                            modifier = Modifier.weight(3.8f),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(onClick = { ncr.id?.let { onEditNcrClick(it) } }, modifier = Modifier.size(32.dp)) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            }

                                            FilledTonalButton(
                                                onClick = { ncr.id?.let { onPrintNcrPdfClick(it) } },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                            ) {
                                                Text("NCR PDF", fontSize = 11.sp)
                                            }
                                            FilledTonalButton(
                                                onClick = { ncr.id?.let { onPrintCarPdfClick(it) } },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                            ) {
                                                Text("CAR PDF", fontSize = 11.sp)
                                            }

                                            // EXCEL BUTTONS
                                            FilledTonalButton(
                                                onClick = { ncr.id?.let { onPrintNcrExcelClick(it) } },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFC8E6C9), contentColor = Color(0xFF1B5E20))
                                            ) {
                                                Text("NCR Excel", fontSize = 11.sp)
                                            }
                                            FilledTonalButton(
                                                onClick = { ncr.id?.let { onPrintCarExcelClick(it) } },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFA5D6A7), contentColor = Color(0xFF1B5E20))
                                            ) {
                                                Text("CAR Excel", fontSize = 11.sp)
                                            }

                                            // SCAN BUTTON
                                            FilledTonalButton(
                                                onClick = { ncr.id?.let { onOpenScannedNcrClick(it) } },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp),
                                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFB3E5FC), contentColor = Color(0xFF01579B))
                                            ) {
                                                Text("Scan", fontSize = 11.sp)
                                            }

                                            Spacer(modifier = Modifier.width(16.dp)) // Added space to separate Archive from other actions

                                            // ARCHIVE/UNARCHIVE BUTTON
                                            if (showArchived) {
                                                FilledTonalButton(
                                                    onClick = { ncr.id?.let { onArchiveNcrClick(it, false) } },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                                                ) {
                                                    Text("Unarchive", fontSize = 11.sp)
                                                }
                                            } else {
                                                FilledTonalButton(
                                                    onClick = { ncr.id?.let { onArchiveNcrClick(it, true) } },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                                                ) {
                                                    Text("Archive", fontSize = 11.sp)
                                                }
                                            }
                                        }
                                    }
                                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = Color.LightGray)
                                }
                            }

                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(listState)
                            )
                        }
                    }
                    HorizontalScrollbar(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(end = 12.dp),
                        adapter = rememberScrollbarAdapter(hScrollState)
                    )
                }
            }
        }
    }
}