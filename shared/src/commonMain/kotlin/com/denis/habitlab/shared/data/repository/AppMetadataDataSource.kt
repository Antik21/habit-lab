package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.core.platform.PlatformDescriptor
import com.denis.habitlab.shared.data.model.AppMetadataEntity

internal interface AppMetadataDataSource {
    fun loadAppMetadata(): AppMetadataEntity
}

internal class PlatformAppMetadataDataSource(
    private val platformDescriptor: PlatformDescriptor,
) : AppMetadataDataSource {
    override fun loadAppMetadata(): AppMetadataEntity = AppMetadataEntity(
        displayName = "Habit Lab",
        platformName = platformDescriptor.name,
    )
}
