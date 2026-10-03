package br.com.willendary.designacoesjw.sync

import java.net.HttpURLConnection
import java.net.URL

/**
 * Busca o `changelog.json` do repositório.
 *
 * ## Por que `raw.githubusercontent.com` e não a release do GitHub
 *
 * A release é publicada **depois** do código. O arquivo está no repositório,
 * então as notas viajam **junto com a versão que as descreve** — e eu consigo
 * escrever a nota no mesmo commit que faz a mudança, sem segunda etapa.
 *
 * ## Por que não no Firestore
 *
 * Ver o KDoc de [Changelog]: semear Firestore exige credencial de administrador
 * e mexe em regra de produção, que já causou uma emergência uma vez.
 *
 * ## Chamar em thread de fundo
 *
 * Bloqueante por natureza. O app Android precisa de `Dispatchers.IO`; o desktop
 * já roda o fluxo de atualização fora da thread de UI.
 */
object ChangelogSource {

    /**
     * Branch `main` e caminho na raiz: o arquivo é versionado, não gerado.
     *
     * `?t=` com a versao evita cache velho: o GitHub segura JSON por um
     * tempo depois do push, e sem isso a tela mostra a lista de duas versões
     * atrás sem nenhum aviso de por quê.
     */
    const val BASE = "https://raw.githubusercontent.com/willendary/designacoes-jw/main/changelog.json"

    const val TIMEOUT_MS = 5000

    /**
     * Devolve `null` em qualquer falha: sem rede, 404, JSON quebrado.
     *
     * **Não é erro mostrar nada.** O changelog é conveniência; transformá-lo em
     * obrigação de rede seria criar um jeito novo de o app não abrir — que foi
     * exatamente o bug da 0.4.2.
     */
    fun buscar(versao: String = "", urlBase: String = BASE): Changelog? = try {
        val url = if (versao.isBlank()) urlBase else "$urlBase?t=$versao"
        val conexao = URL(url).openConnection() as HttpURLConnection
        conexao.requestMethod = "GET"
        conexao.connectTimeout = TIMEOUT_MS
        conexao.readTimeout = TIMEOUT_MS
        conexao.setRequestProperty("Accept", "application/json")

        val texto = if (conexao.responseCode in 200..299) {
            conexao.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
        conexao.disconnect()
        texto?.let { lerChangelog(it) }
    } catch (_: Exception) {
        null
    }
}