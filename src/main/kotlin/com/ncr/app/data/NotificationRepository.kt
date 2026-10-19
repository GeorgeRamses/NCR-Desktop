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
    /** Reminder repeat interval: 1440 daily, 10080 weekly, 20160 every 2 weeks, 43200 monthly */
    val checkIntervalMinutes: Int = 1440,
    val triggerOnCreate: Boolean = false,
    val triggerOnUpdate: Boolean = false,
    val triggerOnClose: Boolean = false,
    val triggerOnOverdue: Boolean = false,
    val triggerOnOpen: Boolean = false,
    /** Hour of day (0-23) reminders are sent */
    val sendHour: Int = 8,
    /** Set by the scheduler only (read-only from the UI) */
    val lastRunAt: LocalDateTime? = null,
    /** Send via the Outlook desktop app on this PC instead of SMTP */
    val useOutlook: Boolean = false,
    /** Send through Microsoft Graph (signed-in Microsoft account) */
    val useGraph: Boolean = false,
    /** Day of week for weekly reminders: 0=Sunday, 1=Monday, ..., 6=Saturday */
    val dayOfWeek: Int = 0
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

    private fun rowToRule(row: ResultRow) = NotificationRule(
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
        triggerOnOpen = row[NotificationRulesTable.triggerOnOpen],
        sendHour = row[NotificationRulesTable.sendHour],
        lastRunAt = row[NotificationRulesTable.lastRunAt],
        useOutlook = row[NotificationRulesTable.useOutlook],
        useGraph = row[NotificationRulesTable.useGraph],
        dayOfWeek = row[NotificationRulesTable.dayOfWeek]
    )

    // Rule CRUD
    fun getAllRules(): List<NotificationRule> = transaction {
        NotificationRulesTable.selectAll()
            .orderBy(NotificationRulesTable.name to SortOrder.ASC)
            .map { rowToRule(it) }
    }

    fun getRuleById(id: Int): NotificationRule? = transaction {
        NotificationRulesTable.selectAll().where { NotificationRulesTable.id eq id }
            .map { rowToRule(it) }.firstOrNull()
    }

    /** lastRunAt is intentionally NOT written here — only markRuleRun() changes it. */
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
                it[sendHour] = rule.sendHour
                it[useOutlook] = rule.useOutlook
                it[useGraph] = rule.useGraph
                it[dayOfWeek] = rule.dayOfWeek
            } get NotificationRulesTable.id
            newId.value
        } else {
            NotificationRulesTable.update({ NotificationRulesTable.id eq rule.id }) {
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
                it[sendHour] = rule.sendHour
                it[useOutlook] = rule.useOutlook
                it[useGraph] = rule.useGraph
                it[dayOfWeek] = rule.dayOfWeek
            }
            rule.id
        }
    }

    fun markRuleRun(id: Int, at: LocalDateTime?) = transaction {
        NotificationRulesTable.update({ NotificationRulesTable.id eq id }) {
            it[lastRunAt] = at
        }
    }

    fun deleteRule(id: Int) = transaction {
        RuleRecipientsTable.deleteWhere { RuleRecipientsTable.ruleId eq EntityID(id, NotificationRulesTable) }
        NotificationSentLogTable.deleteWhere { NotificationSentLogTable.ruleId eq EntityID(id, NotificationRulesTable) }
        NotificationRulesTable.deleteWhere { NotificationRulesTable.id eq id }
    }

    // Recipient CRUD
    fun getRecipients(ruleId: Int): List<RuleRecipient> = transaction {
        RuleRecipientsTable.selectAll()
            .where { RuleRecipientsTable.ruleId eq EntityID(ruleId, NotificationRulesTable) }
            .map { row ->
                RuleRecipient(
                    id = row[RuleRecipientsTable.id].value,
                    ruleId = row[RuleRecipientsTable.ruleId].value,
                    emailAddress = row[RuleRecipientsTable.emailAddress]
                )
            }
    }

    fun addRecipient(ruleId: Int, email: String): Int = transaction {
        val newId = RuleRecipientsTable.insert {
            it[RuleRecipientsTable.ruleId] = EntityID(ruleId, NotificationRulesTable)
            it[RuleRecipientsTable.emailAddress] = email
        } get RuleRecipientsTable.id
        newId.value
    }

    fun removeRecipient(id: Int) = transaction {
        RuleRecipientsTable.deleteWhere { RuleRecipientsTable.id eq EntityID(id, RuleRecipientsTable) }
    }

    fun getRecipientsByRuleId(ruleId: Int): List<String> = transaction {
        RuleRecipientsTable.selectAll()
            .where { RuleRecipientsTable.ruleId eq EntityID(ruleId, NotificationRulesTable) }
            .map { it[RuleRecipientsTable.emailAddress] }
    }

    // Sent log (used to de-duplicate CREATE / CLOSE events and as an audit trail)
    fun logSent(ruleId: Int, ncrId: Int, triggerType: String) = transaction {
        NotificationSentLogTable.insert {
            it[NotificationSentLogTable.ruleId] = EntityID(ruleId, NotificationRulesTable)
            it[NotificationSentLogTable.ncrId] = EntityID(ncrId, NcrDataTable)   // was wrong table before
            it[NotificationSentLogTable.sentAt] = LocalDateTime.now()
            it[NotificationSentLogTable.triggerType] = triggerType
        }
    }

    fun wasSent(ruleId: Int, ncrId: Int, triggerType: String): Boolean = transaction {
        NotificationSentLogTable.selectAll()
            .where {
                (NotificationSentLogTable.ruleId eq EntityID(ruleId, NotificationRulesTable)) and
                        (NotificationSentLogTable.ncrId eq EntityID(ncrId, NcrDataTable)) and
                        (NotificationSentLogTable.triggerType eq triggerType)
            }
            .count() > 0
    }

    // NCR queries for reminders
    fun getOverdueNcrs(): List<NcrModel> = transaction {
        val now = LocalDate.now()
        NcrDataTable.selectAll()
            .where {
                (NcrDataTable.targetCompletionDate less now) and
                        (NcrDataTable.actualCloseDate.isNull()) and
                        (NcrDataTable.isArchived eq false)
            }
            .map { NcrRepository.rowToModel(it) }
    }

    fun getOpenNcrs(): List<NcrModel> = transaction {
        NcrDataTable.selectAll()
            .where { NcrDataTable.actualCloseDate.isNull() and (NcrDataTable.isArchived eq false) }
            .map { NcrRepository.rowToModel(it) }
    }
}