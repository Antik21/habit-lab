package com.denis.habitlab.shared.app

import com.denis.habitlab.shared.presentation.navigation.ExperimentId as PresentationExperimentId
import com.denis.habitlab.shared.presentation.navigation.FlowId as PresentationFlowId

/**
 * App-route façade retained for route/deep-link callers. The shell consumes these IDs through the
 * presentation contract, preserving the app → presentation boundary while core remains shared by
 * the domain observer and data projection.
 */
typealias ExperimentId = PresentationExperimentId
typealias FlowId = PresentationFlowId
