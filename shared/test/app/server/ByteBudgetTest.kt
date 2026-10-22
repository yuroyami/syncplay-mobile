package app.server

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ByteBudgetTest {

    @Test
    fun takingPastTheLimitIsRefused() {
        val budget = ByteBudget(limit = 100)
        assertTrue(budget.take(60))
        assertTrue(budget.take(40))
        assertFalse(budget.take(1), "101 bytes pass a 100-byte budget")
    }

    @Test
    fun handledBytesFreeTheBudgetAgain() {
        val budget = ByteBudget(limit = 100)
        assertTrue(budget.take(100))
        budget.give(100)
        assertTrue(budget.take(100), "the budget holds what is waiting, not what ever passed")
    }
}
