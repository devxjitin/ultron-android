package com.assistant.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class YouTubeDownloadsMcpTest {
    private fun request(method: String, params: JSONObject = JSONObject()) = JSONObject()
        .put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params)

    @Test fun onlyDownloadToolsAreExposedAndConnectorsAreSeparate() = runBlocking {
        val server = YouTubeDownloadsMcp({ "options" }, { _, _, _ -> "started" })
        val tools = server.handle(request("tools/list"))!!.getJSONObject("result").getJSONArray("tools")
        val names = (0 until tools.length()).map { tools.getJSONObject(it).getString("name") }.toSet()
        assertEquals(setOf("youtube_download_options", "download_youtube_video"), names)
        assertEquals(YouTubeDownloadsMcp.TOOLS, PhoneConnectors.tools("youtube_downloads"))
        assertTrue(PhoneConnectors.tools("youtube").none { it in YouTubeDownloadsMcp.TOOLS })
        val rejected = server.handle(request("tools/call", JSONObject().put("name", "youtube_search")))!!
        assertEquals(-32602, rejected.getJSONObject("error").getInt("code"))
    }

    @Test fun missingChoiceShowsOptionsInsteadOfStartingDownload() = runBlocking {
        var started = false
        val server = YouTubeDownloadsMcp({ "status=ok\noptions for $it" }, { _, _, _ -> started = true; "started" })
        val args = JSONObject().put("video", "BaW_jenozKc").put("kind", "video")
        assertTrue(server.callTool("download_youtube_video", args).contains("options for"))
        assertFalse(started)
        args.put("height", 720)
        assertEquals("started", server.callTool("download_youtube_video", args))
        assertTrue(started)
    }

    @Test fun invalidKindOrHeightNeverDownloads() = runBlocking {
        var calls = 0
        val server = YouTubeDownloadsMcp({ calls++; "options" }, { _, _, _ -> calls++; "started" })
        val args = JSONObject().put("video", "BaW_jenozKc").put("kind", "other")
        assertTrue(server.callTool("download_youtube_video", args).startsWith("status=invalid"))
        args.put("kind", "video").put("height", -1)
        assertTrue(server.callTool("download_youtube_video", args).startsWith("status=invalid"))
        args.put("height", 720.5)
        assertTrue(server.callTool("download_youtube_video", args).startsWith("status=invalid"))
        assertEquals(0, calls)
    }

    @Test fun audioSelectionIsPassedToPhoneDownloader() = runBlocking {
        val server = YouTubeDownloadsMcp({ fail("Should not list options"); "" }, { video, audioOnly, height ->
            assertEquals("BaW_jenozKc", video)
            assertTrue(audioOnly)
            assertEquals(0, height)
            "status=ok\nstarted"
        })
        assertTrue(server.callTool("download_youtube_video", JSONObject().put("video", "BaW_jenozKc").put("kind", "audio")).startsWith("status=ok"))
    }

    @Test(expected = CancellationException::class) fun cancellationIsNotReportedAsFailure(): Unit = runBlocking {
        val server = YouTubeDownloadsMcp({ throw CancellationException() }, { _, _, _ -> "" })
        server.callTool("youtube_download_options", JSONObject().put("video", "BaW_jenozKc"))
        Unit
    }
}
