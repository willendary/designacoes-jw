package br.com.willendary.designacoesjw.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.ui.JwSectionLabel

/**
 * Cola a lista que veio da IA e mostra o que o app entendeu, **antes** de
 * gravar.
 *
 * ## Por que a prévia é obrigatória
 *
 * O texto é de IA. O interpretador acerta quase tudo e erra o resto, e os
 * erros que sobram são exatamente os que não dão erro: um irmão que existe mas
 * com o nome escrito de outro jeito, uma parte que casa por prefixo com a parte
 * errada. Nada disso avisa — a designação só aparece errada na tela da reunião,
 * no quadro impresso e na mensagem que o irmão já recebeu.
 *
 * Por isso o botão de gravar só existe depois de "Analisar", e mostra o que vai
 * acontecer, linha a linha.
 *
 * ## Ordem dos botões do prompt
 *
 * Copiar antes de abrir. Se abrir primeiro, a janela do navegador fica na frente
 * do app, e trocar entre as duas para colar é exatamente o atrito que este
 * recurso existe para tirar.
 *
 * Sem ícones nos botões: o módulo `shared` compila contra o Compose do desktop,
 * e puxar o conjunto estendido de ícones por causa de três botões levaria
 * Material Icons inteiro para dentro do APK.
 */
@Composable
fun DialogoImportarDesignacoes(
    reunioes: List<Meeting>,
    irmaos: List<Brother>,
    anoDeReferencia: Int,
    /** Abre a conversa com a IA. */
    aoAbrirConversa: (String) -> Unit,
    /** A pessoa confirmou. [substituir] diz se troca a semana inteira. */
    aoConfirmar: (List<Resultado>, substituir: Boolean) -> Unit,
    aoFechar: () -> Unit
) {
    var texto by remember { mutableStateOf("") }
    var interpretados by remember { mutableStateOf<List<Resultado>?>(null) }
    var substituir by remember { mutableStateOf(false) }

    // O botão de copiar precisa dizer que copiou. Sem isto, clicar não muda nada
    // na tela e a pessoa não sabe se o passo deu certo — e o passo seguinte é
    // abrir o chat e colar.
    var copiado by remember { mutableStateOf(false) }
    val areaTransferencia = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Importar designações coladas") },
        text = {
            val previa = interpretados
            if (previa == null) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Peça a lista a uma IA, cole a resposta aqui e veja o que o app " +
                            "entendeu antes de gravar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    JwSectionLabel("1. Peça a lista a uma IA")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            areaTransferencia.setText(AnnotatedString(PromptImportacao.TEXTO))
                            copiado = true
                        }) {
                            Text(if (copiado) "Copiado — cole no chat" else "Copiar o pedido")
                        }
                        OutlinedButton(onClick = { aoAbrirConversa(PromptImportacao.Conversa.CHATGPT) }) {
                            Text("ChatGPT")
                        }
                        OutlinedButton(onClick = { aoAbrirConversa(PromptImportacao.Conversa.GEMINI) }) {
                            Text("Gemini")
                        }
                    }
                    // O texto pedido, à vista. "Copiar" sem dar para conferir o
                    // que foi copiado obriga a cola cega — e o passo seguinte é
                    // anexar a imagem, que é o que a pessoa veio fazer.
                    Text(
                        PromptImportacao.TEXTO,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                    )
                    JwSectionLabel("2. Cole a resposta da IA aqui")
                    OutlinedTextField(
                        value = texto,
                        onValueChange = { texto = it },
                        label = { Text("Lista colada") },
                        placeholder = {
                            Text(
                                "07/10 - João da Silva - Demonstração\n" +
                                    "07/10 - Maria Souza - Leitura do livro",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Previa(
                    resultados = previa,
                    substituir = substituir,
                    aoMudarSubstituir = { substituir = it }
                )
            }
        },
        confirmButton = {
            val previa = interpretados
            if (previa == null) {
                Button(
                    enabled = texto.isNotBlank(),
                    onClick = {
                        interpretados = ImportadorDesignacoes.interpretar(
                            texto = texto,
                            reunioes = reunioes,
                            irmaos = irmaos,
                            anoDeReferencia = anoDeReferencia
                        )
                    }
                ) { Text("Analisar") }
            } else {
                val prontas = previa.count { it.podeEntrar }
                Button(
                    enabled = prontas > 0,
                    onClick = { aoConfirmar(previa, substituir) }
                ) {
                    Text(if (substituir) "Substituir $prontas" else "Aplicar $prontas")
                }
            }
        },
        dismissButton = {
            if (interpretados == null) {
                TextButton(onClick = aoFechar) { Text("Cancelar") }
            } else {
                TextButton(onClick = { interpretados = null }) { Text("Voltar") }
            }
        }
    )
}

@Composable
private fun Previa(
    resultados: List<Resultado>,
    substituir: Boolean,
    aoMudarSubstituir: (Boolean) -> Unit
) {
    val prontas = resultados.count { it.podeEntrar }
    val comAviso = resultados.count { it.podeEntrar && it.temAviso }
    val fora = resultados.size - prontas

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            buildString {
                append("$prontas designações vão entrar.")
                if (comAviso > 0) append(" $comAviso com aviso.")
                if (fora > 0) append(" $fora linhas ficam de fora.")
            },
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Checkbox(checked = substituir, onCheckedChange = aoMudarSubstituir)
            Text(
                "Substituir a semana inteira",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (substituir) {
            Text(
                "As partes que não vierem na lista serão apagadas dessas reuniões.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (resultados.isEmpty()) {
            Text(
                "Não achei nenhuma linha para importar. Confira se o formato é " +
                    "\"data - nome - parte\".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(resultados) { LinhaDaPrevia(it) }
        }
    }
}

@Composable
private fun LinhaDaPrevia(r: Resultado) {
    val entrou = r.podeEntrar
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = listOfNotNull(
                r.data?.let { diaEMes(it) },
                r.irmao?.name ?: r.nome,
                r.item?.title ?: r.parte
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        r.problemas.forEach { problema ->
            Text(
                "· $problema",
                style = MaterialTheme.typography.labelSmall,
                color = if (entrou) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error
            )
        }
        if (!entrou && r.problemas.isEmpty()) {
            Text(
                "· Esta linha não pode ser aplicada.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** Só dia e mês: o ano só polui a leitura da prévia. */
private fun diaEMes(data: String): String =
    data.substringBefore('/') + "/" + data.substringAfter('/').substringBefore('/')