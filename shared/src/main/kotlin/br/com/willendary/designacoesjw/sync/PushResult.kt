package br.com.willendary.designacoesjw.sync

/**
 * Junta os resultados de um push por coleção e diz **o que não subiu**.
 *
 * Existe porque o defeito era silencioso, não ausente: cada `push*` do cliente
 * Firestore é um `runCatching`, então uma recusa do servidor (403 por falta de
 * permissão, 401 por token vencido, 400 por regra) não era exceção — era um
 * `Result` descartado. O "alteração enviada" era zerado e a barra dizia
 * "Sincronizado" com a congregação inteira só no aparelho.
 *
 * Push é por coleção e não transacional: um `Result` falhado não pode
 * desfazer os outros. Por isso a resposta é uma lista do que ficou de fora, e
 * não um booleano.
 *
 * @param resultados rótulo da coleção e o resultado da escrita dela.
 * @return os rótulos cujo resultado falhou, na ordem em que foram enviados.
 */
fun falhasDoPush(resultados: List<Pair<String, Result<Unit>>>): List<String> =
    resultados.filter { (_, resultado) -> resultado.isFailure }.map { (rotulo, _) -> rotulo }

/**
 * Mensagem para quando o push não subiu tudo.
 *
 * Diz o que ficou **só neste aparelho**, porque é isso que o usuário precisa
 * decidir: se pode fechar o programa ou se precisa sincronizar antes. A
 * alternativa — "FirebaseException: PERMISSION_DENIED" — não diz nada.
 */
fun mensagemDeFalhaNoPush(colecoes: List<String>): String =
    "Não consegui enviar ${colecoes.joinToString(", ")} para a nuvem. " +
        "A alteração ficou só neste computador — verifique sua conexão e tente sincronizar de novo."