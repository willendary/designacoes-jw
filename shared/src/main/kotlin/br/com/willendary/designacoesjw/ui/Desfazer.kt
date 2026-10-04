package br.com.willendary.designacoesjw.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.delay

/**
 * Mostra um snackbar que pode ser **desfeito**.
 *
 * ## O que é "desfazer"
 *
 * Uma janela de tempo. Passou dela, a exclusão é definitiva — e é assim que a
 * palavra funciona. Quem prometer desfazer para sempre precisa de cópia, e
 * cópia de dado de congregations em aparelho compartilhado é outro problema.
 *
 * ## Por que `Long`
 *
 * O botão precisa caber na leitura. Com `Short` (~4 s) a pessoa já teria
 * terminado de ler "irmão excluído" quando o botão some, e toca no vazio.
 *
 * ## Por que um helper e não `showSnackbar` solto
 *
 * `App.kt` e `Main.kt` já tinham dois `showSnackbar` escritos à mão, e nenhum
 * dos dois offers ação. Escrever o terceiro do mesmo jeito é como a exclusão
 * irreversível acontece: porque cada chamada é rápida e ninguém olha as outras.
 */
suspend fun SnackbarHostState.mostrarDesfazivel(
    mensagem: String,
    rotuloDesfazer: String = "Desfazer",
    aoDesfazer: suspend () -> Unit
): Boolean {
    val resultado = showSnackbar(
        message = mensagem,
        actionLabel = rotuloDesfazer,
        withDismissAction = true,
        duration = SnackbarDuration.Long
    )
    if (resultado != SnackbarResult.ActionPerformed) return false
    // O botão some no mesmo instante do toque, e a lista muda embaixo do dedo.
    // Sem esta pausa a pessoa vê a linha sumir e depois reaparecer, que parece
    // falha — e ela toca de novo, desfazendo o desfazer.
    delay(120)
    aoDesfazer()
    return true
}