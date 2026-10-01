package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

    // Quem está designado para o item N do programa. O elo é
    // Privilege.programItem; sem ele a parte fica sem responsável.
    val byProgramItem: Map<Int, Privilege> =
        privileges.mapNotNull { p -> p.programItem?.let { it to p } }.toMap()
    fun assignedNames(index: Int): List<String> {
        val privilege = byProgramItem[index] ?: return emptyList()
        return meeting.assignments
            .filter { it.privilegeId == privilege.id }
            .mapNotNull { a -> brothers.firstOrNull { it.id == a.brotherId }?.name }
    }

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
                val index = item.number.takeIf { it > 0 }
                    ?: (meeting.program.indexOf(item) + 1)
                ProgramPartRow(
                    title = item.title,
                    number = item.number.takeIf { it > 0 },
                    minutes = item.minutes,
                    kind = byProgramItem[index]?.kind ?: PartKind.INDIVIDUAL,
                    names = assignedNames(index)
                )
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
    names: List<String>
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(container, shape).padding(8.dp) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
    }
}
