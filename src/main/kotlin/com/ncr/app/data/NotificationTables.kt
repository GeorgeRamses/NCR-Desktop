package com.ncr.app.data

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.javatime.datetime

object NotificationRulesTable : IntIdTable("NOTIFICATION_RULES") {
    val name = varchar("name", 255)
    val smtpHost = varchar("smtp_host", 255).nullable()
    val smtpPort = integer("smtp_port").nullable()
    val smtpUser = varchar("smtp_user", 255).nullable()
    val smtpPassword = varchar("smtp_password", 500).nullable()
    val useSsl = bool("use_ssl").default(false)
    val enabled = bool("enabled").default(true)
    val checkIntervalMinutes = integer("check_interval_minutes").default(360)
    val triggerOnCreate = bool("trigger_on_create").default(false)
    val triggerOnUpdate = bool("trigger_on_update").default(false)
    val triggerOnClose = bool("trigger_on_close").default(false)
    val triggerOnOverdue = bool("trigger_on_overdue").default(false)
    val triggerOnOpen = bool("trigger_on_open").default(false)
}

object RuleRecipientsTable : IntIdTable("RULE_RECIPIENTS") {
    val ruleId = reference("rule_id", NotificationRulesTable)
    val emailAddress = varchar("email_address", 255)
}

object NotificationSentLogTable : IntIdTable("NOTIFICATION_SENT_LOG") {
    val ruleId = reference("rule_id", NotificationRulesTable)
    val ncrId = reference("ncr_id", NcrDataTable)
    val sentAt = datetime("sent_at")
    val triggerType = varchar("trigger_type", 50)
}
