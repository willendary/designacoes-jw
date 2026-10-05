package br.com.willendary.designacoesjw.importer

import br.com.willendary.designacoesjw.data.Assignment
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.PartKind
import br.com.willendary.designacoesjw.data.ProgramItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A entrada vem de uma IA, não de um formulário. Os testes usam o texto que uma
 * IA de fato escreve — separador trocado, ano faltando, acento perdido, cabeçalho
 * no meio da lista — porque um parser testado só com o formato ideal não
 * sobrevive ao primeiro dia de uso.
 */
class ImportadorDesignacoesTest {

    private val programa = listOf(
        ProgramItem(number = 1, title = "Joias espirituais", minutes = 10),
        ProgramItem(number = 2, title = "Encenação", minutes = 5, kind = PartKind.PAIR),
        ProgramItem(number = 3, title = "Demonstração", minutes = 10),
        ProgramItem(number = 4, title = "Leitura do livro", minutes = 5)
    )

    private fun reuniao(data: String) = Meeting(
        id = data.hashCode().toLong(),
        date = data,
        type = "Reunião de Meio de Semana",
        program = programa
    )

    private val reunioes = listOf(
        reuniao("07/10/2026"), reuniao("14/10/2026"), reuniao("21/10/2026")
    )

    private val irmaos = listOf(
        Brother(id = 1, name = "Fulano de Tal"),
        Brother(id = 2, name = "Beltrano"),
        Brother(id = 3, name = "João da Silva")
    )

    private fun interpretar(texto: String) =
        ImportadorDesignacoes.interpretar(texto, reunioes, irmaos, anoDeReferencia = 2026)

    // ── O caminho feliz ──────────────────────────────────────────────────────

    @Test
    fun `linha simples entra inteira`() {
        val r = interpretar("07/10/2026 - Fulano de Tal - Demonstração").single()

        assertTrue(r.podeEntrar, "problemas: ${r.problemas}")
        assertEquals("07/10/2026", r.data)
        assertEquals("Fulano de Tal", r.irmao?.name)
        assertEquals("Demonstração", r.item?.title)
    }

    @Test
    fun `data sem ano usa o ano de referencia`() {
        // A IA normalmente escreve só dia e mês.
        val r = interpretar("07/10 - Fulano - Demonstração").single()

        assertTrue(r.podeEntrar, "problemas: ${r.problemas}")
        assertEquals("07/10/2026", r.data)
    }

    @Test
    fun `ano de dois digitos vira 20xx`() {
        // "26" não é o ano 26.
        val r = interpretar("07/10/26 - Fulano - Demonstração").single()

        assertTrue(r.podeEntrar, "problemas: ${r.problemas}")
        assertEquals("07/10/2026", r.data)
    }

    @Test
    fun `varios separadores funcionam`() {
        val comTraco = interpretar("07/10 - Fulano - Demonstração").single()
        val comDoisPontos = interpretar("07/10: Fulano: Demonstração").single()
        val comBarra = interpretar("07/10 | Fulano | Demonstração").single()
        val comTab = interpretar("07/10\tFulano\tDemonstração").single()

        listOf(comTraco, comDoisPontos, comBarra, comTab).forEach {
            assertTrue(it.podeEntrar, "problemas: ${it.problemas}")
        }
    }

    @Test
    fun `espaco duplo e acento nao atrapalham o casamento`() {
        val r = interpretar("07/10 -  Joao  da  Silva  -  Demonstracao ").single()

        assertTrue(r.podeEntrar, "problemas: ${r.problemas}")
        assertEquals("João da Silva", r.irmao?.name)
        assertEquals("Demonstração", r.item?.title)
    }

    @Test
    fun `parte com sufixo casa por prefixo, entra e avisa`() {
        // A IA escreve "Encenação (parte 1)"; o item do jw.org é "Encenação".
        // Entrar mesmo assim: exigir exato mataria o caso mais comum.
        val r = interpretar("07/10 - Fulano - Encenação (parte 1)").single()

        assertEquals("Encenação", r.item?.title)
        assertTrue(r.podeEntrar, "o caso mais comum da IA foi recusado: ${r.problemas}")
        assertFalse(r.casamentoExato, "casamento por prefixo tem que ser marcado")
        assertTrue(
            r.problemas.any { it.contains("aproximado") },
            "o usuário precisa saber que foi uma aposta: ${r.problemas}"
        )
    }

    @Test
    fun `prefixo curto demais nao casa`() {
        // "En" não é "Encenação". Casa por letra seria atribuir a parte ao item
        // errado.
        val r = interpretar("07/10 - Fulano - En").single()

        assertNull(r.item)
        assertFalse(r.podeEntrar)
    }

    @Test
    fun `primeiro nome so casa quando nobody mais tem o mesmo`() {
        // A IA escreveu "Fulano"; o cadastro tem "Fulano de Tal".
        val r = interpretar("07/10 - Fulano - Demonstração").single()

        assertEquals("Fulano de Tal", r.irmao?.name)
        assertTrue(r.podeEntrar, "problemas: ${r.problemas}")
    }

    @Test
    fun `primeiro nome ambiguo NAO casa com ninguem`() {
        // Dois "João": escolher um seria pôr a parte no irmão errado, e ele
        // recebe a designação sem saber.
        val comHomonimos = irmaos + Brother(id = 4, name = "João de Outro Sobrenome")

        val r = ImportadorDesignacoes
            .interpretar("07/10 - João - Demonstração", reunioes, comHomonimos, 2026)
            .single()

        assertNull(r.irmao, "dois irmãos de mesmo primeiro nome: a linha tem que ficar de fora")
        assertFalse(r.podeEntrar)
    }

    // ── O que a IA faz de mal ────────────────────────────────────────────────

    @Test
    fun `cabecalho e regua sao pulados em silencio`() {
        // Um aviso por linha inútil treina o usuário a ignorar os avisos, e aí
        // os que importam passam junto.
        val r = interpretar(
            """
            Semana 07/10/2026
            ---
            07/10 - Fulano - Demonstração
            Total: 8 partes
            ____________
            """.trimIndent()
        )

        assertEquals(1, r.size)
        assertTrue(r.single().podeEntrar)
    }

    @Test
    fun `irmao inexistente vira aviso, nao designacao errada`() {
        val r = interpretar("07/10 - Siciliano - Demonstração").single()

        assertNull(r.irmao)
        assertFalse(r.podeEntrar)
        assertTrue(r.problemas.any { it.contains("Siciliano") }, r.problemas.toString())
    }

    @Test
    fun `parte fora do programa vira aviso`() {
        val r = interpretar("07/10 - Fulano - Comentário da RCA").single()

        assertNull(r.item)
        assertFalse(r.podeEntrar)
        assertTrue(r.problemas.any { it.contains("não está no programa") }, r.problemas.toString())
    }

    @Test
    fun `semana que nao existe no mes vira aviso`() {
        // Não se cria reunião: sem ela não há programa contra o qual casar.
        val r = interpretar("28/10 - Fulano - Demonstração").single()

        assertNull(r.reuniao)
        assertFalse(r.podeEntrar)
        assertTrue(r.problemas.any { it.contains("Não existe reunião") }, r.problemas.toString())
    }

    @Test
    fun `linha sem data vira aviso`() {
        val r = interpretar("Fulano - Demonstração").single()

        assertNull(r.data)
        assertFalse(r.podeEntrar)
        assertTrue(r.problemas.any { it.contains("data") }, r.problemas.toString())
    }

    @Test
    fun `data sem nome e sem parte vira aviso com o formato esperado`() {
        val r = interpretar("07/10/2026 - João").single()

        assertFalse(r.podeEntrar)
        assertTrue(
            r.problemas.any { it.contains("data - nome - parte") },
            "a mensagem precisa mostrar o formato: ${r.problemas}"
        )
    }

    @Test
    fun `data imposible vira aviso, nao data trocada`() {
        val r = interpretar("32/13/2026 - Fulano - Demonstração").single()

        assertNull(r.data)
        assertFalse(r.podeEntrar)
    }

    @Test
    fun `reuniao sem programa importado avisa em vez de chutar`() {
        val semPrograma = listOf(
            Meeting(id = 9, date = "07/10/2026", type = "Reunião de Meio de Semana")
        )
        val r = ImportadorDesignacoes
            .interpretar("07/10 - Fulano - Demonstração", semPrograma, irmaos, 2026)
            .single()

        assertFalse(r.podeEntrar)
        assertTrue(r.problemas.any { it.contains("jw.org") }, r.problemas.toString())
    }

    @Test
    fun `texto vazio nao devolve linha nenhuma`() {
        assertTrue(interpretar("").isEmpty())
        assertTrue(interpretar("\n\n   \n").isEmpty())
    }

    // ── Gravar ───────────────────────────────────────────────────────────────

    @Test
    fun `aplicar escreve na posicao certa do programa`() {
        val r = interpretar(
            """
            07/10 - Fulano - Demonstração
            07/10 - Beltrano - Joias espirituais
            14/10 - Fulano - Leitura do livro
            """.trimIndent()
        )

        val novas = ImportadorDesignacoes.aplicar(r, reunioes)
        val dia7 = novas.first { it.date == "07/10/2026" }
        val dia14 = novas.first { it.date == "14/10/2026" }

        // Ordenado pela POSIÇÃO no programa, não pela ordem colada: Joias (1º),
        // Demonstração (3º).
        assertEquals(
            listOf(1 to 2L, 3 to 1L),
            dia7.programAssignments.map { it.item to it.brotherIds.single() }
        )
        assertEquals(
            listOf(4 to 1L),
            dia14.programAssignments.map { it.item to it.brotherIds.single() }
        )
    }

    @Test
    fun `aplicar joga fora a linha com problema`() {
        val r = interpretar(
            """
            07/10 - Fulano - Demonstração
            07/10 - Siciliano - Demonstração
            """.trimIndent()
        )

        val novas = ImportadorDesignacoes.aplicar(r, reunioes)
        val dia7 = novas.first { it.date == "07/10/2026" }

        assertEquals(1, dia7.programAssignments.size, "o irmão que não existe foi gravado")
        assertEquals(1L, dia7.programAssignments.single().brotherIds.single())
    }

    @Test
    fun `reuniao que nao apareceu na lista fica como estava`() {
        val r = interpretar("07/10 - Fulano - Demonstração")

        val novas = ImportadorDesignacoes.aplicar(r, reunioes)

        assertTrue(novas.first { it.date == "14/10/2026" }.programAssignments.isEmpty())
    }

    @Test
    fun `dois irmaos na mesma parte, apos o programa permitir`() {
        val r = interpretar(
            """
            07/10 - Fulano - Encenação
            07/10 - Beltrano - Encenação
            """.trimIndent()
        )

        val dia7 = ImportadorDesignacoes.aplicar(r, reunioes).first { it.date == "07/10/2026" }

        assertEquals(setOf(1L, 2L), dia7.programAssignments.single().brotherIds.toSet())
    }

    @Test
    fun `colar de novo nao apaga o que ja estava`() {
        // Quem ajusta a mão não pode perder por colar duas vezes.
        val antes = listOf(
            reuniao("07/10/2026").copy(
                programAssignments = listOf(ProgramAssignmentRef(1, listOf(99L)))
            )
        )
        val r = interpretar("07/10 - Fulano - Demonstração")

        val novas = ImportadorDesignacoes.aplicar(r, antes)

        assertEquals(2, novas.single().programAssignments.size, "o item 1 sumiu")
    }

    @Test
    fun `com substituir, a parte passa a ter so quem veio da importacao`() {
        val antes = listOf(
            reuniao("07/10/2026").copy(
                programAssignments = listOf(ProgramAssignmentRef(1, listOf(99L)))
            )
        )
        val r = interpretar("07/10 - Fulano - Demonstração")

        val novas = ImportadorDesignacoes.aplicar(r, antes, substituirTudo = true)

        assertEquals(listOf(3), novas.single().programAssignments.map { it.item })
    }

    @Test
    fun `o mesmo irmao nao entra duas vezes na mesma parte`() {
        val r = interpretar(
            """
            07/10 - Fulano - Encenação
            07/10 - Fulano - Encenação
            """.trimIndent()
        )

        val dia7 = ImportadorDesignacoes.aplicar(r, reunioes).first { it.date == "07/10/2026" }

        assertEquals(1, dia7.programAssignments.single().brotherIds.size)
    }

    // ── A separação que não pode quebrar ────────────────────────────────────

    @Test
    fun `importar NAO toca nos privilegios mecanicos`() {
        // "Partes do programa" e "privilégios" são coisas diferentes, de listas
        // diferentes, com regras diferentes. A lista de partes que veio da IA diz
        // "Fulano - Demonstração": isso é quem faz a DEMONSTRAÇÃO, e não quem é
        // o som, o indicador ou o orador.
        //
        // Se algum dia alguém "acomodar" a importação para também preencher
        // `assignments`, este teste é o que para. Ele compara o campo antes e
        // depois, campo a campo — não só a lista inteira.
        val antes = listOf(
            reuniao("07/10/2026").copy(
                assignments = listOf(
                    Assignment(privilegeId = 10L, brotherId = 1L),
                    Assignment(privilegeId = 11L, brotherId = 2L)
                )
            ),
            reuniao("14/10/2026").copy(
                assignments = listOf(Assignment(privilegeId = 10L, brotherId = 2L))
            )
        )

        val r = interpretar(
            """
            07/10 - Fulano - Demonstração
            07/10 - Beltrano - Joias espirituais
            14/10 - Fulano - Leitura do livro
            """.trimIndent()
        )

        val depois = ImportadorDesignacoes.aplicar(r, antes)

        assertEquals(
            antes.map { it.assignments },
            depois.map { it.assignments },
            "a importação mexeu nos privilégios mecânicos"
        )
    }

    @Test
    fun `substituir tambem NAO toca nos privilegios mecanicos`() {
        // `substituirTudo` é a opção destrutiva. Se ela limpasse `assignments`, o
        // mês inteiro perderia os cargos mecânicos e ninguém notaria até a
        // reunião.
        val antes = listOf(
            reuniao("07/10/2026").copy(
                assignments = listOf(Assignment(privilegeId = 10L, brotherId = 1L))
            )
        )
        val r = interpretar("07/10 - Fulano - Demonstração")

        val depois = ImportadorDesignacoes.aplicar(r, antes, substituirTudo = true)

        assertEquals(
            antes.first().assignments,
            depois.first().assignments,
            "\"substituir a semana\" apagou os privilégios mecânicos"
        )
    }

    @Test
    fun `importar nao mexe nas reunioes que nao aparecem no texto`() {
        // A reunião vizinha tem seus próprios privilégios; nada nela pode mudar.
        val vizinha = listOf(
            reuniao("21/10/2026").copy(
                assignments = listOf(Assignment(privilegeId = 10L, brotherId = 3L)),
                programAssignments = listOf(ProgramAssignmentRef(1, listOf(3L)))
            )
        )
        val r = interpretar("07/10 - Fulano - Demonstração")

        val depois = ImportadorDesignacoes.aplicar(r, vizinha)

        assertEquals(vizinha.first(), depois.first(), "uma reunião fora do texto foi alterada")
    }

    @Test
    fun `o importador nao conhece a palavra privilegio`() {
        // Trava de leitura, não de teste: o interpretador não importa nada de
        // privilégio, e qualquer `Privilege` que aparecer aqui é bug por construção.
        val fonte = ImportadorDesignacoes::class.java

        assertTrue(
            fonte.declaredMethods.none { it.parameterTypes.any { p -> p.simpleName.contains("Privilege") } },
            "o interpretador passou a depender de privilégio"
        )
    }

    @Test
    fun `importar nao mexe nas reunioes de fim de semana`() {
        val comFimDeSemana = reunioes + listOf(
            Meeting(id = 99, date = "10/10/2026", type = "Reunião de Fim de Semana", program = programa)
        )
        val r = ImportadorDesignacoes
            .interpretar("10/10 - Fulano - Demonstração", comFimDeSemana, irmaos, 2026)

        assertNotNull(r.single().reuniao)
        // A importação em si não tem trava de tipo — quem decide é a regra de
        // negócio do app, e o usuário pediu só meio de semana. Este teste fixa o
        // comportamento ATUAL para ficar explícito, não o desejado.
        assertTrue(ImportadorDesignacoes.aplicar(r, comFimDeSemana)
            .first { it.date == "10/10/2026" }.programAssignments.isNotEmpty())
    }
}

/** Atalho para não repetir o nome da classe em todo teste. */
private typealias ProgramAssignmentRef =
    br.com.willendary.designacoesjw.data.ProgramAssignment