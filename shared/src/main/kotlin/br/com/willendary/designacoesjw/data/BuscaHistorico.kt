package br.com.willendary.designacoesjw.data

import br.com.willendary.designacoesjw.generator.AssignmentGenerator

/**
 * Busca no histórico de reuniões (#63).
 *
 * ## Por que uma função e não um filtro na tela
 *
 * O Android e o desktop têm telas de histórico diferentes, e um `filter` escrito
 * duas vezes diverge na segunda edição — foi assim que as partes do programa
 * sumiram do histórico do Android enquanto o desktop as mostrava. Aqui a regra é
 * uma, e o teste trava.
 *
 * ## O que a pessoa procura
 *
 * Não a reunião: **o irmão**. A pergunta real é "cadê a reunião em que o Carlos
 * foi.controllers namedado?", e quem digita "Carlos" não sabe o dia, nem o
 *Privilegio, nem o tipo. Por isso a busca varre quem esteve lá — e quem esteve
 *lá inclui quem fez **parte** do programa, não só quem teve privilégio.
 */
object BuscaHistorico {

    /**
     * Reuniões que casam com [termo].
     *
     * Comparação **sem acento e sem maiúscula**: "Joao" precisa achar "João", e
     * no teclado do celular ninguém acenta com frequência. `TextOrder` já tem o
     * normalizador; a regra fica aqui para não haver duas.
     *
     * Texto vazio devolve tudo — não é erro, é o estado inicial da tela.
     */
    fun filtrar(
        termo: String,
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): List<Meeting> {
        // Normaliza **uma vez**, fora do laço: por reuniao e por pessoa daria
        // dezenas de passadas de regex, e a lista pode ter anos de reuniões.
        val procurado = normalize(termo)
        if (procurado.isEmpty()) return meetings

        val porId = brothers.associateBy { it.id }
        val privilegioPorId = privileges.associateBy { it.id }
        val nomes = brothers.associate { it.id to normalize(it.name) }

        return meetings.filter { reuniao ->
            if (contem(procurado, reuniao.date)) return@filter true
            if (contem(procurado, reuniao.type)) return@filter true
            if (contem(procurado, reuniao.theme)) return@filter true

            // Privilegio mecanico.
            if (reuniao.assignments.any { a ->
                    contem(procurado, privilegioPorId[a.privilegeId]?.name) ||
                        contem(procurado, porId[a.brotherId]?.name)
                }
            ) return@filter true

            // Parte do programa. Sem isto a busca acha o irmao quando ele foi
            // "Leitor" e nao acha quando ele fez a "Encenacao" -- que e a maior
            // parte das vezes que alguem procura alguem.
            reuniao.programAssignments.any { parte ->
                parte.brotherIds.any { id -> contem(procurado, nomes[id]) }
            }
        }
    }

    /** Sem acento e sem maiúscula: "Joao" acha "João". */
    private fun normalize(valor: String): String =
        AssignmentGenerator.normalizeName(valor)

    /**
     * `null` é ausência de dado, não texto vazio que casa com tudo.
     *
     * Sem este `null` check, uma reunião cujo irmão foi removido casaria com
     * qualquer busca — e "casa com tudo" é o pior defeito de busca: o usuário
     * digita o nome e recebe o histórico inteiro, e conclui que a busca não
     * funciona.
     */
    private fun contem(procurado: String, valor: String?): Boolean {
        if (valor.isNullOrBlank()) return false
        return normalize(valor).contains(procurado)
    }
}