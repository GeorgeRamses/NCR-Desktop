package com.ncr.app.data

import java.time.LocalDate

enum class NcrStatus {
    ALL,
    OPEN,
    CLOSED,
    OVERDUE,
    UPCOMING
}

data class NcrFilter(
    val searchQuery: String = "",
    val projectNo: String? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val status: NcrStatus = NcrStatus.ALL,
    val deptId: Int? = null,
    val isArchived: Boolean = false
)