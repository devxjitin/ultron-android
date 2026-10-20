"""Minimal stateless MCP server (streamable HTTP, JSON responses) for YouTube search via yt-dlp.

Runs on AWS Lambda behind a Function URL. Only dependency: yt-dlp (bundled in the zip).
Env vars:
  ACCESS_TOKEN     - secret path segment; requests to any other path are rejected
"""
import hmac
import json
import os
import urllib.request

PROTOCOL_VERSION = "2025-03-26"

TOOLS = [
    {
        "name": "search_videos",
        "description": "Search YouTube videos. Returns title, channel, publish date, video id and url.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "query": {"type": "string", "description": "Search text"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 25, "default": 10},
            },
            "required": ["query"],
        },
    },
    {
        "name": "get_video_info",
        "description": "Get details (description, duration, views, likes) for a YouTube video id or url.",
        "inputSchema": {
            "type": "object",
            "properties": {"video": {"type": "string", "description": "Video id or full url"}},
            "required": ["video"],
        },
    },
    {
        "name": "get_transcript",
        "description": "Get the timestamped transcript (manual or auto captions) of a YouTube video.",
        "inputSchema": {
            "type": "object",
            "properties": {
                "video": {"type": "string", "description": "Video id or full url"},
                "lang": {"type": "string", "default": "en", "description": "Language code, e.g. en, hi"},
                "max_chars": {"type": "integer", "default": 20000, "description": "Truncate output (1000-100000)"},
            },
            "required": ["video"],
        },
    },
    {
        "name": "list_videos",
        "description": "List recent videos of a YouTube channel (@handle or channel url) or a playlist (url or id).",
        "inputSchema": {
            "type": "object",
            "properties": {
                "source": {"type": "string", "description": "@handle, channel/playlist url, or playlist id"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 50, "default": 20},
            },
            "required": ["source"],
        },
    },
]


def _extract(target, flat, **extra):
    from yt_dlp import YoutubeDL  # imported lazily so cold start of other methods stays fast

    opts = {
        "quiet": True,
        "no_warnings": True,
        "skip_download": True,
        "extract_flat": flat,
        "cachedir": False,  # Lambda filesystem is read-only except /tmp
        "socket_timeout": 10,
        **extra,
    }
    with YoutubeDL(opts) as ydl:
        return ydl.extract_info(target, download=False)


def search_videos(args):
    limit = max(1, min(int(args.get("limit", 10)), 25))
    data = _extract(f"ytsearch{limit}:{args['query']}", flat=True)
    results = [
        {
            "title": e.get("title"),
            "channel": e.get("channel") or e.get("uploader"),
            "duration_sec": e.get("duration"),
            "views": e.get("view_count"),
            "video_id": e.get("id"),
            "url": f"https://www.youtube.com/watch?v={e.get('id')}",
        }
        for e in data.get("entries", [])
        if e
    ]
    return json.dumps(results, ensure_ascii=False, indent=2)


def get_video_info(args):
    target = args["video"]
    if "/" not in target:
        target = f"https://www.youtube.com/watch?v={target}"
    v = _extract(target, flat=False)
    return json.dumps({
        "title": v.get("title"),
        "channel": v.get("channel"),
        "upload_date": v.get("upload_date"),
        "duration_sec": v.get("duration"),
        "views": v.get("view_count"),
        "likes": v.get("like_count"),
        "description": (v.get("description") or "")[:1500],
        "url": v.get("webpage_url"),
    }, ensure_ascii=False, indent=2)


def _pick_caption(info, lang):
    """Return (url, lang_code, is_auto) for the best json3 caption track, or None."""
    for key, is_auto in (("subtitles", False), ("automatic_captions", True)):
        tracks = info.get(key) or {}
        codes = [c for c in tracks if c == lang or c.startswith(lang + "-")]
        for code in codes:
            for fmt in tracks[code]:
                if fmt.get("ext") == "json3":
                    return fmt["url"], code, is_auto
    return None


def get_transcript(args):
    lang = args.get("lang", "en")
    max_chars = max(1000, min(int(args.get("max_chars", 20000)), 100000))
    target = args["video"]
    if "/" not in target:
        target = f"https://www.youtube.com/watch?v={target}"
    info = _extract(target, flat=False)
    picked = _pick_caption(info, lang)
    if not picked:
        langs = sorted(set((info.get("subtitles") or {}) | (info.get("automatic_captions") or {})))
        return f"No '{lang}' captions for this video. Available: {', '.join(langs[:30]) or 'none'}"
    url, code, is_auto = picked
    with urllib.request.urlopen(url, timeout=10) as resp:
        data = json.load(resp)
    lines = []
    for ev in data.get("events", []):
        text = "".join(s.get("utf8", "") for s in ev.get("segs", [])).replace("\n", " ").strip()
        if text:
            lines.append(f"[{int(ev.get('tStartMs', 0)) // 1000}s] {text}")
    full = "\n".join(lines)
    note = f"(lang={code}, {'auto-generated' if is_auto else 'manual'}, title={info.get('title')})\n"
    if len(full) > max_chars:
        full = full[:max_chars] + f"\n... [truncated, total {len(full)} chars]"
    return note + full


def list_videos(args):
    source = args["source"].strip()
    limit = max(1, min(int(args.get("limit", 20)), 50))
    if source.startswith("@"):
        source = f"https://www.youtube.com/{source}/videos"
    elif "/" not in source:
        source = f"https://www.youtube.com/playlist?list={source}"
    data = _extract(source, flat="in_playlist", playlistend=limit)
    results = [
        {
            "title": e.get("title"),
            "duration_sec": e.get("duration"),
            "views": e.get("view_count"),
            "video_id": e.get("id"),
            "url": f"https://www.youtube.com/watch?v={e.get('id')}",
        }
        for e in (data.get("entries") or [])[:limit]
        if e and e.get("id")
    ]
    return json.dumps({"title": data.get("title"), "videos": results}, ensure_ascii=False, indent=2)


HANDLERS = {
    "search_videos": search_videos,
    "get_video_info": get_video_info,
    "get_transcript": get_transcript,
    "list_videos": list_videos,
}


def _rpc(msg):
    """Handle one JSON-RPC message. Returns a response dict, or None for notifications."""
    method, mid = msg.get("method"), msg.get("id")
    if mid is None:
        return None
    if method == "initialize":
        result = {
            "protocolVersion": PROTOCOL_VERSION,
            "capabilities": {"tools": {}},
            "serverInfo": {"name": "youtube-search", "version": "1.0.0"},
        }
    elif method == "ping":
        result = {}
    elif method == "tools/list":
        result = {"tools": [dict(t, annotations={"readOnlyHint": True}) for t in TOOLS]}
    elif method == "tools/call":
        params = msg.get("params", {})
        handler = HANDLERS.get(params.get("name"))
        if handler is None:
            return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32602, "message": "Unknown tool"}}
        try:
            text, is_error = handler(params.get("arguments", {})), False
        except Exception as e:  # surface tool failures to the model, not as protocol errors
            text, is_error = str(e), True
        result = {"content": [{"type": "text", "text": text}], "isError": is_error}
    else:
        return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": "Method not found"}}
    return {"jsonrpc": "2.0", "id": mid, "result": result}


def _resp(status, body=None):
    return {
        "statusCode": status,
        "headers": {"content-type": "application/json"},
        "body": json.dumps(body) if body is not None else "",
    }


def lambda_handler(event, context):
    token = os.environ.get("ACCESS_TOKEN", "")
    path = event.get("rawPath", "").strip("/")
    if not token or not hmac.compare_digest(path, token):
        return _resp(404, {"error": "not found"})
    if event["requestContext"]["http"]["method"] != "POST":
        return _resp(405, {"error": "use POST"})
    try:
        body = json.loads(event.get("body") or "")
    except ValueError:
        return _resp(400, {"error": "invalid json"})
    if isinstance(body, list):
        out = [r for r in (_rpc(m) for m in body) if r is not None]
        return _resp(200, out) if out else _resp(202)
    out = _rpc(body)
    return _resp(200, out) if out else _resp(202)
