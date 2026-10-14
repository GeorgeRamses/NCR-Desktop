package com.ncr.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ncr.app.data.*
import com.ncr.app.email.EmailService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rules by remember { mutableStateOf(listOf<NotificationRule>()) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<NotificationRule?>(null) }
    var showSmtpDialog by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        rules = NotificationRepository.getAllRules()
    }

    Scaffold(
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = {
                        editingRule = null
                        showRuleDialog = true
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Rule")
                }
                FloatingActionButton(
                    onClick = { showSmtpDialog = true },
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "SMTP Settings")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Notification Settings",
                    style = MaterialTheme.typography.headlineLarge
                )
                Button(onClick = onBack) {
                    Text("Back")
                }
            }

            Spacer(Modifier.height(24.dp))

            if (testResult != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(testResult ?: "")
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { testResult = null }) {
                            Text("Dismiss")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Text(
                "Notification Rules",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(8.dp))

            if (rules.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No notification rules yet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Click + to create your first rule",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rules) { rule ->
                        RuleCard(
                            rule = rule,
                            recipientCount = NotificationRepository.getRecipients(rule.id!!).size,
                            onEdit = {
                                editingRule = rule
                                showRuleDialog = true
                            },
                            onDelete = {
                                NotificationRepository.deleteRule(rule.id!!)
                                rules = NotificationRepository.getAllRules()
                            },
                            onToggleEnabled = {
                                val updated = rule.copy(enabled = !rule.enabled)
                                NotificationRepository.saveRule(updated)
                                rules = NotificationRepository.getAllRules()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRuleDialog) {
        RuleDialog(
            rule = editingRule,
            onDismiss = { showRuleDialog = false },
            onSave = { savedRule ->
                val id = NotificationRepository.saveRule(savedRule)
                if (editingRule == null) {
                    // New rule — refresh list
                    rules = NotificationRepository.getAllRules()
                } else {
                    rules = rules.map { if (it.id == editingRule?.id) savedRule.copy(id = id) else it }
                }
                showRuleDialog = false
            }
        )
    }

    if (showSmtpDialog) {
        SmtpTestDialog(
            onDismiss = { showSmtpDialog = false },
            onTest = { host, port, user, password, useSsl ->
                scope.launch {
                    val config = EmailService.SmtpConfig(host, port, user, password, useSsl)
                    val result = EmailService.testConnection(config)
                    testResult = result.message
                }
            }
        )
    }
}

@Composable
fun RuleCard(
    rule: NotificationRule,
    recipientCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.enabled) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    rule.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (rule.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onToggleEnabled() }
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Recipients: $recipientCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val triggers = mutableListOf<String>()
                if (rule.triggerOnCreate) triggers.add("Create")
                if (rule.triggerOnUpdate) triggers.add("Update")
                if (rule.triggerOnClose) triggers.add("Close")
                if (rule.triggerOnOverdue) triggers.add("Overdue")
                if (rule.triggerOnOpen) triggers.add("Open")
                Text(
                    "Triggers: ${triggers.joinToString(",")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                FilledTonalIconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Rule") },
            text = { Text("Are you sure you want to delete \"${rule.name}\"? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    onDelete()
                    showDeleteDialog = false
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleDialog(
    rule: NotificationRule?,
    onDismiss: () -> Unit,
    onSave: (NotificationRule) -> Unit
) {
    var name by remember { mutableStateOf(rule?.name ?: "") }
    var smtpHost by remember { mutableStateOf(rule?.smtpHost ?: "smtp-mail.outlook.com") }
    var smtpPort by remember { mutableStateOf(rule?.smtpPort?.toString() ?: "587") }
    var smtpUser by remember { mutableStateOf(rule?.smtpUser ?: "") }
    var smtpPassword by remember { mutableStateOf(rule?.smtpPassword ?: "") }
    var useSsl by remember { mutableStateOf(rule?.useSsl ?: false) }
    var enabled by remember { mutableStateOf(rule?.enabled ?: true) }
    var triggerOnCreate by remember { mutableStateOf(rule?.triggerOnCreate ?: false) }
    var triggerOnUpdate by remember { mutableStateOf(rule?.triggerOnUpdate ?: false) }
    var triggerOnClose by remember { mutableStateOf(rule?.triggerOnClose ?: false) }
    var triggerOnOverdue by remember { mutableStateOf(rule?.triggerOnOverdue ?: false) }
    var triggerOnOpen by remember { mutableStateOf(rule?.triggerOnOpen ?: false) }
    var checkIntervalMinutes by remember { mutableStateOf(rule?.checkIntervalMinutes ?: 1440) }

    var recipients: MutableList<String> by remember {
        mutableStateOf(
            if (rule?.id != null) {
                NotificationRepository.getRecipients(rule.id).map { it.emailAddress }.toMutableList()
            } else {
                mutableListOf()
            }
        )
    }
    var newRecipient by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule == null) "New Rule" else "Edit Rule") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Rule Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text("Recipients", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = newRecipient,
                            onValueChange = { newRecipient = it },
                            label = { Text("Email") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newRecipient.isNotBlank()) {
                                    recipients.add(newRecipient)
                                    newRecipient = ""
                                }
                            }
                        ) {
                            Text("Add")
                        }
                    }
                    if (recipients.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Column {
                            recipients.forEach { email ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(email, style = MaterialTheme.typography.bodyMedium)
                                    TextButton(onClick = { recipients.remove(email) }) {
                                        Text("✕")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("SMTP Settings", style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = smtpHost,
                        onValueChange = { smtpHost = it },
                        label = { Text("SMTP Host") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = smtpPort,
                        onValueChange = { smtpPort = it },
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = smtpUser,
                        onValueChange = { smtpUser = it },
                        label = { Text("Username") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = smtpPassword,
                        onValueChange = { smtpPassword = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
                        Text("Use SSL")
                    }
                }
                item {
                    Text("Triggers", style = MaterialTheme.typography.labelLarge)
                    CheckboxRow("When NCR is created", triggerOnCreate) { triggerOnCreate = it }
                    CheckboxRow("When NCR is updated", triggerOnUpdate) { triggerOnUpdate = it }
                    CheckboxRow("When NCR is closed", triggerOnClose) { triggerOnClose = it }
                    CheckboxRow("Overdue NCRs reminder", triggerOnOverdue) { triggerOnOverdue = it }
                    CheckboxRow("Open NCRs reminder", triggerOnOpen) { triggerOnOpen = it }
                }
                item {
                    val hasReminder = triggerOnOverdue || triggerOnOpen
                    if (hasReminder) {
                        Text("Reminder Frequency", style = MaterialTheme.typography.labelLarge)
                        val intervals = listOf(
                            Pair(1440, "Daily"),
                            Pair(10080, "Weekly"),
                            Pair(20160, "Every 2 weeks"),
                            Pair(43200, "Monthly")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            intervals.forEach { (minutes, label) ->
                                val isSelected = checkIntervalMinutes == minutes
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { checkIntervalMinutes = minutes },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                        Text("Enabled")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tempRule = NotificationRule(
                        id = rule?.id,
                        name = name,
                        smtpHost = smtpHost,
                        smtpPort = smtpPort.toIntOrNull(),
                        smtpUser = smtpUser,
                        smtpPassword = smtpPassword,
                        useSsl = useSsl,
                        enabled = enabled,
                        checkIntervalMinutes = checkIntervalMinutes,
                        triggerOnCreate = triggerOnCreate,
                        triggerOnUpdate = triggerOnUpdate,
                        triggerOnClose = triggerOnClose,
                        triggerOnOverdue = triggerOnOverdue,
                        triggerOnOpen = triggerOnOpen
                    )
                    // Save the rule first to get the ID
                    val ruleId = NotificationRepository.saveRule(tempRule)
                    // Save recipients using the returned ID
                    val existing = NotificationRepository.getRecipients(ruleId)
                    existing.forEach { NotificationRepository.removeRecipient(it.id!!) }
                    recipients.forEach { email ->
                        NotificationRepository.addRecipient(ruleId, email)
                    }
                    // Now call onSave with the rule having the correct ID
                    val savedRule = tempRule.copy(id = ruleId)
                    onSave(savedRule)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CheckboxRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmtpTestDialog(
    onDismiss: () -> Unit,
    onTest: (String, Int, String, String, Boolean) -> Unit
) {
    var host by remember { mutableStateOf("smtp-mail.outlook.com") }
    var port by remember { mutableStateOf("587") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var useSsl by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Test SMTP Connection") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("SMTP Host") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
                    Text("Use SSL")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTest(host, port.toIntOrNull() ?: 587, user, password, useSsl)
                    onDismiss()
                }
            ) {
                Text("Test")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
