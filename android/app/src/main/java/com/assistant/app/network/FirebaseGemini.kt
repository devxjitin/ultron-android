package com.assistant.app.network

import android.content.Context
import com.assistant.app.util.UserMessageException
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.json.JSONArray
import org.json.JSONObject

object FirebaseGemini {
    /** True when the build has a Firebase config (`google-services.json`); otherwise the on-device Gemini key is used. */
    fun isAvailable(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /** The Gemini Developer API behind Firebase AI Logic, on the app's default Firebase project. */
    fun ai(): FirebaseAI = FirebaseAI.getInstance(FirebaseApp.getInstance(), GenerativeBackend.googleAI())

    /** Turns the app's JSON function declarations (the shape the REST and Live clients use) into SDK tool declarations. */
    fun functionDeclarations(declarations: JSONArray): List<FunctionDeclaration> =
        (0 until declarations.length()).mapNotNull { declarations.optJSONObject(it) }.map { declaration ->
            val parameters = declaration.optJSONObject("parameters")
            val properties = parameters?.optJSONObject("properties")
            val schemas = linkedMapOf<String, Schema>()
            properties?.keys()?.forEach { name -> schemas[name] = schemaOf(properties.getJSONObject(name)) }
            val required = parameters?.optJSONArray("required")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
            FunctionDeclaration(
                name = declaration.getString("name"),
                description = declaration.optString("description"),
                parameters = schemas,
                optionalParameters = schemas.keys.filter { it !in required }
            )
        }

    private fun schemaOf(json: JSONObject): Schema {
        val description = json.optString("description").takeIf { it.isNotEmpty() }
        return when (json.optString("type").uppercase()) {
            "INTEGER" -> Schema.integer(description = description)
            "NUMBER" -> Schema.double(description = description)
            "BOOLEAN" -> Schema.boolean(description = description)
            "ARRAY" -> Schema.array(
                items = json.optJSONObject("items")?.let(::schemaOf) ?: Schema.string(),
                description = description
            )
            "OBJECT" -> {
                val properties = json.optJSONObject("properties")
                val schemas = linkedMapOf<String, Schema>()
                properties?.keys()?.forEach { name -> schemas[name] = schemaOf(properties.getJSONObject(name)) }
                val required = json.optJSONArray("required")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
                Schema.obj(
                    properties = schemas,
                    optionalProperties = schemas.keys.filter { it !in required },
                    description = description
                )
            }
            else -> {
                val choices = json.optJSONArray("enum")?.let { array -> (0 until array.length()).map { array.getString(it) } }.orEmpty()
                if (choices.isNotEmpty()) Schema.enumeration(choices, description = description) else Schema.string(description = description)
            }
        }
    }

    /** One tool result in the shape a function response needs. */
    fun resultObject(result: String): JsonObject = buildJsonObject { put("result", JsonPrimitive(result)) }

    /** A function call's arguments as the org.json object the app's tool runners take. */
    fun argsObject(args: Map<String, JsonElement>): JSONObject = JSONObject(JsonObject(args).toString())

    /** Maps an SDK failure onto the exceptions the rest of the app already knows how to show. */
    fun asAppFailure(e: FirebaseAIException): Exception = when (e) {
        is QuotaExceededException -> GeminiEmbeddingClient.HttpFailure(429, "Gemini quota exceeded: ${e.message}".take(220))
        is InvalidAPIKeyException, is APINotConfiguredException, is ServiceDisabledException, is PermissionMissingException ->
            GeminiEmbeddingClient.HttpFailure(403, "Firebase AI Logic rejected the request: ${e.message}".take(220))
        is ServerException, is RequestTimeoutException, is ServiceConnectionHandshakeFailedException ->
            GeminiEmbeddingClient.HttpFailure(503, "Gemini is unavailable: ${e.message}".take(220))
        is UnsupportedUserLocationException -> UserMessageException("Gemini is not available in this region.")
        is PromptBlockedException -> UserMessageException("This message was blocked by the model's safety filter.")
        is ContentBlockedException, is ResponseStoppedException -> UserMessageException("The reply was stopped by the model's safety filter.")
        else -> UserMessageException("The model reported an error. Please try again.")
    }
}
