package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.PartKind
import br.com.willendary.designacoesjw.data.Privilege

/**
 * Lista do programa oficial com quem foi designado para cada parte.
 *
 * Esta é a **única** implementação do programa no app. Antes cada tela tinha a
 * sua própria, e elas já divergiram — o mesmo item aparecia com formato
 * diferente no Android e no Desktop.
 *
 * A referência visual é o app Hourglass da congregação: o título precisa ser
 * legível de longe e o tempo de cada parte fica explícito, porque é o que a
 * pessoa vai ler do fundo do salão.
 */
@Composable
fun MeetingProgramList(
    meeting: Meeting,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    /** Alterna um irmão na parte de posição [position] (1-based). */
    onToggleAssignment: (position: Int, brotherId: Long) -> Unit = { _, _ -> },
    /** Irmãos que podem ser designados. Vazio esconde o botão de designar. */
    canAssign: List<Brother> = emptyList(),
    modifier: Modifier = Modifier
) {
    if (meeting.program.isEmpty()) {
        Text(
            "Programa ainda não importado.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    // Quem faz cada parte. O elo é a POSIÇÃO do item no programa
    // (Meeting.programAssignments), não um privilégio cadastrado: a parte muda
    // toda semana e não se cadastra, enquanto o privilégio mecânico é fixo.
    val byPosition: Map<Int, List<Long>> =
        meeting.programAssignments.associate { it.item to it.brotherIds }

    fun assignedNames(index: Int): List<String> =
        byPosition[index].orEmpty()
            .mapNotNull { id -> brothers.firstOrNull { it.id == id }?.name }

    val sections = meeting.program.groupBy { it.section }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        sections.forEach { (section, items) ->
            if (section.isNotBlank()) {
                Text(
                    section,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items.forEach { item ->
                val index = item.positionIn(meeting.program)
                ProgramPartRow(
                    title = item.title,
                    number = item.number.takeIf { it > 0 },
                    minutes = item.minutes,
                    kind = item.kind,
                    names = assignedNames(index),
                    onToggleBrother = { brotherId ->
                        onToggleAssignment(index, brotherId)
                    },
                    canAssign = canAssign
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgramPartRow(
    title: String,
    number: Int?,
    minutes: Int,
    kind: PartKind,
    names: List<String>,
    onToggleBrother: (Long) -> Unit = {},
    canAssign: List<Brother> = emptyList()
) {
    // Encenação é o tipo que mais importa ficar distinto: tem vários
    // participantes e não é "uma parte com dono".
    val highlighted = kind == PartKind.DEMONSTRATION
    val container = if (highlighted) {
        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    } else {
        Color.Transparent
    }
    val shape = RoundedCornerShape(8.dp)
    var aberto by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(container, shape).padding(8.dp) else Modifier)
    ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (number != null) {
            Text(
                "$number.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(Modifier.width(4.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (highlighted) {
                Text(
                    "Encenação",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
            if (minutes > 0) {
                // Sem icone de ampulheta de proposito: material-icons-extended
                // so existe no modulo app, e adicionar a dependencia ao shared
                // por causa de um pictograma nao compensa. O tempo em texto
                // e mais legivel a distancia do que o icone.
                Text(
                    "$minutes min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(Modifier.width(8.dp))

        // Parte com duas ou mais pessoas: os nomes lado a lado, separados por
        // um ponto, para ficar claro que a parte é coletiva.
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (names.isEmpty()) {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (names.size > 1) {
                        Text(
                            "(${names.size} pessoas)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(Modifier.width(4.dp))
                    }
                    Text(
                        names.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End
                    )
                }
            }
        }

        // Designar. É aqui que a parte deixa de ser lista e vira decisão: o
        // programa vem do jw.org, mas quem faz é escolhido aqui.
        if (canAssign.isNotEmpty()) {
            Box(Modifier.width(4.dp))
            AssignButton(
                names = names,
                canAssign = canAssign,
                onToggle = onToggleBrother,
                onAbrir = { aberto = !aberto },
                aberto = aberto
            )
        }
    }

        if (aberto) {
            ListaDeDesignacao(
                names = names,
                canAssign = canAssign,
                onToggle = onToggleBrother,
                onFechar = { aberto = false }
            )
        }
    }
}

/**
 * Botão que abre a lista de designação da parte.
 *
 * Só um botão — a lista em si é [ListaDeDesignacao], desenhada dentro da linha.
 *
 * **Não use `DropdownMenu` neste módulo.** `shared` é `kotlin("jvm")` com o
 * plugin Compose Multiplatform, então compila contra o Compose **desktop**:
 * `DropdownMenu` resolve para a implementação de desktop, que referencia
 * `SkikoMenu_skikoKt`. Essa classe não existe no APK do Android, e o erro só
 * aparece em runtime, na hora de desenhar a linha:
 *
 * ```
 * NoClassDefFoundError: Failed resolution of
 *     Landroidx/compose/material3/SkikoMenu_skikoKt;
 * ```
 *
 * O app compilava, os testes passavam, e o Android caía assim que a lista de
 * irmãos aparecia na tela. API de material3 **comum** (`Text`, `Icon`,
 * `Surface`, botões) funciona; o que não pode entrar aqui é o que tem
 * implementação por plataforma — popup, menu, diálogo.
 */
@Composable
private fun AssignButton(
    names: List<String>,
    canAssign: List<Brother>,
    onToggle: (Long) -> Unit,
    onAbrir: () -> Unit,
    aberto: Boolean
) {
    FilledTonalIconButton(
        onClick = onAbrir,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            if (aberto) Icons.Filled.Edit else if (names.isEmpty()) Icons.Filled.Add else Icons.Filled.Edit,
            contentDescription = if (names.isEmpty()) "Designar para esta parte" else "Alterar designação",
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * Lista de irmãos para designar, aberta dentro da própria linha.
 *
 * Fica inline em vez de popup por causa do [SkikoMenu] — ver [AssignButton].
 * E inline funciona melhor aqui: em tela de salão quem designa quer ver a
 * lista inteira e o nome de quem já está marcado, sem o menu fechar a cada
 * clique.
 *
 * Os **já designados** vêm primeiro, para quem já está na parte achar o
 * próprio nome em um toque. A parte do programa é aberta a qualquer irmão
 * ativo — a qualificação teocrática é do privilégio mecânico, não daqui.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListaDeDesignacao(
    names: List<String>,
    canAssign: List<Brother>,
    onToggle: (Long) -> Unit,
    onFechar: () -> Unit
) {
    val designados = canAssign.filter { it.name in names }
    val disponiveis = canAssign.filter { it.name !in names }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (designados + disponiveis).forEach { irmao ->
                val marcado = irmao.name in names
                Surface(
                    onClick = { onToggle(irmao.id) },
                    shape = RoundedCornerShape(6.dp),
                    color = if (marcado) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }
                ) {
                    Text(
                        if (marcado) "${irmao.name} ✓" else irmao.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (marcado) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (marcado) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
        TextButton(onClick = onFechar) { Text("Pronto", style = MaterialTheme.typography.labelSmall) }
    }
}
