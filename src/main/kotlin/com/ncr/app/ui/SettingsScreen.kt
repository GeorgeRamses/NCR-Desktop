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
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.ncr.app.email.NotificationScheduler
import com.ncr.app.data.*
import com.ncr.app.email.EmailService
import com.ncr.app.email.GraphMailer
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
                            },
                            onSendNow = {
                                scope.launch {
                                    testResult = "Sending..."
                                    testResult = NotificationScheduler.runRuleNow(rule)
                                    rules = NotificationRepository.getAllRules()
                                }
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
    onToggleEnabled: () -> Unit,
    onSendNow: () -> Unit
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

            if (rule.triggerOnOverdue || rule.triggerOnOpen) {
                Text(
                    "Last sent: " + (rule.lastRunAt?.toString()?.replace('T', ' ')?.take(16) ?: "never"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                FilledTonalIconButton(onClick = onSendNow) {
                    Icon(Icons.Default.Send, contentDescription = "Send now")
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
    var checkIntervalMinutes by remember {
        mutableStateOf((rule?.checkIntervalMinutes ?: 1440).takeIf { it in listOf(1440, 10080, 20160, 43200) } ?: 1440)
    }
    var sendHour by remember { mutableStateOf(rule?.sendHour ?: 8) }
    var dayOfWeek by remember { mutableStateOf(rule?.dayOfWeek ?: 0) }
    // 0 = SMTP, 1 = classic Outlook on this PC, 2 = Microsoft account (Graph)
    var sendMethod by remember {
        mutableStateOf(
            when {
                rule?.useGraph == true -> 2
                rule?.useOutlook == true -> 1
                else -> 1
            }
        )
    }
    var graphTenant by remember { mutableStateOf("") }
    var graphClient by remember { mutableStateOf("") }
    var graphEmail by remember { mutableStateOf<String?>(null) }
    var graphSignedIn by remember { mutableStateOf(false) }
    var graphBusy by remember { mutableStateOf(false) }
    val dialogScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val acc = GraphRepository.get()
        if (acc != null) {
            graphTenant = acc.tenantId
            graphClient = acc.clientId
            graphEmail = acc.accountEmail
            graphSignedIn = !acc.refreshToken.isNullOrBlank()
        }
    }

    // mutableStateListOf so Add / remove actually redraw (a plain MutableList inside mutableStateOf does not)
    val recipients = remember {
        mutableStateListOf<String>().apply {
            if (rule?.id != null) {
                addAll(NotificationRepository.getRecipients(rule.id).map { it.emailAddress })
            }
        }
    }
    var newRecipient by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.width(640.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
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
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DropdownField(
                            label = "Send method",
                            options = listOf(
                                0 to "SMTP (host + password)",
                                1 to "Classic Outlook on this PC",
                                2 to "Microsoft account (Graph)"
                            ),
                            selected = sendMethod,
                            onSelect = { sendMethod = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            when (sendMethod) {
                                1 -> "Uses classic Outlook already signed in on this PC."
                                2 -> "Uses the Microsoft account configured below."
                                else -> "Fill in the SMTP settings below."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    if (sendMethod == 0) {
                        Text("SMTP Settings", style = MaterialTheme.typography.labelLarge)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Office 365 / Outlook Settings",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Host: smtp-mail.outlook.com | Port: 587 | SSL: OFF",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    "Use your App Password from the Authenticator app.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        smtpHost = "smtp-mail.outlook.com"
                                        smtpPort = "587"
                                        useSsl = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Apply Office 365 Defaults")
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = smtpHost,
                            onValueChange = { smtpHost = it },
                            label = { Text("SMTP Host") },
                            placeholder = { Text("smtp-mail.outlook.com") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = smtpPort,
                            onValueChange = { smtpPort = it },
                            label = { Text("Port") },
                            placeholder = { Text("587") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = smtpUser,
                            onValueChange = { smtpUser = it },
                            label = { Text("Email Address") },
                            placeholder = { Text("user@company.com") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = smtpPassword,
                            onValueChange = { smtpPassword = it },
                            label = { Text("App Password") },
                            placeholder = { Text("Your App Password from Authenticator") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = useSsl, onCheckedChange = { useSsl = it })
                            Text("Use SSL (port 465)")
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "(Leave OFF for port 587)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item {
                    if (sendMethod == 2) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Microsoft Graph Settings", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "Configure your Microsoft 365 account to send emails via Graph API.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (graphSignedIn) "Signed in${graphEmail?.let { " as $it" } ?: ""}" else "Not signed in",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = graphTenant,
                                    onValueChange = { graphTenant = it },
                                    label = { Text("Directory (tenant) ID") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = graphClient,
                                    onValueChange = { graphClient = it },
                                    label = { Text("Application (client) ID") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    enabled = !graphBusy,
                                    onClick = {
                                        dialogScope.launch {
                                            graphBusy = true
                                            val result = GraphMailer.signIn(graphTenant.trim(), graphClient.trim())
                                            graphBusy = false
                                            val acc = GraphRepository.get()
                                            graphSignedIn = result.success && !acc?.refreshToken.isNullOrBlank()
                                            graphEmail = acc?.accountEmail
                                        }
                                    }
                                ) { Text(if (graphSignedIn) "Sign in again" else "Sign in") }
                                if (graphSignedIn) {
                                    OutlinedButton(onClick = {
                                        GraphMailer.signOut()
                                        graphSignedIn = false
                                        graphEmail = null
                                    }) { Text("Sign out") }
                                }
                                if (graphBusy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
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
                    if (triggerOnOverdue || triggerOnOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Reminder Schedule", style = MaterialTheme.typography.labelLarge)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DropdownField(
                                    label = "Repeat",
                                    options = listOf(
                                        1440 to "Daily",
                                        10080 to "Weekly",
                                        20160 to "Every 2 weeks",
                                        43200 to "Monthly"
                                    ),
                                    selected = checkIntervalMinutes,
                                    onSelect = { checkIntervalMinutes = it },
                                    modifier = Modifier.weight(1f)
                                )
                                DropdownField(
                                    label = "Send at",
                                    options = (0..23).map { it to "%02d:00".format(it) },
                                    selected = sendHour,
                                    onSelect = { sendHour = it },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (checkIntervalMinutes == 10080) {
                                Spacer(Modifier.height(4.dp))
                                DropdownField(
                                    label = "Day of week",
                                    options = listOf(
                                        0 to "Sunday",
                                        1 to "Monday",
                                        2 to "Tuesday",
                                        3 to "Wednesday",
                                        4 to "Thursday",
                                        5 to "Friday",
                                        6 to "Saturday"
                                    ),
                                    selected = dayOfWeek,
                                    onSelect = { dayOfWeek = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Text(
                                "Reminders are sent while the app is running. If it was closed at that time, they are sent at the next start.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                        sendHour = sendHour,
                        dayOfWeek = dayOfWeek,
                        useOutlook = sendMethod == 1,
                        useGraph = sendMethod == 2,
                        triggerOnCreate = triggerOnCreate,
                        triggerOnUpdate = triggerOnUpdate,
                        triggerOnClose = triggerOnClose,
                        triggerOnOverdue = triggerOnOverdue,
                        triggerOnOpen = triggerOnOpen
                    )
                    // Save Graph account if using Graph send method
                    if (sendMethod == 2) {
                        GraphRepository.save(
                            GraphAccount(
                                tenantId = graphTenant.trim(),
                                clientId = graphClient.trim(),
                                refreshToken = GraphRepository.get()?.refreshToken,
                                accountEmail = graphEmail
                            )
                        )
                    }
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> DropdownField(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == selected }?.second ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = { onSelect(value); expanded = false }
                )
            }
        }
    }
}