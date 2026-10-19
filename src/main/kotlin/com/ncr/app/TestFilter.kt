package com.ncr.app

import com.ncr.app.data.DatabaseFactory
import java.sql.DriverManager

fun main() {
    DatabaseFactory.init()
    
    val url = "jdbc:sqlite:ncr_database.db"
    DriverManager.getConnection(url).use { conn ->
        conn.createStatement().use { stmt ->
            var rs = stmt.executeQuery("SELECT COUNT(*) FROM NCR_DATA WHERE is_archived IS NULL")
            if (rs.next()) {
                println("NULL is_archived count: ${rs.getInt(1)}")
            }
            rs = stmt.executeQuery("SELECT COUNT(*) FROM NCR_DATA WHERE is_archived = 0")
            if (rs.next()) {
                println("0 is_archived count: ${rs.getInt(1)}")
            }
            rs = stmt.executeQuery("SELECT COUNT(*) FROM NCR_DATA WHERE is_archived = 1")
            if (rs.next()) {
                println("1 is_archived count: ${rs.getInt(1)}")
            }
        }
    }
}
