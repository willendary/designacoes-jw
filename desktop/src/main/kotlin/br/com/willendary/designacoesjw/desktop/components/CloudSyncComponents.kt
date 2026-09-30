package br.com.willendary.designacoesjw.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.desktop.StoreController

@Composable
fun CloudSyncBar(c: StoreController) {
    var showLoginDialog by remember { mutableStateOf(false) }
    val session = c.authSession

    if (session == null) {
        OutlinedButton(
            onClick = { showLoginDialog = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Entrar na Conta (Nuvem)", fontSize = 13.sp)
        }
    } else {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.clip(RoundedCornerShape(20.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.CloudDone,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = session.email.ifBlank { "Conectado" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                if (c.isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(
                        onClick = { c.syncWithCloud() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Sync,
                            contentDescription = "Sincronizar agora",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { c.logout() },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Sair da conta",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showLoginDialog) {
        DesktopLoginDialog(
            c = c,
            onDismiss = { showLoginDialog = false }
        )
    }
}

@Composable
fun DesktopLoginDialog(
    c: StoreController,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
                Text("Entrar com Firebase", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.width(380.dp)
            ) {
                Text(
                    "Faça login com a mesma conta de e-mail e senha cadastrada no aplicativo Android para acessar os dados da congregação em tempo real.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("E-mail") },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Senha") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Informe o e-mail e a senha."
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    kotlin.concurrent.thread {
                        val err = c.login(email.trim(), password)
                        isLoading = false
                        if (err == null) {
                            onDismiss()
                        } else {
                            errorMessage = err
                        }
                    }
                },
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Entrando...")
                } else {
                    Text("Entrar")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Cancelar")
            }
        }
    )
}
