package br.com.willendary.designacoesjw.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * O cartão do app.
 *
 * Existe para as 13 telas não resolverem "como fica um cartão aqui" 40 vezes,
 * cada uma com um `Card` sem traço, outro com traço, outro com alpha 0.65. O
 * redesenho viraria caça ao pixel, e a próxima tela nova voltaria ao aleatório.
 *
 * **Traço, não só fundo.** Cor de fundo sozinha some no projetor do salão e na
 * impressão a laser, que é onde este app é usado. O traço sobrevive aos dois.
 *
 * Respiro generoso de propósito: no celular a mão é grande, a tela é pequena e
 * o olho cansa. O desktop reusa este cartão — o que muda é a densidade da
 * tela, não o cartão.
 */
@Composable
fun JwCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Realça o cartão: o que a pessoa precisa ver primeiro. */
    destaque: Boolean = false,
    /** Conteúdo antes do corpo — cabeçalho, etiqueta. */
    leading: (@Composable ColumnScope.() -> Unit)? = null,
    /** Ações no rodapé. */
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val espaco = JwTheme.spacing

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (destaque) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            } else {
                superficieDeCartao(isSystemInDarkTheme())
            }
        ),
        border = if (destaque) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
        } else {
            BorderStroke(1.dp, corDeContorno())
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            Modifier.padding(horizontal = espaco.lg, vertical = espaco.md),
            verticalArrangement = Arrangement.spacedBy(espaco.sm)
        ) {
            if (leading != null) leading()
            content()
            if (actions != null) {
                HorizontalDivider(color = corDeContorno())
                JwCardActions { actions() }
            }
        }
    }
}

/**
 * Título de bloco dentro de um cartão.
 *
 * Maior e mais escuro que a etiqueta: é o que se lê primeiro.
 */
@Composable
fun JwCardTitle(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier = modifier,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * Etiqueta de seção, para o olho de quem **varre** a lista.
 *
 * Maiúscula e pequena de propósito. [JwCardTitle] é o que se lê primeiro;
 * esta é o que se reconhece de relance.
 */
@Composable
fun JwSectionLabel(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/** Linha de ação no rodapé de um cartão. */
@Composable
fun JwCardActions(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(JwTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * Trilho colorido no topo do cartão.
 *
 * Substitui "fundo colorido em alpha 0.35", que ficava sujo no modo escuro e
 * não sobrevivia à impressão. Um traço de 3px aguenta.
 */
@Composable
fun JwCardRail(cor: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 3.dp,
        color = cor
    )
}