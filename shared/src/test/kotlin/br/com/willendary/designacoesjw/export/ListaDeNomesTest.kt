package br.com.willendary.designacoesjw.export

import br.com.willendary.designacoesjw.generator.AssignmentGenerator.normalizeName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Importar lista de nomes colada (#64).
 *
 * A falha que motivou: `importBrothersFromCsv` trata `,` como separador de
 * coluna, então `Carlos, Daniel, Marcos` cadastra **só Carlos** e perde os
 * outros dois em silêncio. Quem cola a lista que a issue descreve perde dois
 * terços dos irmãos e não descobre.
 *
 * Este parser não pode ser só testado no aparelho: a falha é **silenciosa**,
 * que é a pior propriedade que um importador pode ter.
 */
class ListaDeNomesTest {

    private fun ler(texto: String) = ListaDeNomes.ler(texto, ::normalizeName)

    @Test
    fun `lista separada por virgula cadastra todos`() {
        // O caso da issue. Antes: 1 irmão. Agora: 3.
        val r = ler("Carlos, Daniel, Marcos")
        assertEquals(ListaDeNomes.Formato.NOMES, r.formato)
        assertEquals(listOf("Carlos", "Daniel", "Marcos"), r.novos)
        assertEquals(3, r.total)
    }

    @Test
    fun `um por linha tambem`() {
        val r = ler("Carlos\nDaniel\nMarcos")
        assertEquals(ListaDeNomes.Formato.NOMES, r.formato)
        assertEquals(listOf("Carlos", "Daniel", "Marcos"), r.novos)
    }

    @Test
    fun `ponto e virgula tambem, porque colar do Excel traz`() {
        val r = ler("Carlos; Daniel; Marcos")
        assertEquals(listOf("Carlos", "Daniel", "Marcos"), r.novos)
    }

    @Test
    fun `csv continua sendo csv`() {
        // Tem telefone, então é CSV e o nome é a primeira coluna.
        val r = ler("Carlos,51999887766,anc,Leitor,1")
        assertEquals(ListaDeNomes.Formato.CSV, r.formato)
        assertEquals(listOf("Carlos"), r.novos)
    }

    @Test
    fun `cabecalho de csv sai fora`() {
        val r = ler("nome,telefone\nCarlos,51999887766")
        assertEquals(ListaDeNomes.Formato.CSV, r.formato)
        assertEquals(listOf("Carlos"), r.novos)
    }

    @Test
    fun `numero pequeno nao vira telefone`() {
        // "2" é quantidade de irmãos por reunião e "1" é ativo. Se contasse
        // como telefone, nenhuma linha de CSV seria lida como CSV.
        val r = ler("Carlos,2,anc")
        assertEquals(ListaDeNomes.Formato.NOMES, r.formato)
        assertEquals(listOf("Carlos", "2", "anc"), r.novos)
    }

    @Test
    fun `aspas protegem virgula dentro do nome`() {
        // "Silva, João" é uma pessoa, não duas. Sem aspas viraria dois irmãos,
        // e o erro é silencioso.
        val r = ler("\"Silva, João\", Daniel")
        assertEquals(listOf("Silva, João", "Daniel"), r.novos)
    }

    @Test
    fun `repetido na propria lista e contado, nao silenciado`() {
        val r = ler("Carlos, Daniel, Carlos")
        assertEquals(2, r.novos.size)
        assertEquals(1, r.repetidosNaMesmaLista.size)
        assertEquals(3, r.total)
    }

    @Test
    fun `acento nao cria irmao novo`() {
        // Mesma regra que o importador CSV, senão "José" e "Jose" entram como
        // duas pessoas — e foi exatamente esse o bug do BOM do Excel.
        val r = ler("José, Jose, JOSÉ")
        assertEquals(1, r.novos.size)
        assertEquals(2, r.repetidosNaMesmaLista.size)
    }

    @Test
    fun `linha vazia e espacos sao ignorados`() {
        // Colar do celular sempre traz quebra no fim.
        val r = ler("Carlos\n\n   \nDaniel\n")
        assertEquals(listOf("Carlos", "Daniel"), r.novos)
    }

    @Test
    fun `texto vazio nao quebra`() {
        val r = ler("")
        assertEquals(0, r.total)
        assertTrue(r.novos.isEmpty())
    }

    @Test
    fun `so virgula final nao cria irmao vazio`() {
        val r = ler("Carlos,")
        assertEquals(listOf("Carlos"), r.novos)
    }

    @Test
    fun `varias linhas em csv contam cada linha como um irmao`() {
        val r = ler("Carlos,51999887766,anc\nDaniel,51999887777,pub")
        assertEquals(ListaDeNomes.Formato.CSV, r.formato)
        assertEquals(listOf("Carlos", "Daniel"), r.novos)
    }
}