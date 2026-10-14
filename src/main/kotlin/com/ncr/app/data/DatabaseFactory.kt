package com.ncr.app.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.io.File

object DatabaseFactory {
    fun init() {
        val baseDir = System.getProperty("jpackage.app-path")?.let { File(it).parentFile } ?: File(System.getProperty("user.dir"))
        val dbFile = File(baseDir, "ncr_database.db")
        Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction {
            SchemaUtils.create(
                DeptsTable,
                NcrDataTable,
                NotificationRulesTable,
                RuleRecipientsTable,
                NotificationSentLogTable
            )
            if (DeptsTable.selectAll().count() == 0L) {
                val defaultDepts =
                    listOf("Quality", "Production", "Engineering", "Purchasing", "Logistics", "Maintenance")
                for (dept in defaultDepts) {
                    DeptsTable.insert {
                        it[dName] = dept
                    }
                }
            }
        }
    }
}
