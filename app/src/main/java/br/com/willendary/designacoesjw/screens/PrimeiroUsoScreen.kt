package br.com.willendary.designacoesjw.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.willendary.designacoesjw.AppViewModel
import br.com.willendary.designacoesjw.Tela
import br.com.willendary.designacoesjw.ui.JwCard
import br.com.willendary.designacoesjw.ui.JwCardTitle
import br.com.willendary.designacoesjw.ui.JwTheme

/**
 * A tela de primeiro uso (#62).
 *
 * ## Por que a ordem é privilégio, irmão, reunião
 *
 * Não é sugestão, é dependência: **sem privilégio não há o que designar, sem
 * irmão não há candidato, sem reunião não há quando.** A ordem também é a que
 * o gerador já usa — ele resolve privilégio, depois irmão, depois
 * disponibilidade — então a tela ensina a sequência que o código executa.
 *
 * Antes disso o caminho era deduzir pelo menu. Quem nunca usou o app cadastrava
 * um irmão, via as reuniões vazias e achava que o app estava quebrado.
 *
 * ## Por que some sozinha
 *
 * Para quem já tem 40 irmãos vindo do desktop, esta tela é ruído: eles não
 * estão começando. Por isso o gatilho é `nunca viu **e** não há irmão`, e não
 * só `nunca viu`. E a pessoa pode fechar a qualquer momento e nunca mais ver.
 */
@Composable
fun PrimeiroUsoScreen(
    vm: AppViewModel,
    aoIrPara: (Tela) -> Unit,
    aoFechar: () -> Unit
) {
    // O mínimo de cada etapa. Um item **ativo** e não um item qualquer: uma
    // lista de privilégios com tudo desativado não gera designação nenhuma, e
    // marcar a etapa como pronta ali seria mentira.
    val privilegiosOk = vm.privileges.value.any { it.active }
    val irmaosOk = vm.brothers.value.any { it.active }
    // Estado local, e nao do ViewModel: e decisao de apresentacao, e a lista de
    // reuniões e a mesma que o `Quadro do Mes` le.
    var reunioesOk by remember { mutableStateOf(false) }

    // Reuniões chegam da nuvem depois do primeiro quadro. Contar só as locais
    // deixaria a etapa travada em "pendente" para sempre em congregação que só
    // usa o desktop.
    LaunchedEffect(vm.meetings.value) {
        reunioesOk = vm.meetings.value.isNotEmpty()
    }

    val etapas = listOf(
        Etapa(
            numero = 1,
            titulo = "Privilégios",
            oQueFaz = "Quem pode fazer o quê, e quantos por reunião. Sem isso não há " +
                "nada para designar.",
            pronta = privilegiosOk,
            tela = Tela.PRIVILEGIOS
        ),
        Etapa(
            numero = 2,
            titulo = "Irmãos",
            oQueFaz = "Quem pode receber as designações. Dá para colar a lista " +
                "inteira de uma vez.",
            pronta = irmaosOk,
            tela = Tela.IRMAOS
        ),
        Etapa(
            numero = 3,
            titulo = "Reuniões",
            oQueFaz = "Quando. A reunião da semana importa com o programa do " +
                "jw.org, que o app lê sozinho.",
            pronta = reunioesOk,
            tela = Tela.REUNIAO_DA_SEMANA
        )
    )

    val completo = etapas.all { it.pronta }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(JwTheme.spacing.md)
    ) {
        Column {
            Text(
                "Vamos começar",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Três passos, nesta ordem. Dá para pular qualquer um e voltar depois.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        etapas.forEach { etapa ->
            JwCard(destaque = completo && etapa.pronta) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.md),
                    verticalAlignment = Alignment.Top
                ) {
                    // Número vira check quando a etapa tem o mínimo. Sem
                    // ícone de "obrigatório": nada aqui é obrigatório, e dizer
                    // o contrário faria a pessoa achar que não pode seguir.
                    Surface(
                        shape = CircleShape,
                        color = if (etapa.pronta) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (etapa.pronta) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Pronto",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Text(
                                    "${etapa.numero}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        JwCardTitle(etapa.titulo)
                        Text(
                            etapa.oQueFaz,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            Modifier.padding(top = JwTheme.spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm)
                        ) {
                            Button(onClick = { aoIrPara(etapa.tela) }) {
                                Text(if (etapa.pronta) "Abrir" else "Começar")
                            }
                            if (etapa.pronta) {
                                Text(
                                    "já cadastrado",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        OutlinedButton(
            onClick = aoFechar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ir para o app")
        }

        Text(
            "Esta tela não aparece de novo. Se precisar do caminho, está na " +
                "ajuda das Configurações.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

/** Uma etapa do primeiro uso. */
private data class Etapa(
    val numero: Int,
    val titulo: String,
    /** Uma linha: o que a pessoa ganha ao fazer. */
    val oQueFaz: String,
    val pronta: Boolean,
    val tela: Tela
)