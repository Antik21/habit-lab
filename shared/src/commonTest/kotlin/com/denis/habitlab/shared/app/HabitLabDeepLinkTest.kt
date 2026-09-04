package com.denis.habitlab.shared.app

import com.denis.habitlab.shared.presentation.navigation.NavigationDialogResultDisplay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class HabitLabDeepLinkTest {
    @Test
    fun canonicalDeepLinksProduceTypedRoutes() {
        val daily = assertIs<AppDestination.Experiment>(
            HabitLabDeepLink.parse("habitlab://experiment/daily-movement"),
        )
        val sleep = assertIs<AppDestination.Experiment>(
            HabitLabDeepLink.parse("habitlab://experiment/sleep-routine"),
        )

        assertEquals(ExperimentId("daily-movement"), daily.experimentId)
        assertEquals(ExperimentId("sleep-routine"), sleep.experimentId)
    }

    @Test
    fun malformedDeepLinksAreRejected() {
        listOf(
            "habitlab://experiment/",
            "habitlab://experiment/daily-movement/extra",
            "habitlab://experiment/daily-movement?source=test",
            "habitlab://experiment/daily-movement#details",
            "https://experiment/daily-movement",
        ).forEach { rawUrl -> assertNull(HabitLabDeepLink.parse(rawUrl), rawUrl) }
    }

    @Test
    fun unknownDeepLinkIdIsRejected() {
        assertNull(HabitLabDeepLink.parse("habitlab://experiment/unknown"))
    }

    @Test
    fun externalIdsUseTheClosedAllowlist() {
        assertEquals(ExperimentId("daily-movement"), ExperimentId.fromExternalValue("daily-movement"))
        assertEquals(ExperimentId("sleep-routine"), ExperimentId.fromExternalValue("sleep-routine"))
        assertNull(ExperimentId.fromExternalValue("unknown"))
    }

    @Test
    fun repeatedUrlDeliveryCreatesASecondEvent() {
        val bridge = AppNavigationEventBridge()
        bridge.accept("habitlab://experiment/daily-movement")
        val first = bridge.latestEvent
        bridge.accept("habitlab://experiment/daily-movement")
        val second = bridge.latestEvent

        assertEquals(first?.rawUrl, second?.rawUrl)
        assertNotEquals(first?.id, second?.id)
    }

    @Test
    fun nullUrlIsStillDeliveredAsASafeFallbackEvent() {
        val bridge = AppNavigationEventBridge()
        bridge.accept(null)

        assertEquals(1L, bridge.latestEvent?.id)
        assertNull(bridge.latestEvent?.rawUrl)
    }

    @Test
    fun dialogResultIsVisibleOnlyToItsCallingExperiment() {
        val result = ExperimentDialogResult.Confirmed(ExperimentId("daily-movement"))

        assertEquals(
            NavigationDialogResultDisplay.Confirmed,
            result.displayFor(ExperimentId("daily-movement")),
        )
        assertNull(result.displayFor(ExperimentId("sleep-routine")))
    }

    @Test
    fun nativeBackRequestsRemainDistinctEvents() {
        val bridge = AppNavigationEventBridge()

        bridge.requestBack()
        val first = bridge.latestBackRequestId
        bridge.requestBack()

        assertEquals(1L, first)
        assertEquals(2L, bridge.latestBackRequestId)
    }
}
