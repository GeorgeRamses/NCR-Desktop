package com.ncr.app.email

import com.ncr.app.data.*
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

object NotificationScheduler {

    private var scope: CoroutineScope? = null
    private var periodicJob: Job? = null
    // Track the last time each rule sent periodic reminders
    private val lastReminderTime = ConcurrentHashMap<Int, LocalDateTime>()

    fun start() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // Run a master loop every hour to re-evaluate rule intervals dynamically
        scope?.launch {
            while (isActive) {
                checkPeriodicReminders()
                delay(60 * 60 * 1000L) // Re-evaluate every hour
            }
        }
    }

    fun stop() {
        periodicJob?.cancel()
        scope?.cancel()
        scope = null
    }

    suspend fun notifyOnCreate(ncr: NcrModel) {
        sendNotificationsForTrigger(ncr, "CREATE", NotificationRulesTable.triggerOnCreate)
    }

    suspend fun notifyOnUpdate(ncr: NcrModel) {
        sendNotificationsForTrigger(ncr, "UPDATE", NotificationRulesTable.triggerOnUpdate)
    }

    suspend fun notifyOnClose(ncr: NcrModel) {
        sendNotificationsForTrigger(ncr, "CLOSE", NotificationRulesTable.triggerOnClose)
    }

    suspend fun checkPeriodicReminders() {
        val rules = NotificationRepository.getAllRules().filter { it.enabled }
        val now = LocalDateTime.now()

        // Check overdue NCRs
        val overdueRules = rules.filter { it.triggerOnOverdue }
        if (overdueRules.isNotEmpty()) {
            val overdueNcrs = NotificationRepository.getOverdueNcrs()
            if (overdueNcrs.isNotEmpty()) {
                for (rule in overdueRules) {
                    if (shouldSendReminder(rule, now)) {
                        sendReminderEmail(rule, overdueNcrs, "OVERDUE", "Overdue NCRs Reminder")
                    }
                }
            }
        }

        // Check open NCRs
        val openRules = rules.filter { it.triggerOnOpen }
        if (openRules.isNotEmpty()) {
            val openNcrs = NotificationRepository.getOpenNcrs()
            if (openNcrs.isNotEmpty()) {
                for (rule in openRules) {
                    if (shouldSendReminder(rule, now)) {
                        sendReminderEmail(rule, openNcrs, "OPEN", "Open NCRs Reminder")
                    }
                }
            }
        }
    }

    private fun shouldSendReminder(rule: NotificationRule, now: LocalDateTime): Boolean {
        if (rule.id == null) return true
        val lastTime = lastReminderTime[rule.id]
        if (lastTime == null) {
            // First time — send immediately
            lastReminderTime[rule.id] = now
            return true
        }
        val minutesSinceLast = java.time.Duration.between(lastTime, now).toMinutes()
        if (minutesSinceLast >= rule.checkIntervalMinutes.toLong()) {
            lastReminderTime[rule.id] = now
            return true
        }
        return false
    }

    private suspend fun sendNotificationsForTrigger(
        ncr: NcrModel,
        triggerType: String,
        triggerColumn: org.jetbrains.exposed.sql.Column<Boolean>
    ) {
        if (ncr.id == null) return

        val rules = NotificationRepository.getAllRules().filter { it.enabled }
        val matchingRules = when (triggerType) {
            "CREATE" -> rules.filter { it.triggerOnCreate }
            "UPDATE" -> rules.filter { it.triggerOnUpdate }
            "CLOSE" -> rules.filter { it.triggerOnClose }
            else -> emptyList()
        }

        for (rule in matchingRules) {
            if (NotificationRepository.wasSent(rule.id!!, ncr.id, triggerType)) continue

            val config = EmailService.createSmtpConfig(rule) ?: continue
            val recipients = NotificationRepository.getRecipientsByRuleId(rule.id!!)
            if (recipients.isEmpty()) continue

            val htmlBody = EmailService.buildNcrHtmlBody(listOf(ncr), "NCR Notification - $triggerType")
            val pdfFile = EmailService.generateRegisterPdf(listOf(ncr))

            val result = EmailService.sendEmail(config, recipients, "NCR Notification: ${ncr.ncr}", htmlBody, pdfFile)

            if (result.success) {
                NotificationRepository.logSent(rule.id!!, ncr.id, triggerType)
            }

            // Clean up temp PDF file
            pdfFile?.delete()
        }
    }

    private suspend fun sendReminderEmail(
        rule: NotificationRule,
        ncrs: List<NcrModel>,
        triggerType: String,
        subject: String
    ) {
        val config = EmailService.createSmtpConfig(rule) ?: return
        val recipients = NotificationRepository.getRecipientsByRuleId(rule.id!!)
        if (recipients.isEmpty()) return

        // Filter NCRs that haven't been sent for this rule
        val unsentNcrs = ncrs.filter { ncr ->
            ncr.id != null && !NotificationRepository.wasSent(rule.id!!, ncr.id, triggerType)
        }
        if (unsentNcrs.isEmpty()) return

        val htmlBody = EmailService.buildNcrHtmlBody(unsentNcrs, subject)
        val pdfFile = EmailService.generateRegisterPdf(unsentNcrs)

        val result = EmailService.sendEmail(config, recipients, subject, htmlBody, pdfFile)

        if (result.success) {
            for (ncr in unsentNcrs) {
                ncr.id?.let { NotificationRepository.logSent(rule.id!!, it, triggerType) }
            }
        }

        // Clean up temp PDF file
        pdfFile?.delete()
    }
}
