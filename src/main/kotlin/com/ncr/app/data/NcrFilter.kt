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
    val projectNos: Set<String> = emptySet(),   // empty = all projects
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val statuses: Set<NcrStatus> = emptySet(),  // empty = all statuses (matches ANY selected)
    val deptIds: Set<Int> = emptySet(),         // empty = all departments
    val isArchived: Boolean = false
)