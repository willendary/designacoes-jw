package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * As cinco paletas que o usuário escolhe.
 *
 * Aqui, e não em cada plataforma: o Android tinha a lista dentro do
 * `MainActivity` e o desktop tinha outra. Duas listas de cor é o caminho mais
 * curto para os dois ficarem parecidos, não iguais.
 *
 * O par é `(primária, secundária)`. O claro e o escuro **não** são o mesmo com
 * brilho diferente: em fundo escuro a cor saturada brilha e cansa, então a
 * variante escura usa o tom claro da rampa.
 */
enum class JwPalette(
    val nome: String,
    val claro: Pair<Color, Color>,
    val escuro: Pair<Color, Color>
) {
    AZUL(
        "Azul",
        Color(0xFF1565C0) to Color(0xFF42A5F5),
        Color(0xFF90CAF9) to Color(0xFF80DEEA)
    ),
    VERDE(
        "Verde",
        Color(0xFF2E7D32) to Color(0xFF66BB6A),
        Color(0xFFA5D6A7) to Color(0xFF80CBC4)
    ),
    ROXO(
        "Roxo",
        Color(0xFF6A1B9A) to Color(0xFFAB47BC),
        Color(0xFFCE93D8) to Color(0xFFB39DDB)
    ),
    LARANJA(
        "Laranja",
        Color(0xFFEF6C00) to Color(0xFFFFA726),
        Color(0xFFFFCC80) to Color(0xFFFFB74D)
    ),
    VINHO(
        "Vinho",
        Color(0xFF8E244D) to Color(0xFFAD4F73),
        Color(0xFFF48FB1) to Color(0xFFF06292)
    );

    companion object {
        /** Índice seguro: tema gravado antigo ou arquivo editado na mão. */
        fun porIndice(indice: Int): JwPalette = entries.getOrElse(indice) { AZUL }
    }
}

/**
 * Instala o tema do app.
 *
 * Os dois apps chamam isto, então a escolha de paleta, o modo escuro e os papéis
 * semânticos saem iguais nos dois. Antes cada um tinha a sua lista e nenhuma
 * verificação de que continuavam iguais.
 */
@Composable
fun JwThemeProvider(
    paleta: JwPalette = JwPalette.AZUL,
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val cores = paleta.cores(isDark)

    CompositionLocalProvider(
        LocalJwColors provides cores.jw,
        LocalJwSpacing provides JwSpacing()
    ) {
        MaterialTheme(colorScheme = cores.esquema) {
            content()
        }
    }
}

/**
 * Esquema do Material3 a partir da paleta.
 *
 * O modo escuro **não** é só a paleta clara invertida: `surface` recebe um
 * azul-carvão em vez de cinza puro. Cinza em fundo escuro fica sujo ao lado de
 * uma cor primária saturada.
 */
@Composable
private fun JwPalette.cores(isDark: Boolean): CoresDoTema {
    val (primaria, secundaria) = if (isDark) escuro else claro

    val esquema: ColorScheme = if (isDark) {
        darkColorScheme(
            primary = primaria,
            secondary = secundaria,
            surface = Color(0xFF12161C),
            background = Color(0xFF0B0E12),
            surfaceVariant = Color(0xFF232B36)
        )
    } else {
        lightColorScheme(
            primary = primaria,
            secondary = secundaria,
            surface = Color(0xFFFCFDFE),
            background = Color(0xFFF4F6F9),
            surfaceVariant = Color(0xFFE7EBF0)
        )
    }

    return CoresDoTema(
        esquema = esquema,
        jw = if (isDark) {
            JwColors(
                sucesso = Color(0xFF81C784),
                sucessoContainer = Color(0xFF1B3A20),
                alerta = Color(0xFFFFB74D),
                perigo = Color(0xFFEF9A9A),
                whatsapp = Color(0xFF25D366),
                salaoFundo = Color(0xFFFFFFFF),
                salaoTexto = Color(0xFF1E293B),
                salaoPrimaria = Color(0xFF1565C0),
                telaoFundo = Color(0xFF0B1220),
                telaoTexto = Color(0xFFF1F5F9),
                telaoPrimaria = Color(0xFF60A5FA),
                grade = Color(0xFF94A3B8)
            )
        } else {
            JwColors(
                sucesso = Color(0xFF2E7D32),
                sucessoContainer = Color(0xFFE8F5E9),
                alerta = Color(0xFFEF6C00),
                perigo = Color(0xFFC62828),
                whatsapp = Color(0xFF25D366),
                salaoFundo = Color(0xFFFFFFFF),
                salaoTexto = Color(0xFF1E293B),
                salaoPrimaria = Color(0xFF1565C0),
                telaoFundo = Color(0xFF0B1220),
                telaoTexto = Color(0xFFF1F5F9),
                telaoPrimaria = Color(0xFF60A5FA),
                grade = Color(0xFFCBD5E1)
            )
        }
    )
}

@Immutable
private data class CoresDoTema(val esquema: ColorScheme, val jw: JwColors)