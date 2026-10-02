package br.com.willendary.designacoesjw.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.view.View
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Log
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.ui.MonthBoard
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Imprime o quadro do mês no Android.
 *
 * **O que foi resolvido aqui.** O quadro do mês nasceu como tela, com a
 * intenção de o usuário imprimir com `Ctrl+P`. Isso funciona no desktop e
 * **não existe no celular** — não há atalho de teclado, e a única saída era
 * fotografar a tela. A capacidade estava faltando, não o botão.
 *
 * `PrintManager` é a via nativa: o app entrega o bitmap e o sistema cuida da
 * caixa de diálogo, das impressoras e do formato.
 */
object MonthBoardPrint {

    private const val TAG = "MonthBoardPrint"

    /**
     * A4 paisagem em 300 dpi, que é a resolução de matriz que a maioria das
     * impressoras do salão usa para letra grande.
     *
     * 297 x 210 mm × 300/25.4 ≈ 3508 x 2480. O quadro já vem com a proporção
     * certa, então sobra só a folha.
     */
    private const val LARGURA_A4_300DPI = 3508
    private const val ALTURA_A4_300DPI = 2480

    /**
     * Desenha o quadro e abre a caixa de impressão.
     *
     * O render é o mesmo do `ImageExport.render`: `ComposeView` fora da árvore,
     * `measure`/`layout` manuais e `view.draw(Canvas)`. `GraphicsLayer`, que
     * seria mais direto, mudou de API entre versões do Compose.
     */
    fun imprimir(
        context: Context,
        month: YearMonth,
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): Boolean {
        val bitmap = renderizar(context, month, meetings, brothers, privileges)
            ?: return false
        val impressora = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false
        impressora.print(
            jobName(context, month),
            QuadroAdapter(bitmap),
            PrintAttributes.Builder()
                // A orientação é do `MediaSize`, não do `PrintDocumentInfo`:
                // em retrato a impressora corta a largura do quadro ao meio.
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
                .setResolution(PrintAttributes.Resolution("300dpi", "300dpi", 300, 300))
                // Margem zero: o quadro já tem o próprio respiro, e margem
                // padrão empurra o conteúdo para dentro da folha utilizável.
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        )
        return true
    }

    /**
     * Desenha o quadro num bitmap.
     *
     * Devolve `null` em vez de lançar: quem chama mostra o erro na tela, e uma
     * exceção aqui dentro morria dentro da composição.
     */
    fun renderizar(
        context: Context,
        month: YearMonth,
        meetings: List<Meeting>,
        brothers: List<Brother>,
        privileges: List<Privilege>
    ): Bitmap? = try {
        // Sem um tema resolvido o ComposeView não consegue criar a composição.
        val themed = context.createConfigurationContext(
            android.content.res.Configuration(context.resources.configuration)
        )
        val density = Density(context.resources.displayMetrics.density)

        val view = ComposeView(themed).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                MonthBoard(
                    month = month,
                    meetings = meetings,
                    brothers = brothers,
                    privileges = privileges,
                    // A folha é branca: imprimir o fundo escuro do tema sairia
                    // como um retângulo preto queimando toner.
                    modifier = Modifier.width(LARGURA_A4_300DPI.dp)
                )
            }
        }

        var alturaPx = ALTURA_A4_300DPI
        var tentativas = 0
        var resultado: Bitmap
        do {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(LARGURA_A4_300DPI, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(alturaPx, View.MeasureSpec.AT_MOST)
            )
            val medida = view.measuredHeight.coerceAtLeast(1)
            view.layout(0, 0, LARGURA_A4_300DPI, medida)
            val bitmap = Bitmap.createBitmap(LARGURA_A4_300DPI, medida, Bitmap.Config.ARGB_8888)
            // O Compose desenha com alpha; sem pintar de branco o PDF sai
            // transparente e a impressora joga o fundo fora.
            Canvas(bitmap).drawColor(Color.WHITE)
            view.draw(Canvas(bitmap))
            resultado = bitmap
            alturaPx = medida
            tentativas++
        } while (view.measuredHeight >= alturaPx && tentativas < 3)

        resultado
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao desenhar o quadro do mês", e)
        null
    }

    private fun jobName(context: Context, month: YearMonth): String {
        val mes = month.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
            .replaceFirstChar { it.uppercase(Locale("pt", "BR")) }
        return "$mes ${month.year} — Designações"
    }

    /** Uma folha só, com o bitmap desenhado inteiro na página. */
    private class QuadroAdapter(private val bitmap: Bitmap) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(nomeJob)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(1)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out android.print.PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            // Uma página só: um quadro, uma folha. `pages` viria dizer qual, e
            // não há para onde ir.
            if (cancellationSignal?.isCanceled == true) {
                callback.onWriteCancelled()
                return
            }
            try {
                val pdf = PdfDocument()
                val pagina = pdf.startPage(
                    PdfDocument.PageInfo.Builder(
                        LARGURA_A4_300DPI, ALTURA_A4_300DPI, 1
                    ).create()
                )
                pagina.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(pagina)

                FileOutputStream(destination.fileDescriptor).use { saida ->
                    pdf.writeTo(saida)
                }
                pdf.close()
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao escrever o quadro para impressão", e)
                callback.onWriteFailed(e.message)
            }
        }

        private val nomeJob = "Designações"
    }
}
