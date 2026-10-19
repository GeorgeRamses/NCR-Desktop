package com.ncr.app.email

import com.ncr.app.data.NcrModel
import com.ncr.app.data.NcrRepository
import com.ncr.app.data.NotificationRule
import com.ncr.app.pdf.PdfExporter
import jakarta.mail.Message
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.util.Date
import java.util.Properties

object EmailService {

    data class SmtpConfig(
        val host: String,
        val port: Int,
        val user: String,       // also used as the From address
        val password: String,   // may be blank for an SMTP relay (no authentication)
        val useSsl: Boolean
    )

    data class SendResult(val success: Boolean, val message: String)

    fun createSmtpConfig(rule: NotificationRule): SmtpConfig? {
        val host = rule.smtpHost?.takeIf { it.isNotBlank() } ?: return null
        val port = rule.smtpPort ?: return null
        val user = rule.smtpUser?.takeIf { it.isNotBlank() } ?: return null
        return SmtpConfig(host, port, user, rule.smtpPassword ?: "", rule.useSsl)
    }

    /** Blank password = "no login", which is only valid for an SMTP relay on port 25. */
    private fun passwordProblem(config: SmtpConfig): String? =
        if (config.password.isBlank() && config.port != 25)
            "The password for this rule is empty. Edit the rule and enter the App Password " +
                    "(a blank password is only allowed for an SMTP relay on port 25)."
        else null

    private fun newSession(config: SmtpConfig): Session {
        val props = createSmtpProperties(config)
        return if (config.password.isBlank()) {
            Session.getInstance(props)
        } else {
            Session.getInstance(props, object : jakarta.mail.Authenticator() {
                override fun getPasswordAuthentication() =
                    jakarta.mail.PasswordAuthentication(config.user, config.password)
            })
        }
    }

    /** Every nested cause, e.g. "MessagingException: Can't send command... -> SocketException: Connection reset" */
    private fun causeChain(e: Throwable): String =
        generateSequence(e) { it.cause?.takeIf { c -> c !== it } }
            .joinToString(" -> ") { "${it.javaClass.simpleName}: ${it.message?.lineSequence()?.firstOrNull() ?: ""}" }

    private fun describeError(e: Exception): String {
        val msg = e.message ?: e.toString()
        return when {
            msg.contains("5.7.57") ->
                "The server received no login (5.7.57). Check that the rule has the App Password saved, " +
                        "and that SMTP AUTH is enabled for this mailbox in Microsoft 365."
            msg.contains("5.7.30") || msg.contains("Basic authentication is not supported", true) ->
                "Microsoft rejected Basic authentication for SMTP (5.7.30). Use an SMTP relay connector (no password) or OAuth2."
            msg.contains("SmtpClientAuthentication is disabled", true) || msg.contains("5.7.139") ->
                "SMTP AUTH is disabled for this mailbox/tenant. Ask your Microsoft 365 admin to enable it or use an SMTP relay."
            e is jakarta.mail.AuthenticationFailedException ->
                "Authentication failed: check the email address / App Password."
            msg.contains("Connection refused", true) || msg.contains("connect", true) && msg.contains("timed out", true) ->
                "Cannot reach ${msg.take(120)} - check host/port and firewall."
            msg.contains("SSL", true) || msg.contains("TLS", true) ->
                "SSL/TLS error: port 587 = STARTTLS (SSL off), port 465 = SSL on. ($msg)"
            else -> msg
        }
    }

    suspend fun testConnection(config: SmtpConfig): SendResult = withContext(Dispatchers.IO) {
        passwordProblem(config)?.let { return@withContext SendResult(false, it) }
        try {
            val transport = newSession(config).getTransport("smtp")
            if (config.password.isBlank()) transport.connect()
            else transport.connect(config.host, config.port, config.user, config.password)
            transport.close()
            SendResult(true, "Connection successful")
        } catch (e: Exception) {
            SendResult(false, "Connection failed: ${describeError(e)}")
        }
    }

    suspend fun sendEmail(
        config: SmtpConfig,
        recipients: List<String>,
        subject: String,
        htmlBody: String,
        pdfFile: File? = null
    ): SendResult = withContext(Dispatchers.IO) {
        try {
            if (recipients.isEmpty()) return@withContext SendResult(false, "No recipients")
            passwordProblem(config)?.let { return@withContext SendResult(false, it) }

            val session = newSession(config)
            val message = MimeMessage(session)
            message.setFrom(InternetAddress(config.user))
            message.setRecipients(Message.RecipientType.TO, recipients.map { InternetAddress(it.trim()) }.toTypedArray())
            message.subject = subject
            message.sentDate = Date()

            val multipart = MimeMultipart()
            val htmlPart = MimeBodyPart()
            htmlPart.setContent(htmlBody, "text/html; charset=utf-8")
            multipart.addBodyPart(htmlPart)

            if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
                val attachment = MimeBodyPart()
                attachment.attachFile(pdfFile)
                attachment.fileName = "NCR_Register.pdf"
                multipart.addBodyPart(attachment)
            }

            message.setContent(multipart)
            // Same explicit connect/login path as testConnection() (instead of Transport.send)
            val transport = session.getTransport("smtp")
            try {
                if (config.password.isBlank()) transport.connect()
                else transport.connect(config.host, config.port, config.user, config.password)
                transport.sendMessage(message, message.allRecipients)
            } finally {
                try { transport.close() } catch (_: Exception) {}
            }
            SendResult(true, "Email sent successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            SendResult(false, "Failed to send email: ${describeError(e)}\n\nDetails: ${causeChain(e)}")
        }
    }

    /** Picks Outlook (desktop app) or SMTP depending on the rule. */
    suspend fun sendForRule(
        rule: NotificationRule,
        recipients: List<String>,
        subject: String,
        htmlBody: String,
        pdfFile: File? = null
    ): SendResult {
        if (rule.useGraph) return GraphMailer.sendMail(recipients, subject, htmlBody, pdfFile)
        if (rule.useOutlook) return sendViaOutlook(recipients, subject, htmlBody, pdfFile)
        val config = createSmtpConfig(rule)
            ?: return SendResult(false, "SMTP settings incomplete (host, port, email address)")
        return sendEmail(config, recipients, subject, htmlBody, pdfFile)
    }

    // '~' is replaced by '$' so the PowerShell script can be written without escaping
    private val OUTLOOK_SCRIPT = """
        param(~To, ~Subject, ~BodyFile, ~Attachment)
        ~ErrorActionPreference = 'Stop'
        try {
            ~ol = New-Object -ComObject Outlook.Application
            ~mail = ~ol.CreateItem(0)
            ~mail.To = ~To
            ~mail.Subject = ~Subject
            ~mail.HTMLBody = [System.IO.File]::ReadAllText(~BodyFile, [System.Text.Encoding]::UTF8)
            if (~Attachment -and (Test-Path ~Attachment)) { ~null = ~mail.Attachments.Add(~Attachment) }
            ~mail.Send()
        } catch {
            [Console]::Error.WriteLine(~_.Exception.Message)
            exit 1
        }
    """.trimIndent().replace('~', '$')

    /** Creates the mail in the classic Outlook desktop app already signed in on this PC and sends it. */
    suspend fun sendViaOutlook(
        recipients: List<String>,
        subject: String,
        htmlBody: String,
        pdfFile: File? = null
    ): SendResult = withContext(Dispatchers.IO) {
        if (recipients.isEmpty()) return@withContext SendResult(false, "No recipients")
        val workDir = Files.createTempDirectory("ncr_outlook_").toFile()
        try {
            val script = File(workDir, "send.ps1").apply { writeText(OUTLOOK_SCRIPT, Charsets.UTF_8) }
            val bodyFile = File(workDir, "body.html").apply { writeText(htmlBody, Charsets.UTF_8) }

            val cmd = mutableListOf(
                "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.absolutePath,
                "-To", recipients.joinToString(";"),
                "-Subject", subject,
                "-BodyFile", bodyFile.absolutePath
            )
            if (pdfFile != null && pdfFile.exists() && pdfFile.length() > 0) {
                val nicePdf = File(workDir, "NCR_Register.pdf")
                pdfFile.copyTo(nicePdf, overwrite = true)
                cmd += listOf("-Attachment", nicePdf.absolutePath)
            }

            val process = ProcessBuilder(cmd).redirectErrorStream(true).start()
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@withContext SendResult(
                    false,
                    "Outlook did not respond within 90 seconds. A security prompt may be waiting on screen."
                )
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            if (process.exitValue() == 0) SendResult(true, "Sent through Outlook")
            else SendResult(false, "Outlook failed: ${output.ifBlank { "unknown error" }}")
        } catch (e: Exception) {
            SendResult(false, "Outlook failed: ${e.message}")
        } finally {
            workDir.deleteRecursively()
        }
    }

    private fun createSmtpProperties(config: SmtpConfig): Properties {
        val props = Properties()
        props["mail.smtp.host"] = config.host
        props["mail.smtp.port"] = config.port.toString()
        props["mail.smtp.auth"] = if (config.password.isBlank()) "false" else "true"
        // Without timeouts a blocked firewall can hang the sender forever
        props["mail.smtp.connectiontimeout"] = "15000"
        props["mail.smtp.timeout"] = "30000"
        props["mail.smtp.writetimeout"] = "30000"
        props["mail.smtp.ssl.protocols"] = "TLSv1.2"

        when {
            // 465 = implicit SSL
            config.port == 465 || (config.useSsl && config.port != 587 && config.port != 25) ->
                props["mail.smtp.ssl.enable"] = "true"
            // 25 = relay connector: use TLS if offered
            config.port == 25 ->
                props["mail.smtp.starttls.enable"] = "true"
            // 587 = STARTTLS required (Office 365 default)
            else -> {
                props["mail.smtp.starttls.enable"] = "true"
                props["mail.smtp.starttls.required"] = "true"
            }
        }
        return props
    }

    private fun esc(s: String?): String =
        (s ?: "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")

    fun buildNcrHtmlBody(
        ncrs: List<NcrModel>,
        title: String,
        deptMap: Map<Int, String> = NcrRepository.getDeptMap()
    ): String {
        val today = LocalDate.now()
        val ncrRows = ncrs.joinToString("\n") { ncr ->
            val status = when {
                ncr.actualCloseDate != null -> "Closed"
                ncr.targetCompletionDate != null && ncr.targetCompletionDate.isBefore(today) -> "Overdue"
                else -> "Open"
            }
            val statusColor = when (status) { "Closed" -> "#2E7D32"; "Overdue" -> "#C62828"; else -> "#EF6C00" }
            """
            <tr>
                <td>${esc(ncr.ncr)}</td>
                <td>${esc(ncr.jobNo)}</td>
                <td>${ncr.date ?: ""}</td>
                <td>${esc(ncr.descriptionOfNonConformity)}</td>
                <td>${esc(ncr.ncrRaisedBy)}</td>
                <td>${esc(deptMap[ncr.responsibleDept])}</td>
                <td>${ncr.targetCompletionDate ?: ""}</td>
                <td>${ncr.actualCloseDate ?: ""}</td>
                <td style="color:$statusColor;font-weight:bold;">$status</td>
            </tr>
            """.trimIndent()
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                h2 { color: #333; }
                table { border-collapse: collapse; width: 100%; margin-top: 15px; font-size: 13px; }
                th { background-color: #4CAF50; color: white; padding: 8px; text-align: left; border: 1px solid #ddd; }
                td { border: 1px solid #ddd; padding: 6px; vertical-align: top; }
                tr:nth-child(even) { background-color: #f2f2f2; }
                .footer { margin-top: 20px; font-size: 12px; color: #666; }
            </style>
        </head>
        <body>
            <h2>${esc(title)}</h2>
            <p>Total NCRs: ${ncrs.size}</p>
            <table>
                <tr>
                    <th>NCR No</th><th>Job No</th><th>Date</th><th>Description</th><th>Raised By</th>
                    <th>Responsible Dept</th><th>Target Close</th><th>Actual Close</th><th>Status</th>
                </tr>
                $ncrRows
            </table>
            <div class="footer">
                <p>Automated notification from the NCR Management System. The register PDF is attached.</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    suspend fun generateRegisterPdf(ncrs: List<NcrModel>): File? = withContext(Dispatchers.IO) {
        try {
            val tempPdf = File.createTempFile("ncr_register_", ".pdf")
            PdfExporter.exportRegister(ncrs, tempPdf.absolutePath)
            // createTempFile() already creates an EMPTY file, so exists() is always true;
            // a failed Excel->PDF conversion leaves 0 bytes and must not be attached.
            if (tempPdf.length() > 0) tempPdf else { tempPdf.delete(); null }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}