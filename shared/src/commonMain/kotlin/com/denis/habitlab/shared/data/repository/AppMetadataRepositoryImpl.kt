package com.denis.habitlab.shared.data.repository

import com.denis.habitlab.shared.data.model.AppMetadataEntity
import com.denis.habitlab.shared.domain.model.AppMetadata
import com.denis.habitlab.shared.domain.repository.AppMetadataRepository

internal class AppMetadataRepositoryImpl(
    private val dataSource: AppMetadataDataSource,
) : AppMetadataRepository {
    override fun getAppMetadata(): AppMetadata = dataSource.loadAppMetadata().toDomain()
}

private fun AppMetadataEntity.toDomain(): AppMetadata = AppMetadata(
    name = displayName,
    platformName = platformName,
)
