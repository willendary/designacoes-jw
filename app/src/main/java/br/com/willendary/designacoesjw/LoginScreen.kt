package br.com.willendary.designacoesjw

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import br.com.willendary.designacoesjw.data.UserAccessRepository
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val context = LocalContext.current
    val activity = context as? Activity
    val auth = remember { FirebaseAuth.getInstance() }
    val credentialManager = remember { CredentialManager.create(context) }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRegister by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("designacoes_jw", android.content.Context.MODE_PRIVATE) }
    var pendingEmailLink by remember { mutableStateOf(prefs.getString("pending_email_link", null)) }
    val inviteMode = pendingEmailLink?.let { auth.isSignInWithEmailLink(it) } == true

    fun firebaseErrorMessage(t: Throwable): String = when {
        t.message?.contains("INVALID_LOGIN_CREDENTIALS", true) == true ->
            "E-mail ou senha incorretos."
        t.message?.contains("INVALID_PASSWORD", true) == true ->
            "Senha incorreta."
        t.message?.contains("USER_NOT_FOUND", true) == true ->
            "Não existe uma conta com este e-mail."
        t.message?.contains("EMAIL_EXISTS", true) == true ->
            "Este e-mail já está cadastrado."
        t.message?.contains("WEAK_PASSWORD", true) == true ->
            "A senha precisa ter pelo menos 6 caracteres."
        t.message?.contains("INVALID_EMAIL", true) == true ->
            "Informe um e-mail válido."
        else -> t.localizedMessage ?: "Não foi possível concluir a operação."
    }

    fun runAuth(block: () -> Unit) {
        error = null
        loading = true
        block()
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(
            Modifier.fillMaxWidth().widthIn(max = 430.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_logo),
                    contentDescription = "Logo Designações JW",
                    modifier = Modifier.size(82.dp)
                )
                Text("Designações JW", style = MaterialTheme.typography.headlineMedium)
                Text(
                    when {
                        inviteMode -> "Aceite o convite da congregação"
                        showRegister -> "Crie sua conta"
                        else -> "Entre para continuar"
                    },
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("E-mail") },
                    leadingIcon = { Icon(Icons.Filled.Email, null) }
                )
                if (!inviteMode) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Senha") },
                        leadingIcon = { Icon(Icons.Filled.Lock, null) },
                        visualTransformation = PasswordVisualTransformation()
                    )
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Button(
                    enabled = !loading && email.isNotBlank() && (inviteMode || password.isNotBlank()),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runAuth {
                            if (inviteMode && pendingEmailLink != null) {
                                auth.signInWithEmailLink(email, pendingEmailLink!!)
                                    .addOnCompleteListener { task ->
                                        loading = false
                                        if (task.isSuccessful) {
                                            val uid = task.result?.user?.uid
                                            val inviteId = runCatching {
                                                val outer = android.net.Uri.parse(pendingEmailLink!!)
                                                val continueUrl = outer.getQueryParameter("continueUrl")
                                                android.net.Uri.parse(continueUrl ?: "").getQueryParameter("inviteId")
                                            }.getOrNull()
                                            if (uid != null && !inviteId.isNullOrBlank()) {
                                                UserAccessRepository().claimInvitation(inviteId, uid, email) { claimError ->
                                                    if (claimError != null) error = claimError
                                                    prefs.edit().remove("pending_email_link").apply()
                                                    pendingEmailLink = null
                                                }
                                            } else {
                                                prefs.edit().remove("pending_email_link").apply()
                                                pendingEmailLink = null
                                            }
                                        } else {
                                            error = firebaseErrorMessage(task.exception ?: Exception())
                                        }
                                    }
                            } else if (showRegister) {
                                auth.createUserWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { task ->
                                        loading = false
                                        if (!task.isSuccessful) error = firebaseErrorMessage(task.exception ?: Exception())
                                    }
                            } else {
                                auth.signInWithEmailAndPassword(email, password)
                                    .addOnCompleteListener { task ->
                                        loading = false
                                        if (!task.isSuccessful) error = firebaseErrorMessage(task.exception ?: Exception())
                                    }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Login, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (inviteMode) "Aceitar convite" else if (showRegister) "Criar conta" else "Entrar")
                }

                if (!inviteMode) OutlinedButton(
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (activity == null) {
                            error = "Não foi possível iniciar o login do Google."
                            return@OutlinedButton
                        }
                        error = null
                        loading = true
                        scope.launch {
                            try {
                                val googleIdOption = GetGoogleIdOption.Builder()
                                    .setServerClientId(context.getString(R.string.default_web_client_id))
                                    .setFilterByAuthorizedAccounts(false)
                                    .build()

                                val request = GetCredentialRequest.Builder()
                                    .addCredentialOption(googleIdOption)
                                    .build()

                                val result = credentialManager.getCredential(
                                    context = activity,
                                    request = request
                                )
                                val credential = result.credential
                                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                val firebaseCredential = GoogleAuthProvider.getCredential(
                                    googleCredential.idToken,
                                    null
                                )
                                auth.signInWithCredential(firebaseCredential)
                                    .addOnCompleteListener { task ->
                                        loading = false
                                        if (!task.isSuccessful) {
                                            error = firebaseErrorMessage(task.exception ?: Exception())
                                        }
                                    }
                            } catch (e: GetCredentialException) {
                                loading = false
                                error = "Login com Google cancelado ou indisponível."
                            } catch (e: Exception) {
                                loading = false
                                error = e.localizedMessage ?: "Não foi possível entrar com Google."
                            }
                        }
                    }
                ) {
                    Text("Continuar com Google")
                }

                if (!inviteMode && !showRegister) {
                    TextButton(
                        enabled = !loading && email.isNotBlank(),
                        onClick = {
                            loading = true
                            error = null
                            auth.sendPasswordResetEmail(email)
                                .addOnCompleteListener { task ->
                                    loading = false
                                    error = if (task.isSuccessful) {
                                        "Se o e-mail existir, enviamos as instruções para redefinir a senha."
                                    } else {
                                        firebaseErrorMessage(task.exception ?: Exception())
                                    }
                                }
                        }
                    ) { Text("Esqueci minha senha") }
                }

                if (!inviteMode) TextButton(enabled = !loading, onClick = {
                    showRegister = !showRegister
                    error = null
                }) {
                    Text(if (showRegister) "Já tenho uma conta" else "Criar uma conta")
                }
            }
        }
    }
}
