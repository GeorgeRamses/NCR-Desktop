package com.ncr.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ncr.app.data.NcrModel
import com.ncr.app.data.NcrRepository
import com.ncr.app.email.NotificationScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NcrDataEntryScreen(ncrId: Int?, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var model by remember { mutableStateOf(NcrModel()) }
    var initialModel by remember { mutableStateOf(NcrModel()) }
    var depts by remember { mutableStateOf(listOf<Pair<Int, String>>()) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(ncrId) {
        depts = NcrRepository.getAllDepts()
        if (ncrId != null) {
            val existing = NcrRepository.getNcrById(ncrId)
            if (existing != null) {
                model = existing
                initialModel = existing.copy()
            }
        } else {
            model = model.copy(date = LocalDate.now())
            initialModel = model.copy()
        }
    }

    val hasChanges = model != initialModel

    fun handleBack() {
        if (hasChanges) {
            showExitConfirmation = true
        } else {
            onBack()
        }
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes. Are you sure you want to go back? All changes will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showExitConfirmation = false
                    onBack()
                }) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    handleBack()
                    true
                } else if (event.type == KeyEventType.KeyDown && event.key == Key.Tab) {
                    focusManager.moveFocus(if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next)
                    true
                } else {
                    false
                }
            },
        topBar = {
            TopAppBar(
                title = { Text(if (ncrId == null) "Create New NCR" else "Edit NCR ${model.ncr ?: ""}") },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    Button(onClick = {
                        val isNew = ncrId == null
                        val wasClosed = initialModel.actualCloseDate == null
                        val isNowClosed = model.actualCloseDate != null
                        NcrRepository.saveNcr(model)
                        scope.launch {
                            if (isNew) {
                                NotificationScheduler.notifyOnCreate(model)
                            } else if (wasClosed && isNowClosed) {
                                NotificationScheduler.notifyOnClose(model)
                            } else {
                                NotificationScheduler.notifyOnUpdate(model)
                            }
                        }
                        onBack()
                    }, modifier = Modifier.padding(end = 16.dp)) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 32.dp).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            
            // Section 1: General Info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("General Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = model.jobNo ?: "",
                            onValueChange = { 
                                val newJobNo = it
                                val newNcrNo = if (ncrId == null && newJobNo.length >= 2) NcrRepository.generateNextNcrNo(newJobNo) else model.ncr
                                model = model.copy(
                                    jobNo = newJobNo, 
                                    ncr = newNcrNo,
                                    carNo = if (ncrId == null) newNcrNo else model.carNo
                                ) 
                            },
                            label = { Text("Job No") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = model.ncr ?: "",
                            onValueChange = { model = model.copy(ncr = it) },
                            label = { Text("NCR No") },
                            readOnly = ncrId == null, // Auto generated initially
                            modifier = Modifier.weight(1f)
                        )
                        DatePickerField(
                            label = "Date",
                            value = model.date ?: LocalDate.now(),
                            onValueChange = { model = model.copy(date = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = model.bay ?: "",
                            onValueChange = { model = model.copy(bay = it) },
                            label = { Text("Bay") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = model.wo?.toString() ?: "",
                            onValueChange = { model = model.copy(wo = it.toIntOrNull()) },
                            label = { Text("W.O.") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = model.drawingPoNo ?: "",
                            onValueChange = { model = model.copy(drawingPoNo = it) },
                            label = { Text("Drawing / PO No") },
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = model.itemNo ?: "",
                            onValueChange = { model = model.copy(itemNo = it) },
                            label = { Text("Item No") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Section 2: Issue Type
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Issue Type", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            LabeledCheckbox("Customer Complaint", model.customerComplain) { model = model.copy(customerComplain = it) }
                            LabeledCheckbox("Product", model.product) { model = model.copy(product = it) }
                        }
                        Column {
                            LabeledCheckbox("Material", model.material) { model = model.copy(material = it) }
                            LabeledCheckbox("Environmental Issue", model.environmentalIssue) { model = model.copy(environmentalIssue = it) }
                        }
                        Column {
                            LabeledCheckbox("Safety Issue", model.safetyIssue) { model = model.copy(safetyIssue = it) }
                            LabeledCheckbox("Quality Management Issue", model.qualityManagementIssue) { model = model.copy(qualityManagementIssue = it) }
                        }
                    }
                }
            }

            // Section 3: Details
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Description of Non-Conformity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = model.descriptionOfNonConformity ?: "",
                        onValueChange = { model = model.copy(descriptionOfNonConformity = it) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = model.reference ?: "",
                            onValueChange = { model = model.copy(reference = it) },
                            label = { Text("Reference") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = model.ncrRaisedBy ?: "",
                            onValueChange = { model = model.copy(ncrRaisedBy = it) },
                            label = { Text("NCR Raised By") },
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Responsible Department Selector
                        var deptDropdownExpanded by remember { mutableStateOf(false) }
                        val currentDeptName = depts.find { it.first == model.responsibleDept }?.second ?: "Select Department"
                        Box(modifier = Modifier.weight(1f)) {
                            ExposedDropdownMenuBox(
                                expanded = deptDropdownExpanded,
                                onExpandedChange = { deptDropdownExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = currentDeptName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Responsible Department") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptDropdownExpanded) },
                                    modifier = Modifier.menuAnchor().fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = deptDropdownExpanded,
                                    onDismissRequest = { deptDropdownExpanded = false }
                                ) {
                                    depts.forEach { dept ->
                                        DropdownMenuItem(
                                            text = { Text(dept.second) },
                                            onClick = {
                                                model = model.copy(responsibleDept = dept.first)
                                                deptDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Section 4: Disposition
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recommended Disposition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = model.recommendedDisposition ?: "",
                        onValueChange = { model = model.copy(recommendedDisposition = it) },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = model.suggestedBy ?: "",
                            onValueChange = { model = model.copy(suggestedBy = it) },
                            label = { Text("Suggested By") },
                            modifier = Modifier.weight(1f)
                        )
                        DatePickerField(
                            label = "Disposition Date",
                            value = model.dispositionDate,
                            onValueChange = { model = model.copy(dispositionDate = it) },
                            modifier = Modifier.weight(1f)
                        )
                        DatePickerField(
                            label = "Target Completion Date",
                            value = model.targetCompletionDate,
                            onValueChange = { model = model.copy(targetCompletionDate = it) },
                            modifier = Modifier.weight(1f)
                        )
                        DatePickerField(
                            label = "Actual Close Date",
                            value = model.actualCloseDate,
                            onValueChange = { model = model.copy(actualCloseDate = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            // Section 5: Corrective Action Report (CAR)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    var isCarNoEditable by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isCarNoEditable) {
                            Text("Corrective Action Report (CAR) - CAR No:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            OutlinedTextField(
                                value = model.carNo ?: "",
                                onValueChange = { model = model.copy(carNo = it) },
                                singleLine = true,
                                modifier = Modifier.width(180.dp)
                            )
                            IconButton(onClick = { isCarNoEditable = false }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            val carNoText = if (!model.carNo.isNullOrBlank()) " - ${model.carNo}" else ""
                            Text(
                                text = "Corrective Action Report (CAR)$carNoText",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { isCarNoEditable = true }) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit CAR No",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = model.methodUsed ?: "",
                        onValueChange = { model = model.copy(methodUsed = it) },
                        label = { Text("Method Used") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = model.rootCause ?: "",
                        onValueChange = { model = model.copy(rootCause = it) },
                        label = { Text("Root Cause") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4
                    )
                    OutlinedTextField(
                        value = model.correctiveActionAgreed ?: "",
                        onValueChange = { model = model.copy(correctiveActionAgreed = it) },
                        label = { Text("Corrective Action Agreed") },
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        maxLines = 4
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun LabeledCheckbox(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: LocalDate?,
    onValueChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value?.toString() ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Row {
                    if (value != null) {
                        IconButton(onClick = { onValueChange(null) }) {
                            Icon(androidx.compose.material.icons.Icons.Default.Clear, contentDescription = "Clear Date")
                        }
                    }
                    IconButton(onClick = { showDialog = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().clickable { showDialog = true }
        )
    }

    if (showDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis
                    if (selectedMillis != null) {
                        val date = Instant.ofEpochMilli(selectedMillis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                        onValueChange(date)
                    }
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onValueChange(null)
                        showDialog = false
                    }) {
                        Text("Clear")
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
