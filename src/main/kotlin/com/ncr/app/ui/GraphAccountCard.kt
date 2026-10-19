package com.ncr.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ncr.app.data.GraphRepository
import com.ncr.app.email.GraphMailer
import kotlinx.coroutines.launch

/** Sign-in card for "Send method = Microsoft account (Graph)". */
@Composable
fun GraphAccountCard(onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tenant by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var email by remember { mutableStateOf<String?>(null) }
    var signedIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val acc = GraphRepository.get()
        if (acc != null) {
            tenant = acc.tenantId
            client = acc.clientId
            email = acc.accountEmail
            signedIn = !acc.refreshToken.isNullOrBlank()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Microsoft account (Graph)", style = MaterialTheme.typography.titleMedium)
            Text(
                if (signedIn) "Signed in${email?.let { " as $it" } ?: ""}" else "Not signed in",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tenant,
                    onValueChange = { tenant = it },
                    label = { Text("Directory (tenant) ID") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = client,
                    onValueChange = { client = it },
                    label = { Text("Application (client) ID") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true
                            onMessage("A browser window opened. Sign in and approve the request...")
                            val result = GraphMailer.signIn(tenant.trim(), client.trim())
                            busy = false
                            val acc = GraphRepository.get()
                            signedIn = result.success && !acc?.refreshToken.isNullOrBlank()
                            email = acc?.accountEmail
                            onMessage(result.message)
                        }
                    }
                ) { Text(if (signedIn) "Sign in again" else "Sign in") }
                if (signedIn) {
                    OutlinedButton(onClick = {
                        GraphMailer.signOut()
                        signedIn = false
                        email = null
                        onMessage("Signed out")
                    }) { Text("Sign out") }
                }
                if (busy) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}
