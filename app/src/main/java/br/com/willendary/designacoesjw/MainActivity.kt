package br.com.willendary.designacoesjw

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import br.com.willendary.designacoesjw.data.ThemeMode
import br.com.willendary.designacoesjw.notification.MeetingReminderHelper
import br.com.willendary.designacoesjw.ui.JwPalette
import br.com.willendary.designacoesjw.ui.JwThemeProvider
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Primeiro código do app: é aqui que a rede de queda tem que entrar,
        // senão a exceção do start acontece antes de existir quem a capture.
        CrashLog.instalar(this)
        captureEmailLink(intent)
        val prefs = getSharedPreferences("designacoes_jw", Context.MODE_PRIVATE)
        // O canal precisa existir antes de pedir a permissao: pedir sem um canal
        // visivel nas configuracoes nao ensina nada ao usuario.
        MeetingReminderHelper.createChannel(this)
        setContent {
            val themeIndexState = remember { mutableIntStateOf(prefs.getInt("theme_index", 0)) }
            val themeIndex = themeIndexState.intValue

            // Claro/escuro escolhido pela pessoa. Antes o Android so aceitava
            // o que o sistema decidisse, e nao havia onde escolher (#45). O
            // desktop tinha a escolha desde antes; aqui faltava.
            //
            // `theme_index` e `theme_mode` sao **por aparelho**, em
            // SharedPreferences: tema e preferencia de tela, e nao dado da
            // congregacao. Uma tela escura forcada por outra pessoa no mesmo
            // tablet e o contrario de Preference.
            val themeModeState = remember {
                mutableStateOf(
                    runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "SYSTEM") }
                        .getOrDefault(ThemeMode.SYSTEM)
                )
            }
            val themeMode = themeModeState.value
            val isDark = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            JwThemeProvider(paleta = JwPalette.porIndice(themeIndex), isDark = isDark) {
                Surface {
                    FirebaseAuthGate(
                        themeIndex = themeIndex,
                        onThemeChange = { novoIndice ->
                            themeIndexState.intValue = JwPalette.porIndice(novoIndice).ordinal
                            prefs.edit().putInt("theme_index", themeIndexState.intValue).apply()
                        },
                        themeMode = themeMode,
                        onThemeModeChange = { novo ->
                            themeModeState.value = novo
                            prefs.edit().putString("theme_mode", novo.name).apply()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        captureEmailLink(intent)
    }

    private fun captureEmailLink(intent: Intent?) {
        val link = intent?.data?.toString() ?: return
        val auth = FirebaseAuth.getInstance()
        if (auth.isSignInWithEmailLink(link)) {
            getSharedPreferences("designacoes_jw", Context.MODE_PRIVATE)
                .edit()
                .putString("pending_email_link", link)
                .apply()
        }
    }
}

@Composable
private fun FirebaseAuthGate(
    themeIndex: Int,
    onThemeChange: (Int) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    val prefs = remember { context.getSharedPreferences("designacoes_jw", Context.MODE_PRIVATE) }
    var inviteClaimInProgress by remember { mutableStateOf(prefs.getBoolean("invite_claim_in_progress", false)) }

    // POST_NOTIFICATIONS estava no manifest desde o inicio, mas nada no app a
    // pedia: no Android 13+ nenhuma notificacao aparecia e o usuario nunca era
    // avisado. Uma vez so — negar nao pode virar loop a cada abertura.
    val askNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { prefs.edit().putBoolean("notif_decidido", true).apply() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !prefs.getBoolean("notif_decidido", false) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (user == null || inviteClaimInProgress) {
        LoginScreen(onInviteClaimFinished = { inviteClaimInProgress = false })
    } else {
        App(
            themeIndex = themeIndex,
            onThemeChange = onThemeChange,
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onSignOut = {
                auth.signOut()
                user = null
            }
        )
    }
}
