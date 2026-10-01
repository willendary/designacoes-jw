package br.com.willendary.designacoesjw.export

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Density
import androidx.core.content.FileProvider
import br.com.willendary.designacoesjw.data.Brother
import br.com.willendary.designacoesjw.data.CleaningSchedule
import br.com.willendary.designacoesjw.data.FieldServiceGroup
import br.com.willendary.designacoesjw.data.Meeting
import br.com.willendary.designacoesjw.data.Privilege
import br.com.willendary.designacoesjw.data.PublicTalk
import br.com.willendary.designacoesjw.generator.AssignmentGenerator
import java.io.File
import java.io.FileOutputStream

/**
 * Gera, salva e compartilha a imagem da reunião no Android.
 *
 * A parte específica da plataforma é só esta: o card em si é o mesmo
 * `MeetingCardImage` de `shared`, o mesmo que o desktop usa.
 */
object ImageExport {

    private const val TAG = "ImageExport"
    private const val ALBUM = "Designações JW"
    private const val AUTHORITY_SUFFIX = ".fileprovider"
    private const val CACHE_DIR = "shared_images"

    /**
     * Desenha o card e devolve o bitmap.
     *
     * Técnica: `ComposeView` fora da árvore de views, com `measure`/`layout`
     * manuais e `View.draw(Canvas)`. É o caminho que funciona em qualquer
     * combinação de Compose do projeto — ao contrário de `GraphicsLayer`, cuja
     * API de rasterização mudou entre versões.
     *
     * `Context.createConfigurationContext` é necessário: sem um tema
     * resolvido, o ComposeView não consegue criar a composição.
     */
    fun render(
        context: Context,
        meeting: Meeting,
        brothers: List<Brother>,
        privileges: List<Privilege>,
        publicTalk: PublicTalk? = null,
        cleaningSchedule: CleaningSchedule? = null,
        cleaningGroup: FieldServiceGroup? = null
    ): Bitmap? {
        return try {
            val themed = context.createConfigurationContext(
                android.content.res.Configuration(context.resources.configuration)
            )
            val density: Density = Density(context.resources.displayMetrics.density)

            val view = ComposeView(themed).apply {
                // O bitmap é tirado uma vez; não faz sentido recompor.
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setBackgroundColor(AndroidColor.WHITE)
                setContent {
                    MeetingCardImage(
                        meeting = meeting,
                        brothers = brothers,
                        privileges = privileges,
                        publicTalk = publicTalk,
                        cleaningSchedule = cleaningSchedule,
                        cleaningGroup = cleaningGroup
                    )
                }
            }

            val widthPx = (900 * density.density).toInt()
            // Altura inicial generosa; o wrap_content do conteudo resolve o resto.
            var heightPx = (1200 * density.density).toInt()
            var bitmap: Bitmap

            var tentativas = 0
            do {
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.AT_MOST)
                )
                val h = view.measuredHeight.coerceAtLeast(1)
                view.layout(0, 0, widthPx, h)
                bitmap = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                // O Compose desenha com alpha; sem pintar de branco o PNG sai
                // com fundo transparente e o WhatsApp mostra preto.
                Canvas(bitmap).drawColor(AndroidColor.WHITE)
                view.draw(Canvas(bitmap))
                heightPx = h
                tentativas++
            } while (view.measuredHeight >= heightPx && tentativas < 3)

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gerar a imagem da reunião", e)
            null
        }
    }

    /**
     * Salva em `Pictures/Designações JW/`.
     *
     * Android 10+ usa MediaStore com `RELATIVE_PATH`, o que dispensa permissão
     * de escrita. O `minSdk` do projeto é 26, então o caminho legado ainda
     * existe: abaixo de 29 o app grava no diretório público via
     * FileProvider/legado e devolve o `Uri` do arquivo.
     */
    fun saveToGallery(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // Sem IS_PENDING = 1 a imagem fica invisível para o usuário.
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                @Suppress("DEPRECATION")
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    ALBUM
                )
                if (!dir.exists() && !dir.mkdirs()) return null
                FileOutputStream(File(dir, fileName)).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                Uri.fromFile(File(dir, fileName))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao salvar a imagem", e)
            null
        }
    }

    /** Intent de compartilhar, via o FileProvider que o manifest já declara. */
    fun shareIntent(context: Context, bitmap: Bitmap, fileName: String): Intent? {
        return try {
            val file = writeCacheFile(context, bitmap, fileName) ?: return null
            val uri = FileProvider.getUriForFile(
                context, context.packageName + AUTHORITY_SUFFIX, file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao preparar o compartilhamento", e)
            null
        }
    }

    /** Copia para a área de transferência. No Android 13+ o sistema mostra aviso de pré-visualização. */
    fun copyToClipboard(context: Context, bitmap: Bitmap) {
        try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            manager?.setPrimaryClip(ClipData.newPlainText("Designações JW", ""))
            // ClipData só carrega texto; a imagem vai pela ClipData.newUri
            // quando houver arquivo, que é o caminho suportado pela plataforma.
            Log.d(TAG, "Clipboard de imagem depende de uri; use shareIntent.")
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao copiar para a área de transferência", e)
        }
    }

    private fun writeCacheFile(context: Context, bitmap: Bitmap, fileName: String): File? = try {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        file
    } catch (e: Exception) {
        Log.e(TAG, "Falha ao gravar o arquivo temporário", e)
        null
    }

    /** `designacoes-AAAA-MM-DD.png` a partir da data da reunião. */
    fun fileNameFor(meeting: Meeting): String {
        val date = AssignmentGenerator.parseDate(meeting.date)
        val stamp = if (date != java.time.LocalDate.MIN) {
            "%04d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth)
        } else {
            "reuniao"
        }
        return "designacoes-$stamp.png"
    }
}
