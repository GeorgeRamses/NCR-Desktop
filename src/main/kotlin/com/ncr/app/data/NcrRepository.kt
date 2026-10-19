package com.ncr.app.data

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate

object NcrRepository {

    fun rowToModel(it: ResultRow): NcrModel {
        return NcrModel(
            id = it[NcrDataTable.id].value,
            bay = it[NcrDataTable.bay],
            wo = it[NcrDataTable.wo],
            date = it[NcrDataTable.date],
            jobNo = it[NcrDataTable.jobNo],
            ncr = it[NcrDataTable.ncr],
            drawingPoNo = it[NcrDataTable.drawingPoNo],
            itemNo = it[NcrDataTable.itemNo],
            descriptionOfNonConformity = it[NcrDataTable.descriptionOfNonConformity],
            reference = it[NcrDataTable.reference],
            ncrRaisedBy = it[NcrDataTable.ncrRaisedBy],
            dispositionDate = it[NcrDataTable.dispositionDate],
            recommendedDisposition = it[NcrDataTable.recommendedDisposition],
            suggestedBy = it[NcrDataTable.suggestedBy],
            targetCompletionDate = it[NcrDataTable.targetCompletionDate],
            actualCloseDate = it[NcrDataTable.actualCloseDate],
            customerComplain = it[NcrDataTable.customerComplain] ?: false,
            product = it[NcrDataTable.product] ?: false,
            material = it[NcrDataTable.material] ?: false,
            environmentalIssue = it[NcrDataTable.environmentalIssue] ?: false,
            safetyIssue = it[NcrDataTable.safetyIssue] ?: false,
            qualityManagementIssue = it[NcrDataTable.qualityManagementIssue] ?: false,
            responsibleDept = it[NcrDataTable.responsibleDept]?.value,
            carNo = it[NcrDataTable.carNo],
            methodUsed = it[NcrDataTable.methodUsed],
            rootCause = it[NcrDataTable.rootCause],
            correctiveActionAgreed = it[NcrDataTable.correctiveActionAgreed],
            isArchived = it[NcrDataTable.isArchived]
        )
    }

    fun searchNcr(query: String): List<NcrModel> = transaction {
        if (query.isBlank()) {
            NcrDataTable.selectAll().where { NcrDataTable.isArchived eq false }
                .orderBy(NcrDataTable.date to SortOrder.DESC, NcrDataTable.ncr to SortOrder.DESC).limit(100).map { rowToModel(it) }
        } else {
            val lowerQuery = "%${query.lowercase()}%"
            NcrDataTable.selectAll()
                .where {
                    ((NcrDataTable.ncr.lowerCase() like lowerQuery) or
                            (NcrDataTable.jobNo.lowerCase() like lowerQuery)) and
                            (NcrDataTable.isArchived eq false)
                }
                .orderBy(NcrDataTable.date to SortOrder.DESC, NcrDataTable.ncr to SortOrder.DESC)
                .limit(100)
                .map { rowToModel(it) }
        }
    }

    fun getNcrById(id: Int): NcrModel? = transaction {
        NcrDataTable.selectAll().where { NcrDataTable.id eq id }.map { rowToModel(it) }.singleOrNull()
    }

    fun saveNcr(model: NcrModel): Int = transaction {
        if (model.id == null) {
            // Insert
            val insertedId = NcrDataTable.insertAndGetId {
                it[bay] = model.bay
                it[wo] = model.wo
                it[date] = model.date
                it[jobNo] = model.jobNo
                it[ncr] = model.ncr
                it[drawingPoNo] = model.drawingPoNo
                it[itemNo] = model.itemNo
                it[descriptionOfNonConformity] = model.descriptionOfNonConformity
                it[reference] = model.reference
                it[ncrRaisedBy] = model.ncrRaisedBy
                it[dispositionDate] = model.dispositionDate
                it[recommendedDisposition] = model.recommendedDisposition
                it[suggestedBy] = model.suggestedBy
                it[targetCompletionDate] = model.targetCompletionDate
                it[actualCloseDate] = model.actualCloseDate
                it[customerComplain] = model.customerComplain
                it[product] = model.product
                it[material] = model.material
                it[environmentalIssue] = model.environmentalIssue
                it[safetyIssue] = model.safetyIssue
                it[qualityManagementIssue] = model.qualityManagementIssue
                if (model.responsibleDept != null) {
                    it[responsibleDept] = model.responsibleDept
                }
                it[carNo] = model.carNo
                it[methodUsed] = model.methodUsed
                it[rootCause] = model.rootCause
                it[correctiveActionAgreed] = model.correctiveActionAgreed
                it[isArchived] = model.isArchived
            }
            insertedId.value
        } else {
            // Update
            NcrDataTable.update({ NcrDataTable.id eq model.id }) {
                it[bay] = model.bay
                it[wo] = model.wo
                it[date] = model.date
                it[jobNo] = model.jobNo
                it[ncr] = model.ncr
                it[drawingPoNo] = model.drawingPoNo
                it[itemNo] = model.itemNo
                it[descriptionOfNonConformity] = model.descriptionOfNonConformity
                it[reference] = model.reference
                it[ncrRaisedBy] = model.ncrRaisedBy
                it[dispositionDate] = model.dispositionDate
                it[recommendedDisposition] = model.recommendedDisposition
                it[suggestedBy] = model.suggestedBy
                it[targetCompletionDate] = model.targetCompletionDate
                it[actualCloseDate] = model.actualCloseDate
                it[customerComplain] = model.customerComplain
                it[product] = model.product
                it[material] = model.material
                it[environmentalIssue] = model.environmentalIssue
                it[safetyIssue] = model.safetyIssue
                it[qualityManagementIssue] = model.qualityManagementIssue
                if (model.responsibleDept != null) {
                    it[responsibleDept] = model.responsibleDept
                }
                it[carNo] = model.carNo
                it[methodUsed] = model.methodUsed
                it[rootCause] = model.rootCause
                it[correctiveActionAgreed] = model.correctiveActionAgreed
                it[isArchived] = model.isArchived
            }
            model.id
        }
    }

    fun searchNcrFiltered(filter: NcrFilter): List<NcrModel> = transaction {
        var query = NcrDataTable.select { NcrDataTable.isArchived eq filter.isArchived }

        // Text search
        if (filter.searchQuery.isNotBlank()) {
            val lowerQuery = "%${filter.searchQuery.lowercase()}%"
            query = query.andWhere {
                (NcrDataTable.ncr.lowerCase() like lowerQuery) or
                        (NcrDataTable.jobNo.lowerCase() like lowerQuery)
            }
        }

        // Project filter
        if (!filter.projectNo.isNullOrBlank()) {
            query = query.andWhere {
                NcrDataTable.jobNo eq filter.projectNo
            }
        }

        // Date range
        if (filter.dateFrom != null) {
            query = query.andWhere {
                NcrDataTable.date greaterEq filter.dateFrom
            }
        }
        if (filter.dateTo != null) {
            query = query.andWhere {
                NcrDataTable.date lessEq filter.dateTo
            }
        }

        // Status filter
        when (filter.status) {
            NcrStatus.OPEN -> query = query.andWhere {
                NcrDataTable.actualCloseDate.isNull()
            }
            NcrStatus.CLOSED -> query = query.andWhere {
                NcrDataTable.actualCloseDate.isNotNull()
            }
            NcrStatus.OVERDUE -> query = query.andWhere {
                NcrDataTable.actualCloseDate.isNull() and (NcrDataTable.targetCompletionDate less LocalDate.now())
            }
            NcrStatus.UPCOMING -> query = query.andWhere {
                NcrDataTable.actualCloseDate.isNull() and (NcrDataTable.targetCompletionDate greaterEq LocalDate.now())
            }
            NcrStatus.ALL -> { /* no filter */ }
        }

        // Department filter
        if (filter.deptId != null) {
            query = query.andWhere {
                NcrDataTable.responsibleDept eq filter.deptId
            }
        }

        query.orderBy(NcrDataTable.date to SortOrder.DESC, NcrDataTable.ncr to SortOrder.DESC).map { rowToModel(it) }
    }

    fun getDistinctProjects(): List<String> = transaction {
        NcrDataTable
            .select(NcrDataTable.jobNo)
            .where { NcrDataTable.jobNo.isNotNull() }
            .withDistinct()
            .mapNotNull { it[NcrDataTable.jobNo] }
            .filter { it.isNotBlank() }
            .sorted()
    }

    fun getAllDepts(): List<Pair<Int, String>> = transaction {
        DeptsTable.selectAll().map {
            it[DeptsTable.id].value to (it[DeptsTable.dName] ?: "")
        }
    }

    fun getDeptMap(): Map<Int, String> = transaction {
        DeptsTable.selectAll().associate {
            it[DeptsTable.id].value to (it[DeptsTable.dName] ?: "")
        }
    }

    fun generateNextNcrNo(jobNo: String, date: LocalDate = LocalDate.now()): String = transaction {
        val maxNcr = NcrDataTable
            .select(NcrDataTable.ncr)
            .where { NcrDataTable.jobNo eq jobNo }
            .orderBy(NcrDataTable.ncr, SortOrder.DESC)
            .limit(1)
            .map { it[NcrDataTable.ncr] }
            .firstOrNull()

        val yearStr = (date.year % 100).toString().padStart(2, '0')
        var counter = 1

        if (!maxNcr.isNullOrEmpty()) {
            val parts = maxNcr.split("/")
            if (parts.size >= 3) {
                val maxYearStr = parts[1]
                val maxCounterStr = parts[2]
                if (maxYearStr == yearStr) {
                    counter = (maxCounterStr.toIntOrNull() ?: 0) + 1
                }
            }
        }

        val counterStr = counter.toString().padStart(3, '0')
        "$jobNo/$yearStr/$counterStr"
    }

    fun archiveNcr(id: Int, archive: Boolean): Boolean = transaction {
        val count = NcrDataTable.update({ NcrDataTable.id eq id }) {
            it[isArchived] = archive
        }
        count > 0
    }
}