package br.com.willendary.designacoesjw.export

import android.app.Activity
import android.content.ContextWrapper
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.core.view.doOnPreDraw
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private const val CACHE_DIR = "reports/images"

    /**
     * Desenha o card e devolve o bitmap.
     *
     * Anexa temporariamente o ComposeView à Activity: sem uma árvore com
     * LifecycleOwner e recompositor, a imagem pode ser vazia ou falhar.
     * Aguarda a primeira composição e só então desenha o bitmap.
     */
    suspend fun render(
        context: Context, meeting: Meeting, brothers: List<Brother>,
        privileges: List<Privilege>, publicTalk: PublicTalk? = null,
        cleaningSchedule: CleaningSchedule? = null, cleaningGroup: FieldServiceGroup? = null
    ): Bitmap? = withContext(Dispatchers.Main.immediate) {
        var base: Context = context
        while (base !is Activity && base is ContextWrapper) base = base.baseContext
        val activity = base as? Activity ?: return@withContext null
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@withContext null
        val width = (900 * activity.resources.displayMetrics.density).toInt()
        val view = ComposeView(activity).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            translationX = -width.toFloat() - 64f
            setContent {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    MeetingCardImage(meeting, brothers, privileges, publicTalk, cleaningSchedule, cleaningGroup)
                }
            }
        }
        val completed = CompletableDeferred<Bitmap?>()
        try {
            root.addView(view, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT))
            view.doOnPreDraw {
                view.post {
                    if (!completed.isCompleted) {
                        val bitmap = runCatching {
                            view.measure(
                                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                            )
                            val height = view.measuredHeight
                            require(height > 0) { "Imagem sem altura" }
                            view.layout(0, 0, width, height)
                            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                                Canvas(it).apply { drawColor(AndroidColor.WHITE); view.draw(this) }
                            }
                        }.onFailure { Log.e(TAG, "Falha ao renderizar imagem", it) }.getOrNull()
                        completed.complete(bitmap)
                    }
                }
            }
            withTimeoutOrNull(8000L) { completed.await() }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao gerar imagem", e)
            null
        } finally {
            completed.cancel()
            root.removeView(view)
            view.disposeComposition()
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
                try {
                    val gravou = resolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    } ?: false
                    check(gravou) { "Não foi possível gravar o PNG." }
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    check(resolver.update(uri, values, null, null) > 0) {
                        "Não foi possível finalizar a imagem na galeria."
                    }
                    uri
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
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
