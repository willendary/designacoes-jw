package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.PartKind
import br.com.willendary.designacoesjw.data.Privilege

/**
 * Programa da reunião inspirado na organização editorial da Biblioteca
 * On-line da Torre de Vigia: cabeçalho da semana, grandes blocos de seção e
 * itens numerados com duração e designado em destaque.
 */
@Composable
fun MeetingProgramList(
    meeting: Meeting,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    onToggleAssignment: (position: Int, brotherId: Long) -> Unit = { _, _ -> },
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

    val byPosition = meeting.programAssignments.associate { it.item to it.brotherIds }

    fun assignedNames(position: Int): List<String> =
        byPosition[position].orEmpty()
            .mapNotNull { id -> brothers.firstOrNull { it.id == id }?.name }

    val sections = meeting.program.groupBy { it.section.trim() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)) {
        sections.forEach { (section, items) ->
            Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs)) {
                if (section.isNotBlank()) SectionHeader(section)
                items.forEach { item ->
                    val position = item.positionIn(meeting.program)
                    ProgramPartRow(
                        title = item.title,
                        number = item.number.takeIf { it > 0 },
                        minutes = item.minutes,
                        kind = item.kind,
                        names = assignedNames(position),
                        onToggleBrother = { brotherId -> onToggleAssignment(position, brotherId) },
                        canAssign = canAssign
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(Modifier.fillMaxWidth().padding(top = JwTheme.spacing.xs)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 5.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )
    }
}

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
    val highlighted = kind == PartKind.DEMONSTRATION
    var aberto by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (highlighted) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.42f)
                else superficieDeCartao(isSystemInDarkTheme())
            )
            .border(1.dp, corDeContorno(), shape)
            .padding(horizontal = JwTheme.spacing.md, vertical = JwTheme.spacing.sm)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            if (number != null) {
                Text(
                    "$number",
                    modifier = Modifier.width(30.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (minutes > 0) Text(
                        "$minutes min",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (highlighted) Text(
                        "ENCENAÇÃO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            if (canAssign.isNotEmpty()) {
                AssignButton(names, canAssign, onToggleBrother, { aberto = !aberto }, aberto)
            }
        }

        if (names.isNotEmpty()) {
            Text(
                names.joinToString(" · "),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (number != null) 30.dp else 0.dp, top = 7.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (canAssign.isEmpty()) {
            Text(
                "Sem designação",
                modifier = Modifier.padding(start = if (number != null) 30.dp else 0.dp, top = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }

        if (aberto) {
            ListaDeDesignacao(names, canAssign, onToggleBrother, { aberto = false })
        }
    }
}

@Composable
private fun AssignButton(
    names: List<String>,
    canAssign: List<Brother>,
    onToggle: (Long) -> Unit,
    onAbrir: () -> Unit,
    aberto: Boolean
) {
    FilledTonalIconButton(onClick = onAbrir, modifier = Modifier.size(34.dp)) {
        Icon(
            if (aberto) Icons.Filled.Edit else if (names.isEmpty()) Icons.Filled.Add else Icons.Filled.Edit,
            contentDescription = if (names.isEmpty()) "Designar para esta parte" else "Alterar designação",
            modifier = Modifier.size(17.dp)
        )
    }
}

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

    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    color = if (marcado) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        if (marcado) "${irmao.name} ✓" else irmao.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (marcado) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        TextButton(onClick = onFechar) { Text("Pronto", style = MaterialTheme.typography.labelSmall) }
    }
}
