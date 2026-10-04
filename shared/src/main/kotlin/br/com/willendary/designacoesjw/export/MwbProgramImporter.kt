package br.com.willendary.designacoesjw.export

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Importador do programa da Reunião Vida e Ministério (meio de semana) do jw.org.
 *
 * Fonte: a página bimestral `.../jw-apostila-do-mes/{bimestre}-{ano}-mwb/` lista os
 * links das 8 semanas (o texto do link é o intervalo, ex.: "5-11 de outubro"), e a
 * página de cada semana traz tema, seções e itens numerados.
 *
 * Ex.: 05/10/2026 → tema "JEREMIAS 40-41", seções TESOUROS / FAÇA SEU MELHOR /
 * NOSSA VIDA CRISTÃ com os itens 1..9.
 */
object MwbProgramImporter {

    data class Part(
        /** Seção do programa ("TESOUROS DA PALAVRA DE DEUS"). Vazio para itens avulsos. */
        val section: String,
        /** Número exibido no programa (1..9). 0 para itens sem número. */
        val number: Int,
        /** Título do item, já sem o número ("Iniciando conversas"). */
        val title: String,
        /** Duração em minutos, quando o programa informa. 0 quando não informa. */
        val minutes: Int
    ) {
        /** Texto pronto para virar nome de privilégio no gerador. */
        val label: String get() = if (minutes > 0) "$title ($minutes min)" else title
    }

    data class Program(
        val weekStart: LocalDate,
        val weekEnd: LocalDate,
        /** Leitura do dia / tema da semana ("JEREMIAS 40-41"). */
        val theme: String,
        val parts: List<Part>,
        val sourceUrl: String
    )

    private val MONTHS = mapOf(
        "janeiro" to 1, "fevereiro" to 2, "março" to 3, "marco" to 3,
        "abril" to 4, "maio" to 5, "junho" to 6, "julho" to 7,
        "agosto" to 8, "setembro" to 9, "outubro" to 10, "novembro" to 11, "dezembro" to 12
    )

    /** Nomes pt-BR dos meses — Month.name é sempre em inglês. */
    private val MONTH_NAMES_PT = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro"
    )

    private const val BIB = "/pt/biblioteca/jw-apostila-do-mes/"

    // ── Padrões compilados uma única vez ────────────────────────────────────
    //
    // Em Kotlin cada `Regex(...)` compila um Pattern novo. Dentro de função — e
    // pior, dentro de `for (m in headRe.findAll(body))` — o mesmo padrão era
    // recompilado uma vez por item do programa e por chamada de `plain()`, que
    // é chamada em todo cabeçalho. Aqui ficam prontos: só o padrão e as flags
    // importam, então nada muda além de quando o Pattern nasce.

    /** Ano do slug bimestral: "setembro-outubro-2026-mwb" → "2026". */
    private val RE_YEAR_IN_SLUG = Regex("""(\d{4})-mwb""")

    private val RE_WEEK_LINK = Regex(
        """<a[^>]+href="([^"]*Programa[^"]*)"[^>]*>(.*?)</a>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    /** Marcações de ordinal do jw.org: "1.º" → "1". */
    private val RE_ORDINAL_MARK = Regex("""[.\u00ba\u00b0]""")

    /** Ano solto no rótulo da semana ("5-11 de outubro de 2026"). */
    private val RE_LOOSE_YEAR = Regex("""\bde\s+\d{4}\b""", RegexOption.IGNORE_CASE)

    /** \b evita que "2026" vire o dia "20". */
    private val RE_DAY_TOKEN = Regex("""\b(\d{1,2})\b(?:\s*de\s+([a-zçãç]+))?""", RegexOption.IGNORE_CASE)

    private val RE_HEADING = Regex(
        """<h([23])([^>]*)>(.*?)</h\1>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val RE_NUMBERED_ITEM = Regex("""^(\d{1,2})\.\s+(.*)$""")
    private val RE_MINUTES = Regex("""\((\d{1,3})\s*min""", RegexOption.IGNORE_CASE)
    private val RE_MINUTES_IN_TITLE = Regex("""\s*\(\d{1,3}\s*min[^)]*\)""", RegexOption.IGNORE_CASE)

    private val RE_SCRIPTURE = Regex("""^[\p{Lu}\p{L} .'\-]+\s*\d+([–\-]\d+)?$""")

    private val RE_SCRIPT = Regex("""(?is)<script.*?</script>""")
    private val RE_STYLE = Regex("""(?is)<style.*?</style>""")
    private val RE_TAG = Regex("""<[^>]+>""")
    private val RE_WHITESPACE = Regex("""[\s\u00a0]+""")

    /**
     * {bimestre}-{ano}-mwb, ex.: setembro-outubro-2026-mwb.
     *
     * O bimestre é definido pela **semana** (segunda a domingo), não pelo mês do
     * dia: uma reunião nos primeiros dias de janeiro pode pertencer à semana
     * iniciada em dezembro do ano anterior (ex.: 02/01/2027 cai na semana de
     * 28/12/2026 → novembro-dezembro-2026-mwb). O ano do slug é o do período.
     */
    /**
     * Endereco publico do programa de [date] no jw.org (#50).
     *
     * Publico porque a tela mostra o link **quando a leitura falha**: se o app
     * nao conseguiu baixar o programa, o caminho que resolve e abrir a pagina
     * no navegador e copiar na mao. Esconder o endereco que o proprio app usa
     * seria esconder a saida.
     */
    fun urlPublica(date: LocalDate): String =
        "https://www.jw.org" + BIB + bimestreSlug(date) + "/"

    internal fun bimestreSlug(date: LocalDate): String {
        val monday = date.minusDays((date.dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
        val ym = YearMonth.from(monday)
        val end = if (ym.monthValue % 2 == 0) ym else ym.plusMonths(1)
        val start = end.minusMonths(1)
        return "${MONTH_NAMES_PT[start.monthValue - 1]}-${MONTH_NAMES_PT[end.monthValue - 1]}-${end.year}-mwb"
    }

    /**
     * Baixa e interpreta o programa da semana que contém [date].
     * @throws IllegalArgumentException se a semana não estiver publicada.
     */
    fun fetch(date: LocalDate): Program {
        val slug = bimestreSlug(date)
        val year = RE_YEAR_IN_SLUG.find(slug)!!.groupValues[1].toInt()
        val indexUrl = "https://www.jw.org$BIB$slug/"
        val index = get(indexUrl) ?: throw IllegalStateException(
            "O jw.org ainda não publicou o programa de ${MONTH_NAMES_PT[date.monthValue - 1]}/${year}. " +
                "A apostila costuma sair com até 2 meses de antecedência."
        )
        val link = findWeekLink(index, date, year)
            ?: throw IllegalArgumentException("O jw.org não tem programa publicado para a semana de $date.")
        val url = if (link.path.startsWith("http")) link.path else "https://www.jw.org${link.path}"
        return parse(get(url) ?: throw IllegalStateException("Não foi possível abrir a página do programa."), link.start, link.end, url)
    }

    // ── Net ──────────────────────────────────────────────────────────────────

    /** null quando a página não existe (404) — o chamador decide a mensagem. */
    private fun get(url: String): String? {
        val conn = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36"
            )
            setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9")
            connectTimeout = 15000
            readTimeout = 15000
        }
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("jw.org respondeu ${conn.responseCode} para $url")
            }
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    // ── Parser ───────────────────────────────────────────────────────────────

    internal data class WeekLink(val path: String, val start: LocalDate, val end: LocalDate)

    /**
     * Localiza o <a> da semana de [date] lendo o rótulo do link ("5-11 de outubro").
     * O ano vem do slug bimestral — os rótulos do jw.org não trazem ano.
     */
    internal fun findWeekLink(html: String, date: LocalDate, year: Int): WeekLink? {
        for (m in RE_WEEK_LINK.findAll(html)) {
            val range = parseWeekRange(plain(m.groupValues[2]), year) ?: continue
            if (date >= range.first && date <= range.second) {
                return WeekLink(decodePath(m.groupValues[1]), range.first, range.second)
            }
        }
        return null
    }

    /**
     * Decodifica percent-encoding de **caminho** de URL, preservando o `+`.
     *
     * `URLDecoder` é para query string (`application/x-www-form-urlencoded`),
     * onde `+` significa espaço. Num caminho o `+` é o próprio sinal de mais —
     * e o jw.org usa `+` nos slugs. Decodificar com `URLDecoder` corrompia o
     * caminho e o bimestre deixava de ser encontrado.
     *
     * Sequência malformada (`%` no fim, `%ZZ`) é devolvida como está, sem estourar.
     */
    internal fun decodePath(value: String): String {
        if (!value.contains('%')) return value
        // Percorre os bytes UTF-8: um %XX solto pode montar um caractere
        // multibyte, e qualquer caractere literal precisa sobreviver inteiro.
        val src = value.toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream(src.size)
        var i = 0
        while (i < src.size) {
            val b = src[i].toInt() and 0xFF
            if (b == PERCENT && i + 2 < src.size) {
                val code = hex(src[i + 1].toInt()) shl 4 or hex(src[i + 2].toInt())
                if (code >= 0) {
                    out.write(code)
                    i += 3
                    continue
                }
            }
            out.write(b)
            i++
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private const val PERCENT = 0x25 // '%'

    /** Valor do nibble hexadecimal, ou -1 quando o byte não é hex. */
    private fun hex(b: Int): Int = when (b) {
        in 0x30..0x39 -> b - 0x30          // 0-9
        in 0x41..0x46 -> b - 0x41 + 10     // A-F
        in 0x61..0x66 -> b - 0x61 + 10     // a-f
        else -> -1
    }

    /**
     * Interpreta o rótulo de semana. Formatos reais do jw.org:
     * "5-11 de outubro", "28 de setembro–4 de outubro", "26 de outubro–1.º de novembro".
     * O ano é sempre informado pelo chamador (vem do slug bimestral).
     */
    internal fun parseWeekRange(label: String, year: Int): Pair<LocalDate, LocalDate>? {
        // Limpa "1.º" → "1" e o ano solto antes de tokenizar.
        val text = label.replace(RE_ORDINAL_MARK, "")
            .replace(RE_LOOSE_YEAR, "")

        // \b evita que "2026" vire o dia "20".
        val tokens = RE_DAY_TOKEN
            .findAll(text)
            .map { m ->
                m.groupValues[1].toInt() to m.groupValues[2].lowercase(Locale.ROOT).let { MONTHS[it] }
            }
            .filter { it.first in 1..31 }
            .toList()
        if (tokens.size < 2) return null

        // "5-11 de outubro": só o último dia traz mês — os anteriores herdam o seguinte.
        val months = IntArray(tokens.size)
        var next = 0
        for (i in tokens.indices.reversed()) {
            val explicit = tokens[i].second
            if (explicit != null) next = explicit
            months[i] = next
        }
        if (months.any { it == 0 }) return null

        val start = safeDate(year, months.first(), tokens.first().first)
        var end = safeDate(year, months.last(), tokens.last().first)
        // Virada de ano: bimestre dez/jan com a semana de janeiro já no ano seguinte.
        if (end < start) end = safeDate(year + 1, months.last(), tokens.last().first)
        return start to end
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val ym = YearMonth.of(year, month)
        return ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
    }

    /** Extrai tema e itens do programa semanal. */
    internal fun parse(html: String, weekStart: LocalDate, weekEnd: LocalDate, url: String): Program {
        val body = sliceBody(html)

        var theme = ""
        var currentSection = ""
        val parts = mutableListOf<Part>()

        for (m in RE_HEADING.findAll(body)) {
            val level = m.groupValues[1]
            val text = plain(m.groupValues[3])
            if (text.isEmpty()) continue

            val numbered = RE_NUMBERED_ITEM.find(text)
            if (numbered != null) {
                val minutes = RE_MINUTES
                    .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                // A duração entra em Part.minutes; não deve sobrar no título.
                val title = RE_MINUTES_IN_TITLE
                    .replace(numbered.groupValues[2], "")
                    .trim()
                    .trimEnd('.')
                parts += Part(
                    section = currentSection,
                    number = numbered.groupValues[1].toInt(),
                    title = title,
                    minutes = minutes
                )
                continue
            }

            // Leitura do dia / tema: caixa alta COM números ("JEREMIAS 40-41").
            // Precisa vir antes do teste de seção, senão o título do tema vira seção.
            if (theme.isEmpty() && level == "2" && looksLikeScripture(text)) {
                theme = text
                continue
            }

            // Cabeçalho de seção: caixa alta sem números ("TESOUROS DA PALAVRA DE DEUS").
            val isAllCaps = text.length > 3 && text == text.uppercase(Locale.ROOT) &&
                text.count { it.isLetter() } >= 3 && text.none { it.isDigit() }
            if (isAllCaps) currentSection = text
        }

        return Program(weekStart, weekEnd, theme, parts, url)
    }

    private fun sliceBody(html: String): String {
        val start = html.indexOf("<h1")
        if (start < 0) return html
        val marker = html.indexOf("Selecione seu idioma")
        val end = if (marker > start) marker else html.length
        return html.substring(start, end)
    }

    /** "JEREMIAS 40-41", "SALMOS 1-8" — sem marcadores como "(10 min)". */
    private fun looksLikeScripture(text: String): Boolean =
        RE_SCRIPTURE.matches(text.trim())

    /** HTML → texto puro, colapsando espaços. */
    private fun plain(html: String): String {
        var t = RE_SCRIPT.replace(html, " ")
        t = RE_STYLE.replace(t, " ")
        t = RE_TAG.replace(t, " ")
        for (e in ENTITIES) t = t.replace(e.first, e.second)
        return RE_WHITESPACE.replace(t, " ").trim()
    }

    /**
     * Entidades HTML, na ordem em que precisam ser aplicadas.
     *
     * `&amp;` fica por **último** de propósito: o texto `&amp;lt;` representa
     * literalmente `&lt;` na página, e decodificar `&amp;` antes deixaria o
     * `&lt;` recém-decodificado virar `<` — o navegador exibiria `<` onde
     * deveria aparecer `&lt;`. Decodificar uma vez é o comportamento do navegador.
     */
    private val ENTITIES = listOf(
        "&nbsp;" to " ", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "–" to "-", "—" to "-",
        "&amp;" to "&"
    )
}
