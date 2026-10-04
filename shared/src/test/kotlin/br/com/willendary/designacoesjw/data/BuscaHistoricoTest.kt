package br.com.willendary.designacoesjw.data

import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Busca no histórico (#63).
 *
 * O ponto que trava aqui não é "achar texto": é **achar quem fez parte**. A
 * tela de histórico do Android mostrava só `Meeting.assignments` — o
 * privilégio mecânico — e a parte do programa, que é a maioria das designações,
 * era invisível tanto na tela quanto na busca.
 */
class BuscaHistoricoTest {

    private val privilege = Privilege(1, "Leitor", quantity = 1)

    private fun reuniao(
        data: String,
        tema: String = "",
        Assignments: List<Assignment> = emptyList(),
        partes: List<ProgramAssignment> = emptyList()
    ) = Meeting(
        id = data.hashCode().toLong(),
        date = data,
        type = "Reunião de Meio de Semana",
        theme = tema,
        assignments = Assignments,
        programAssignments = partes
    )

    private val carlos = Brother(1, "João Carlos", active = true)
    private val daniel = Brother(2, "Daniel", active = true)
    private val irmaos = listOf(carlos, daniel)

    private fun buscar(termo: String, meetings: List<Meeting>) =
        BuscaHistorico.filtrar(termo, meetings, irmaos, listOf(privilege))

    @Test
    fun `termo vazio devolve tudo`() {
        // Estado inicial da tela, não erro.
        val todas = listOf(reuniao("05/10/2026"), reuniao("06/10/2026"))
        assertEquals(2, buscar("", todas).size)
        assertEquals(2, buscar("   ", todas).size)
    }

    @Test
    fun `acha por data`() {
        val r = buscar("05/10", listOf(reuniao("05/10/2026"), reuniao("06/10/2026")))
        assertEquals(listOf("05/10/2026"), r.map { it.date })
    }

    @Test
    fun `acha por nome ignorando acento e maiuscula`() {
        // No teclado do celular ninguém acenta com frequência. Digitar "joao"
        // tem de achar "João Carlos".
        val r = buscar("joao", listOf(reuniao("05/10/2026", partes = listOf(ProgramAssignment(1, listOf(1L))))))
        assertEquals(1, r.size, "\"joao\" precisa achar \"João Carlos\"")
    }

    @Test
    fun `acha quem fez PARTE do programa`() {
        // O defeito do Android: histórico mostrava só privilégio mecânico, e a
        // busca nascia junto com essa falha.
        val r = buscar("Daniel", listOf(reuniao("05/10/2026", partes = listOf(ProgramAssignment(2, listOf(2L))))))
        assertEquals(1, r.size, "quem fez parte tem de aparecer na busca")
    }

    @Test
    fun `acha por privilegio`() {
        val r = buscar(
            "leitor",
            listOf(reuniao("05/10/2026", Assignments = listOf(Assignment(1, 1L))))
        )
        assertEquals(1, r.size)
    }

    @Test
    fun `acha por tema`() {
        val r = buscar("maior", listOf(reuniao("05/10/2026", tema = "O Maior Valor")))
        assertEquals(1, r.size)
    }

    @Test
    fun `irmao removido nao faz a busca casar com tudo`() {
        // `brotherId` órfão: sem este teste, uma reunião com irmão removido
        // casa com qualquer termo e o usuário recebe o histórico inteiro.
        val comOrfao = Meeting(
            id = 9, date = "05/10/2026", type = "Reunião",
            assignments = listOf(Assignment(999L, 1L))
        )
        assertTrue(buscar("qualquer-coisa", listOf(comOrfao)).isEmpty())
    }

    @Test
    fun `parte com varias pessoas acha cada uma`() {
        // Encenação: mais de um irmão na mesma parte.
        val enc = listOf(ProgramAssignment(3, listOf(1L, 2L)))
        assertEquals(1, buscar("Daniel", listOf(reuniao("05/10/2026", partes = enc))).size)
        assertEquals(1, buscar("Joao", listOf(reuniao("05/10/2026", partes = enc))).size)
    }

    @Test
    fun `varios resultados vem do mais novo`() {
        // A busca não reordena: quem ordena é a lista, e ela já sabe.
        val todas = listOf(reuniao("05/09/2026", partes = listOf(ProgramAssignment(1, listOf(2L)))),
            reuniao("05/10/2026", partes = listOf(ProgramAssignment(1, listOf(2L)))))
        assertEquals(listOf("05/09/2026", "05/10/2026"), buscar("Daniel", todas).map { it.date })
    }

    @Test
    fun `reuniao sem programa nao quebra`() {
        assertTrue(buscar("x", listOf(Meeting(1, "05/10/2026", "Reunião"))).isEmpty())
    }

    @Test
    fun `acento doPrivilegio tambem e ignorado`() {
        val audio = listOf(Privilege(2, "Áudio", quantity = 1))
        val m = listOf(reuniao("05/10/2026", Assignments = listOf(Assignment(2, 2L))))
        assertEquals(1, BuscaHistorico.filtrar("audio", m, irmaos, audio).size)
    }

    @Test
    fun `o normalizador e o mesmo de identificar irmao`() {
        // Se a busca normalizar de um jeito e a comparação de nome de outro, a
        // busca acha um irmão que o app trata como outro. Mesmo função, sempre.
        assertEquals(AssignmentGenerator.normalizeName("João"), AssignmentGenerator.normalizeName(" joao "))
    }
}