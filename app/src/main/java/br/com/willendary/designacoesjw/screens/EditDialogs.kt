package br.com.willendary.designacoesjw.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.BrotherRole
import br.com.willendary.designacoesjw.data.BrotherStatus
import br.com.willendary.designacoesjw.data.Gender
import br.com.willendary.designacoesjw.data.PartKind
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.data.ReaderGrant
import br.com.willendary.designacoesjw.dayLabel
import br.com.willendary.designacoesjw.kindLabel
import br.com.willendary.designacoesjw.readerGrantLabel
import java.util.Locale

/**
 * Edição de irmão e de privilégio no Android.
 *
 * Antes só existia o diálogo de cadastro e o de nome/telefone: quem usava
 * somente celular não conseguia corrigir gênero, situação, papel mínimo nem
 * ativar/desativar um registro. Dado errado aqui quebra a geração de
 * designações — o gerador lê `active`, `role`, `baptized`, `readerGrant` e
 * `allowedStatus` — então o diálogo abre com o **valor atual**, grava só no
 * botão Salvar e fechar sem salvar não altera nada.
 *
 * **Sem `DropdownMenu`.** O `shared` é `kotlin("jvm")` e compila contra o
 * Compose desktop, onde `DropdownMenu` resolve para `SkikoMenu_skikoKt` — que
 * não existe no APK (foi o `NoClassDefFoundError` da 0.4.2). Aqui as escolhas
 * são `FilterChip` em `FlowRow`. Ver `AssignButton`, em `MeetingProgramList`.
 *
 * A permissão de cada campo é validada no [AppViewModel], que escreve o
 * recado em `lastActionError`. A tela não repete essa guarda de propósito:
 * regra em dois lugares é como uma delas deixa de valer.
 */

/** Rótulos curtos dos dias, para caber em tela de celular. */
private val DIAS = listOf(1 to "Seg", 2 to "Ter", 3 to "Qua", 4 to "Qui", 5 to "Sex", 6 to "Sáb", 7 to "Dom")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditBrotherDialog(
    vm: AppViewModel,
    brother: Brother,
    onErro: (String) -> Unit,
    onFechar: () -> Unit
) {
    var nome by remember(brother.id) { mutableStateOf(brother.name) }
    var telefone by remember(brother.id) { mutableStateOf(brother.phone) }
    var genero by remember(brother.id) { mutableStateOf(brother.gender) }
    var papel by remember(brother.id) { mutableStateOf(brother.role) }
    var ativo by remember(brother.id) { mutableStateOf(brother.active) }
    var batizado by remember(brother.id) { mutableStateOf(brother.baptized) }
    var aprendiz by remember(brother.id) { mutableStateOf(brother.trainee) }
    var leitor by remember(brother.id) { mutableStateOf(brother.isReader) }
    var leitorSentinela by remember(brother.id) { mutableStateOf(brother.isSentinelReader) }
    var privilegios by remember(brother.id) { mutableStateOf(brother.privileges) }

    // "Leitor de A Sentinela" é leitor: é o que o ViewModel grava
    // (setBrotherIsSentinelReader liga o isReader junto, setBrotherIsReader
    // desligar os dois). A tela precisa marcar o mesmo estado, senão salva o
    // contrário do que o usuário vê marcado.
    fun marcaLeitor(valor: Boolean) {
        leitor = valor
        if (!valor) leitorSentinela = false
    }

    fun marcaSentinela(valor: Boolean) {
        leitorSentinela = valor
        if (valor) leitor = true
    }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Editar irmão", fontWeight = FontWeight.Bold) },
        text = {
            ColunaRolavel {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome completo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = telefone,
                    onValueChange = { telefone = it },
                    label = { Text("WhatsApp (com DDD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Rotulo("Gênero")
                FluxoDeChips(
                    itens = Gender.entries,
                    marcado = { it == genero },
                    rotulo = { it.label },
                    onAlternar = { genero = it }
                )

                LinhaInterruptor("Ativo na congregação", ativo) { ativo = it }
                LinhaInterruptor("Batizado", batizado) { batizado = it }
                LinhaInterruptor("Aprendiz", aprendiz) { aprendiz = it }
                LinhaInterruptor("Leitor", leitor) { marcaLeitor(it) }
                LinhaInterruptor("Leitor de A Sentinela", leitorSentinela) { marcaSentinela(it) }

                Rotulo("Papel")
                FluxoDeChips(
                    itens = BrotherRole.entries,
                    marcado = { it == papel },
                    rotulo = { it.label },
                    onAlternar = { papel = it }
                )

                Rotulo("Privilégios que ele pode fazer")
                if (vm.privileges.value.isEmpty()) {
                    Text(
                        "Nenhum privilégio cadastrado ainda.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FluxoDeChips(
                    itens = vm.privileges.value.sortedBy { it.name.lowercase(Locale.getDefault()) },
                    marcado = { it.id in privilegios },
                    rotulo = { it.name },
                    onAlternar = { p ->
                        privilegios = if (p.id in privilegios) privilegios - p.id else privilegios + p.id
                    }
                )
                Text(
                    "Privilégio inativo não entra na escala, mesmo marcado aqui.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                salvaIrmao(
                    vm = vm,
                    antes = brother,
                    depois = brother.copy(
                        name = nome,
                        phone = telefone,
                        gender = genero,
                        role = papel,
                        active = ativo,
                        baptized = batizado,
                        trainee = aprendiz,
                        isReader = leitor,
                        isSentinelReader = leitorSentinela,
                        privileges = privilegios
                    ),
                    onErro = onErro,
                    onFechar = onFechar
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}

/**
 * Grava a edição do irmão, um método do [AppViewModel] por campo.
 *
 * Só grava o que mudou: cada setter reescreve a lista inteira, então chamar
 * todos sempre multiply as escritas na nuvem.
 */
private fun salvaIrmao(vm: AppViewModel, antes: Brother, depois: Brother, onErro: (String) -> Unit, onFechar: () -> Unit) {
    val erro = vm.updateBrother(antes.id, depois.name, depois.phone)
    if (erro != null) {
        onErro(erro)
        return
    }
    if (antes.gender != depois.gender) vm.setBrotherGender(antes.id, depois.gender)
    if (antes.role != depois.role) vm.setBrotherRole(antes.id, depois.role)
    if (antes.active != depois.active) vm.setBrotherActive(antes.id, depois.active)
    if (antes.baptized != depois.baptized) vm.setBrotherBaptized(antes.id, depois.baptized)
    if (antes.trainee != depois.trainee) vm.setBrotherTrainee(antes.id, depois.trainee)
    if (antes.isReader != depois.isReader) vm.setBrotherIsReader(antes.id, depois.isReader)
    if (antes.isSentinelReader != depois.isSentinelReader) vm.setBrotherIsSentinelReader(antes.id, depois.isSentinelReader)
    alternaPrivilegios(vm, antes.id, antes.privileges, depois.privileges)
    onFechar()
}

/**
 * Aplica a diferença de privilégios com [AppViewModel.togglePrivilege].
 *
 * Compara com o estado **atual**, lido do ViewModel na hora de salvar: o
 * diálogo pode ter ficado aberto enquanto a nuvem reponha o cadastro, e
 * alternar a partir de uma fotografia velha desligaria o que já estava ligado.
 */
private fun alternaPrivilegios(vm: AppViewModel, irmaoId: Long, antes: Set<Long>, depois: Set<Long>) {
    val atual = vm.brothers.value.firstOrNull { it.id == irmaoId }?.privileges ?: antes
    (atual - depois).forEach { vm.togglePrivilege(irmaoId, it) }
    (depois - atual).forEach { vm.togglePrivilege(irmaoId, it) }
}

@Composable
fun EditPrivilegeDialog(
    vm: AppViewModel,
    privilege: Privilege,
    onErro: (String) -> Unit,
    onFechar: () -> Unit
) {
    var nome by remember(privilege.id) { mutableStateOf(privilege.name) }
    var quantidade by remember(privilege.id) { mutableStateOf(privilege.quantity.toString()) }
    var ativo by remember(privilege.id) { mutableStateOf(privilege.active) }
    var dias by remember(privilege.id) { mutableStateOf(privilege.allowedDays) }
    var papelMinimo by remember(privilege.id) { mutableStateOf(privilege.minRole) }
    var tipo by remember(privilege.id) { mutableStateOf(privilege.kind) }
    var somenteIrmãos by remember(privilege.id) { mutableStateOf(privilege.maleOnly) }
    var habilitacao by remember(privilege.id) { mutableStateOf(privilege.readerGrant) }
    var estados by remember(privilege.id) { mutableStateOf(privilege.allowedStatus) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text("Editar privilégio", fontWeight = FontWeight.Bold) },
        text = {
            ColunaRolavel {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Nome do privilégio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = quantidade,
                    onValueChange = { quantidade = it.filter(Char::isDigit) },
                    label = { Text("Quantidade por reunião") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LinhaInterruptor("Ativo (entra na escala)", ativo) { ativo = it }

                Rotulo("Dias que valem")
                FluxoDeChips(
                    itens = DIAS,
                    marcado = { it.first in dias },
                    rotulo = { it.second },
                    onAlternar = { (dia) ->
                        dias = if (dia in dias) dias - dia else dias + dia
                    }
                )
                Text(
                    if (dias.isEmpty()) {
                        "Sem dia marcado vale qualquer dia — é o que o gerador lê."
                    } else {
                        "Vale em: " + DIAS.filter { it.first in dias }.joinToString(", ") { dayLabel(it.first) }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Rotulo("Papel mínimo")
                FluxoDeChips(
                    itens = BrotherRole.entries,
                    marcado = { it == papelMinimo },
                    rotulo = { it.label },
                    onAlternar = { papelMinimo = it }
                )

                Rotulo("Tipo de parte")
                FluxoDeChips(
                    itens = PartKind.entries,
                    marcado = { it == tipo },
                    rotulo = { kindLabel(it) },
                    onAlternar = { tipo = it }
                )

                // `maleOnly` decide se a parte é exclusiva de irmãos. Não é
                // decoração: é o que o geredor consulta em `isAuthorized`, e
                // ficar preso ao que veio do desktop deixava o campo
                // intocável pelo celular.
                LinhaInterruptor(
                    texto = "Somente irmãos",
                    marcado = somenteIrmãos,
                    onChange = { somenteIrmãos = it }
                )

                Rotulo("Concedido a quem é")
                FluxoDeChips(
                    itens = ReaderGrant.entries,
                    marcado = { it == habilitacao },
                    rotulo = { readerGrantLabel(it) },
                    onAlternar = { habilitacao = it }
                )

                Rotulo("Pode ser feito por")
                FluxoDeChips(
                    itens = BrotherStatus.entries,
                    marcado = { it in estados },
                    rotulo = { it.label },
                    onAlternar = { s ->
                        val proximo = estados.toMutableSet().also { set -> if (!set.add(s)) set.remove(s) }
                        if (proximo.isEmpty()) {
                            // Mesma trava da tela de privilégios: estado vazio
                            // não é "qualquer um", é "ninguém pode".
                            vm.reportError("Escolha pelo menos um. Remover todos deixaria a parte sem regra.")
                        } else {
                            estados = proximo
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                salvaPrivilegio(
                    vm = vm,
                    antes = privilege,
                    depois = privilege.copy(
                        name = nome,
                        quantity = quantidade.toIntOrNull() ?: privilege.quantity,
                        active = ativo,
                        allowedDays = dias,
                        minRole = papelMinimo,
                        kind = tipo,
                        readerGrant = habilitacao,
                        allowedStatus = estados
                    ),
                    onErro = onErro,
                    onFechar = onFechar
                )
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } }
    )
}

/**
 * Grava a edição do privilégio, um método do [AppViewModel] por campo, e
 * só o que mudou — cada setter reescreve a lista inteira.
 */
private fun salvaPrivilegio(
    vm: AppViewModel,
    antes: Privilege,
    depois: Privilege,
    onErro: (String) -> Unit,
    onFechar: () -> Unit
) {
    val erro = vm.updatePrivilege(antes.id, depois.name, depois.quantity)
    if (erro != null) {
        onErro(erro)
        return
    }
    if (antes.active != depois.active) vm.setPrivilegeActive(antes.id, depois.active)
    if (antes.allowedDays != depois.allowedDays) vm.setPrivilegeAllowedDays(antes.id, depois.allowedDays)
    if (antes.minRole != depois.minRole) vm.setPrivilegeMinRole(antes.id, depois.minRole)
    if (antes.kind != depois.kind) vm.setPrivilegeKind(antes.id, depois.kind)
    if (antes.maleOnly != depois.maleOnly) vm.setPrivilegeMaleOnly(antes.id, depois.maleOnly)
    if (antes.readerGrant != depois.readerGrant) vm.setPrivilegeReaderGrant(antes.id, depois.readerGrant)
    if (antes.allowedStatus != depois.allowedStatus) vm.setPrivilegeAllowedStatus(antes.id, depois.allowedStatus)
    onFechar()
}

/** Corpo do diálogo: coluna rolável, para a lista de privilégios caber na tela. */
@Composable
private fun ColunaRolavel(conteudo: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = conteudo
    )
}

@Composable
private fun Rotulo(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * Escolha de um valor entre vários, sem `DropdownMenu` — chip marcado é o
 * estado atual, e clicar troca ou alterna.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FluxoDeChips(
    itens: List<T>,
    marcado: (T) -> Boolean,
    rotulo: (T) -> String,
    onAlternar: (T) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itens.forEach { item ->
            FilterChip(
                selected = marcado(item),
                onClick = { onAlternar(item) },
                label = { Text(rotulo(item)) }
            )
        }
    }
}

@Composable
private fun LinhaInterruptor(texto: String, marcado: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(checked = marcado, onCheckedChange = onChange)
        Spacer(Modifier.width(8.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}