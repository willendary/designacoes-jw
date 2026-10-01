package br.com.willendary.designacoesjw.desktop.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import java.awt.Desktop
import java.net.URI

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
    var googleLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var authUrl by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading && !googleLoading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CloudSync, null, tint = MaterialTheme.colorScheme.primary)
                Text("Entrar na Conta", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.width(400.dp)
            ) {
                Text(
                    "Sincronize seus dados com a nuvem. Use a mesma conta do aplicativo Android.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // ── Botão Google ─────────────────────────────────────────
                Surface(
                    onClick = {
                        if (!isLoading && !googleLoading) {
                            googleLoading = true
                            errorMessage = null
                            authUrl = null
                            statusMessage = "Abrindo navegador para login com Google…"
                            c.loginWithGoogle(
                                // loginWithGoogle roda em thread de background.
                                // Escrever estado do Compose de fora da thread de
                                // UI não é seguro; o mesmo padrao ja usado em
                                // Main.kt:252.
                                onAuthUrl = { url ->
                                    java.awt.EventQueue.invokeLater { authUrl = url }
                                },
                                onResult = { err ->
                                    java.awt.EventQueue.invokeLater {
                                        googleLoading = false
                                        statusMessage = null
                                        if (err == null) {
                                            onDismiss()
                                        } else {
                                            errorMessage = err
                                        }
                                    }
                                }
                            )
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(8.dp)
                        ),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (googleLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Aguardando Google…", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        } else {
                            // Ícone "G" do Google com cores oficiais
                            Box(
                                modifier = Modifier.size(24.dp).background(Color.White, CircleShape)
                                    .border(1.dp, Color(0xFFDDDDDD), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "G",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4285F4)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text("Continuar com Google", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }

                // Status do Google login (mensagem informativa + fallback manual)
                statusMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Text(msg, style = MaterialTheme.typography.bodySmall)
                            }

                            authUrl?.let { url ->
                                Text(
                                    "O navegador não abriu? Use o botão abaixo ou copie o link:",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    url,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 3,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                                        .padding(6.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Abrir no navegador", fontSize = 12.sp)
                                    }
                                    TextButton(
                                        onClick = {
                                            googleLoading = false
                                            statusMessage = null
                                            authUrl = null
                                            c.cancelGoogleLogin()
                                        }
                                    ) {
                                        Text("Cancelar", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Divisor ──────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("ou entre com e-mail", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    Divider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                // ── Formulário E-mail/Senha ───────────────────────────────
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("E-mail") },
                    leadingIcon = { Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    enabled = !isLoading && !googleLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Senha") },
                    leadingIcon = { Icon(Icons.Default.Lock, null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !isLoading && !googleLoading,
                    modifier = Modifier.fillMaxWidth()
                )

                // ── Mensagem de Erro ─────────────────────────────────────
                errorMessage?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Error,
                                null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
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
                enabled = !isLoading && !googleLoading && email.isNotBlank() && password.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Entrando…")
                } else {
                    Icon(Icons.Default.Login, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Entrar com E-mail")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading && !googleLoading
            ) {
                Text("Cancelar")
            }
        }
    )
}
