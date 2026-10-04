package br.com.willendary.designacoesjw.export

/**
 * Lê uma lista de nomes colada, ou um CSV, e diz o que entendeu.
 *
 * ## O problema que motivou
 *
 * `importBrothersFromCsv` trata `,` como **separador de coluna**. Então colar
 *
 * ```
 * Carlos, Daniel, Marcos
 * ```
 *
 * vira **uma** linha: nome `Carlos`, telefone `Daniel` (filtrado para vazio
 * porque não tem dígito), cargo `Marcos`. **Só Carlos entra, e Daniel e Marcos
 * são perdidos em silêncio** — sem erro, sem aviso, sem contagem.
 *
 * É o oposto do que a pessoa quer ao colar uma lista.
 *
 * ## A regra
 *
 * > **Se algum campo da linha parece telefone, é CSV. Senão, é lista de nomes.**
 *
 * Nome de pessoa não é formado por dígito; telefone é. Isso decide sem
 * adivinhar formato, e a regra cabe numa frase que dá para explicar a quem vai
 * usar.
 *
 * Não há `sniffDelimiter` aqui: adivinhar separador por frequência foi o que
 * produziu a falha original.
 */
object ListaDeNomes {

    /** Como o texto colado se divide. */
    enum class Formato {
        /** Um nome por linha, ou nomes separados por vírgula. */
        NOMES,

        /** CSV com as colunas que o importador já espera. */
        CSV
    }

    /**
     * O que saiu da leitura.
     *
     * Contar é o mínimo. Uma importação que não diz quantos entraram e quantos
     * foram ignorados **não tem como ser conferida**, e quem cola 40 nomes não
     * vai abrir o app 40 vezes para ver se deu certo.
     */
    data class Resultado(
        val formato: Formato,
        /** Nomes novos, na ordem em que apareceram e sem repetir. */
        val novos: List<String>,
        /** Nomes que já estavam na lista colada, em si. */
        val repetidosNaMesmaLista: List<String>
    ) {
        val total: Int get() = novos.size + repetidosNaMesmaLista.size
    }

    /** Separadores aceitos entre nomes. `;` também, porque colar do Excel traz. */
    private val SEPARADORES = charArrayOf(',', ';', '\n', '\r', '\t')

    /**
     * Separa os campos de uma linha, respeitando aspas.
     *
     * Sem aspas, um nome com vírgula — `Silva, João` — viraria dois irmãos. É
     * por isso que o CSV usa aspas, e a lista colada também precisa poder.
     */
    private fun campos(linha: String): List<String> {
        val saida = mutableListOf<String>()
        val atual = StringBuilder()
        var dentroDeAspas = false

        linha.forEach { c ->
            when {
                c == '"' -> dentroDeAspas = !dentroDeAspas
                c in SEPARADORES && !dentroDeAspas -> {
                    saida += atual.toString()
                    atual.clear()
                }
                else -> atual.append(c)
            }
        }
        saida += atual.toString()
        return saida.map { it.trim() }
    }

    /**
     * O campo parece telefone?
     *
     * Exige **4 dígitos ou mais**. `2` é o número de irmãos por reunião e
     * `1` é ativo/ativo — se contasse, quase toda linha de CSV vira lista de
     * nomes e nenhum irmão entra com telefone.
     */
    private fun pareceTelefone(valor: String): Boolean =
        valor.filter { it.isDigit() }.length >= 4

    /**
     * Lê o texto.
     *
     * @param texto o que a pessoa colou.
     * @param normalizar a mesma função que o importador CSV usa
     *   (`AssignmentGenerator.normalizeName`), para que os dois caminhos
     *   deduplicam **igual**. Duas regras de identidade é como o mesmo irmão
     *   entra duas vezes.
     */
    fun ler(texto: String, normalizar: (String) -> String): Resultado {
        val linhas = texto.lines().filter { it.isNotBlank() }
        if (linhas.isEmpty()) {
            return Resultado(Formato.NOMES, emptyList(), emptyList())
        }

        // Cabeçalho do CSV ("nome,telefone,...") sai fora antes de decidir.
        val semCabecalho = linhas
            .dropWhile { campos(it).firstOrNull()?.lowercase()?.startsWith("nome") == true }
            .ifEmpty { linhas }

        val formato = if (semCabecalho.any { linha -> campos(linha).any(::pareceTelefone) }) {
            Formato.CSV
        } else {
            Formato.NOMES
        }

        val vistos = mutableSetOf<String>()
        val novos = mutableListOf<String>()
        val repetidos = mutableListOf<String>()

        semCabecalho.forEach { linha ->
            val c = campos(linha)
            // **CSV:** o nome é a primeira coluna e o resto é telefone, cargo e
            // privilégio. **Lista:** *todo* campo é um nome — é exatamente a
            // diferença entre os dois formatos, e pegar só o primeiro campo é o
            // defeito que fez `Carlos, Daniel, Marcos` cadastrar um irmão só.
            val nomesDaLinha = if (formato == Formato.CSV) listOfNotNull(c.firstOrNull())
            else c

            nomesDaLinha.forEach { bruto ->
                val nome = bruto.replace("\"", "").trim()
                if (nome.isBlank()) return@forEach

                val chave = normalizar(nome)
                if (chave.isBlank()) return@forEach
                if (vistos.add(chave)) novos += nome else repetidos += nome
            }
        }

        return Resultado(formato, novos, repetidos)
    }
}