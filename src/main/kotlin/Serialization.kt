package org.lumina

import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.security.sasl.AuthenticationException

fun Application.configureSerialization() {

    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val status = when (cause) {
                is ResponseException -> cause.response.status
                else -> HttpStatusCode.InternalServerError
            }

            val message = when (cause) {
                is ResponseException, is AuthenticationException, is BadRequestException, is OAuth1aException, is IllegalStateException, is IllegalArgumentException -> cause.message
                else -> "服务端错误"
            }

            call.respond(
                status = status, message = ErrorResponse(
                    message = message ?: "服务端错误", statusCode = status.value
                )
            )
            cause.printStackTrace()
            this@configureSerialization.log.error(cause.toString())
        }
    }
}

@Serializable
data class ErrorResponse(
    val message: String, val statusCode: Int
)
