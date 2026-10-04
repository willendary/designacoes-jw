package br.com.willendary.designacoesjw

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * As telas do aplicativo, por nome.
 *
 * ## Por que um enum e nao `Int`
 *
 * `when (tab)` com `Int` nao da erro nenhum quando o numero esta errado: a tela
 * nao abre, e a unica pista e o sumir do conteudo. Trocar `16` por `6` — digito
 * de tanto — e um bug que passa pelo build, pelo teste e so aparece em aparelho.
 * Com [Tela], o mesmo erro nao compila.
 *
 * ## O numero do `Kiosk`
 *
 * Antes [TELA_KIOSK] era o `10`, e o `if (tab == 10)` vivia no topo do `App`,
 * longe do `when`. Ficava dificil ver que `10` era uma tela como as outras. O
 * enum nao guarda numero nenhum: e a ordem de declaracao que substitui o numero
 * antigo, e quem chama usa o nome.
 */
enum class Tela {
    CONFIGURACOES,
    HISTORICO,
    IRMAOS,
    QUADRO_DO_MES,
    PRIVILEGIOS,
    USUARIOS,
    DISCURSOS,
    GRUPOS_LIMPEZA,
    IMPRIMIR,
    FERIAS,
    ESTATISTICAS,
    REUNIAO_DA_SEMANA,
    TELAO;

    companion object {
        /** Tela de inicio. Fica aqui para nao repetir o literal em dois lugares. */
        val INICIAL = QUADRO_DO_MES
    }
}

/**
 * O menu do aplicativo, como dado.
 *
 * **Por que uma lista e nao treze `NavigationDrawerItem` escritos a mao.** O
 * menu era copy-paste com um `Divider` a cada secao. Reordenar ou renomear
 * significava editar bloco de codigo, e foi assim que os nomes ficaram
 * confusos: ninguem le 180 linhas para achar o rotulo certo.
 *
 * Com a lista, reorganizar e editar uma linha.
 *
 * Os nomes foram revistos porque tres telas de reuniao se chamavam "Inicio",
 * "Historico de Reunioes" e "Reunioes", e nada dizia qual era qual:
 *
 * - **Quadro do Mes** — o mes inteiro, onde se gera. E a tela inicial, e e
 *   mensal, nao semanal como o rotulo antigo dizia.
 * - **Reuniao da Semana** — uma semana, onde se importa o programa do jw.org.
 * - **Historico** — arquivo do que ja passou.
 *
 * E o item de relatorios se chamava "Relatorio de Impressao A4", que nao
 * promete o quadro do mes — era o que escondia o quadro de quem procurava
 * "quadro para imprimir".
 */
sealed interface MenuEntry

/** Cabecalho de secao. */
data class MenuSection(val titulo: String) : MenuEntry

/** Item que abre uma tela. */
data class MenuItem(
    val tela: Tela,
    val rotulo: String,
    val icone: ImageVector
) : MenuEntry

/**
 * Ordem do menu.
 *
 * Agrupado por **o que a pessoa quer fazer**, nao por modulo do codigo. Quem
 * chega para imprimir procura "Imprimir"; quem chega para corrigir o genero de
 * um irmao procura "Irmaos".
 */
val MENU: List<MenuEntry> = listOf(
    MenuSection("REUNIÕES"),
    MenuItem(Tela.QUADRO_DO_MES, "Quadro do Mês", Icons.Filled.CalendarMonth),
    MenuItem(Tela.REUNIAO_DA_SEMANA, "Reunião da Semana", Icons.Filled.Event),
    MenuItem(Tela.HISTORICO, "Histórico", Icons.Filled.History),

    MenuSection("IMPRIMIR E RELATÓRIOS"),
    MenuItem(Tela.IMPRIMIR, "Quadro e Imagens do Mês", Icons.Filled.Print),
    MenuItem(Tela.ESTATISTICAS, "Estatísticas de Equidade", Icons.Filled.EventAvailable),

    MenuSection("PESSOAS"),
    MenuItem(Tela.IRMAOS, "Irmãos & Irmãs", Icons.Filled.People),
    MenuItem(Tela.PRIVILEGIOS, "Privilégios", Icons.Filled.Star),
    MenuItem(Tela.FERIAS, "Férias & Ausências", Icons.Filled.PersonOff),
    MenuItem(Tela.USUARIOS, "Usuários & Acesso", Icons.Filled.Security),

    MenuSection("CONGREGAÇÃO"),
    MenuItem(Tela.DISCURSOS, "Discursos Públicos", Icons.Filled.MenuBook),
    MenuItem(Tela.GRUPOS_LIMPEZA, "Grupos & Limpeza", Icons.Filled.CleaningServices),
    MenuItem(Tela.TELAO, "Modo Telão", Icons.Filled.Tv),

    MenuSection("AJUSTES"),
    MenuItem(Tela.CONFIGURACOES, "Configurações", Icons.Filled.Settings)
)

/**
 * Rotulo de cada tela, usado no topo.
 *
 * Vem da propria lista do menu, entao renomear o item renomeia o topo junto —
 * antes os dois podiam divergir em silencio.
 */
fun tituloDaTela(tela: Tela): String =
    (MENU.firstOrNull { it is MenuItem && it.tela == tela } as? MenuItem)?.rotulo
        ?: "Designações JW"