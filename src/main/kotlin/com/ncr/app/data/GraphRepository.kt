package com.ncr.app.data

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

data class GraphAccount(
    val tenantId: String,
    val clientId: String,
    val refreshToken: String?,
    val accountEmail: String?
)

/** One signed-in Microsoft account shared by every rule that uses "Microsoft account (Graph)". */
object GraphRepository {

    fun get(): GraphAccount? = transaction {
        GraphAccountTable.selectAll().limit(1).map {
            GraphAccount(
                tenantId = it[GraphAccountTable.tenantId],
                clientId = it[GraphAccountTable.clientId],
                refreshToken = it[GraphAccountTable.refreshToken],
                accountEmail = it[GraphAccountTable.accountEmail]
            )
        }.firstOrNull()
    }

    fun save(account: GraphAccount) = transaction {
        GraphAccountTable.deleteAll()
        GraphAccountTable.insert {
            it[tenantId] = account.tenantId
            it[clientId] = account.clientId
            it[refreshToken] = account.refreshToken
            it[accountEmail] = account.accountEmail
        }
    }
}
