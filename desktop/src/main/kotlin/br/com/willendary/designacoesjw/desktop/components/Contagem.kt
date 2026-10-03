package br.com.willendary.designacoesjw.desktop.components

/**
 * Concordância de número em português.
 *
 * Antes o desktop escrevia `"${n} irmão(s)"` em oito lugares: lista de irmãos,
 * privilégios, histórico, grupos e o diálogo de equidade. `"1 irmão(s)"` lê
 * como robô, e é o usuário quem lê.
 *
 * Fica em um arquivo só porque são duas telas que precisam (`Main.kt` e
 * `GroupsAndCleaningScreen`) — o desktop não tinha helper nenhum.
 *
 * ```kotlin
 * Text(contar(3, "irmão", "irmãos"))   // "3 irmãos"
 * Text(contar(1, "irmão", "irmãos"))   // "1 irmão"
 * ```
 */
internal fun contar(n: Int, singular: String, plural: String): String =
    "$n " + if (n == 1) singular else plural
