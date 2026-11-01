package br.com.willendary.designacoesjw.sync

/**
 * Uma lista local é somente uma visão parcial da nuvem.
 *
 * Nunca remover do servidor um ID que não estava no cache antes da edição:
 * o registro pode ter sido criado por outro aparelho no intervalo.
 */
object CloudDeletionPolicy {
    fun removedIds(previousIds: Set<String>, currentIds: Set<String>): Set<String> =
        previousIds - currentIds
}
