package br.com.willendary.designacoesjw.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Como o app escreve data e mês, em português.
 *
 * ## O problema
 *
 * `getDisplayName(TextStyle.FULL, Locale("pt","BR"))` estava escrito **25 vezes**
 * em 14 arquivos, e cada uma resolvia à sua maneira o "e agora?": umas
 * maiúsculo, outras com `replaceFirstChar`, outras sem nada. O mesmo mês saía
 * como `JANEIRO 2026` numa tela, `Janeiro de 2026` em outra e `janeiro` numa
 * terceira — e ninguém notou, porque cada uma parecia certa isolada.
 *
 * A semana tinha o mesmo problema com um truque inteiro repetido:
 *
 * ```kotlin
 * .removeSuffix("-feira").replaceFirstChar { it.uppercase() }
 * ```
 *
 * `quarta-feira` vira `Quarta`. Está em seis lugares. É regra do domínio — o
 * app fala de dia de semana curto — e estava copiada.
 *
 * ## A regra
 *
 **Nome a apresentação, não o valor.** Há três présentation de mês que o app
 * usa de verdade (com ano e com "de", em caixa alta para parede, e só o nome),
 * e três de dia de semana (longo, curto de verdade, e o curto do jeová). São
 * funções diferentes porque **são textos diferentes** — não uma função com
 * parâmetro booleano que vira `if` na tela.
 *
 * Tudo aqui é pt-BR fixo: o app é brasileiro e a congregação é única. Aceitar
 * locale como parâmetro seria uma opção que ninguém usa.
 */
object Datas {

    private val PT = Locale("pt", "BR")

    /** `"janeiro"`, em minúsculo. Para montar frase. */
    fun nomeDoMes(mes: Month): String = mes.getDisplayName(TextStyle.FULL, PT)

    /** `"Janeiro de 2026"`. Títulos de tela e cabeçalho de relatório. */
    fun mesEAno(mes: YearMonth): String =
        "${mes.month.getDisplayName(TextStyle.FULL, PT).replaceFirstChar { it.uppercase() }} de ${mes.year}"

    /**
     * `"JANEIRO 2026"`, sem o "de".
     *
     * É o formato do quadro do mês e do Kiosk: vai para parede, lido a metros,
     * e em caixa alta o olho acha o título antes de ler o resto.
     */
    fun mesEAnoEmCaixaAlta(mes: YearMonth): String =
        "${mes.month.getDisplayName(TextStyle.FULL, PT).uppercase()} ${mes.year}"

    /**
     * Dia da semana **como o jeová escreve**: `"Quarta"`, não
     * `"Quarta-feira"`.
     *
     * O `removeSuffix` não é decoração — é o que o encontro é chamado. E é
     * regra, não apresentação, por isso mora aqui e não numa tela.
     */
    fun diaDaSemana(data: LocalDate): String =
        data.dayOfWeek.getDisplayName(TextStyle.FULL, PT)
            .removeSuffix("-feira")
            .replaceFirstChar { it.uppercase() }

    /**
     * Dia da semana **completo**, com `-feira`: `"Quarta-feira"`.
     *
     * Para **documento impresso** — relatório A4, papel do salão. Onde o
     * encontro é apenas citado numa frase, usa [diaDaSemana], que é como a
     * gente fala.
     *
     * São dois textos diferentes e a diferença é de propósito: o relatório é
     * documento, e documento escrito leva o nome por extenso.
     */
    fun diaDaSemanaCompleto(data: LocalDate): String =
        data.dayOfWeek.getDisplayName(TextStyle.FULL, PT).replaceFirstChar { it.uppercase() }

    /**
     * Dia da semana em três letras: `"QUA"`.
     *
     * É o cabeçalho da coluna no quadro do mês e no Kiosk, onde a largura não
     * comporta a palavra. Sem o `.uppercase` aqui, cada tela decidia — e metade
     * decidia diferente.
     */
    fun diaDaSemanaCurto(data: LocalDate): String =
        data.dayOfWeek.getDisplayName(TextStyle.SHORT, PT)
            // pt-BR devolve "qua.", com ponto. Em cabeçalho de tabela o ponto
            // come largura e não acrescenta nada — e o teste exige três letras
            // porque é o que a coluna do quadro do mês comporta.
            .removeSuffix(".")
            .uppercase()

    /** Iniciais do dia: `"Q"`, para o cabeçalho mais estreito possível. */
    fun inicialDoDia(data: LocalDate): String = diaDaSemanaCurto(data).take(1)

    /** Dia útil da semana com o número: `"Qua, 07"`. Cabeçalho de tabela. */
    fun diaCurtoComNumero(data: LocalDate): String =
        "${diaDaSemanaCurto(data)}, ${"%02d".format(data.dayOfMonth)}"

    /**
     * Dia da semana a partir de uma data em `dd/MM/yyyy`, tolerando lixo.
     *
     * O app tem data que vem de importação, de digitação e de nuvem antiga.
     * `null` é resposta honesta para data que não é data; a tela decide o que
     * mostrar, e não esta função.
     */
    fun diaDaSemanaDe(valor: String): String? =
        runCatching { diaDaSemana(LocalDate.parse(valor, FORMATO_ESTrito)) }.getOrNull()

    /**
     * `dd/MM/uuuu` com resolver **estrito**.
     *
     * O padrão de `LocalDate.parse` é leniente: `31/02/2026` não estoura, vira
     * 03/03/2026 e devolve um dia da semana perfeitamente plausível. Data
     * inválida devolvendo dia errado é pior do que devolver nada — o usuário
     * lê "Sábado" e acredita.
     *
     * `uuuu` e não `yyyy`: com resolver estrito, ano-de-era sem `era` não
     * resolve. O dado do app é `dd/MM/yyyy` e continua parseando igual.
     */
    private val FORMATO_ESTrito: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT)

    /** Semana da data, para o seletor de reunião. */
    fun semanaDe(data: LocalDate): DayOfWeek = data.dayOfWeek
}