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
import br.com.willendary.designacoesjw.notification.MeetingReminderHelper
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureEmailLink(intent)
        val prefs = getSharedPreferences("designacoes_jw", Context.MODE_PRIVATE)
        // O canal precisa existir antes de pedir a permissao: pedir sem um canal
        // visivel nas configuracoes nao ensina nada ao usuario.
        MeetingReminderHelper.createChannel(this)
        setContent {
            val themeIndexState = remember { mutableIntStateOf(prefs.getInt("theme_index", 0)) }
            val themeIndex = themeIndexState.intValue
            // O Android so tinha lightColorScheme: quem usa o sistema em modo
            // escuro tinha o app estourando a tela. Cada variante ganha o par.
            val isDark = isSystemInDarkTheme()
            val scheme = remember(themeIndex, isDark) {
                val palettes = listOf(
                    Color(0xFF1565C0) to Color(0xFF42A5F5),
                    Color(0xFF2E7D32) to Color(0xFF66BB6A),
                    Color(0xFF6A1B9A) to Color(0xFFAB47BC),
                    Color(0xFFEF6C00) to Color(0xFFFFA726),
                    Color(0xFF8E244D) to Color(0xFFAD4F73)
                )
                val (primary, secondary) = palettes[themeIndex.coerceIn(0, palettes.lastIndex)]
                if (isDark) {
                    darkColorScheme(
                        primary = Color(0xFF90CAF9),
                        secondary = Color(0xFF80CBC4),
                        surface = Color(0xFF12161C)
                    )
                } else {
                    lightColorScheme(primary = primary, secondary = secondary)
                }
            }
            MaterialTheme(colorScheme = scheme) {
                Surface {
                    FirebaseAuthGate(
                        themeIndex = themeIndex,
                        onThemeChange = { newIndex ->
                            themeIndexState.intValue = newIndex.coerceIn(0, 4)
                            prefs.edit().putInt("theme_index", themeIndexState.intValue).apply()
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
    onThemeChange: (Int) -> Unit
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
            onSignOut = {
                auth.signOut()
                user = null
            }
        )
    }
}
