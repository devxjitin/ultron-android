package com.assistant.app.data

import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

/** Private in-process MCP server. No listening socket; only download tools run on this phone. */
class YouTubeDownloadsMcp(
    private val options: suspend (String) -> String,
    private val download: (String, Boolean, Int) -> String
) {
    suspend fun handle(message: JSONObject): JSONObject? {
        val id = message.opt("id") ?: return null
        fun error(code: Int, text: String) = JSONObject().put("jsonrpc", "2.0").put("id", id)
            .put("error", JSONObject().put("code", code).put("message", text))
        if (message.optString("jsonrpc") != "2.0") return error(-32600, "Invalid Request")
        val result = when (message.optString("method")) {
            "initialize" -> JSONObject().put("protocolVersion", "2025-03-26")
                .put("capabilities", JSONObject().put("tools", JSONObject()))
                .put("serverInfo", JSONObject().put("name", "youtube-downloads-phone").put("version", "1.0.0"))
            "ping" -> JSONObject()
            "tools/list" -> JSONObject().put("tools", JSONArray(TOOLS.map { tool ->
                val properties = JSONObject()
                tool.params.forEach { param ->
                    properties.put(param.name, JSONObject().put("type", param.type.name.lowercase())
                        .put("description", param.description).apply {
                            if (param.choices.isNotEmpty()) put("enum", JSONArray(param.choices))
                        })
                }
                JSONObject().put("name", tool.functionName).put("description", tool.description)
                    .put("inputSchema", JSONObject().put("type", "object").put("properties", properties)
                        .put("required", JSONArray(tool.params.filter { it.required }.map { it.name })))
                    .put("annotations", JSONObject().put("readOnlyHint", tool == DeviceTool.YOUTUBE_DOWNLOAD_OPTIONS))
            }))
            "tools/call" -> {
                val params = message.optJSONObject("params") ?: return error(-32602, "Invalid params")
                val name = params.optString("name")
                if (TOOLS.none { it.functionName == name }) return error(-32602, "Unknown tool")
                val args = if (params.has("arguments")) params.optJSONObject("arguments")
                    ?: return error(-32602, "Invalid arguments") else JSONObject()
                val video = (args.opt("video") as? String)?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2048 }
                    ?: return error(-32602, "Give a YouTube link or video id")
                val kind = if (args.has("kind")) args.opt("kind") as? String else null
                if (args.has("kind") && kind !in listOf("audio", "video")) return error(-32602, "kind must be audio or video")
                val height = if (args.has("height")) {
                    val raw = args.opt("height") as? Number ?: return error(-32602, "height must be an integer")
                    val value = raw.toInt()
                    if (raw.toDouble() != value.toDouble() || value !in 1..4320) return error(-32602, "height must be 1-4320")
                    value
                } else null
                try {
                    val text = if (name == DeviceTool.YOUTUBE_DOWNLOAD_OPTIONS.functionName || kind == null ||
                        (kind == "video" && height == null)) options(video)
                    else download(video, kind == "audio", height ?: 0)
                    content(text, text.startsWith("status=failed") || text.startsWith("status=invalid"))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    content("status=failed\nCould not prepare the download on this phone.", true)
                }
            }
            else -> return error(-32601, "Method not found")
        }
        return JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result)
    }

    suspend fun callTool(name: String, arguments: JSONObject): String {
        val response = handle(JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", "tools/call")
            .put("params", JSONObject().put("name", name).put("arguments", arguments)))!!
        response.optJSONObject("error")?.let { return "status=invalid\n${it.optString("message")}" }
        return response.getJSONObject("result").getJSONArray("content").getJSONObject(0).getString("text")
    }

    private fun content(text: String, error: Boolean) = JSONObject().put("isError", error)
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))

    companion object {
        val TOOLS = listOf(DeviceTool.YOUTUBE_DOWNLOAD_OPTIONS, DeviceTool.DOWNLOAD_YOUTUBE)
    }
}
