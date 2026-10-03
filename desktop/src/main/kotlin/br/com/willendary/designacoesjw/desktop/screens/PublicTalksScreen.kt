package br.com.willendary.designacoesjw.desktop.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.PublicTalk
import br.com.willendary.designacoesjw.desktop.StoreController
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import br.com.willendary.designacoesjw.util.WhatsAppHelper
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwCardRail
import br.com.willendary.designacoesjw.ui.JwTheme
import br.com.willendary.designacoesjw.ui.corDeContorno
import java.awt.Desktop
import java.net.URI
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun PublicTalksScreen(c: StoreController) {
    var showDialog by remember { mutableStateOf(false) }
    var editingTalk by remember { mutableStateOf<PublicTalk?>(null) }
    var deletingTalk by remember { mutableStateOf<PublicTalk?>(null) }
    var filterConfirmedOnly by remember { mutableStateOf(false) }

    val allTalks = c.data.publicTalks.sortedByDescending { AssignmentGenerator.parseDate(it.date) }
    val talks = if (filterConfirmedOnly) allTalks.filter { it.confirmed } else allTalks

    Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)) {
        // Barra Superior
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Discursos Públicos & Oradores", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Agendamento de oradores visitantes, hospitalidade e confirmações via WhatsApp",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = filterConfirmedOnly,
                    onClick = { filterConfirmedOnly = !filterConfirmedOnly },
                    label = { Text("Apenas confirmados") },
                    leadingIcon = if (filterConfirmedOnly) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null
                )

                Button(onClick = { editingTalk = null; showDialog = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Agendar Discurso")
                }
            }
        }

        if (talks.isEmpty()) {
            JwCard {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)
                ) {
                    Icon(Icons.Default.RecordVoiceOver, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Nenhum discurso público agendado", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("Cadastre os oradores visitantes dos próximos fins de semana para organizar a hospitalidade.", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { editingTalk = null; showDialog = true }) {
                        Text("Agendar primeiro discurso")
                    }
                }
            }
        } else {
            // Um discurso é uma unidade, com hospitalidade e ações: cartão.
            LazyColumn(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                items(talks) { talk ->
                    PublicTalkCardItem(
                        talk = talk,
                        brothers = c.data.brothers,
                        onEdit = { editingTalk = talk; showDialog = true },
                        onDelete = { deletingTalk = talk },
                        onToggleConfirm = {
                            c.addOrUpdatePublicTalk(talk.copy(confirmed = !talk.confirmed))
                        }
                    )
                }
            }
        }
    }

    if (showDialog) {
        PublicTalkDialog(
            existing = editingTalk,
            brothers = c.data.brothers,
            onDismiss = { showDialog = false; editingTalk = null },
            onSave = { saved ->
                c.addOrUpdatePublicTalk(saved)
                showDialog = false
                editingTalk = null
            }
        )
    }

    deletingTalk?.let { talk ->
        AlertDialog(
            onDismissRequest = { deletingTalk = null },
            title = { Text("Excluir discurso agendado?") },
            text = { Text("Deseja remover o discurso de ${talk.date} (${talk.speakerName})?") },
            confirmButton = {
                TextButton(onClick = {
                    c.deletePublicTalk(talk.id)
                    deletingTalk = null
                }) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { deletingTalk = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PublicTalkCardItem(
    talk: PublicTalk,
    brothers: List<Brother>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleConfirm: () -> Unit
) {
    val hospBrother = brothers.firstOrNull { it.id == talk.hospitalityBrotherId }
    val dateObj = AssignmentGenerator.parseDate(talk.date)
    val weekday = if (dateObj != LocalDate.MIN) {
        dateObj.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .removeSuffix("-feira")
            .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
    } else ""

    JwCard {
        // Situação no trilho, não no fundo: `destaque` é "o que se precisa ver
        // primeiro", e status não é destaque — pendente é a maioria da lista.
        JwCardRail(if (talk.confirmed) JwTheme.colors.sucesso else JwTheme.colors.alerta)

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs), modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)) {
                    Text(
                        text = "📅 ${talk.date} ($weekday)",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Badge(
                        containerColor = if (talk.confirmed) JwTheme.colors.sucesso else MaterialTheme.colorScheme.tertiary
                    ) {
                        Text(
                            text = if (talk.confirmed) "CONFIRMADO" else "PENDENTE",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                val themeStr = if (talk.themeNumber != null) "Nº ${talk.themeNumber} — \"${talk.themeTitle}\"" else "\"${talk.themeTitle}\""
                Text(
                    text = "📖 $themeStr",
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyLarge
                )

                val congStr = if (talk.speakerCongregation.isNotBlank()) " • Congregação ${talk.speakerCongregation}" else ""
                Text(
                    text = "🎤 Orador: ${talk.speakerName}$congStr",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (hospBrother != null || talk.hospitalityNotes.isNotBlank()) {
                    val hospName = hospBrother?.name ?: "A definir"
                    val notes = if (talk.hospitalityNotes.isNotBlank()) " (${talk.hospitalityNotes})" else ""
                    Text(
                        text = "🍽 Hospitalidade: $hospName$notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                // Botão WhatsApp Orador
                Button(
                    onClick = {
                        val text = WhatsAppHelper.buildPublicTalkSpeakerMessage(
                            talk = talk,
                            hospitalityBrotherName = hospBrother?.name ?: "",
                            hospitalityBrotherPhone = hospBrother?.phone ?: ""
                        )
                        val url = WhatsAppHelper.buildWebLink(talk.speakerPhone, text)
                        if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(url))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = JwTheme.colors.whatsapp)
                ) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("WhatsApp")
                }

                IconButton(onClick = onToggleConfirm) {
                    Icon(
                        if (talk.confirmed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Confirmar",
                        modifier = Modifier.size(22.dp),
                        tint = if (talk.confirmed) JwTheme.colors.sucesso else MaterialTheme.colorScheme.outline
                    )
                }

                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(22.dp))
                }

                // Destrutivo separado dos demais ícones da linha.
                VerticalDivider(color = corDeContorno())
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Excluir", modifier = Modifier.size(22.dp), tint = JwTheme.colors.perigo)
                }
            }
        }
    }
}

@Composable
private fun PublicTalkDialog(
    existing: PublicTalk?,
    brothers: List<Brother>,
    onDismiss: () -> Unit,
    onSave: (PublicTalk) -> Unit
) {
    var date by remember { mutableStateOf(existing?.date ?: "") }
    var themeNumStr by remember { mutableStateOf(existing?.themeNumber?.toString() ?: "") }
    var themeTitle by remember { mutableStateOf(existing?.themeTitle ?: "") }
    var speakerName by remember { mutableStateOf(existing?.speakerName ?: "") }
    var speakerCong by remember { mutableStateOf(existing?.speakerCongregation ?: "") }
    var speakerPhone by remember { mutableStateOf(existing?.speakerPhone ?: "") }
    var hospitalityId by remember { mutableStateOf(existing?.hospitalityBrotherId) }
    var hospitalityNotes by remember { mutableStateOf(existing?.hospitalityNotes ?: "") }
    var confirmed by remember { mutableStateOf(existing?.confirmed ?: false) }
    var hospDropdownOpen by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val hospBrother = brothers.firstOrNull { it.id == hospitalityId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Agendar Discurso Público" else "Editar Discurso") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(460.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Data (dd/MM/yyyy)") },
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = themeNumStr,
                        onValueChange = { themeNumStr = it },
                        label = { Text("Nº Tema") },
                        modifier = Modifier.weight(0.7f)
                    )
                }

                OutlinedTextField(
                    value = themeTitle,
                    onValueChange = { themeTitle = it },
                    label = { Text("Tema do Discurso") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = speakerName,
                        onValueChange = { speakerName = it },
                        label = { Text("Nome do Orador") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = speakerPhone,
                        onValueChange = { speakerPhone = it },
                        label = { Text("WhatsApp do Orador") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = speakerCong,
                    onValueChange = { speakerCong = it },
                    label = { Text("Congregação de Origem") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Seleção de Irmão para Hospitalidade
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { hospDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hospBrother != null) "Hospitalidade: ${hospBrother.name}" else "Hospitalidade: (Nenhum selecionado)",
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    DropdownMenu(
                        expanded = hospDropdownOpen,
                        onDismissRequest = { hospDropdownOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nenhum") },
                            onClick = { hospitalityId = null; hospDropdownOpen = false }
                        )
                        brothers.filter { it.active }.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b.name) },
                                onClick = { hospitalityId = b.id; hospDropdownOpen = false }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = hospitalityNotes,
                    onValueChange = { hospitalityNotes = it },
                    label = { Text("Notas de Hospitalidade (ex: Almoço na casa do irmão)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("Orador já confirmou presença", style = MaterialTheme.typography.bodyMedium)
                }

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (date.isBlank() || speakerName.isBlank()) {
                    errorMsg = "Informe ao menos a data e o nome do orador."
                    return@Button
                }
                val talk = PublicTalk(
                    id = existing?.id ?: AssignmentGenerator.nextId(),
                    date = date.trim(),
                    themeNumber = themeNumStr.trim().toIntOrNull(),
                    themeTitle = themeTitle.trim(),
                    speakerName = speakerName.trim(),
                    speakerCongregation = speakerCong.trim(),
                    speakerPhone = speakerPhone.trim(),
                    hospitalityBrotherId = hospitalityId,
                    hospitalityNotes = hospitalityNotes.trim(),
                    confirmed = confirmed
                )
                onSave(talk)
            }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
