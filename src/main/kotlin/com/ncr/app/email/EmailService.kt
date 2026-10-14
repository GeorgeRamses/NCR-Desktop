package com.ncr.app.email

import com.ncr.app.data.NcrModel
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
import java.util.*
import java.util.Properties

object EmailService {

    data class SmtpConfig(
        val host: String,
        val port: Int,
        val user: String,
        val password: String,
        val useSsl: Boolean
    )

    data class SendResult(
        val success: Boolean,
        val message: String
    )

    fun createSmtpConfig(rule: NotificationRule): SmtpConfig? {
        val host = rule.smtpHost ?: return null
        val port = rule.smtpPort ?: return null
        val user = rule.smtpUser ?: return null
        val password = rule.smtpPassword ?: return null
        return SmtpConfig(host, port, user, password, rule.useSsl)
    }

    suspend fun testConnection(config: SmtpConfig): SendResult = withContext(Dispatchers.IO) {
        try {
            val props = createSmtpProperties(config)
            val session = Session.getInstance(props)
            session.debug = false
            val transport = session.getTransport("smtp")
            transport.connect(
                config.host,
                config.port,
                config.user,
                config.password
            )
            transport.close()
            SendResult(true, "Connection successful")
        } catch (e: Exception) {
            SendResult(false, "Connection failed: ${e.message}")
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
            if (recipients.isEmpty()) {
                return@withContext SendResult(false, "No recipients")
            }

            val props = createSmtpProperties(config)
            val session = Session.getInstance(props)
            session.debug = false

            val message = MimeMessage(session)
            message.setFrom(InternetAddress(config.user))
            message.setRecipients(Message.RecipientType.TO, recipients.joinToString(","))
            message.subject = subject

            val multipart = MimeMultipart()
            val htmlPart = MimeBodyPart()
            htmlPart.setContent(htmlBody, "text/html; charset=utf-8")
            multipart.addBodyPart(htmlPart)

            if (pdfFile != null && pdfFile.exists()) {
                val attachment = MimeBodyPart()
                attachment.attachFile(pdfFile)
                multipart.addBodyPart(attachment)
            }

            message.setContent(multipart)
            Transport.send(message)
            SendResult(true, "Email sent successfully")
        } catch (e: Exception) {
            SendResult(false, "Failed to send email: ${e.message}")
        }
    }

    private fun createSmtpProperties(config: SmtpConfig): Properties {
        val props = Properties()
        props["mail.smtp.host"] = config.host
        props["mail.smtp.port"] = config.port.toString()
        props["mail.smtp.auth"] = "true"
        if (config.useSsl) {
            props["mail.smtp.ssl.enable"] = "true"
            props["mail.smtp.socketFactory.port"] = config.port.toString()
            props["mail.smtp.socketFactory.class"] = "javax.net.ssl.SSLSocketFactory"
        } else {
            props["mail.smtp.starttls.enable"] = "true"
        }
        return props
    }

    fun buildNcrHtmlBody(ncrs: List<NcrModel>, title: String): String {
        val ncrRows = ncrs.map { ncr ->
            """
            <tr>
                <td style="border: 1px solid #ddd; padding: 8px;">${ncr.ncr ?: ""}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${ncr.jobNo ?: ""}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${ncr.date?.toString() ?: ""}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${ncr.descriptionOfNonConformity ?: ""}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${ncr.ncrRaisedBy ?: ""}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${ncr.targetCompletionDate?.toString() ?: ""}</td>
                <td style="border: 1px solid #ddd; padding: 8px;">${ncr.actualCloseDate?.toString() ?: ""}</td>
            </tr>
            """.trimIndent()
        }.joinToString("\n")

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { font-family: Arial, sans-serif; margin: 20px; }
                h2 { color: #333; }
                table { border-collapse: collapse; width: 100%; margin-top: 15px; }
                th { background-color: #4CAF50; color: white; padding: 10px; text-align: left; border: 1px solid #ddd; }
                td { border: 1px solid #ddd; padding: 8px; }
                tr:nth-child(even) { background-color: #f2f2f2; }
                .footer { margin-top: 20px; font-size: 12px; color: #666; }
            </style>
        </head>
        <body>
            <h2>$title</h2>
            <p>Total NCRs: ${ncrs.size}</p>
            <table>
                <tr>
                    <th>NCR Number</th>
                    <th>Job Number</th>
                    <th>Date</th>
                    <th>Description</th>
                    <th>Raised By</th>
                    <th>Target Close Date</th>
                    <th>Actual Close Date</th>
                </tr>
                $ncrRows
            </table>
            <div class="footer">
                <p>This is an automated notification from the NCR Management System.</p>
            </div>
        </body>
        </html>
        """.trimIndent()
    }

    suspend fun generateRegisterPdf(ncrs: List<NcrModel>): File? = withContext(Dispatchers.IO) {
        try {
            val tempPdf = File.createTempFile("ncr_register_", ".pdf")
            PdfExporter.exportRegister(ncrs, tempPdf.absolutePath)
            if (tempPdf.exists()) {
                tempPdf
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
