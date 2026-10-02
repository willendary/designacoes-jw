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
 * O menu do aplicativo, como dado.
 *
 * **Por que uma lista e não treze `NavigationDrawerItem` escritos à mão.** O
 * menu era copy-paste com um `Divider` a cada seção. Reordenar ou renomear
 * significava editar bloco de código, e foi assim que os nomes ficaram
 * confusos: ninguém lê 180 linhas para achar o rótulo certo.
 *
 * Com a lista, reorganizar é editar uma linha.
 *
 * Os nomes foram revistos porque três telas de reunião se chamavam "Início",
 * "Histórico de Reuniões" e "Reuniões", e nada dizia qual era qual:
 *
 * - **Quadro do Mês** — o mês inteiro, onde se gera. É a tela inicial, e é
 *   mensal, não semanal como o rótulo antigo dizia.
 * - **Reunião da Semana** — uma semana, onde se importa o programa do jw.org.
 * - **Histórico** — arquivo do que já passou.
 *
 * E o item de relatórios se chamava "Relatório de Impressão A4", que não
 * promete o quadro do mês — era o que escondia o quadro de quem procurava
 * "quadro para imprimir".
 */
sealed interface MenuEntry

/** Cabeçalho de seção. */
data class MenuSection(val titulo: String) : MenuEntry

/** Item que abre uma tela. */
data class MenuItem(
    val tab: Int,
    val rotulo: String,
    val icone: ImageVector
) : MenuEntry

/**
 * Ordem do menu.
 *
 * Agrupado por **o que a pessoa quer fazer**, não por módulo do código. Quem
 * chega para imprimir procura "Imprimir"; quem chega para corrigir o gênero de
 * um irmão procura "Irmãos".
 */
val MENU: List<MenuEntry> = listOf(
    MenuSection("REUNIÕES"),
    MenuItem(3, "Quadro do Mês", Icons.Filled.CalendarMonth),
    MenuItem(16, "Reunião da Semana", Icons.Filled.Event),
    MenuItem(1, "Histórico", Icons.Filled.History),

    MenuSection("IMPRIMIR E RELATÓRIOS"),
    MenuItem(13, "Quadro e Imagens do Mês", Icons.Filled.Print),
    MenuItem(15, "Estatísticas de Equidade", Icons.Filled.EventAvailable),

    MenuSection("PESSOAS"),
    MenuItem(2, "Irmãos & Irmãs", Icons.Filled.People),
    MenuItem(4, "Privilégios", Icons.Filled.Star),
    MenuItem(14, "Férias & Ausências", Icons.Filled.PersonOff),
    MenuItem(5, "Usuários & Acesso", Icons.Filled.Security),

    MenuSection("CONGREGAÇÃO"),
    MenuItem(11, "Discursos Públicos", Icons.Filled.MenuBook),
    MenuItem(12, "Grupos & Limpeza", Icons.Filled.CleaningServices),
    MenuItem(10, "Modo Telão", Icons.Filled.Tv),

    MenuSection("AJUSTES"),
    MenuItem(0, "Configurações", Icons.Filled.Settings)
)

/** Rótulo de cada tela, usado no topo. */
fun tituloDaTela(tab: Int): String =
    (MENU.firstOrNull { it is MenuItem && it.tab == tab } as? MenuItem)?.rotulo
        ?: "Designações JW"