package br.com.willendary.designacoesjw.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Duas grafias do tipo de reunião conviviam em produção (#48):
 *
 * - import do jw.org gravava `"Reunião de Meio de Semana"`
 * - gerador gravava `"Reunião do meio de semana"`
 *
 * O app distinguia os dois tipos por `contains("meio de semana", ignoreCase)`,
 * espalhado por quatro arquivos. Funcionava por acaso: só a capitalização
 * diferia, e qualquer tela nova que usasse igualdade pegaria metade das
 * reuniões.
 */
class MeetingTypeTest {

    @Test
    fun `as duas grafias antigas produzem o mesmo tipo`() {
        // É esta a garantia que importa: o dado já gravado em qualquer aparelho
        // continua sendo lido como a mesma coisa.
        assertEquals(MeetingType.MIDWEEK, MeetingType.parse("Reunião de Meio de Semana"))
        assertEquals(MeetingType.MIDWEEK, MeetingType.parse("Reunião do meio de semana"))
        assertEquals(MeetingType.MIDWEEK, MeetingType.parse("Meio de Semana"))
        assertEquals(MeetingType.MIDWEEK, MeetingType.parse("MEIO DE SEMANA"))
    }

    @Test
    fun `fim de semana e fim de semana em qualquer caixa`() {
        assertEquals(MeetingType.WEEKEND, MeetingType.parse("Reunião de Fim de Semana"))
        assertEquals(MeetingType.WEEKEND, MeetingType.parse("Reunião do fim de semana"))
        assertEquals(MeetingType.WEEKEND, MeetingType.parse("fim de semana"))
    }

    @Test
    fun `tipo desconhecido e meio de semana, nunca fim`() {
        // Deliberado: um texto novo não pode virar "fim de semana" e sumir da
        // geração de meio de semana. Falhar para o lado que mostra mais é o
        // lado seguro.
        assertEquals(MeetingType.MIDWEEK, MeetingType.parse(""))
        assertEquals(MeetingType.MIDWEEK, MeetingType.parse("Reunião deCulto"))
        assertEquals(MeetingType.MIDWEEK, MeetingType.parse("Reunião"))
    }

    @Test
    fun `grafia canonica e sempre a mesma para o mesmo tipo`() {
        // É o que impede as duas grafias de nascerem de novo: tudo que grava usa
        // `label`.
        assertEquals("Reunião de Meio de Semana", MeetingType.MIDWEEK.label)
        assertEquals("Reunião de Fim de Semana", MeetingType.WEEKEND.label)
        assertEquals(MeetingType.MIDWEEK.label, MeetingType.parse("Reunião do meio de semana").label)
    }

    @Test
    fun `reuniao expoe o tipo e ja entrega a grafia normalizada`() {
        val antiga = Meeting(1, "07/10/2026", "Reunião do meio de semana")
        assertTrue(antiga.isMidweek)
        assertEquals(MeetingType.MIDWEEK, antiga.tipoReuniao)
        assertEquals("Reunião de Meio de Semana", antiga.typeCanonical)

        val fimDeSemana = Meeting(2, "10/10/2026", "Reunião de Fim de Semana")
        assertFalse(fimDeSemana.isMidweek)
        assertEquals(MeetingType.WEEKEND, fimDeSemana.tipoReuniao)
    }

    @Test
    fun `isMidweek concorda com typeCanonical depois de normalizar`() {
        // Se algum dia alguém gravar direto sem passar por `label`, a comparação
        // e a exibição não podem divergir.
        listOf(
            "Reunião de Meio de Semana", "Reunião do meio de semana",
            "Meio de Semana", "Reunião de Fim de Semana", "Fim de semana", ""
        ).forEach { bruto ->
            val reuniao = Meeting(1, "07/10/2026", bruto)
            assertEquals(
                reuniao.typeCanonical.contains("Meio de Semana"),
                reuniao.isMidweek,
                "bruto='$bruto'"
            )
        }
    }
}