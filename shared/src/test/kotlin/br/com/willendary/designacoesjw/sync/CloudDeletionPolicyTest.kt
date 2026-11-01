package br.com.willendary.designacoesjw.sync

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudDeletionPolicyTest {
    @Test fun removeSomenteOQueExistiaAntes() {
        assertEquals(setOf("1"), CloudDeletionPolicy.removedIds(setOf("1", "2"), setOf("2")))
    }

    @Test fun cacheAntigoVazioNaoApagaRegistrosDeOutroAparelho() {
        assertTrue(CloudDeletionPolicy.removedIds(emptySet(), setOf("3")).isEmpty())
    }

    @Test fun adicionarNaoExcluiNinguem() {
        assertTrue(CloudDeletionPolicy.removedIds(setOf("1"), setOf("1", "2")).isEmpty())
    }

    @Test fun removerUltimoRegistroEExclusaoExplicita() {
        assertEquals(setOf("1"), CloudDeletionPolicy.removedIds(setOf("1"), emptySet()))
    }
}
