package br.com.willendary.designacoesjw.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.sync.Changelog
import br.com.willendary.designacoesjw.sync.ChangelogSource
import br.com.willendary.designacoesjw.sync.Plataforma
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwSectionLabel
import java.io.File

private const val PREF_ARQUIVO = "changelog_ultima_mostrada"

/**
 * "O que há de novo" no desktop.
 *
 * ## Onde aparece
 *
 * **Em diálogo próprio na abertura, uma vez por versão.** A alternativa era
 * enfiar no diálogo de atualização que já existe, e foi descartada: quem não
 * tem versão nova não veria as novidades, que é justamente quem ficou atrás.
 *
 * E não é modal a cada abertura — quem abre o app todo dia não quer ler
 * novidades todo dia. A marcação fica em arquivo, ao lado do resto do estado.
 *
 * ## As mesmas três regras do Android
 *
 * Mostra desde a versão instalada; só a plataforma de quem lê; e falha de rede
 * não impede nada. Ver `shared/.../sync/Changelog.kt`.
 */
@Composable
fun DialogoNovidades(changelog: Changelog, aoFechar: () -> Unit) {
    val novidades = changelog.novidades(CURRENT_VERSION, Plataforma.DESKTOP)

    LaunchedEffect(Unit) { marcarUltimaMostrada(changelog.versaoMaisRecente()) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { JwCardTitle("O que há de novo") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                novidades.forEach { novidade ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        JwSectionLabel("Versão ${novidade.versao}")
                        novidade.itens.forEach { item ->
                            Text("• $item", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = aoFechar) { Text("Fechar") } }
    )
}

/**
 * O changelog a mostrar, ou `null` se não há o que mostrar.
 *
 * Bloqueante: quem chama já está em `Dispatchers.IO` — ver o `LaunchedEffect`
 * em `main()`.
 */
fun carregarChangelogSeMostrar(c: StoreController): Changelog? {
    val jaMostrada = arquivoDeMarca().takeIf { it.exists() }?.readText()?.trim().orEmpty()

    // Busca o arquivo inteiro mesmo já ter visto: o dono pode acrescentar uma
    // versão depois. O filtro do que já apareceu é `jaMostrada`.
    val changelog = ChangelogSource.buscar() ?: return null

    val limite = jaMostrada.ifBlank { changelog.versaoMaisRecente() }
    return if (changelog.novidades(limite, Plataforma.DESKTOP).isEmpty()) null else changelog
}

private fun marcarUltimaMostrada(versao: String) {
    runCatching { arquivoDeMarca().writeText(versao) }
}

private fun arquivoDeMarca(): File =
    File(System.getProperty("user.home"), ".designacoes-jw/ultima-novidade.txt")