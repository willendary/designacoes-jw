package br.com.willendary.designacoesjw

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.*
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        captureEmailLink(intent)
        val prefs = getSharedPreferences("designacoes_jw", MODE_PRIVATE)
        setContent {
            val themeIndexState = remember { mutableIntStateOf(prefs.getInt("theme_index", 0)) }
            val themeIndex = themeIndexState.intValue
            val schemes = listOf(
                lightColorScheme(primary = Color(0xFF1565C0), secondary = Color(0xFF42A5F5)),
                lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFF66BB6A)),
                lightColorScheme(primary = Color(0xFF6A1B9A), secondary = Color(0xFFAB47BC)),
                lightColorScheme(primary = Color(0xFFEF6C00), secondary = Color(0xFFFFA726)),
                lightColorScheme(primary = Color(0xFF8E244D), secondary = Color(0xFFAD4F73))
            )
            MaterialTheme(colorScheme = schemes[themeIndex.coerceIn(0, schemes.lastIndex)]) {
                Surface {
                    FirebaseAuthGate(
                        themeIndex = themeIndex,
                        onThemeChange = { newIndex ->
                            themeIndexState.intValue = newIndex.coerceIn(0, schemes.lastIndex)
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
            getSharedPreferences("designacoes_jw", MODE_PRIVATE)
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
    val context = androidx.compose.ui.platform.LocalContext.current\n    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    if (user == null) {
        LoginScreen()
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
