package com.denis.habitlab.shared.domain.repository

import com.denis.habitlab.shared.domain.model.AppMetadata

interface AppMetadataRepository {
    fun getAppMetadata(): AppMetadata
}
