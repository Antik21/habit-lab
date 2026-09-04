package com.denis.habitlab.shared.domain.model

/** A runtime preference shared by common UI; persistence is deliberately outside the v1 slice. */
enum class ThemePreference {
    SYSTEM,
    LIGHT,
    DARK,
}
