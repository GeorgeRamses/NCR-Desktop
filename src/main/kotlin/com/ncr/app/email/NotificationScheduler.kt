package com.ncr.app.email

import com.ncr.app.data.*
import kotlinx.coroutines.*
import java.time.LocalDateTime

object NotificationScheduler {

    data class RunResult(val ok: Boolean, val message: String, val sent: Int = 0)

    private var scope: CoroutineScope? = null
    private const val TICK_MS = 15 * 60 * 1000L // check which rules are due every 15 min

    /** Safe to call more than once — only one loop is ever started. */
    @Synchronized
    fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        s.launch {
            while (isActive) {
                try {
                    checkPeriodicReminders()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                delay(TICK_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        scope?.cancel()
        scope = null
    }

    /**
     * Runs [block] on the scheduler's own scope, so it is NOT cancelled when a screen
     * leaves the composition (the Save button calls onBack() right after).
     */
    fun dispatch(block: suspend NotificationScheduler.() -> Unit) {
        val s = scope ?: run { start(); scope!! }
        s.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // ---------- Event triggers ----------
    suspend fun notifyOnCreate(ncr: NcrModel) = sendEventEmail(ncr, "CREATE")
    suspend fun notifyOnUpdate(ncr: NcrModel) = sendEventEmail(ncr, "UPDATE")
    suspend fun notifyOnClose(ncr: NcrModel) = sendEventEmail(ncr, "CLOSE")

    private suspend fun sendEventEmail(ncr: NcrModel, type: String) = withContext(Dispatchers.IO) {
        val ncrId = ncr.id ?: return@withContext
        val rules = NotificationRepository.getAllRules().filter {
            it.enabled && when (type) {
                "CREATE" -> it.triggerOnCreate
                "UPDATE" -> it.triggerOnUpdate
                "CLOSE" -> it.triggerOnClose
                else -> false
            }
        }
        if (rules.isEmpty()) return@withContext

        val deptMap = NcrRepository.getDeptMap()
        val title = when (type) {
            "CREATE" -> "New NCR created"
            "UPDATE" -> "NCR updated"
            else -> "NCR closed"
        }

        for (rule in rules) {
            val ruleId = rule.id ?: continue
            // Dedupe only CREATE / CLOSE. An NCR can legitimately be updated many times.
            if (type != "UPDATE" && NotificationRepository.wasSent(ruleId, ncrId, type)) continue

            val recipients = NotificationRepository.getRecipientsByRuleId(ruleId)
            if (recipients.isEmpty()) continue

            val html = EmailService.buildNcrHtmlBody(listOf(ncr), title, deptMap)
            val pdf = EmailService.generateRegisterPdf(listOf(ncr))
            try {
                val result = EmailService.sendForRule(rule, recipients, "$title: ${ncr.ncr}", html, pdf)
                if (result.success) {
                    runCatching { NotificationRepository.logSent(ruleId, ncrId, type) }
                } else {
                    System.err.println("Notification '${rule.name}' failed: ${result.message}")
                }
            } finally {
                pdf?.delete()
            }
        }
    }

    // ---------- Periodic reminders ----------
    suspend fun checkPeriodicReminders() {
        val now = LocalDateTime.now()
        val dueRules = NotificationRepository.getAllRules().filter {
            it.enabled && (it.triggerOnOverdue || it.triggerOnOpen) && isDue(it, now)
        }
        for (rule in dueRules) {
            val ruleId = rule.id ?: continue

            // 1) RECORD FIRST. If the "sent" time cannot be saved, do not send at all -
            //    otherwise the mail would go out again every time the app starts.
            try {
                NotificationRepository.markRuleRun(ruleId, now)
            } catch (e: Exception) {
                System.err.println("Cannot save last-sent time for '${rule.name}', skipping: ${e.message}")
                continue
            }

            // 2) Send once.
            val result = try {
                runReminderRule(rule)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RunResult(false, e.message ?: e.javaClass.simpleName)
            }

            // 3) Only if NOTHING was sent (e.g. sign-in/SMTP problem) give the slot back so it is retried.
            if (!result.ok && result.sent == 0) {
                System.err.println("Reminder '${rule.name}' failed: ${result.message}")
                runCatching { NotificationRepository.markRuleRun(ruleId, rule.lastRunAt) }
            }
        }
    }

    /** Daily / weekly / every 2 weeks / monthly, at rule.sendHour. Catches up if the app was closed. */
    internal fun isDue(rule: NotificationRule, now: LocalDateTime): Boolean {
        if (now.hour < rule.sendHour) return false
        val last = rule.lastRunAt ?: return true
        val lastDate = last.toLocalDate()
        val nextDate = if (rule.checkIntervalMinutes >= 43200) {
            lastDate.plusMonths(1)
        } else {
            lastDate.plusDays(maxOf(1, rule.checkIntervalMinutes / 1440).toLong())
        }
        return !now.toLocalDate().isBefore(nextDate)
    }

    /** Used by the "Send now" button in Settings — does not touch the schedule. */
    suspend fun runRuleNow(rule: NotificationRule): String = withContext(Dispatchers.IO) {
        if (!rule.triggerOnOverdue && !rule.triggerOnOpen) {
            "Send now only works for reminder rules (enable 'Overdue' or 'Open' reminder)."
        } else {
            val result = runReminderRule(rule)
            // A manual send counts as "sent": remember it so the app does not send the same reminder again
            if (result.sent > 0) runCatching { NotificationRepository.markRuleRun(rule.id!!, LocalDateTime.now()) }
            result.message
        }
    }

    private suspend fun runReminderRule(rule: NotificationRule): RunResult {
        val ruleId = rule.id ?: return RunResult(false, "Rule not saved")
        val recipients = NotificationRepository.getRecipientsByRuleId(ruleId)
        if (recipients.isEmpty()) return RunResult(false, "Rule has no recipients")

        val deptMap = NcrRepository.getDeptMap()
        val jobs = buildList {
            if (rule.triggerOnOverdue) add(Triple("OVERDUE", "Overdue NCRs Reminder", NotificationRepository.getOverdueNcrs()))
            if (rule.triggerOnOpen) add(Triple("OPEN", "Open NCRs Reminder", NotificationRepository.getOpenNcrs()))
        }

        var sent = 0
        for ((type, subject, ncrs) in jobs) {
            if (ncrs.isEmpty()) continue
            // Reminders always include ALL matching NCRs every time (no "already sent" filter)
            val sorted = ncrs.sortedWith(compareByDescending<NcrModel> { it.date }.thenByDescending { it.id })
            val html = EmailService.buildNcrHtmlBody(sorted, subject, deptMap)
            val pdf = EmailService.generateRegisterPdf(sorted)
            try {
                val res = EmailService.sendForRule(rule, recipients, "$subject (${sorted.size})", html, pdf)
                if (!res.success) return RunResult(false, res.message, sent)
                // audit log only: a failure here must never cause a second email
                sorted.forEach { n -> n.id?.let { runCatching { NotificationRepository.logSent(ruleId, it, type) } } }
                sent++
            } finally {
                pdf?.delete()
            }
        }
        return RunResult(
            true,
            if (sent == 0) "Nothing to report - no matching NCRs"
            else "Sent $sent email(s) to ${recipients.size} recipient(s)",
            sent
        )
    }
}