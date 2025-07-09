package org.lumina

import io.ktor.server.application.*
import org.jetbrains.exposed.v1.core.StdOutSqlLogger
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.lumina.models.Approvals
import org.lumina.models.Groups
import org.lumina.models.UserGroups
import org.lumina.models.Users

fun Application.configureDatabases() {
    val database = Database.connect(
        url = environment.config.property("db.url").getString(),
        user = environment.config.property("db.user").getString(),
        driver = environment.config.property("db.driver").getString(),
        password = environment.config.property("db.password").getString()
    )
    transaction(database) {
        addLogger(StdOutSqlLogger)
        val tables = listOf(
            Users,
            Groups,
            UserGroups,
            Approvals
        )
        tables.forEach { table ->
            SchemaUtils.createMissingTablesAndColumns(table)
        }
    }
}