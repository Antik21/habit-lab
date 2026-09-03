package com.denis.habitlab.shared.core.platform

/** A host-provided description that common code can consume without platform SDK dependencies. */
interface PlatformDescriptor {
    val name: String
}
