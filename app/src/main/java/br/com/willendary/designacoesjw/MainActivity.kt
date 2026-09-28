package br.com.willendary.designacoesjw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                    App(
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
}
