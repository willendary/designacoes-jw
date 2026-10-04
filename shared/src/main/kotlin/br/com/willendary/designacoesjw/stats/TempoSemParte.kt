package br.com.willendary.designacoesjw.stats

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.util.Datas
import br.com.willendary.designacoesjw.util.TextOrder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Há quanto tempo cada irmão não faz **parte do programa**.
 *
 * ## Por que separado do relatório de equidade
 *
 * `EquityStatisticsHelper` conta **privilégio mecânico**: quantas vezes fez
 * "Leitor" no mês. E a pergunta é outra — "somente privilégios, não por hora",
 * como o usuário pediu.
 *
 * Privilério mecânico é cargo fixo: o "Som" é sempre o mesmo irmão, e quem
 * nunca faz "Leitor" pode estar designado de propósito. Parte de programa é o
 * que se distribui **para que todos participem**, e é aí que o silêncio é o
 * sintoma.
 *
 * ## A pergunta é "quando foi a última vez", não "quantas vezes"
 *
 * "0 partes este mês" não diz há quanto tempo. "4 meses sem fazer parte" diz, e
 * é o que chega ao responsável.
 */
object TempoSemParte {

    /**
     * O que se sabe sobre um irmão.
     *
     * @param ultimaParte data da reunião mais recente em que foi designado para
     *   uma parte. `null` = nunca fez parte.
     * @param entrouEm data de entrada na congregação, quando conhecida.
     */
    data class Situacao(
        val irmao: Brother,
        val ultimaParte: LocalDate?,
        val entrouEm: LocalDate?
    ) {
        /**
         * Meses sem fazer parte.
         *
         * **Medido a partir da entrada, nunca de 1 de janeiro.** Um irmão que
         * entrou em março e nunca fez parte está há 0 meses de "não fazer", não
         * há 8 — e dizer 8 transforma entrada recente em acusação.
         *
         * `null` quando nunca fez parte: ausência de dado não é 0 nem 8.
         */
        /**
         * Dias desde a última parte (ou desde a entrada, se nunca fez parte).
         *
         * O dia é a verdade; o mês é arredondamento.
         */
        val diasSemParte: Int?
            get() {
                val desde = ultimaParte ?: entrouEm ?: return null
                return ChronoUnit.DAYS.between(desde, hoje).toInt().coerceAtLeast(0)
            }

        /**
         * Meses, arredondados a partir dos **dias**.
         *
         * Contando de 21/set a 04/out, a fronteira de calendário diz "1 mês" — e
         * são 13 dias. Quem leu "1 mês sem fazer parte" já acha que o irmão
         * está ausente há um mês, e não está. Por isso o arredondamento fecha
         * 30 dias, e não a virada do mês.
         */
        val mesesSemParte: Int?
            get() = diasSemParte?.let { it / DIAS_POR_MES }

        /** O que a tela mostra: a resposta para "quanto tempo". */
        fun rotulo(): String {
            // Quem **nunca** fez parte não tem "há quanto tempo": tem "nunca".
            // Contar os dias desde a entrada e responder "semana passada" é
            // dizer que ele fez parte semana passada, que é falso.
            if (ultimaParte == null) return quandoNunca

            val dias = diasSemParte ?: return "nunca"
            val meses = dias / DIAS_POR_MES
            return when {
                dias < 7 -> "hoje"
                dias < 15 -> "semana passada"
                dias < DIAS_POR_MES -> "${dias / DIAS_SEMANA} semanas"
                dias < DIAS_POR_ANO -> "$meses ${if (meses == 1) "mês" else "meses"}"
                else -> {
                    val anos = dias / DIAS_POR_ANO
                    "$anos ${if (anos == 1) "ano" else "anos"}"
                }
            }
        }

        /**
         * O que dizer de quem nunca fez parte.
         *
         * `"nunca"` é a resposta comum, mas quem acabou de entrar não é um caso
         * de ausência: é irmão novo, e dizer "nunca" transforma entrada
         * recente em acusação.
         */
        val quandoNunca: String
            get() {
                val dias = diasSemParte ?: return "nunca"
                return if (ultimaParte == null && dias < DIAS_POR_MES) "entrou este mês" else "nunca"
            }
    }

    private const val DIAS_SEMANA = 7
    private const val DIAS_POR_MES = 30
    private const val DIAS_POR_ANO = 365

    /** Hoje, injetavel para o calculo ser deterministico no teste. */
    var hoje: LocalDate = LocalDate.now()
        private set

    /** Só para teste. Fora daqui a data muda no meio do cálculo. */
    internal fun fixarHoje(data: LocalDate) { hoje = data }

    internal fun restaurarHoje() { hoje = LocalDate.now() }

    /**
     * Calcula para todos os irmãos **ativos**.
     *
     * Inativo não entra: quem saiu da congregação não está "sem fazer parte",
     * está fora. E `Meeting.programAssignments` é a fonte — parte do programa é
     * `ProgramAssignment`, não `Assignment`, e confundir os dois volta ao
     * problema que o #51 resolveu.
     */
    fun calcular(
        meetings: List<Meeting>,
        brothers: List<Brother>,
        entrouEm: Map<Long, LocalDate> = emptyMap()
    ): List<Situacao> {
        val porIrmao = mutableMapOf<Long, LocalDate>()

        meetings.forEach { reuniao ->
            // Parse estrito, o mesmo do `Datas`: em modo leniente `31/02/2026` virava
            // `28/02/2026` e produzia um dia de semana plausivel para uma
            // data que nao existe.
            val data = Datas.dataEstrita(reuniao.date) ?: return@forEach
            reuniao.programAssignments.forEach { parte ->
                parte.brotherIds.forEach { id ->
                    val atual = porIrmao[id]
                    if (atual == null || data.isAfter(atual)) porIrmao[id] = data
                }
            }
        }

        return brothers
            .filter { it.active }
            .map { irmao ->
                Situacao(irmao, porIrmao[irmao.id], entrouEm[irmao.id])
            }
    }

    /**
     * Ordena por quem está há mais tempo sem fazer parte.
     *
     * É a pergunta que a pessoa realmente tem — "quem preciso olhar?" — e ela
     * se responde olhando o topo, sem cruzar referência nenhuma.
     *
     * Quem **nunca** fez parte vem primeiro: é o caso mais grave, e é o que o
     * zero esconde. Dentro de cada grupo, mais meses primeiro.
     */
    fun ordenarPorTempoSemParte(lista: List<Situacao>): List<Situacao> =
        lista.sortedWith(
            compareByDescending<Situacao> { it.ultimaParte == null }
                .thenByDescending { it.mesesSemParte ?: 0 }
                .thenComparator { a, b -> TextOrder.ptBr.compare(a.irmao.name, b.irmao.name) }
        )
}