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
    val checkIntervalMinutes = integer("check_interval_minutes").default(1440)
    val triggerOnCreate = bool("trigger_on_create").default(false)
    val triggerOnUpdate = bool("trigger_on_update").default(false)
    val triggerOnClose = bool("trigger_on_close").default(false)
    val triggerOnOverdue = bool("trigger_on_overdue").default(false)
    val triggerOnOpen = bool("trigger_on_open").default(false)

    // NEW: hour of day (0-23) when reminders go out, and when the rule last ran (persisted)
    val sendHour = integer("send_hour").default(8)
    val lastRunAt = datetime("last_run_at").nullable()

    // NEW: send through the Outlook desktop app instead of SMTP
    val useOutlook = bool("use_outlook").default(false)

    // NEW: send through Microsoft Graph (signed-in Microsoft account)
    val useGraph = bool("use_graph").default(false)

    // NEW: day of week for weekly reminders (0=Sunday, 1=Monday, ..., 6=Saturday)
    val dayOfWeek = integer("day_of_week").default(0)
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

/** The single signed-in Microsoft account used by rules with useGraph = true. */
object GraphAccountTable : IntIdTable("GRAPH_ACCOUNT") {
    val tenantId = varchar("tenant_id", 100)
    val clientId = varchar("client_id", 100)
    val refreshToken = text("refresh_token").nullable()
    val accountEmail = varchar("account_email", 255).nullable()
}