package com.denis.habitlab.shared.app

/**
 * Pure destructive-dialog policy. It deliberately knows nothing about Compose, Nav3, or the
 * command implementation so common tests can cover the lock invariant without a navigation host.
 */
internal enum class ConfirmDeleteDismissalLock {
    UNLOCKED,
    LOCKED,
}

internal sealed interface ConfirmDeleteDismissalDecision {
    data object Ignore : ConfirmDeleteDismissalDecision
    data object ResolveCancelled : ConfirmDeleteDismissalDecision
}

internal object ConfirmDeleteDismissalPolicy {
    fun decide(lock: ConfirmDeleteDismissalLock): ConfirmDeleteDismissalDecision = when (lock) {
        ConfirmDeleteDismissalLock.LOCKED -> ConfirmDeleteDismissalDecision.Ignore
        ConfirmDeleteDismissalLock.UNLOCKED -> ConfirmDeleteDismissalDecision.ResolveCancelled
    }
}
