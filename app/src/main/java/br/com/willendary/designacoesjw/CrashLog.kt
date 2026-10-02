package br.com.willendary.designacoesjw

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.time.LocalDateTime

/**
 * Captura **qualquer** queda do app e grava onde o usuário consegue ler.
 *
 * Existe por causa de um defeito concreto: o app abria, pintava a tela
 * principal e fechava sozinho. Ninguém — nem o usuário, nem quem reparasse —
 * sabia o porquê, porque:
 *
 * - não havia `try/catch` em lugar nenhum do caminho de abertura, e
 * - o `logcat` exige cabo USB e ferramentas que o usuário não tem.
 *
 * A entrada do log fica em `filesDir` (visível em Configurações > Diagnóstico)
 * **e** uma cópia vai para a pasta Downloads, porque o defeito_impedia o
 * usuário de abrir o app para ler a primeira.
 */
object CrashLog {

    private const val TAG = "CrashLog"
    const val ARQUIVO = "erros.log"

    /**
     * Instala a rede. Chamar o mais cedo possível — `MainActivity.onCreate` é o
     * primeiro código do app que roda.
     *
     * Encadeia no handler anterior em vez de substituí-lo: o Android usa o
     * handler padrão para mostrar a caixa de diálogo "o app parou de
     * responder", e perder isso esconde o sintoma do próprio usuário.
     */
    fun instalar(contexto: Context) {
        val anterior = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, erro ->
            try {
                gravar(contexto, erro)
            } catch (e: Throwable) {
                Log.e(TAG, "Nem o log de crash conseguiu ser gravado", e)
            }
            // O processo morre de qualquer jeito; repassar garante a caixa de
            // diálogo do sistema e o envio para o diagnóstico do fabricante.
            anterior?.uncaughtException(thread, erro)
        }
    }

    /** Anexa a queda ao log e joga uma cópia em Downloads. */
    fun gravar(contexto: Context, erro: Throwable) {
        val texto = buildString {
            appendLine("--- ${LocalDateTime.now()} ---")
            appendLine(erro.javaClass.name)
            appendLine(erro.message)
            appendLine(erro.stackTraceToString().take(6000))
        }
        Log.e(TAG, "Queda não tratada", erro)

        // Primeiro o interno: é o que a tela de Diagnóstico mostra.
        try {
            File(contexto.filesDir, ARQUIVO).appendText(texto)
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao gravar o log interno", e)
        }

        // Depois o externo, que é o único jeito de ler quando o app não abre.
        exportarParaDownloads(contexto, texto)
    }

    private fun exportarParaDownloads(contexto: Context, texto: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val valores = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "designacoes-erros.log")
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Designacoes JW")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = contexto.contentResolver
                // Apaga o anterior: o nome é fixo e o interestante é o último.
                resolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                    arrayOf("designacoes-erros.log")
                )
                val uri: Uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, valores)
                    ?: return
                resolver.openOutputStream(uri)?.use { out -> out.write(texto.toByteArray()) }
                valores.clear()
                valores.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, valores, null, null)
            } else {
                // Abaixo do 29 o Downloads é o diretório público, e gravar
                // depende de permissão que o app não pede de propósito.
                Log.w(TAG, "Android ${Build.VERSION.SDK_INT}: sem exportação para Downloads")
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Falha ao exportar para Downloads", e)
        }
    }

    /** O log interno, para a tela de Diagnóstico. */
    fun ler(contexto: Context): String = try {
        File(contexto.filesDir, ARQUIVO).takeIf { it.exists() }?.readText().orEmpty()
    } catch (e: Exception) {
        "Não consegui ler o log: ${e.message}"
    }
}