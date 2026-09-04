package com.denis.habitlab.shared.domain.interactor

import kotlinx.datetime.LocalDate

/** Domain boundary for selecting a date without allowing presentation to read a platform clock. */
interface CurrentLocalDateSource {
    fun current(): LocalDate
}

class GetCurrentLocalDate(
    private val source: CurrentLocalDateSource,
) {
    operator fun invoke(): LocalDate = source.current()
}
