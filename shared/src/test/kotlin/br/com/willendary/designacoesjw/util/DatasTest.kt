package br.com.willendary.designacoesjw.util

import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A escrita de data e mês em português (#46).
 *
 * Estava em 25 lugares, cada um resolvendo "e maiúscula?" do seu jeito. O mesmo
 * mês aparecia como `JANEIRO 2026`, `Janeiro de 2026` e `janeiro` em três
 * telas diferentes, e nenhuma delas parecia errada sozinha.
 */
class DatasTest {

    private val janeiro = YearMonth.of(2026, 1)
    // 07/10/2026 é quarta-feira.
    private val quarta = LocalDate.of(2026, 10, 7)
    private val domingo = LocalDate.of(2026, 10, 4)

    @Test
    fun `mes tem tres apresentacoes e elas sao diferentes de proposito`() {
        // Não é um `if` com parâmetro: são textos diferentes, e a tela escolhe.
        assertEquals("janeiro", Datas.nomeDoMes(Month.JANUARY))
        assertEquals("Janeiro de 2026", Datas.mesEAno(janeiro))
        assertEquals("JANEIRO 2026", Datas.mesEAnoEmCaixaAlta(janeiro))
    }

    @Test
    fun `o nome do mes nunca vem em ingles`() {
        // `Month.name` é o nome do enum: foi bug duas vezes, e o botão dizia
        // "Gerar Escala de January".
        Month.entries.forEach { mes ->
            val nome = Datas.nomeDoMes(mes)
            assertTrue(
                nome.first().isLowerCase() || nome.first().isDigit(),
                "mes $mes devolveu '$nome', que parece nome de enum"
            )
        }
    }

    @Test
    fun `dia da semana perde o sufixo de feira`() {
        // É o que o encontro é chamado: "Quarta", não "Quarta-feira".
        assertEquals("Quarta", Datas.diaDaSemana(quarta))
        assertEquals("Domingo", Datas.diaDaSemana(domingo))
        // Segunda e sábado não têm "-feira" e não podem perder nada.
        assertEquals("Segunda", Datas.diaDaSemana(LocalDate.of(2026, 10, 5)))
        assertEquals("Sábado", Datas.diaDaSemana(LocalDate.of(2026, 10, 10)))
    }

    @Test
    fun `nenhum dia da semana devolve a palavra feira`() {
        (1..7).forEach { dia ->
            val texto = Datas.diaDaSemana(quarta.plusDays((dia - 1).toLong()))
            assertTrue("-feira" !in texto, "dia $dia devolveu '$texto'")
        }
    }

    @Test
    fun `curto e sempre tres letras em caixa alta`() {
        // Sem o `.uppercase` aqui, cada tela decidia — e metade decidia diferente.
        assertEquals("QUA", Datas.diaDaSemanaCurto(quarta))
        assertEquals("DOM", Datas.diaDaSemanaCurto(domingo))
        (1..7).forEach { dia ->
            val curto = Datas.diaDaSemanaCurto(quarta.plusDays((dia - 1).toLong()))
            assertEquals(3, curto.length, "dia $ dia devolveu '$curto'")
            assertEquals(curto.uppercase(), curto)
        }
    }

    @Test
    fun `inicial e o primeiro caractere do curto`() {
        assertEquals("Q", Datas.inicialDoDia(quarta))
        assertEquals("D", Datas.inicialDoDia(domingo))
    }

    @Test
    fun `data invalida devolve nulo em vez de texto errado`() {
        // O app tem data que vem de importação, de digitação e de nuvem antiga.
        // `null` é honesto; a tela decide o que mostrar.
        assertNull(Datas.diaDaSemanaDe("31/02/2026"))
        assertNull(Datas.diaDaSemanaDe(""))
        assertNull(Datas.diaDaSemanaDe("reuniao"))
        assertEquals("Quarta", Datas.diaDaSemanaDe("07/10/2026"))
    }

    @Test
    fun `dia curto com numero tem o dia com dois digitos`() {
        assertEquals("QUA, 07", Datas.diaCurtoComNumero(quarta))
        // Dia 4 não pode virar "QUA, 4" e quebrar a coluna da tabela.
        assertEquals("DOM, 04", Datas.diaCurtoComNumero(domingo))
    }

    @Test
    fun `documento impresso leva o dia por extenso`() {
        // "Quarta-feira" e "Quarta" sao textos diferentes, de proposito: o
        // relatorio A4 e documento, e documento escrito leva o nome inteiro.
        assertEquals("Quarta-feira", Datas.diaDaSemanaCompleto(quarta))
        assertEquals("Domingo", Datas.diaDaSemanaCompleto(domingo).let {
            // "Domingo" nao tem sufixo: o dia completo e "domingo".
            it
        })
        // E o curto continua curto: a coluna da tabela cabe em tres letras.
        assertEquals("Quarta", Datas.diaDaSemana(quarta))
    }
}
