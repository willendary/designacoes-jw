package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.generator.MeetingReview

@Composable
fun AgendaOverview(reviews: List<MeetingReview>, people: Int) {
    val pending = reviews.count { it.pending }
    val total = reviews.sumOf { it.total }
    val filled = reviews.sumOf { it.filled }
    JwCard {
        JwSectionLabel("Planejamento do mês")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AgendaMetric("Reuniões", reviews.size, Modifier.weight(1f))
            AgendaMetric("A revisar", pending, Modifier.weight(1f))
            AgendaMetric("Pessoas ativas", people, Modifier.weight(1f))
        }
        if (total > 0) {
            LinearProgressIndicator(progress = { filled.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(4.dp))
            Text("$filled de $total vagas preenchidas", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(if (reviews.isEmpty()) "Gere a escala para começar o planejamento."
             else if (pending > 0) "Revise as vagas, os programas e a disponibilidade antes de compartilhar."
             else "Tudo revisado para compartilhar a programação.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AgendaMetric(label: String, count: Int, modifier: Modifier) {
    Column(modifier) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun MeetingReviewPanel(review: MeetingReview) {
    if (!review.pending) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Antes de compartilhar", fontWeight = FontWeight.SemiBold, color = JwTheme.colors.alerta)
            review.issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}
