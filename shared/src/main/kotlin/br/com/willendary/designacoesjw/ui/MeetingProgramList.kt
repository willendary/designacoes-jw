package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.PartKind
import br.com.willendary.designacoesjw.data.Privilege

/*
 * Presentation follows the editorial hierarchy of wol.jw.org: plain page
 * background, restrained typography, uppercase section headings, numbered
 * parts, duration on its own line, and blue text for assignment/link-like data.
 * Assignment controls remain available without turning each program part into
 * a decorative card.
 */
private val JwText = Color(0xFF333333)
private val JwMuted = Color(0xFF666666)
private val JwLink = Color(0xFF426B8A)
private val JwRule = Color(0xFFD6D6D6)

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
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }

    val byPosition = meeting.programAssignments.associate { it.item to it.brotherIds }
    fun assignedNames(position: Int): List<String> =
        byPosition[position].orEmpty().mapNotNull { id ->
            brothers.firstOrNull { it.id == id }?.name
        }

    val sections = meeting.program.groupBy { it.section.trim() }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val primaryText = if (dark) MaterialTheme.colorScheme.onSurface else JwText
    val mutedText = if (dark) MaterialTheme.colorScheme.onSurfaceVariant else JwMuted
    val linkText = if (dark) MaterialTheme.colorScheme.primary else JwLink
    val rule = if (dark) MaterialTheme.colorScheme.outlineVariant else JwRule

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        sections.forEach { (section, items) ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (section.isNotBlank()) {
                    Text(
                        section.uppercase(),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryText
                    )
                }
                items.forEachIndexed { index, item ->
                    val position = item.positionIn(meeting.program)
                    ProgramPartRow(
                        title = item.title,
                        number = item.number.takeIf { it > 0 },
                        minutes = item.minutes,
                        kind = item.kind,
                        names = assignedNames(position),
                        onToggleBrother = { brotherId -> onToggleAssignment(position, brotherId) },
                        canAssign = canAssign,
                        primaryText = primaryText,
                        mutedText = mutedText,
                        linkText = linkText,
                        rule = rule,
                        showRule = index > 0
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgramPartRow(
    title: String,
    number: Int?,
    minutes: Int,
    kind: PartKind,
    names: List<String>,
    onToggleBrother: (Long) -> Unit,
    canAssign: List<Brother>,
    primaryText: Color,
    mutedText: Color,
    linkText: Color,
    rule: Color,
    showRule: Boolean
) {
    var aberto by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (showRule) 10.dp else 0.dp)
    ) {
        if (showRule) {
            HorizontalDivider(color = rule, thickness = 0.7.dp)
            Spacer(Modifier.height(14.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f)) {
                val heading = if (number != null) "$number. $title" else title
                Text(
                    heading,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = primaryText
                )
                if (minutes > 0 || kind == PartKind.DEMONSTRATION) {
                    Spacer(Modifier.height(3.dp))
                    val detail = buildList {
                        if (minutes > 0) add("($minutes min)")
                        if (kind == PartKind.DEMONSTRATION) add("Demonstração")
                    }.joinToString(" ")
                    Text(
                        detail,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = mutedText
                    )
                }
            }
            if (canAssign.isNotEmpty()) {
                IconButton(
                    onClick = { aberto = !aberto },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (aberto || names.isNotEmpty()) Icons.Filled.Edit else Icons.Filled.Add,
                        contentDescription = if (names.isEmpty()) "Designar para esta parte" else "Alterar designação",
                        tint = linkText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (names.isNotEmpty()) {
            Text(
                names.joinToString(" · "),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                fontFamily = FontFamily.SansSerif,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = linkText
            )
        } else if (canAssign.isEmpty()) {
            Text(
                "Sem designação",
                modifier = Modifier.padding(top = 5.dp),
                fontFamily = FontFamily.SansSerif,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = mutedText
            )
        }

        if (aberto) {
            ListaDeDesignacao(names, canAssign, onToggleBrother) { aberto = false }
        }
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

    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp),
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
                    color = if (marcado) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Text(
                        if (marcado) "${irmao.name} ✓" else irmao.name,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        fontSize = 14.sp,
                        fontWeight = if (marcado) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
        TextButton(onClick = onFechar) { Text("Concluir") }
    }
}
