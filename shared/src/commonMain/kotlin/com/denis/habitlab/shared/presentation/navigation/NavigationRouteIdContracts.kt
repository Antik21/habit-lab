package com.denis.habitlab.shared.presentation.navigation

/**
 * Presentation's route-ID contract. The underlying IDs remain pure core value objects so domain
 * observers and data projections can share them; the app shell accesses the contract through its
 * allowed presentation dependency rather than reaching into core.
 */
typealias ExperimentId = com.denis.habitlab.shared.core.navigation.ExperimentId
typealias FlowId = com.denis.habitlab.shared.core.navigation.FlowId
