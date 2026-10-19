package com.ncr.app.data

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
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
            // NCR tables: createMissingTablesAndColumns adds the new is_archived column to the existing NCR_DATA
            SchemaUtils.createMissingTablesAndColumns(DeptsTable, NcrDataTable)
            // Notification tables: created if missing; their new columns are added by addColumnIfMissing below
            SchemaUtils.create(
                NotificationRulesTable,
                RuleRecipientsTable,
                NotificationSentLogTable,
                GraphAccountTable
            )

            // SchemaUtils.create() never adds columns to an existing table,
            // so add the two new scheduling columns by hand (only NOTIFICATION_RULES is touched).
            addColumnIfMissing(
                "NOTIFICATION_RULES", "send_hour",
                "ALTER TABLE NOTIFICATION_RULES ADD COLUMN send_hour INTEGER NOT NULL DEFAULT 8"
            )
            addColumnIfMissing(
                "NOTIFICATION_RULES", "last_run_at",
                "ALTER TABLE NOTIFICATION_RULES ADD COLUMN last_run_at DATETIME"
            )

            addColumnIfMissing(
                "NOTIFICATION_RULES", "use_outlook",
                "ALTER TABLE NOTIFICATION_RULES ADD COLUMN use_outlook BOOLEAN NOT NULL DEFAULT 0"
            )

            addColumnIfMissing(
                "NOTIFICATION_RULES", "use_graph",
                "ALTER TABLE NOTIFICATION_RULES ADD COLUMN use_graph BOOLEAN NOT NULL DEFAULT 0"
            )

            addColumnIfMissing(
                "NOTIFICATION_RULES", "day_of_week",
                "ALTER TABLE NOTIFICATION_RULES ADD COLUMN day_of_week INTEGER NOT NULL DEFAULT 0"
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

    private fun Transaction.addColumnIfMissing(table: String, column: String, ddl: String) {
        val existing = exec("PRAGMA table_info($table)") { rs ->
            val names = mutableSetOf<String>()
            while (rs.next()) names += rs.getString("name").lowercase()
            names
        } ?: emptySet()
        if (column.lowercase() !in existing) exec(ddl)
    }
}