package com.ncr.app.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.date

object DeptsTable : IntIdTable("Depts") {
    val dName = varchar("DName", 255).nullable()
}

object NcrDataTable : IntIdTable("NCR_DATA") {
    val bay = varchar("Bay", 10).nullable()
    val wo = integer("WO").nullable()
    val date = date("date").nullable()
    val jobNo = varchar("job_no", 5).nullable()
    val ncr = varchar("ncr", 10).nullable()
    val drawingPoNo = varchar("drawing_PONO", 150).nullable()
    val itemNo = varchar("ITEM_NO", 200).nullable()
    val descriptionOfNonConformity = text("DESCRIPTION_OF_NON_CONFORMITY").nullable()
    val reference = varchar("REFERNCE", 150).nullable()
    val ncrRaisedBy = varchar("NCR_RAISED_BY", 100).nullable()
    val dispositionDate = date("DISPOSITION_DATE").nullable()
    val recommendedDisposition = text("RECOMMENDED_DESPOSITION").nullable()
    val suggestedBy = varchar("SUGGESTED_BY", 100).nullable()
    val targetCompletionDate = date("TARGET_COMPLETION_DATE").nullable()
    val actualCloseDate = date("ACTUAL_CLOSE_DATE").nullable()
    val customerComplain = bool("Customer_Complain").nullable().default(false)
    val product = bool("Product").nullable().default(false)
    val material = bool("Material").nullable().default(false)
    val environmentalIssue = bool("Environomintal_issue").nullable().default(false)
    val safetyIssue = bool("Safty_issue").nullable().default(false)
    val qualityManagementIssue = bool("Quality_Managment_issue").nullable().default(false)
    val responsibleDept = reference("Responsible_Dept", DeptsTable).nullable()
    val carNo = varchar("carNo", 255).nullable()
    val methodUsed = varchar("MethodUsed", 50).nullable()
    val rootCause = varchar("RootCause", 255).nullable()
    val correctiveActionAgreed = text("Corrective_Action_Agreed").nullable()
}
