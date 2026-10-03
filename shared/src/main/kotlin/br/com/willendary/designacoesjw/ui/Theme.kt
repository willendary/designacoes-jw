package br.com.willendary.designacoesjw.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * O sistema visual do app, em um lugar só.
 *
 * ## Por que isto existe
 *
 * Havia 130 cores `0xFF...` espalhadas por 13 arquivos, e a contagem enganava:
 * quase metade era tela de salão (kiosk) e imagem exportada por `java.awt`,
 * que **não devem** seguir o tema. A dívida real era ~45 — mas mesmo essas não
 * tinham nome. `Color(0xFF2E7D32)` não diz se é sucesso, destaque ou borda;
 * `MaterialTheme.colorScheme` tem 40+ papéis e o app usava 6.
 *
 * Aqui ficam só os papéis que o Material3 **não tem** e que o app usa de
 * verdade. O resto continua vindo de `MaterialTheme.colorScheme` — não foi
 * substituto, foi complemento.
 *
 * ## A regra
 *
 * Cor em tela de app se chama por **o que significa**, nunca pelo valor:
 *
 * ```kotlin
 * Text("Salvo", color = JwTheme.colors.sucesso)        // sim
 * Text("Salvo", color = Color(0xFF2E7D32))              // não
 * ```
 *
 * ## Exceção: tela de salão
 *
 * Kiosk, quadro do mês e imagem exportada têm paleta **fixa**, e está certo.
 * São lidos do fundo do salão, por gente que não escolheu tema nenhum, e
 * imprimir fundo escuro queima toner. `JwColors.salao*` existe para dizer
 * isso em vez de deixar 49 `0xFF` sem justificativa.
 */
@Immutable
data class JwColors(
    /** Confirmação: gravou, sincronizou, estar presente. */
    val sucesso: Color,
    val sucessoContainer: Color,
    /** Atenção sem urgência: falta designar, prazo próximo. */
    val alerta: Color,
    /** Erro e recusa. Não substitui `colorScheme.error`, que é quem manda. */
    val perigo: Color,
    /** Verde do WhatsApp. Cor de marca: não segue o tema. */
    val whatsapp: Color,
    /**
     * **Impressão.** Fundo do quadro do mês, porque vai para papel.
     *
     * Claro, e não por gosto: fundo escuro consome toner e a folha sai suja.
     */
    val salaoFundo: Color,
    val salaoTexto: Color,
    val salaoPrimaria: Color,
    /**
     * **Display.** Fundo da tela do salão, porque vai para projetor.
     *
     * Escuro, e o oposto de [salaoFundo]: projetor em sala fechada ofusca quem
     * está na frente, e o quadro do telão é lido a metros por gente que não pode
     * levantar.
     *
     * Estes dois não podem ser o mesmo token. Eram um só, e a parede ficou
     * branca: fica bom no telefone e é ilegível no salão.
     */
    val telaoFundo: Color,
    val telaoTexto: Color,
    val telaoPrimaria: Color,
    /** Traço de grade, no quadro do mês. */
    val grade: Color
)

/**
 * Escala de espaçamento.
 *
 * Existe para a mesma razão: `Arrangement.spacedBy(6.dp)`, `(10.dp)` e
 * `(14.dp)` apareciam lado a lado no mesmo arquivo. Com a escala, o respiro
 * vira decisão e a inconsistência fica visível.
 */
@Immutable
data class JwSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp
)

val LocalJwColors = staticCompositionLocalOf {
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

val LocalJwSpacing = staticCompositionLocalOf { JwSpacing() }

/**
 * Acesso curto aos papéis.
 *
 * ```kotlin
 * Text("Salvo", color = JwTheme.colors.sucesso)
 * Spacer(Modifier.width(JwTheme.spacing.sm))
 * ```
 */
object JwTheme {
    val colors: JwColors
        @Composable @ReadOnlyComposable get() = LocalJwColors.current

    val spacing: JwSpacing
        @Composable @ReadOnlyComposable get() = LocalJwSpacing.current
}

/**
 * Cor de papel que **sobe** para a variante escura do Material3.
 *
 * Material3 já escurece `surface` e `onSurface`. O que falta é o meio-termo:
 * `surfaceVariant` é o cinza de cartão de verdade, e no esquema claro ele é
 * claro demais para texto secundário. Estas duas funções dão os pares certos
 * sem duplicar a paleta inteira para o modo escuro.
 */
@Composable
fun superficieDeCartao(isDark: Boolean): Color =
    if (isDark) Color(0xFF1B2430) else Color(0xFFF6F8FB)

/** Traço de separação entre blocos. */
@Composable
fun corDeContorno(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)