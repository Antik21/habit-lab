package com.denis.habitlab.shared.domain.interactor

import com.denis.habitlab.shared.domain.model.AppMetadata
import com.denis.habitlab.shared.domain.repository.AppMetadataRepository

class GetAppMetadata(
    private val repository: AppMetadataRepository,
) {
    operator fun invoke(): AppMetadata = repository.getAppMetadata()
}
