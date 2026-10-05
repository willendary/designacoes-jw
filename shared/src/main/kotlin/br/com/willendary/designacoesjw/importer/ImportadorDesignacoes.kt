package br.com.willendary.designacoesjw.importer

import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.ProgramAssignment
import br.com.willendary.designacoesjw.data.ProgramItem
import br.com.willendary.designacoesjw.generator.AssignmentGenerator

/**
 * O que o app faz com um texto colado que veio de uma IA.
 *
 * ## De onde vem o texto
 *
 * A congregação tem a lista de partes da reunião de meio de semana em imagem.
 * Manda a imagem para um chat de IA, pede "o nome e a parte de cada irmão para
 * cada semana", e a IA devolve texto:
 *
 * ```
 * 07/10 - Fulano - demonstração
 * 07/10 - Beltrano - Joias espirituais
 * 14/10 - Fulano - Joias espirituais
 * ```
 *
 * Isso é colado no app, que espalha cada linha pela reunião da data.
 *
 * ## Por que isto é um interpretador e não um importador cego
 *
 * Texto de IA não é dado estruturado: o separador varia, a data vem ora com ano
 * ora sem, o nome às vezes vem abreviado, a parte às vezes é "Encenação (parte
 * 1)" quando o item é "Encenação". Se o app adivinhasse e gravasse, o erro
 * apareceria **depois** — na tela da reunião, no quadro impresso, no WhatsApp que
 * o irmão já recebeu.
 *
 * Por isso o caminho é em duas etapas: [interpretar] diz o que entendeu e o que
 * não entendeu, e nada é gravado antes de a pessoa olhar.
 *
 * ## Nada é adivinhado em silêncio
 *
 * Cada linha sai com um [Resultado.problemas]. Se a parte não casou com o
 * programa oficial, isso é dito — não é o sistema inventar um item. O mesmo
 * vale para irmão: "Fulano" que não existe no cadastro vira aviso, não uma
 * designação para o irmão errado.
 */
object ImportadorDesignacoes {

    /**
     * Separadores aceitos entre data, nome e parte.
     *
     * Tracejado, dois pontos, barra vertical e tabulação. Hífen e travessão
     * contam como o mesmo sinal porque o Word e o WhatsApp trocam um pelo outro
     * o tempo todo. A vírgula **não** entra: parte do programa é preenchida por
     * nome, mas "Leitor, Irmão Fulano" inverteria nome e parte.
     */
    private val SEPARADORES = listOf(" - ", " – ", " — ", " -", " – ", " — ", ": ", "|", "\t")

    /** Primeiro nome só casa com este tamanho para baixo, para não adivinhar. */
    private const val MINIMO_PARA_SOBRENOME = 3

    /** Tamanho mínimo de um prefixo para contar como casamento. */
    private const val MINIMO_PARA_PREFIXO = 5

    /**
     * Data na linha: `dd/MM/yyyy` ou `dd/MM`.
     *
     * O ano é opcional porque a IA normalmente escreve só dia e mês. Sem ano,
     * vale o [anoDeReferencia] — que a tela passa como o mês que está à vista.
     */
    private val REGEX_DATA = Regex("""(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?""")

    /**
     * Cabeçalhos e ruído que a IA costuma emitir junto da lista.
     *
     * São pulados em silêncio, sem virar aviso: uma linha "Semana 07/10" ou uma
     * régua de `---` não é um erro do usuário, é ruído da IA. Um aviso por
     * linha inútil treina o usuário a ignorar os avisos, e aí os que importam
     * passam junto.
     */
    private val RUIDO = Regex(
        """^\s*(total|resumo|observação|observacoes|obs\.?|semana|s[ée]mana)\b.*""",
        RegexOption.IGNORE_CASE
    )

    private fun ehRuido(linha: String): Boolean {
        val t = linha.trim()
        if (t.isEmpty()) return true
        // Régua, asterisco, tracinho repetido.
        if (t.all { it == '-' || it == '*' || it == '=' || it == '_' || it == '~' }) return true
        return RUIDO.matches(t)
    }

    /** A data no formato do app, com o ano de referência quando a linha não traz. */
    private fun dataDaLinha(trecho: String, anoDeReferencia: Int): String? {
        val m = REGEX_DATA.find(trecho) ?: return null
        val dia = m.groupValues[1].toIntOrNull() ?: return null
        val mes = m.groupValues[2].toIntOrNull() ?: return null
        val ano = m.groupValues[3].takeIf { it.isNotBlank() }?.let {
            // "26" vira 2026: duas casas não podem ser o ano 26.
            if (it.length <= 2) "20$it" else it
        }?.toIntOrNull() ?: anoDeReferencia

        if (dia !in 1..31 || mes !in 1..12) return null
        return "%02d/%02d/%04d".format(dia, mes, ano)
    }

    /** Separa "nome" e "parte" no primeiro separador encontrado. */
    private fun separarNomeEParte(texto: String): Pair<String, String>? {
        for (sep in SEPARADORES) {
            val i = texto.indexOf(sep)
            if (i > 0) {
                val nome = texto.substring(0, i).trim()
                val parte = texto.substring(i + sep.length).trim()
                if (nome.isNotEmpty() && parte.isNotEmpty()) return nome to parte
            }
        }
        return null
    }

    /**
     * A chave de comparação de um nome.
     *
     * [AssignmentGenerator.normalizeName] tira acento e caixa; aqui também
     * entra o espaço repetido. "Joao  da  Silva" e "João da Silva" são a mesma
     * pessoa, e a IA escreve com espaço duplo mais vezes do que se imagina.
     */
    private fun chave(texto: String): String =
        AssignmentGenerator.normalizeName(texto).replace(ESPACO_REPETIDO, " ").trim()

    private val ESPACO_REPETIDO = Regex("""\s+""")

    /**
     * Casa um nome escrito com um irmão do cadastro.
     *
     * Exato primeiro: "Fulano de Tal" com "Fulano de Tal".
     *
     * Depois, pelo primeiro nome, e **só se for unívoco**. A IA às vezes escreve
     * só "Fulano" para quem se cadastrou como "Fulano de Tal". Com dois irmãos
     * de mesmo primeiro nome isso não decide nada, e escolher o primeiro da lista
     * seria atribuir a parte ao irmão errado — que é o defeito mais caro que
     * existe aqui: o irmão recebe a designação e nem sabe.
     *
     * @return o irmão, ou `null` se ninguém casou ou se houve ambiguidade.
     */
    fun acharIrmao(nome: String, irmaos: List<Brother>): Brother? {
        val alvo = chave(nome)
        if (alvo.isBlank()) return null

        irmaos.firstOrNull { chave(it.name) == alvo }?.let { return it }

        val primeiroNome = alvo.substringBefore(' ')
        if (primeiroNome.length < MINIMO_PARA_SOBRENOME) return null
        val candidatos = irmaos.filter { chave(it.name).substringBefore(' ') == primeiroNome }
        return candidatos.singleOrNull()
    }

    /**
     * Casa o texto da parte com um item do programa da reunião.
     *
     * Exato primeiro. Depois, por prefixo em qualquer um dos dois sentidos, para
     * os dois formatos que a IA usa e o jw.org não:
     *
     * - o item é "Encenação" e veio "Encenação (parte 1)";
     * - o item é "Encenação (parte 1)" e veio "Encenação".
     *
     * O casamento por prefixo é sinalizado em [Resultado.problemas] e não conta
     * como exato, porque é uma aposta — mas a linha **entra** se a pessoa
     * confirmar. Exigir exato faria o recurso ser recusado no caso mais comum.
     *
     * @return o item e se o casamento foi exato.
     */
    fun acharParte(texto: String, programa: List<ProgramItem>): Pair<ProgramItem, Boolean>? {
        val alvo = chave(texto)
        if (alvo.isBlank() || programa.isEmpty()) return null

        programa.firstOrNull { chave(it.title) == alvo }?.let { return it to true }

        // Os dois sentidos do prefixo, com um piso de tamanho: "En" casando com
        // "Encenação" não é um casamento, é coincidência de letra.
        programa.firstOrNull {
            val titulo = chave(it.title)
            titulo.length >= MINIMO_PARA_PREFIXO && alvo.startsWith(titulo)
        }?.let { return it to false }

        programa.firstOrNull {
            val titulo = chave(it.title)
            alvo.length >= MINIMO_PARA_PREFIXO && titulo.startsWith(alvo)
        }?.let { return it to false }

        return null
    }

    /**
     * Lê o texto e diz, linha a linha, o que entendeu.
     *
     * **Nada é gravado aqui.** É o passo de leitura; [aplicar] é o de escrita, e
     * a tela só o chama depois de a pessoa ver esta lista.
     *
     * @param texto o que foi colado.
     * @param reunioes as reuniões que já existem. Uma linha de reunião que não
     *   existe não é criada: sem ela não há programa oficial contra o qual casar
     *   a parte, e inventar uma reunião é pior do que avisar.
     * @param irmaos o cadastro.
     * @param anoDeReferencia ano para a data que vier só com dia e mês.
     */
    fun interpretar(
        texto: String,
        reunioes: List<Meeting>,
        irmaos: List<Brother>,
        anoDeReferencia: Int
    ): List<Resultado> = texto.lineSequence()
        .filterNot { ehRuido(it) }
        .map { linha -> interpretarLinha(linha.trim(), reunioes, irmaos, anoDeReferencia) }
        .toList()

    private fun interpretarLinha(
        linha: String,
        reunioes: List<Meeting>,
        irmaos: List<Brother>,
        anoDeReferencia: Int
    ): Resultado {
        val problemas = mutableListOf<String>()

        val achadoData = REGEX_DATA.find(linha)
        if (achadoData == null) {
            return Resultado(
                linhaOriginal = linha,
                problemas = listOf("Não achei a data da semana nesta linha.")
            )
        }
        val data = dataDaLinha(achadoData.value, anoDeReferencia)
        if (data == null) {
            return Resultado(
                linhaOriginal = linha,
                problemas = listOf("A data \"${achadoData.value}\" não é um dia de reunião válido.")
            )
        }

        val resto = linha.removeRange(achadoData.range)
            .trim(' ', '-', '–', '—', ':', '|', '\t')
        val separado = separarNomeEParte(resto)
        if (separado == null) {
            return Resultado(
                linhaOriginal = linha,
                data = data,
                problemas = listOf("Não achei o nome e a parte. Use \"data - nome - parte\".")
            )
        }
        val nome = separado.first
        val parte = separado.second

        val reuniao = reunioes.firstOrNull { it.date == data }
        if (reuniao == null) {
            problemas += "Não existe reunião em $data. Gere o mês antes de importar."
            return Resultado(linhaOriginal = linha, data = data, nome = nome, parte = parte, problemas = problemas)
        }

        if (reuniao.program.isEmpty()) {
            problemas += "A reunião de $data não tem programa importado do jw.org, então não deu para casar a parte."
            return Resultado(linhaOriginal = linha, data = data, nome = nome, parte = parte, reuniao = reuniao, problemas = problemas)
        }

        val irmao = acharIrmao(nome, irmaos)
        if (irmao == null) {
            problemas += "Não achei nenhum irmão chamado \"$nome\" no cadastro."
        }

        val achadoParte = acharParte(parte, reuniao.program)
        if (achadoParte == null) {
            problemas += "A parte \"$parte\" não está no programa de $data."
        } else if (!achadoParte.second) {
            problemas += "Casamento aproximado: \"$parte\" virou \"${achadoParte.first.title}\"."
        }

        return Resultado(
            linhaOriginal = linha,
            data = data,
            nome = nome,
            parte = parte,
            reuniao = reuniao,
            irmao = irmao,
            item = achadoParte?.first,
            casamentoExato = achadoParte?.second == true,
            problemas = problemas
        )
    }

    /**
     * Grava as linhas que deram certo.
     *
     * @param resultados o que [interpretar] devolveu.
     * @param reunioes as reuniões atuais; devolve a lista nova, sem mexer na
     *   original.
     * @param substituirTudo `true` troca as designações das reuniões tocadas
     *   pelas que vieram da importação. `false` só acrescenta.
     *
     *   Acrescentar é o padrão porque colar de novo não deve apagar o que o
     *   responsável ajustou à mão.
     *
     *   "Troca a reunião inteira", e não item a item: a lista da IA cobre a
     *   semana, e o que se quer é que a semana passe a ser a lista. Um
     *   "substituir" que só troca as partes citadas deixa intactas as que o GPT
     *   não mencionou — o oposto de substituir, e ninguém perceberia depois de
     *   confirmar na tela.
     */
    fun aplicar(
        resultados: List<Resultado>,
        reunioes: List<Meeting>,
        substituirTudo: Boolean = false
    ): List<Meeting> {
        val porReuniao = resultados
            .filter { it.podeEntrar }
            .groupBy { it.reuniao!!.id }

        return reunioes.map { reuniao ->
            val daImportacao = porReuniao[reuniao.id] ?: return@map reuniao
            val novas = daImportacao.mapNotNull { r ->
                ProgramAssignment(r.item!!.positionIn(reuniao.program), listOf(r.irmao!!.id))
            }
            reuniao.copy(
                programAssignments = mesclarAcrescentando(
                    atuais = if (substituirTudo) emptyList() else reuniao.programAssignments,
                    novas = novas
                )
            )
        }
    }

    /**
     * Junta as novas com as antigas, sem repetir irmão na mesma parte.
     *
     * A saída vem **ordenada pela posição no programa**, e não na ordem em que
     * as linhas foram coladas: quem lê o programa segue a ordem dele, e o
     * `programAssignments` é consumido por posição.
     */
    private fun mesclarAcrescentando(
        atuais: List<ProgramAssignment>,
        novas: List<ProgramAssignment>
    ): List<ProgramAssignment> {
        val porItem = atuais.associateBy { it.item }.toMutableMap()
        for (nova in novas) {
            val existente = porItem[nova.item]
            porItem[nova.item] = if (existente == null) {
                nova
            } else {
                ProgramAssignment(nova.item, (existente.brotherIds + nova.brotherIds).distinct())
            }
        }
        return porItem.values.sortedBy { it.item }
    }
}

/**
 * Uma linha interpretada.
 *
 * @property data no formato do app, quando a linha tinha data legível.
 * @property reuniao a reunião da data. Nula quando a data não existe no mês.
 * @property irmao o irmão casado. Nulo quando não achou ninguém.
 * @property item o item do programa casado. Nulo quando a parte não existe.
 * @property casamentoExato `false` quando o item veio por prefixo.
 */
data class Resultado(
    val linhaOriginal: String,
    val data: String? = null,
    val nome: String? = null,
    val parte: String? = null,
    val reuniao: Meeting? = null,
    val irmao: Brother? = null,
    val item: ProgramItem? = null,
    val casamentoExato: Boolean = true,
    val problemas: List<String> = emptyList()
) {
    /**
     * Tudo nesta linha foi encontrado: reunião, irmão e parte.
     *
     * Não exige [casamentoExato] de propósito. "Encenação (parte 1)" casando com
     * "Encenação" é o caso mais comum, e exigir exato faria o recurso ser
     * recusado justamente nele. A aposta está marcada em [problemas] e aparece
     * na prévia, então quem confirma está vendo.
     *
     * [temAviso] é o que separa "gravado em silêncio" de "gravado e dito".
     */
    val podeEntrar: Boolean
        get() = reuniao != null && irmao != null && item != null

    /** Houve algo que a pessoa precisa olhar antes de confirmar? */
    val temAviso: Boolean get() = problemas.isNotEmpty()
}