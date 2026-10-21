package com.assistant.app.network

import com.assistant.app.data.McpToolSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class McpException(message: String, val httpCode: Int = 0) : Exception(message)

/**
 * Minimal MCP client over "streamable HTTP": every request is a JSON-RPC POST, and the answer comes
 * back either as plain JSON or as a short event stream. One instance keeps one server session.
 */
class McpClient(
    private val url: String,
    /** Headers sent with every request, read fresh each time (the token can change). */
    private val headers: () -> Map<String, String>
) {
    private val http = HttpClients.shared.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val lock = Mutex()
    /** The request in flight; calls are serialized by [lock], so cancelling this cancels the right one. */
    @Volatile private var activeCall: okhttp3.Call? = null
    private var sessionId: String? = null
    private var initialized = false
    private var nextId = 1

    suspend fun listTools(): List<McpToolSpec> = lock.withLock {
        withContext(Dispatchers.IO) {
            ensureInitialized()
            val tools = mutableListOf<McpToolSpec>()
            var cursor: String? = null
            repeat(MAX_PAGES) {
                val params = JSONObject().apply { cursor?.let { put("cursor", it) } }
                val result = request("tools/list", params)
                val array = result.optJSONArray("tools") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val tool = array.getJSONObject(i)
                    tools += McpToolSpec(
                        name = tool.getString("name"),
                        description = tool.optString("description"),
                        schema = (tool.optJSONObject("inputSchema") ?: JSONObject()).toString(),
                        readOnly = tool.optJSONObject("annotations")?.optBoolean("readOnlyHint", false) ?: false
                    )
                }
                cursor = result.optString("nextCursor").takeIf { it.isNotEmpty() }
                if (cursor == null) return@withContext tools
            }
            tools
        }
    }

    /** Runs one tool and returns its text output; throws [McpException] on protocol errors. */
    suspend fun callTool(name: String, arguments: JSONObject): Result<String> = lock.withLock {
        withContext(Dispatchers.IO) {
            val watcher = cancelActiveCallOnCancellation()
            try {
                ensureInitialized()
                val result = request("tools/call", JSONObject().put("name", name).put("arguments", arguments))
                val content = result.optJSONArray("content") ?: JSONArray()
                val text = (0 until content.length()).mapNotNull { i ->
                    content.getJSONObject(i).takeIf { it.optString("type") == "text" }?.optString("text")
                }.joinToString("\n")
                if (result.optBoolean("isError")) Result.failure(McpException(text.ifBlank { "The tool reported an error." }))
                else Result.success(text)
            } catch (e: McpException) {
                Result.failure(e)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(McpException("Could not reach the server."))
            } finally {
                withContext(kotlinx.coroutines.NonCancellable) { watcher.cancelAndJoin() }
            }
        }
    }

    /** When the calling coroutine is cancelled, the open request is dropped at once instead of waiting for the server. */
    private suspend fun cancelActiveCallOnCancellation(): kotlinx.coroutines.Job =
        kotlinx.coroutines.CoroutineScope(kotlin.coroutines.coroutineContext).launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                activeCall?.cancel()
            }
        }

    private fun ensureInitialized() {
        if (initialized) return
        val params = JSONObject()
            .put("protocolVersion", PROTOCOL_VERSION)
            .put("capabilities", JSONObject())
            .put("clientInfo", JSONObject().put("name", "ultron").put("version", "1.0"))
        request("initialize", params)
        post(JSONObject().put("jsonrpc", "2.0").put("method", "notifications/initialized"), expectBody = false)
        initialized = true
    }

    /** One JSON-RPC call; re-opens the session once if the server forgot it. */
    private fun request(method: String, params: JSONObject): JSONObject {
        val id = nextId++
        val body = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
        return try {
            post(body, expectBody = true, id = id)
        } catch (e: McpException) {
            if (e.httpCode == 404 && method != "initialize" && sessionId != null) {
                sessionId = null
                initialized = false
                ensureInitialized()
                post(body, expectBody = true, id = id)
            } else {
                throw e
            }
        }
    }

    private fun post(body: JSONObject, expectBody: Boolean, id: Int = 0): JSONObject {
        val builder = Request.Builder().url(url)
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json, text/event-stream")
        headers().forEach { (key, value) -> builder.header(key, value) }
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        val call = http.newCall(builder.build())
        activeCall = call
        call.execute().use { response ->
            response.header("Mcp-Session-Id")?.let { sessionId = it }
            val streaming = response.isSuccessful && expectBody &&
                response.header("Content-Type").orEmpty().contains("text/event-stream")
            if (streaming) {
                // Answer as soon as the matching event arrives; the server may keep the stream open afterwards.
                val body = response.body ?: throw McpException("The server sent no answer.")
                val message = readEvents(body.source(), id)
                message.optJSONObject("error")?.let { throw McpException(it.optString("message", "The server returned an error.")) }
                return message.optJSONObject("result") ?: JSONObject()
            }
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                android.util.Log.w("McpClient", "HTTP ${response.code}: ${text.take(400)}")
                val detail = text.take(160).replace(Regex("\\s+"), " ")
                val reason = when (response.code) {
                    401 -> "The server rejected the sign-in. Reconnect the connector."
                    403 -> "The server refused access (missing permission). $detail"
                    else -> "The server answered HTTP ${response.code}. $detail"
                }
                throw McpException(reason, response.code)
            }
            if (!expectBody) return JSONObject()
            val message = JSONObject(text)
            message.optJSONObject("error")?.let { throw McpException(it.optString("message", "The server returned an error.")) }
            return message.optJSONObject("result") ?: JSONObject()
        }
    }

    /** Reads server-sent events until the JSON-RPC answer for [id] arrives; events may span several `data:` lines. */
    private fun readEvents(source: okio.BufferedSource, id: Int): JSONObject {
        val data = StringBuilder()
        fun dispatch(): JSONObject? {
            if (data.isEmpty()) return null
            val message = runCatching { JSONObject(data.toString()) }.getOrNull()
            data.setLength(0)
            return message?.takeIf { it.optInt("id", -1) == id }
        }
        while (true) {
            val line = source.readUtf8Line() ?: break
            if (line.isEmpty()) {
                dispatch()?.let { return it }
            } else if (line.startsWith("data:")) {
                if (data.isNotEmpty()) data.append("\n")
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
        }
        dispatch()?.let { return it }
        throw McpException("The server sent no answer.")
    }

    private companion object {
        const val PROTOCOL_VERSION = "2025-03-26"
        const val MAX_PAGES = 5
    }
}
