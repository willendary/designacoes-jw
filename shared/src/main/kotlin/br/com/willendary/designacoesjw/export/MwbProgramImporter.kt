package br.com.willendary.designacoesjw.export

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLDecoder
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

    /** {bimestre}-{ano}-mwb, ex.: setembro-outubro-2026-mwb */
    internal fun bimestreSlug(date: LocalDate): String {
        val ym = YearMonth.from(date)
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
        val year = Regex("""(\d{4})-mwb""").find(slug)!!.groupValues[1].toInt()
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
        val linkRe = Regex(
            """<a[^>]+href="([^"]*Programa[^"]*)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (m in linkRe.findAll(html)) {
            val range = parseWeekRange(plain(m.groupValues[2]), year) ?: continue
            if (date >= range.first && date <= range.second) {
                return WeekLink(URLDecoder.decode(m.groupValues[1], "UTF-8"), range.first, range.second)
            }
        }
        return null
    }

    /**
     * Interpreta o rótulo de semana. Formatos reais do jw.org:
     * "5-11 de outubro", "28 de setembro–4 de outubro", "26 de outubro–1.º de novembro".
     * O ano é sempre informado pelo chamador (vem do slug bimestral).
     */
    internal fun parseWeekRange(label: String, year: Int): Pair<LocalDate, LocalDate>? {
        // Limpa "1.º" → "1" e o ano solto antes de tokenizar.
        val text = label.replace(Regex("""[.\u00ba\u00b0]"""), "")
            .replace(Regex("""\bde\s+\d{4}\b""", RegexOption.IGNORE_CASE), "")

        // \b evita que "2026" vire o dia "20".
        val tokens = Regex("""\b(\d{1,2})\b(?:\s*de\s+([a-zçãç]+))?""", RegexOption.IGNORE_CASE)
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

        val headRe = Regex(
            """<h([23])([^>]*)>(.*?)</h\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        for (m in headRe.findAll(body)) {
            val level = m.groupValues[1]
            val text = plain(m.groupValues[3])
            if (text.isEmpty()) continue

            val numbered = Regex("""^(\d{1,2})\.\s+(.*)$""").find(text)
            if (numbered != null) {
                val minutes = Regex("""\((\d{1,3})\s*min""", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                // A duração entra em Part.minutes; não deve sobrar no título.
                val title = Regex("""\s*\(\d{1,3}\s*min[^)]*\)""", RegexOption.IGNORE_CASE)
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
        Regex("""^[\p{Lu}\p{L} .'\-]+\s*\d+([–\-]\d+)?$""").matches(text.trim())

    /** HTML → texto puro, colapsando espaços. */
    private fun plain(html: String): String {
        var t = Regex("""(?is)<script.*?</script>""").replace(html, " ")
        t = Regex("""(?is)<style.*?</style>""").replace(t, " ")
        t = Regex("""<[^>]+>""").replace(t, " ")
        for (e in ENTITIES) t = t.replace(e.first, e.second)
        return t.replace(Regex("""[\s\u00a0]+"""), " ").trim()
    }

    private val ENTITIES = listOf(
        "&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">",
        "&quot;" to "\"", "&#39;" to "'", "–" to "-", "—" to "-"
    )
}
