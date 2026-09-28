package br.com.willendary.designacoesjw

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ActionCodeSettings
import com.google.firebase.auth.FirebaseAuth
import br.com.willendary.designacoesjw.data.*

@Composable
fun UserManagementScreen(vm: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var selectedPermissions by remember { mutableStateOf(setOf(AppPermissions.VIEW_ASSIGNMENTS, AppPermissions.EXPORT_REPORTS)) }
    var status by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<UserProfile?>(null) }

    fun toggle(permission: String) {
        selectedPermissions = selectedPermissions.toMutableSet().also { if (!it.add(permission)) it.remove(permission) }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                Text("Usuários e convites", style = MaterialTheme.typography.headlineSmall)
            }
            Text("Convide irmãos por e-mail e defina exatamente o que cada conta poderá fazer.")
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.GroupAdd, contentDescription = null)
                        Text("Novo convite", style = MaterialTheme.typography.titleLarge)
                    }
                    OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(name, { name = it }, label = { Text("Nome (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Permissões concedidas após aceitar", style = MaterialTheme.typography.titleMedium)
                    AppPermissions.all.forEach { permission ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(AppPermissions.label(permission), Modifier.weight(1f))
                            Checkbox(permission in selectedPermissions, { toggle(permission) })
                        }
                    }
                    Button(
                        enabled = email.isNotBlank() && AppPermissions.VIEW_ASSIGNMENTS in selectedPermissions,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            status = "Criando convite..."
                            vm.createInvitation(email, name, selectedPermissions) { invitation, error ->
                                if (error != null) {
                                    status = error
                                } else if (invitation == null) {
                                    status = "Não foi possível criar o convite."
                                } else {
                                    val settings = ActionCodeSettings.newBuilder()
                                        .setUrl("https://designacoes-jw.firebaseapp.com/finishSignUp?inviteId=" + invitation.id)
                                        .setHandleCodeInApp(true)
                                        .setAndroidPackageName("br.com.willendary.designacoesjw", true, null)
                                        .build()
                                    FirebaseAuth.getInstance().sendSignInLinkToEmail(invitation.email, settings)
                                        .addOnSuccessListener {
                                            status = "Convite enviado para " + invitation.email + "."
                                            email = ""
                                            name = ""
                                        }
                                        .addOnFailureListener {
                                            status = "Convite criado, mas o Firebase não enviou o e-mail: " + (it.localizedMessage ?: "erro desconhecido")
                                        }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Email, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Enviar convite por e-mail")
                    }
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }

        item { Text("Usuários cadastrados", style = MaterialTheme.typography.titleLarge) }

        items(vm.users.value, key = { it.uid }) { user ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(user.name.ifBlank { user.email }, style = MaterialTheme.typography.titleMedium)
                    Text(user.email, style = MaterialTheme.typography.bodySmall)
                    Text(
                        if (user.role == "admin") "Administrador — todas as permissões"
                        else if (user.active) "Usuário ativo" else "Usuário bloqueado"
                    )
                    if (user.role != "admin") {
                        Text(user.permissions.sorted().joinToString(" • ") { AppPermissions.label(it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ editing = user }) { Text("Editar permissões") }
                        if (user.uid != FirebaseAuth.getInstance().currentUser?.uid) {
                            OutlinedButton({
                                vm.updateUser(user.copy(active = !user.active)) { error ->
                                    status = error ?: if (user.active) "Usuário bloqueado." else "Usuário reativado."
                                }
                            }) { Text(if (user.active) "Bloquear" else "Ativar") }
                        }
                    }
                }
            }
        }

        item { Text("Convites recentes", style = MaterialTheme.typography.titleLarge) }
        items(vm.invitations.value, key = { it.id }) { invitation ->
            ListItem(
                headlineContent = { Text(invitation.email) },
                supportingContent = {
                    Text((if (invitation.status == "accepted") "Aceito" else "Pendente") + " • " + invitation.permissions.size + " permissões")
                }
            )
        }
    }

    editing?.let { user ->
        EditUserPermissionsDialog(
            user = user,
            onSave = { updated ->
                vm.updateUser(updated) { error ->
                    status = error ?: "Permissões atualizadas."
                    if (error == null) editing = null
                }
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun EditUserPermissionsDialog(
    user: UserProfile,
    onSave: (UserProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var permissions by remember(user.uid) { mutableStateOf(user.permissions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissões de " + user.name.ifBlank { user.email }) },
        text = {
            Column {
                if (user.role == "admin") {
                    Text("Administrador possui todas as permissões.")
                } else {
                    AppPermissions.all.forEach { permission ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(AppPermissions.label(permission), Modifier.weight(1f))
                            Checkbox(
                                checked = permission in permissions,
                                onCheckedChange = { checked ->
                                    permissions = permissions.toMutableSet().also {
                                        if (checked) it.add(permission) else it.remove(permission)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton({
                if (user.role == "admin") onSave(user) else onSave(user.copy(permissions = permissions))
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancelar") } }
    )
}
