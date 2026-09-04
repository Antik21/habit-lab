package com.denis.habitlab.shared.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfirmDeleteDismissalPolicyTest {
    @Test
    fun lockedDeleteConfirmationIgnoresSystemOrEdgeBack() {
        assertEquals(
            ConfirmDeleteDismissalDecision.Ignore,
            ConfirmDeleteDismissalPolicy.decide(ConfirmDeleteDismissalLock.LOCKED),
        )
    }

    @Test
    fun unlockedDeleteConfirmationResolvesSystemOrEdgeBackAsCancelled() {
        assertEquals(
            ConfirmDeleteDismissalDecision.ResolveCancelled,
            ConfirmDeleteDismissalPolicy.decide(ConfirmDeleteDismissalLock.UNLOCKED),
        )
    }
}
