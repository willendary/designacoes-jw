package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Quadro do mês, para a parede do salão.
 *
 * Uma linha por reunião, uma coluna por privilégio mecânico. É o formato de
 * quem olha de dois metros: data na esquerda, nomes embaixo dos títulos.
 *
 * **Por que tela e não PNG.** O `HtmlReportGenerator` já faz uma página por
 * reunião, e o `ReportGenerator` do Android já fazia esta grade em PDF — mas
 * em dois lugares, com duas quebras de linha diferentes, e o desktop não tinha
 * nada. Aqui é uma composable só, que os dois apps mostram em tela cheia e o
 * usuário imprime com Ctrl+P. Sem rasterização, sem código de desenho, sem
 * layout duplicado.
 *
 * Proporção de paisagem A4 (~1,41), que é o que sai da impressora do salão.
 */
@Composable
fun MonthBoard(
    month: YearMonth,
    meetings: List<Meeting>,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    /** Nome da congregação, se o usuário tiver informado. */
    congregation: String = "",
    modifier: Modifier = Modifier
) {
    // Só os mecânicos viram coluna. As partes do programa mudam toda semana e
    // não cabem numa coluna fixa — elas ficam na página da reunião (#51).
    val colunas = privileges
        .filter { it.active }
        .sortedBy { it.name.lowercase(Locale("pt", "BR")) }

    val reunioes = meetings.sortedBy { AssignmentGenerator.parseDate(it.date) }

    val rotuloMes = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
        .uppercase(Locale("pt", "BR")) + " ${month.year}"

    // Piso de altura para todas as linhas, calculado pela reunião com mais
    // nomes. É `heightIn(min)`, não `height`: um nome longo que quebra dentro
    // da coluna estreita não cabe nesse piso, e com altura fixa ele era
    // cortado. Na parede do salão, nome cortado é irmão errado.
    val alturaMinima = remember(reunioes, colunas) {
        val maiorNomes = reunioes.maxOfOrNull { reuniao ->
            colunas.maxOfOrNull { privilegio ->
                reuniao.assignments.count { it.privilegeId == privilegio.id }
            } ?: 0
        } ?: 1
        val linhasDeTexto = maiorNomes.coerceAtLeast(1)
        LINHA_ALTURA * linhasDeTexto + 6.dp
    }

    Box(modifier.background(Color.White)) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                // fillMaxWidth primeiro, aspectRatio depois: o espaco que sobra
                // em baixo e o que o aspectRatio usa para fechar a proporcao
                // da folha. Invertido, ele calcula sobre uma altura ja travada.
                .aspectRatio(PROPORCAO)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Cabeçalho
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "DESIGNAÇÕES — $rotuloMes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (congregation.isNotBlank()) {
                    Text(
                        congregation,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 2.dp)

            if (reunioes.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhuma reunião neste mês.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                return@Column
            }

            // Cabeçalho das colunas. Mesma altura da linha, para as células
            // alinharem com os nomes sem nenhum cálculo de fonte.
            Row(Modifier.fillMaxWidth().heightIn(min = alturaMinima)) {
                Cell(
                    texto = "Data",
                    peso = PESO_DATA,
                    destaque = true,
                    alinhamento = TextAlign.Start
                )
                colunas.forEach { Cell(texto = it.name, peso = 1f, destaque = true) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            reunioes.forEach { reuniao ->
                BoardRow(
                    meeting = reuniao,
                    colunas = colunas,
                    brothers = brothers,
                    altura = alturaMinima
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun BoardRow(
    meeting: Meeting,
    colunas: List<Privilege>,
    brothers: List<Brother>,
    altura: Dp
) {
    val data = AssignmentGenerator.parseDate(meeting.date)
    val diaSemana = if (data != LocalDate.MIN) {
        data.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .removeSuffix("-feira")
            .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
    } else ""

    Row(Modifier.fillMaxWidth().heightIn(min = altura)) {
        Cell(
            texto = listOf(meeting.date, diaSemana).filter { it.isNotBlank() }.joinToString("\n"),
            peso = PESO_DATA,
            alinhamento = TextAlign.Start
        )

        colunas.forEach { privilegio ->
            val nomes = meeting.assignments
                .filter { it.privilegeId == privilegio.id }
                .mapNotNull { a -> brothers.firstOrNull { it.id == a.brotherId }?.name }

            Cell(
                texto = textoDaCelula(privilegio, data, nomes),
                peso = 1f,
                destaque = destaqueDaCelula(privilegio, data, nomes),
                alinhamento = TextAlign.Center
            )
        }
    }
}

/**
 * O que a célula de um privilégio mostra naquele dia.
 *
 * **Célula vazia e "sem designação" não são a mesma coisa.** Vazio: o
 * privilégio não vale naquele dia (`allowedDays`), então não é falta de
 * ninguém. "Sem designação": valia, e ninguém foi designado — e isso é um
 * buraco que quem conduz a reunião precisa ver. Colapsar os dois casos num
 * "—" sumia justamente o buraco.
 */
fun textoDaCelula(privilegio: Privilege, data: LocalDate, nomes: List<String>): String {
    if (!privilegioSeAplicaNoDia(privilegio, data)) return ""
    if (nomes.isEmpty()) return "sem designação"
    return nomes.joinToString("\n")
}

/** A célula merece destaque quando valia naquele dia e ficou sem nome. */
fun destaqueDaCelula(privilegio: Privilege, data: LocalDate, nomes: List<String>): Boolean =
    privilegioSeAplicaNoDia(privilegio, data) && nomes.isEmpty()

/**
 * Data inválida (`LocalDate.MIN`) não tem dia da semana, então não há como
 * dizer se o privilégio valia: trata como válido e mostra o buraco, que é o
 * lado seguro de um quadro do salão.
 */
private fun privilegioSeAplicaNoDia(privilegio: Privilege, data: LocalDate): Boolean =
    data == LocalDate.MIN ||
        AssignmentGenerator.isPrivilegeApplicableToMeeting(privilegio, data)

/**
 * Uma célula da grade.
 *
 * O texto já vem com `\n` — quem monta decide onde quebrar, porque só quem
 * desenhou conhece a largura. A célula não mede nada e não corta: cortar
 * nome aqui é esconder irmão, e o quadro do salão é lido por quem não está
 * com o app na mão para conferir.
 */
@Composable
private fun RowScope.Cell(
    texto: String,
    peso: Float,
    destaque: Boolean = false,
    alinhamento: TextAlign = TextAlign.Center
) {
    val cor = when {
        texto.isBlank() -> MaterialTheme.colorScheme.outlineVariant
        destaque -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        Modifier
            .weight(peso)
            .fillMaxHeight()
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        if (texto.isNotBlank()) {
            Text(
                texto,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (destaque) FontWeight.Medium else FontWeight.Normal,
                color = cor,
                textAlign = alinhamento,
                lineHeight = MaterialTheme.typography.labelMedium.fontSize * 1.25
            )
        }
    }
}

/** Proporção de paisagem A4. */
private const val PROPORCAO = 297f / 210f

/**
 * Altura de **uma** linha de nome.
 *
 * A altura da linha da grade é um múltiplo deste, conforme quantos nomes a
 * pior reunião do mês tem na mesma coluna. Uma altura única para todas as
 * linhas é o que impede um "Leitor da Sentinela" de cortar o sobrenome de
 * baixo: a grade inteira cresce, e não uma célula escondida dentro dela.
 */
private val LINHA_ALTURA = 15.dp

/**
 * A coluna da data é um pouco mais larga que as de privilégio: cabe
 * "07/10/2026" numa linha e o dia da semana na outra.
 */
private const val PESO_DATA = 1.1f