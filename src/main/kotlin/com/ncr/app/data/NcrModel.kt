package com.ncr.app.data

import java.time.LocalDate

data class NcrModel(
    val id: Int? = null,
    val bay: String? = null,
    val wo: Int? = null,
    val date: LocalDate? = null,
    val jobNo: String? = null,
    val ncr: String? = null,
    val drawingPoNo: String? = null,
    val itemNo: String? = null,
    val descriptionOfNonConformity: String? = null,
    val reference: String? = null,
    val ncrRaisedBy: String? = null,
    val dispositionDate: LocalDate? = null,
    val recommendedDisposition: String? = null,
    val suggestedBy: String? = null,
    val targetCompletionDate: LocalDate? = null,
    val actualCloseDate: LocalDate? = null,
    
    val customerComplain: Boolean = false,
    val product: Boolean = false,
    val material: Boolean = false,
    val environmentalIssue: Boolean = false,
    val safetyIssue: Boolean = false,
    val qualityManagementIssue: Boolean = false,
    
    val responsibleDept: Int? = null,
    val carNo: String? = null,
    val methodUsed: String? = null,
    val rootCause: String? = null,
    val correctiveActionAgreed: String? = null
)
