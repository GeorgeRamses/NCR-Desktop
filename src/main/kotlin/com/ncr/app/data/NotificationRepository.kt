package com.ncr.app.data

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime

data class NotificationRule(
    val id: Int? = null,
    val name: String,
    val smtpHost: String? = null,
    val smtpPort: Int? = null,
    val smtpUser: String? = null,
    val smtpPassword: String? = null,
    val useSsl: Boolean = false,
    val enabled: Boolean = true,
    val checkIntervalMinutes: Int = 360,
    val triggerOnCreate: Boolean = false,
    val triggerOnUpdate: Boolean = false,
    val triggerOnClose: Boolean = false,
    val triggerOnOverdue: Boolean = false,
    val triggerOnOpen: Boolean = false
)

data class RuleRecipient(
    val id: Int? = null,
    val ruleId: Int,
    val emailAddress: String
)

data class NotificationSentLog(
    val id: Int? = null,
    val ruleId: Int,
    val ncrId: Int,
    val sentAt: LocalDateTime,
    val triggerType: String
)

object NotificationRepository {

    // Rule CRUD
    fun getAllRules(): List<NotificationRule> = transaction {
        NotificationRulesTable.selectAll().orderBy(NotificationRulesTable.name to SortOrder.ASC).map { row ->
            NotificationRule(
                id = row[NotificationRulesTable.id].value,
                name = row[NotificationRulesTable.name],
                smtpHost = row[NotificationRulesTable.smtpHost],
                smtpPort = row[NotificationRulesTable.smtpPort],
                smtpUser = row[NotificationRulesTable.smtpUser],
                smtpPassword = row[NotificationRulesTable.smtpPassword],
                useSsl = row[NotificationRulesTable.useSsl],
                enabled = row[NotificationRulesTable.enabled],
                checkIntervalMinutes = row[NotificationRulesTable.checkIntervalMinutes],
                triggerOnCreate = row[NotificationRulesTable.triggerOnCreate],
                triggerOnUpdate = row[NotificationRulesTable.triggerOnUpdate],
                triggerOnClose = row[NotificationRulesTable.triggerOnClose],
                triggerOnOverdue = row[NotificationRulesTable.triggerOnOverdue],
                triggerOnOpen = row[NotificationRulesTable.triggerOnOpen]
            )
        }
    }

    fun getRuleById(id: Int): NotificationRule? = transaction {
        NotificationRulesTable.select { NotificationRulesTable.id eq id }.map { row ->
            NotificationRule(
                id = row[NotificationRulesTable.id].value,
                name = row[NotificationRulesTable.name],
                smtpHost = row[NotificationRulesTable.smtpHost],
                smtpPort = row[NotificationRulesTable.smtpPort],
                smtpUser = row[NotificationRulesTable.smtpUser],
                smtpPassword = row[NotificationRulesTable.smtpPassword],
                useSsl = row[NotificationRulesTable.useSsl],
                enabled = row[NotificationRulesTable.enabled],
                checkIntervalMinutes = row[NotificationRulesTable.checkIntervalMinutes],
                triggerOnCreate = row[NotificationRulesTable.triggerOnCreate],
                triggerOnUpdate = row[NotificationRulesTable.triggerOnUpdate],
                triggerOnClose = row[NotificationRulesTable.triggerOnClose],
                triggerOnOverdue = row[NotificationRulesTable.triggerOnOverdue],
                triggerOnOpen = row[NotificationRulesTable.triggerOnOpen]
            )
        }.firstOrNull()
    }

    fun saveRule(rule: NotificationRule): Int = transaction {
        if (rule.id == null) {
            val newId = NotificationRulesTable.insert {
                it[name] = rule.name
                it[smtpHost] = rule.smtpHost
                it[smtpPort] = rule.smtpPort
                it[smtpUser] = rule.smtpUser
                it[smtpPassword] = rule.smtpPassword
                it[useSsl] = rule.useSsl
                it[enabled] = rule.enabled
                it[checkIntervalMinutes] = rule.checkIntervalMinutes
                it[triggerOnCreate] = rule.triggerOnCreate
                it[triggerOnUpdate] = rule.triggerOnUpdate
                it[triggerOnClose] = rule.triggerOnClose
                it[triggerOnOverdue] = rule.triggerOnOverdue
                it[triggerOnOpen] = rule.triggerOnOpen
            } get NotificationRulesTable.id
            newId.value
        } else {
            NotificationRulesTable.update({ NotificationRulesTable.id eq rule.id!! }) {
                it[name] = rule.name
                it[smtpHost] = rule.smtpHost
                it[smtpPort] = rule.smtpPort
                it[smtpUser] = rule.smtpUser
                it[smtpPassword] = rule.smtpPassword
                it[useSsl] = rule.useSsl
                it[enabled] = rule.enabled
                it[checkIntervalMinutes] = rule.checkIntervalMinutes
                it[triggerOnCreate] = rule.triggerOnCreate
                it[triggerOnUpdate] = rule.triggerOnUpdate
                it[triggerOnClose] = rule.triggerOnClose
                it[triggerOnOverdue] = rule.triggerOnOverdue
                it[triggerOnOpen] = rule.triggerOnOpen
            }
            rule.id!!
        }
    }

    fun deleteRule(id: Int) = transaction {
        RuleRecipientsTable.deleteWhere { RuleRecipientsTable.ruleId eq EntityID(id, NotificationRulesTable) }
        NotificationSentLogTable.deleteWhere { NotificationSentLogTable.ruleId eq EntityID(id, NotificationRulesTable) }
        NotificationRulesTable.deleteWhere { NotificationRulesTable.id eq id }
    }

    // Recipient CRUD
    fun getRecipients(ruleId: Int): List<RuleRecipient> = transaction {
        RuleRecipientsTable.select { RuleRecipientsTable.ruleId eq EntityID(ruleId, RuleRecipientsTable) }.map { row ->
            RuleRecipient(
                id = row[RuleRecipientsTable.id].value,
                ruleId = row[RuleRecipientsTable.ruleId].value,
                emailAddress = row[RuleRecipientsTable.emailAddress]
            )
        }
    }

    fun addRecipient(ruleId: Int, email: String): Int = transaction {
        val newId = RuleRecipientsTable.insert {
            it[RuleRecipientsTable.ruleId] = EntityID(ruleId, RuleRecipientsTable)
            it[RuleRecipientsTable.emailAddress] = email
        } get RuleRecipientsTable.id
        newId.value
    }

    fun removeRecipient(id: Int) = transaction {
        RuleRecipientsTable.deleteWhere { RuleRecipientsTable.id eq EntityID(id, RuleRecipientsTable) }
    }

    fun getRecipientsByRuleId(ruleId: Int): List<String> = transaction {
        RuleRecipientsTable.select { RuleRecipientsTable.ruleId eq EntityID(ruleId, RuleRecipientsTable) }
            .map { it[RuleRecipientsTable.emailAddress] }
    }

    // Sent Log
    fun logSent(ruleId: Int, ncrId: Int, triggerType: String) = transaction {
        NotificationSentLogTable.insert {
            it[NotificationSentLogTable.ruleId] = EntityID(ruleId, NotificationSentLogTable)
            it[NotificationSentLogTable.ncrId] = EntityID(ncrId, NotificationSentLogTable)
            it[NotificationSentLogTable.sentAt] = LocalDateTime.now()
            it[NotificationSentLogTable.triggerType] = triggerType
        }
    }

    fun wasSent(ruleId: Int, ncrId: Int, triggerType: String): Boolean = transaction {
        val count = NotificationSentLogTable
            .select {
                (NotificationSentLogTable.ruleId eq EntityID(ruleId, NotificationSentLogTable)) and
                        (NotificationSentLogTable.ncrId eq EntityID(ncrId, NotificationSentLogTable)) and
                        (NotificationSentLogTable.triggerType eq triggerType)
            }
            .count()
        count > 0
    }

    // NCR queries for reminders
    fun getOverdueNcrs(): List<NcrModel> = transaction {
        val now = LocalDate.now()
        NcrDataTable
            .select {
                (NcrDataTable.targetCompletionDate less now) and
                        (NcrDataTable.actualCloseDate.isNull())
            }
            .map { NcrRepository.rowToModel(it) }
    }

    fun getOpenNcrs(): List<NcrModel> = transaction {
        NcrDataTable
            .select { NcrDataTable.actualCloseDate.isNull() }
            .map { NcrRepository.rowToModel(it) }
    }
}
