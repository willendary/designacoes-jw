package br.com.willendary.designacoesjw

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Abre a Activity real no emulador e detecta crashes no fluxo inicial. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    fun abrirAplicativoNaoEncerraActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertFalse("A tela inicial foi encerrada inesperadamente", activity.isFinishing)
            }
        }
    }
}
