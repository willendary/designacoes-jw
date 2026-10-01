package br.com.willendary.designacoesjw.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.CleaningSchedule
import br.com.willendary.designacoesjw.data.FieldServiceGroup
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.data.PublicTalk
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.ui.MeetingProgramList
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Card da reunião desenhado em Compose, para gerar a imagem de compartilhamento.
 *
 * Substitui a versão do desktop que desenhava tudo com `java.awt.Graphics2D`
 * (`ImageExportHelper`): `java.awt` não existe no Android, então a imagem
 * simplesmente não podia ser gerada no celular. Aqui o card é Compose, e cada
 * plataforma decide só **como** salvar o bitmap.
 *
 * Sem ícone de ampulheta de propósito: `material-icons-extended` só existe no
 * módulo `app` e a dependência no `shared` não se justifica por um pictograma.
 */
@Composable
fun MeetingCardImage(
    meeting: Meeting,
    brothers: List<Brother>,
    privileges: List<Privilege>,
    publicTalk: PublicTalk? = null,
    cleaningSchedule: CleaningSchedule? = null,
    cleaningGroup: FieldServiceGroup? = null,
    modifier: Modifier = Modifier
) {
    val date = AssignmentGenerator.parseDate(meeting.date)
    val weekday = if (date != LocalDate.MIN) {
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .removeSuffix("-feira")
            .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
    } else ""

    Surface(
        modifier = modifier.width(900.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabeçalho
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    "DESIGNAÇÕES DA REUNIÃO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    listOf(meeting.date, weekday, meeting.type)
                        .filter { it.isNotBlank() }
                        .joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
            }

            if (publicTalk != null &&
                (publicTalk.themeTitle.isNotBlank() || publicTalk.speakerName.isNotBlank())
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(14.dp)
                ) {
                    Text(
                        "DISCURSO PÚBLICO",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    val tema = if (publicTalk.themeNumber != null) {
                        "Nº ${publicTalk.themeNumber} — \"${publicTalk.themeTitle}\""
                    } else {
                        "\"${publicTalk.themeTitle}\""
                    }
                    Text(tema, style = MaterialTheme.typography.bodyMedium)
                    val congregacao =
                        if (publicTalk.speakerCongregation.isNotBlank()) " (${publicTalk.speakerCongregation})" else ""
                    Text(
                        "Orador: ${publicTalk.speakerName}$congregacao",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (meeting.program.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MeetingProgramList(meeting, brothers, privileges)
            }

            if (meeting.assignments.isNotEmpty() && meeting.program.isEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    meeting.assignments.forEach { a ->
                        val p = privileges.firstOrNull { it.id == a.privilegeId }?.name ?: "Privilégio"
                        val b = brothers.firstOrNull { it.id == a.brotherId }?.name ?: "—"
                        Row(Modifier.fillMaxWidth()) {
                            Text(p, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(b, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            if (cleaningSchedule != null || cleaningGroup != null) {
                val nome = cleaningGroup?.name ?: "Grupo da Limpeza"
                val detalhe = if (cleaningSchedule?.details.isNullOrBlank()) "" else " (${cleaningSchedule?.details})"
                Text(
                    "Limpeza do Salão: $nome$detalhe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Text(
                "Gerado pelo Designações JW em ${LocalDate.now().format(AssignmentGenerator.DATE_FORMATTER)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End
            )
        }
    }
}
