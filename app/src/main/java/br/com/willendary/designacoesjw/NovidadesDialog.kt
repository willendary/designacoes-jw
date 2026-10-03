package br.com.willendary.designacoesjw

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.sync.Changelog
import br.com.willendary.designacoesjw.sync.ChangelogSource
import br.com.willendary.designacoesjw.sync.Plataforma
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwSectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS = "designacoes_jw"
private const val PREF_ULTIMA_VERSAO_MOSTRADA = "changelog_ultima_mostrada"

/**
 * "O que há de novo" no Android.
 *
 * ## O problema que isso resolve
 *
 * De 0.4.3 a 0.5.0 saíram quatro versões e **três quebraram**. A explicação do
 * que mudou estava na mensagem de commit, no GitHub, que ninguém lê antes de
 * atualizar. Quem ficou na 0.4.0 não tinha como saber que a 0.5.1 só mexia no
 * login do desktop — e por isso achou que o celular também tinha consertado.
 *
 * ## Três decisões
 *
 * **Mostra desde a versão instalada**, não desde a última release. Quem pulou
 * três versões precisa das três, em ordem — a soma não diz nada.
 *
 * **Só a plataforma de quem está lendo.** O APK e o `.exe` do mesmo número não
 * são o mesmo app.
 *
 * **Falha de rede não impede nada.** Sem rede, `ChangelogSource` devolve `null`
 * e a tela não abre. Um changelog quebrado jamais pode ser motivo para o app
 * não abrir — foi o que a 0.4.2 ensinou.
 */
@Composable
fun DialogoNovidades(changelog: Changelog, aoFechar: () -> Unit) {
    val contexto = LocalContext.current
    val novidades = changelog.novidades(
        versaoInstalada = BuildConfig.VERSION_NAME,
        plataforma = Plataforma.ANDROID
    )

    // Grava antes de desenhar: quem fecha sem rolar até o fim não volta a ver.
    LaunchedEffect(Unit) {
        prefs(contexto).edit()
            .putString(PREF_ULTIMA_VERSAO_MOSTRADA, changelog.versaoMaisRecente())
            .apply()
    }

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
 * Busca em background e mostra **uma vez por versão**.
 */
suspend fun carregarChangelogSeMostrar(contexto: Context): Changelog? {
    val jaMostrada = prefs(contexto).getString(PREF_ULTIMA_VERSAO_MOSTRADA, "").orEmpty()

    val changelog = withContext(Dispatchers.IO) { ChangelogSource.buscar() } ?: return null

    // Limite é a última mostrada, e não a instalada: o dono pode acrescentar
    // uma versão depois. O filtro é sempre por plataforma.
    val limite = jaMostrada.ifBlank { changelog.versaoMaisRecente() }
    return if (changelog.novidades(limite, Plataforma.ANDROID).isEmpty()) null else changelog
}

private fun prefs(contexto: Context) =
    contexto.getSharedPreferences(PREFS, Context.MODE_PRIVATE)