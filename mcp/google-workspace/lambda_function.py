"""Stateless MCP server (streamable HTTP, JSON responses) for Google Workspace, built on Google's REST APIs.

Runs on AWS Lambda behind a Function URL. No third-party dependencies and nothing is stored: the caller sends
its own Google OAuth access token as `Authorization: Bearer ...`, and every request is forwarded to Google
with that token. URL shape: /<ACCESS_TOKEN>/<service>, service one of calendar, gmail, drive, docs, sheets, slides.

Env vars:
  ACCESS_TOKEN  - secret first path segment; requests with any other path are rejected
"""
import base64
import concurrent.futures
import hmac
import json
import os
import re
import socket
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from email.message import EmailMessage

PROTOCOL_VERSION = "2025-03-26"
MAX_TEXT = 20000
# The app waits 25 s for an answer, so a whole operation gets less than that; every Google call uses what is left.
OPERATION_BUDGET_S = 22.0
_deadline = None


def _time_left():
    return 20.0 if _deadline is None else _deadline - time.monotonic()


class GoogleError(RuntimeError):
    """A failed Google call. `kind` lets the app tell an expired token from a missing file from a timeout without parsing the text."""

    def __init__(self, message, kind, status=0):
        super().__init__(message)
        self.kind = kind
        self.status = status


def _kind(status):
    return {401: "auth_rejected", 403: "permission_denied", 404: "not_found", 429: "rate_limited"}.get(status, "upstream_error")


# A write that times out may still have happened; this much time is kept back so the result can be looked up afterwards.
WRITE_RESERVE_S = 4.0


def _call_timeout(reserve=0.0):
    left = _time_left() - reserve
    if left < 1.0:
        raise GoogleError("The operation ran out of time before Google answered. Nothing more was attempted; check the result before retrying.", "timeout")
    return min(20.0, left)


# ---------------------------------------------------------------- Google HTTP helpers

def _google(method, url, token, params=None, body=None, raw=False, data=None, content_type=None, with_type=False):
    if params:
        url += ("&" if "?" in url else "?") + urllib.parse.urlencode({k: v for k, v in params.items() if v is not None}, doseq=True)
    if data is None and body is not None:
        data, content_type = json.dumps(body).encode(), "application/json"
    headers = {"Authorization": f"Bearer {token}"}
    if data is not None:
        headers["Content-Type"] = content_type or "application/json"
    write = method != "GET"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=_call_timeout(WRITE_RESERVE_S if write else 0.0)) as resp:
            payload = resp.read(2_000_000)
            answer_type = resp.headers.get("Content-Type", "")
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")
        try:
            detail = json.loads(detail)["error"]["message"]
        except (ValueError, KeyError, TypeError):
            detail = detail[:300]
        raise GoogleError(f"Google API error {e.code}: {detail}", _kind(e.code), e.code)
    except (socket.timeout, TimeoutError, urllib.error.URLError):
        if write:
            raise GoogleError("Google did not answer in time. The change may or may not have been made; check before trying again.", "outcome_unknown")
        raise GoogleError("Google did not answer in time.", "timeout")
    if with_type:
        return payload, answer_type
    if raw:
        return payload
    return json.loads(payload) if payload else {}


def _q(value):
    return urllib.parse.quote(str(value), safe="")


def _text(value, limit=MAX_TEXT):
    return value if len(value) <= limit else value[:limit] + f"\n... [truncated, {len(value)} chars total]"


def _window(value, offset=0, limit=MAX_TEXT):
    """A slice of long text; says where to continue when more is left."""
    offset = max(0, int(offset or 0))
    part = value[offset:offset + limit]
    end = offset + len(part)
    return part + (f"\n... [more text: call again with offset={end}; {len(value)} chars total]" if end < len(value) else "")


def _paged(items, token):
    """Plain list when everything fit; otherwise the list plus the token that fetches the next page."""
    return items if not token else {"items": items, "next_page_token": token}


def _json(value):
    return json.dumps(value, ensure_ascii=False, indent=2)


def _obj(properties, required=()):
    return {"type": "object", "properties": properties, "required": list(required)}


S = {"type": "string"}


def _s(description):
    return {"type": "string", "description": description}


def _tool(name, description, schema, fn, read_only):
    return {"name": name, "description": description, "inputSchema": schema, "fn": fn, "readOnly": read_only}


# ---------------------------------------------------------------- Calendar

CAL = "https://www.googleapis.com/calendar/v3"


def _event_summary(e):
    start, end = e.get("start", {}), e.get("end", {})
    return {
        "id": e.get("id"),
        "summary": e.get("summary"),
        "start": start.get("dateTime") or start.get("date"),
        "end": end.get("dateTime") or end.get("date"),
        "location": e.get("location"),
        "description": (e.get("description") or "")[:500] or None,
        "attendees": [{"email": a.get("email"), "response": a.get("responseStatus")} for a in e.get("attendees", [])] or None,
        "link": e.get("htmlLink"),
    }


def cal_list_calendars(a, t):
    items = _google("GET", f"{CAL}/users/me/calendarList", t).get("items", [])
    return _json([{"id": c["id"], "name": c.get("summary"), "primary": c.get("primary", False), "timezone": c.get("timeZone")} for c in items])


def cal_list_events(a, t):
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    data = _google("GET", f"{CAL}/calendars/{_q(a.get('calendar_id', 'primary'))}/events", t, {
        "timeMin": a.get("time_min", now), "timeMax": a.get("time_max"), "q": a.get("query"),
        "maxResults": min(int(a.get("max_results", 15)), 50), "singleEvents": "true", "orderBy": "startTime",
        "pageToken": a.get("page_token")})
    return _json(_paged([_event_summary(e) for e in data.get("items", [])], data.get("nextPageToken")))


def cal_get_event(a, t):
    return _json(_event_summary(_google("GET", f"{CAL}/calendars/{_q(a.get('calendar_id', 'primary'))}/events/{_q(a['event_id'])}", t)))


def _when(value, tz):
    if "T" in value:
        out = {"dateTime": value}
        if tz:
            out["timeZone"] = tz
        return out
    return {"date": value}


def cal_create_event(a, t):
    body = {"summary": a["summary"], "start": _when(a["start"], a.get("timezone")), "end": _when(a["end"], a.get("timezone"))}
    for key in ("description", "location"):
        if a.get(key):
            body[key] = a[key]
    if a.get("attendees"):
        body["attendees"] = [{"email": x} for x in a["attendees"]]
    try:
        e = _google("POST", f"{CAL}/calendars/{_q(a.get('calendar_id', 'primary'))}/events", t,
                    {"sendUpdates": "all" if a.get("send_invites") else "none"}, body)
    except GoogleError as err:
        if err.kind != "outcome_unknown":
            raise
        found = _find_event(a, t)
        if found:
            return _json(_event_summary(found)) + "\nGoogle was slow to answer, but the event exists."
        if found is None:
            raise GoogleError("Google did not answer in time and the event could not be confirmed. Look at the calendar before creating it again.", "outcome_unknown")
        raise GoogleError("Google did not answer in time and no matching event was found, so it was most likely not created.", "outcome_unknown")
    return _json(_event_summary(e))


def _find_event(a, t):
    """The event with this title and start after a timed-out create: the event, False when none matches, None when the lookup itself failed."""
    start, end = a["start"], a["end"]
    try:
        items = _google("GET", f"{CAL}/calendars/{_q(a.get('calendar_id', 'primary'))}/events", t, {
            "timeMin": start if "T" in start else start + "T00:00:00Z", "timeMax": end if "T" in end else end + "T23:59:59Z",
            "q": a["summary"], "singleEvents": "true", "maxResults": 10}).get("items", [])
    except RuntimeError:
        return None
    wanted = _when(start, a.get("timezone"))
    for e in items:
        if e.get("summary") == a["summary"] and (e.get("start", {}).get("dateTime") or e.get("start", {}).get("date")) is not None:
            if "date" in wanted or e["start"].get("dateTime", "")[:16] == start[:16]:
                return e
    return False


def cal_delete_event(a, t):
    _google("DELETE", f"{CAL}/calendars/{_q(a.get('calendar_id', 'primary'))}/events/{_q(a['event_id'])}", t)
    return "Event deleted."


def cal_respond(a, t):
    cid = _q(a.get("calendar_id", "primary"))
    url = f"{CAL}/calendars/{cid}/events/{_q(a['event_id'])}"
    event = _google("GET", url, t)
    attendees = event.get("attendees", [])
    mine = [x for x in attendees if x.get("self")]
    if not mine:
        raise RuntimeError("You are not an attendee of this event.")
    mine[0]["responseStatus"] = a["response"]
    return _json(_event_summary(_google("PATCH", url, t, {"sendUpdates": "none"}, {"attendees": attendees})))


CALENDAR_TOOLS = [
    _tool("list_calendars", "List the user's calendars.", _obj({}), cal_list_calendars, True),
    _tool("list_events", "List upcoming events, soonest first. Times are RFC3339 like 2026-10-20T09:00:00+05:30.",
          _obj({"calendar_id": _s("Default primary"), "time_min": _s("Start of range, default now"), "time_max": _s("End of range"),
                "query": _s("Free-text filter"), "max_results": {"type": "integer", "description": "1-50, default 15"},
                "page_token": _s("next_page_token from the previous call, to get the next page")}), cal_list_events, True),
    _tool("get_event", "Get one event by id.", _obj({"event_id": S, "calendar_id": S}, ["event_id"]), cal_get_event, True),
    _tool("create_event", "Create an event. start/end are RFC3339 date-times, or YYYY-MM-DD for all-day.",
          _obj({"summary": S, "start": S, "end": S, "timezone": _s("IANA zone, e.g. Asia/Kolkata"), "description": S, "location": S,
                "attendees": {"type": "array", "items": S, "description": "Attendee emails"},
                "send_invites": {"type": "boolean", "description": "Email invitations; default false"}, "calendar_id": S},
               ["summary", "start", "end"]), cal_create_event, False),
    _tool("delete_event", "Delete an event by id.", _obj({"event_id": S, "calendar_id": S}, ["event_id"]), cal_delete_event, False),
    _tool("respond_to_event", "Answer an invitation: accepted, declined or tentative.",
          _obj({"event_id": S, "response": {"type": "string", "enum": ["accepted", "declined", "tentative"]}, "calendar_id": S},
               ["event_id", "response"]), cal_respond, False),
]


# ---------------------------------------------------------------- Gmail

GM = "https://gmail.googleapis.com/gmail/v1/users/me"


def _header(msg, name):
    for h in msg.get("payload", {}).get("headers", []):
        if h["name"].lower() == name.lower():
            return h["value"]
    return None


def _b64(data):
    return base64.urlsafe_b64decode(data + "=" * (-len(data) % 4)).decode("utf-8", "replace")


def _body_text(payload):
    """Plain text of a message payload; falls back to tag-stripped HTML."""
    plain, html = [], []

    def walk(part):
        mime, data = part.get("mimeType", ""), part.get("body", {}).get("data")
        if data and mime == "text/plain":
            plain.append(_b64(data))
        elif data and mime == "text/html":
            html.append(_b64(data))
        for sub in part.get("parts", []):
            walk(sub)

    walk(payload)
    if plain:
        return "\n".join(plain)
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", " ", "\n".join(html))).strip()


GM_BATCH = "https://gmail.googleapis.com/batch/gmail/v1"
META_QUERY = urllib.parse.urlencode({"format": "metadata", "metadataHeaders": ["From", "Subject", "Date"]}, doseq=True)


def _parse_batch(payload, content_type):
    """{index: message} from a multipart/mixed batch answer; parts that failed or cannot be read are left out."""
    boundary = re.search(r"boundary=\"?([^;\"\s]+)", content_type or "")
    if not boundary:
        return {}
    found = {}
    for chunk in payload.decode("utf-8", "replace").split("--" + boundary.group(1)):
        ident, status = re.search(r"Content-ID:\s*<response-item(\d+)>", chunk, re.I), re.search(r"HTTP/\d(?:\.\d)? (\d+)", chunk)
        if not ident or not status or status.group(1) != "200":
            continue
        start, end = chunk.find("{", status.end()), chunk.rfind("}")
        try:
            found[int(ident.group(1))] = json.loads(chunk[start:end + 1])
        except ValueError:
            continue
    return found


def _message_metadata(ids, t):
    """Headers of several messages with a single request to Gmail; any the batch did not return are fetched one by one."""
    boundary = "batch_ultron_7f3a"
    parts = "".join(f"--{boundary}\r\nContent-Type: application/http\r\nContent-ID: <item{i}>\r\n\r\n"
                    f"GET /gmail/v1/users/me/messages/{_q(mid)}?{META_QUERY}\r\n" for i, mid in enumerate(ids))
    messages = {}
    try:
        payload, content_type = _google("POST", GM_BATCH, t, data=(parts + f"--{boundary}--\r\n").encode(),
                                        content_type=f"multipart/mixed; boundary={boundary}", with_type=True)
        messages = _parse_batch(payload, content_type)
    except GoogleError as e:
        if e.kind == "auth_rejected":
            raise  # the token is bad for every message; fetching them one by one would fail the same way

    def one(i):
        try:
            return _google("GET", f"{GM}/messages/{_q(ids[i])}", t, {"format": "metadata", "metadataHeaders": ["From", "Subject", "Date"]})
        except RuntimeError:
            return None  # out of time or refused for this one message; the rest are still useful

    missing = [i for i in range(len(ids)) if i not in messages]
    if missing:
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            for i, msg in zip(missing, pool.map(one, missing)):
                if msg is not None:
                    messages[i] = msg
    return [messages[i] for i in range(len(ids)) if i in messages]


def gm_search(a, t):
    listing = _google("GET", f"{GM}/messages", t, {"q": a.get("query"), "maxResults": min(int(a.get("max_results", 10)), 20),
                                                   "pageToken": a.get("page_token")})
    ids, next_token = listing.get("messages", []), listing.get("nextPageToken")
    found = [{"id": msg["id"], "thread_id": msg.get("threadId"), "from": _header(msg, "From"), "subject": _header(msg, "Subject"),
              "date": _header(msg, "Date"), "snippet": msg.get("snippet"), "unread": "UNREAD" in msg.get("labelIds", [])}
             for msg in (_message_metadata([m["id"] for m in ids], t) if ids else [])]
    if len(found) < len(ids):
        return _json({"messages": found, "next_page_token": next_token,
                      "note": f"Only {len(found)} of {len(ids)} messages could be read in time."})
    return _json(_paged(found, next_token))


def gm_get(a, t):
    msg = _google("GET", f"{GM}/messages/{_q(a['message_id'])}", t, {"format": "full"})
    return _json({"id": msg["id"], "from": _header(msg, "From"), "to": _header(msg, "To"), "cc": _header(msg, "Cc"),
                  "subject": _header(msg, "Subject"), "date": _header(msg, "Date"), "labels": msg.get("labelIds"),
                  "body": _window(_body_text(msg.get("payload", {})), a.get("offset"), 8000)})


def gm_draft(a, t):
    mail = EmailMessage()
    mail["To"], mail["Subject"] = a["to"], a["subject"]
    if a.get("cc"):
        mail["Cc"] = a["cc"]
    if a.get("thread_id"):
        # A reply must point at the message it answers, or mail apps will not thread it.
        thread = _google("GET", f"{GM}/threads/{_q(a['thread_id'])}", t,
                         {"format": "metadata", "metadataHeaders": ["Message-ID", "References", "Subject"]})
        last = (thread.get("messages") or [{}])[-1]
        message_id = _header(last, "Message-ID")
        if message_id:
            mail["In-Reply-To"] = message_id
            mail["References"] = ((_header(last, "References") or "") + " " + message_id).strip()
        original = _header(thread.get("messages", [{}])[0], "Subject")
        if original:
            del mail["Subject"]
            mail["Subject"] = original if original.lower().startswith("re:") else "Re: " + original
    mail.set_content(a["body"])
    raw = base64.urlsafe_b64encode(mail.as_bytes()).decode()
    body = {"message": {"raw": raw}}
    if a.get("thread_id"):
        body["message"]["threadId"] = a["thread_id"]
    draft = _google("POST", f"{GM}/drafts", t, body=body)
    return f"Draft saved (id {draft.get('id')}). It was not sent; the user can review and send it from Gmail."


def gm_labels(a, t):
    return _json([{"id": x["id"], "name": x["name"]} for x in _google("GET", f"{GM}/labels", t).get("labels", [])])


GMAIL_TOOLS = [
    _tool("search_messages", "Search Gmail with Gmail search syntax (from:, subject:, is:unread, newer_than:7d...). Empty query = latest mail.",
          _obj({"query": S, "max_results": {"type": "integer", "description": "1-20, default 10"},
                "page_token": _s("next_page_token from the previous call, to get the next page")}), gm_search, True),
    _tool("get_message", "Read one message (headers and text body) by id.", _obj({"message_id": S, "offset": {"type": "integer", "description": "Start of the body text, to read a long mail in parts"}}, ["message_id"]), gm_get, True),
    _tool("create_draft", "Save an email draft. It is never sent automatically.",
          _obj({"to": S, "subject": S, "body": S, "cc": S, "thread_id": _s("Reply inside this thread")}, ["to", "subject", "body"]), gm_draft, False),
    _tool("list_labels", "List Gmail labels.", _obj({}), gm_labels, True),
]


# ---------------------------------------------------------------- Drive

DR = "https://www.googleapis.com/drive/v3"
FILE_FIELDS = "id,name,mimeType,modifiedTime,size,webViewLink"
# Files that live in a shared drive are invisible to Drive unless the request says it understands them.
ALL_DRIVES = {"supportsAllDrives": "true"}
ALL_DRIVES_LIST = {"supportsAllDrives": "true", "includeItemsFromAllDrives": "true", "corpora": "allDrives"}


def _files(data):
    return _json(_paged([{k: f.get(k) for k in ("id", "name", "mimeType", "modifiedTime", "size", "webViewLink")} for f in data.get("files", [])],
                        data.get("nextPageToken")))


def dr_search(a, t):
    if a.get("raw_query"):
        q = a["raw_query"]
    else:
        text = a["query"].replace("\\", "\\\\").replace("'", "\\'")
        q = f"fullText contains '{text}' and trashed = false"
    return _files(_google("GET", f"{DR}/files", t, {"q": q, "pageSize": min(int(a.get("max_results", 10)), 25), "pageToken": a.get("page_token"),
                                    "fields": f"nextPageToken,files({FILE_FIELDS})", **ALL_DRIVES_LIST}))


def dr_recent(a, t):
    return _files(_google("GET", f"{DR}/files", t, {"q": "trashed = false", "orderBy": "modifiedTime desc",
                                                    "pageSize": min(int(a.get("max_results", 10)), 25), "pageToken": a.get("page_token"),
                                                    "fields": f"nextPageToken,files({FILE_FIELDS})", **ALL_DRIVES_LIST}))


def dr_meta(a, t):
    return _json(_google("GET", f"{DR}/files/{_q(a['file_id'])}", t, {"fields": FILE_FIELDS + ",owners(displayName,emailAddress),parents", **ALL_DRIVES}))


def dr_read(a, t):
    fid = _q(a["file_id"])
    meta = _google("GET", f"{DR}/files/{fid}", t, {"fields": "name,mimeType,size", **ALL_DRIVES})
    mime = meta["mimeType"]
    export = {"application/vnd.google-apps.document": "text/plain", "application/vnd.google-apps.presentation": "text/plain",
              "application/vnd.google-apps.spreadsheet": "text/csv"}.get(mime)
    if export:
        raw = _google("GET", f"{DR}/files/{fid}/export", t, {"mimeType": export}, raw=True)
    elif mime.startswith("text/") or mime in ("application/json", "application/xml"):
        raw = _google("GET", f"{DR}/files/{fid}", t, {"alt": "media", **ALL_DRIVES}, raw=True)
    else:
        return f"'{meta['name']}' is a {mime} file; only Google Docs/Sheets/Slides and text files can be read here."
    return f"# {meta['name']}\n" + _window(raw.decode("utf-8", "replace"), a.get("offset"))


def dr_create(a, t):
    boundary = "mcpboundary7f3a"
    meta = json.dumps({"name": a["name"], "mimeType": a.get("mime_type", "text/plain")})
    payload = (f"--{boundary}\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n{meta}\r\n"
               f"--{boundary}\r\nContent-Type: {a.get('mime_type', 'text/plain')}\r\n\r\n{a['content']}\r\n--{boundary}--").encode()
    started = datetime.now(timezone.utc)
    try:
        created = _google("POST", "https://www.googleapis.com/upload/drive/v3/files", t,
                          {"uploadType": "multipart", "fields": FILE_FIELDS, **ALL_DRIVES}, data=payload,
                          content_type=f"multipart/related; boundary={boundary}")
    except GoogleError as err:
        if err.kind != "outcome_unknown":
            raise
        name = a["name"].replace("\\", "\\\\").replace("'", "\\'")
        try:
            files = _google("GET", f"{DR}/files", t, {
                "q": f"name = '{name}' and trashed = false and createdTime > '{(started - timedelta(seconds=60)).strftime('%Y-%m-%dT%H:%M:%SZ')}'",
                "orderBy": "createdTime desc", "pageSize": 1, "fields": f"files({FILE_FIELDS})", **ALL_DRIVES_LIST}).get("files", [])
        except RuntimeError:
            raise GoogleError("Google did not answer in time and the file could not be confirmed. Look in Drive before creating it again.", "outcome_unknown")
        if files:
            return _json(files[0]) + "\nGoogle was slow to answer, but the file exists."
        raise GoogleError("Google did not answer in time and no new file with that name was found, so it was most likely not created.", "outcome_unknown")
    return _json(created)


DRIVE_TOOLS = [
    _tool("search_files", "Search Drive files by text (name and content).",
          _obj({"query": S, "raw_query": _s("Optional Drive query syntax, used instead of query"), "max_results": {"type": "integer"},
                "page_token": _s("next_page_token from the previous call, to get the next page")}, ["query"]), dr_search, True),
    _tool("list_recent_files", "List recently modified files.", _obj({"max_results": {"type": "integer"}, "page_token": _s("next_page_token from the previous call")}), dr_recent, True),
    _tool("get_file_metadata", "Get name, type, owners and link of a file.", _obj({"file_id": S}, ["file_id"]), dr_meta, True),
    _tool("read_file_content", "Read a Google Doc/Sheet/Slides or a plain text file as text.", _obj({"file_id": S, "offset": {"type": "integer", "description": "Start of the text, to read a long file in parts"}}, ["file_id"]), dr_read, True),
    _tool("create_file", "Create a new text file in Drive.",
          _obj({"name": S, "content": S, "mime_type": _s("Default text/plain")}, ["name", "content"]), dr_create, False),
]


# ---------------------------------------------------------------- Docs

DOCS = "https://docs.googleapis.com/v1/documents"


def _doc_text(content):
    out = []
    for el in content:
        if "paragraph" in el:
            out.append("".join(r.get("textRun", {}).get("content", "") for r in el["paragraph"].get("elements", [])))
        elif "table" in el:
            for row in el["table"].get("tableRows", []):
                out.append(" | ".join(_doc_text(c.get("content", [])).strip() for c in row.get("tableCells", [])) + "\n")
    return "".join(out)


def _flat_tabs(tabs):
    for tab in tabs:
        yield tab
        yield from _flat_tabs(tab.get("childTabs", []))


def docs_read(a, t):
    doc = _google("GET", f"{DOCS}/{_q(a['document_id'])}", t, {"includeTabsContent": "true"})
    tabs = list(_flat_tabs(doc.get("tabs", [])))
    if not tabs:
        return f"# {doc.get('title')}\n" + _window(_doc_text(doc.get("body", {}).get("content", [])), a.get("offset"))
    sections = [f"## Tab: {tab.get('tabProperties', {}).get('title', '')} (tab_id {tab.get('tabProperties', {}).get('tabId')})\n"
                + _doc_text(tab.get("documentTab", {}).get("body", {}).get("content", [])) for tab in tabs]
    return f"# {doc.get('title')}\n" + _window("\n".join(sections), a.get("offset"))


def docs_append(a, t):
    tab_id = a.get("tab_id")
    if tab_id:
        doc = _google("GET", f"{DOCS}/{_q(a['document_id'])}", t, {"includeTabsContent": "true"})
        tab = next((x for x in _flat_tabs(doc.get("tabs", [])) if x.get("tabProperties", {}).get("tabId") == tab_id), None)
        if tab is None:
            return f"status=not_found\nNo tab with tab_id {tab_id}. Use read_doc to list the tabs."
        location = {"index": tab["documentTab"]["body"]["content"][-1]["endIndex"] - 1, "tabId": tab_id}
    else:
        doc = _google("GET", f"{DOCS}/{_q(a['document_id'])}", t, {"fields": "body(content(endIndex))"})
        location = {"index": doc["body"]["content"][-1]["endIndex"] - 1}
    _google("POST", f"{DOCS}/{_q(a['document_id'])}:batchUpdate", t,
            body={"requests": [{"insertText": {"location": location, "text": a["text"]}}]})
    return "Text added at the end of the document."


def docs_replace(a, t):
    res = _google("POST", f"{DOCS}/{_q(a['document_id'])}:batchUpdate", t, body={"requests": [
        {"replaceAllText": {"containsText": {"text": a["find"], "matchCase": True}, "replaceText": a["replace"]}}]})
    return f"Replaced {res['replies'][0].get('replaceAllText', {}).get('occurrencesChanged', 0)} occurrence(s)."


DOCS_TOOLS = [
    _tool("read_doc", "Read a Google Doc as text.", _obj({"document_id": S, "offset": {"type": "integer", "description": "Start of the text, to read a long document in parts"}}, ["document_id"]), docs_read, True),
    _tool("append_text", "Add text at the end of a Google Doc.", _obj({"document_id": S, "text": S, "tab_id": _s("Optional. Add to this tab (ids are shown by read_doc); default is the first tab.")}, ["document_id", "text"]), docs_append, False),
    _tool("replace_text", "Replace every occurrence of a text in a Google Doc.",
          _obj({"document_id": S, "find": S, "replace": S}, ["document_id", "find", "replace"]), docs_replace, False),
]


# ---------------------------------------------------------------- Sheets

SH = "https://sheets.googleapis.com/v4/spreadsheets"
ROWS = {"type": "array", "items": {"type": "array", "items": S}, "description": "Rows of cell values"}


def sh_meta(a, t):
    data = _google("GET", f"{SH}/{_q(a['spreadsheet_id'])}", t, {"fields": "properties.title,sheets.properties(title,sheetId,gridProperties)"})
    return _json({"title": data["properties"]["title"], "sheets": [
        {"name": s["properties"]["title"], "rows": s["properties"].get("gridProperties", {}).get("rowCount"),
         "columns": s["properties"].get("gridProperties", {}).get("columnCount")} for s in data.get("sheets", [])]})


def _rows_window(values, start, limit=MAX_TEXT):
    """Whole rows from `start` while they fit in `limit` characters, and the row to continue from (None when all were returned)."""
    start, rows, size = max(0, int(start or 0)), [], 0
    for i in range(start, len(values)):
        size += len(json.dumps(values[i], ensure_ascii=False)) + 1
        if rows and size > limit:
            return rows, i
        rows.append(values[i])
    return rows, None


def sh_get(a, t):
    render = a.get("value_render_option")
    if render not in (None, "FORMATTED_VALUE", "UNFORMATTED_VALUE", "FORMULA"):
        return "status=invalid\nvalue_render_option must be FORMATTED_VALUE, UNFORMATTED_VALUE or FORMULA."
    data = _google("GET", f"{SH}/{_q(a['spreadsheet_id'])}/values/{_q(a['range'])}", t, {"valueRenderOption": render})
    values = data.get("values", [])
    rows, more = _rows_window(values, a.get("start_row"))
    out = {"range": data.get("range"), "total_rows": len(values), "first_row": max(0, int(a.get("start_row") or 0)), "values": rows}
    if more is not None:
        out["next_start_row"] = more
    return _json(out)


def sh_update(a, t):
    res = _google("PUT", f"{SH}/{_q(a['spreadsheet_id'])}/values/{_q(a['range'])}?valueInputOption=USER_ENTERED", t, body={"values": a["values"]})
    return f"Updated {res.get('updatedCells', 0)} cell(s) in {res.get('updatedRange')}."


def sh_append(a, t):
    res = _google("POST", f"{SH}/{_q(a['spreadsheet_id'])}/values/{_q(a['range'])}:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS",
                  t, body={"values": a["values"]})
    return f"Added rows at {res.get('updates', {}).get('updatedRange')}."


SHEETS_TOOLS = [
    _tool("get_spreadsheet", "Get the title and tabs of a spreadsheet.", _obj({"spreadsheet_id": S}, ["spreadsheet_id"]), sh_meta, True),
    _tool("get_values", "Read cells, e.g. range Sheet1!A1:D20. A large range comes back in parts: call again with start_row set to next_start_row.",
          _obj({"spreadsheet_id": S, "range": S, "start_row": {"type": "integer", "description": "Row (0 = first row of the range) to start from, to read a long range in parts"},
                "value_render_option": {"type": "string", "enum": ["FORMATTED_VALUE", "UNFORMATTED_VALUE", "FORMULA"], "description": "Default FORMATTED_VALUE; FORMULA shows the formulas"}},
               ["spreadsheet_id", "range"]), sh_get, True),
    _tool("update_values", "Overwrite cells in a range.", _obj({"spreadsheet_id": S, "range": S, "values": ROWS}, ["spreadsheet_id", "range", "values"]), sh_update, False),
    _tool("append_values", "Add rows after the last row of a table.", _obj({"spreadsheet_id": S, "range": S, "values": ROWS}, ["spreadsheet_id", "range", "values"]), sh_append, False),
]


# ---------------------------------------------------------------- Slides

SL = "https://slides.googleapis.com/v1/presentations"


def _slide_text(elements, depth=0):
    out = []
    for el in elements:
        if depth < 6:  # shapes inside a group are the slide's text too
            out += _slide_text(el.get("elementGroup", {}).get("children", []), depth + 1)
        text = el.get("shape", {}).get("text", {}).get("textElements", [])
        joined = "".join(x.get("textRun", {}).get("content", "") for x in text).strip()
        if joined:
            out.append(joined)
        for row in el.get("table", {}).get("tableRows", []):
            for cell in row.get("tableCells", []):
                joined = "".join(x.get("textRun", {}).get("content", "") for x in cell.get("text", {}).get("textElements", [])).strip()
                if joined:
                    out.append(joined)
    return out


def sl_read(a, t):
    pres = _google("GET", f"{SL}/{_q(a['presentation_id'])}", t)
    every = pres.get("slides", [])
    first, last = max(1, int(a.get("from_slide") or 1)), int(a.get("to_slide") or len(every))
    slides = [{"slide": i + 1, "id": s["objectId"], "text": _slide_text(s.get("pageElements", []))}
              for i, s in enumerate(every) if first <= i + 1 <= last]
    return _window(_json({"title": pres.get("title"), "total_slides": len(every), "slides": slides}), a.get("offset"))


def sl_replace(a, t):
    res = _google("POST", f"{SL}/{_q(a['presentation_id'])}:batchUpdate", t, body={"requests": [
        {"replaceAllText": {"containsText": {"text": a["find"], "matchCase": True}, "replaceText": a["replace"]}}]})
    return f"Replaced {res['replies'][0].get('replaceAllText', {}).get('occurrencesChanged', 0)} occurrence(s)."


SLIDES_TOOLS = [
    _tool("read_presentation", "Read the text of the slides of a presentation (all by default).",
          _obj({"presentation_id": S, "from_slide": {"type": "integer", "description": "First slide to read, 1-based"},
                "to_slide": {"type": "integer", "description": "Last slide to read"},
                "offset": {"type": "integer", "description": "Start of the text, to read a long result in parts"}}, ["presentation_id"]), sl_read, True),
    _tool("replace_text", "Replace every occurrence of a text in a presentation.",
          _obj({"presentation_id": S, "find": S, "replace": S}, ["presentation_id", "find", "replace"]), sl_replace, False),
]

SERVICES = {"calendar": CALENDAR_TOOLS, "gmail": GMAIL_TOOLS, "drive": DRIVE_TOOLS, "docs": DOCS_TOOLS, "sheets": SHEETS_TOOLS, "slides": SLIDES_TOOLS}


# ---------------------------------------------------------------- MCP plumbing

def _public(tool):
    return {"name": tool["name"], "description": tool["description"], "inputSchema": tool["inputSchema"],
            "annotations": {"readOnlyHint": tool["readOnly"]}}


def _rpc(msg, tools, token):
    if not isinstance(msg, dict):
        return {"jsonrpc": "2.0", "id": None, "error": {"code": -32600, "message": "Invalid Request"}}
    method, mid = msg.get("method"), msg.get("id")
    if mid is None:
        return None
    if method == "initialize":
        result = {"protocolVersion": PROTOCOL_VERSION, "capabilities": {"tools": {}},
                  "serverInfo": {"name": "google-workspace", "version": "1.0.0"}}
    elif method == "ping":
        result = {}
    elif method == "tools/list":
        result = {"tools": [_public(t) for t in tools]}
    elif method == "tools/call":
        params = msg.get("params", {})
        arguments = params.get("arguments", {}) if isinstance(params, dict) else None
        if not isinstance(params, dict) or not isinstance(arguments, dict):
            return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32602, "message": "Invalid params"}}
        tool = next((t for t in tools if t["name"] == params.get("name")), None)
        if tool is None:
            return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32602, "message": "Unknown tool"}}
        structured = None
        if not token:
            text, is_error = "No Google access token was sent. Sign in to the Google service again.", True
            structured = {"error": {"kind": "auth_rejected", "status": 401}}
        else:
            try:
                text, is_error = tool["fn"](arguments, token), False
            except KeyError as e:
                text, is_error = f"Missing argument: {e}", True
            except GoogleError as e:
                text, is_error, structured = str(e), True, {"error": {"kind": e.kind, "status": e.status}}
            except Exception as e:  # surface failures to the model, not as protocol errors
                text, is_error = str(e), True
        result = {"content": [{"type": "text", "text": text}], "isError": is_error}
        if structured:
            result["structuredContent"] = structured
    else:
        return {"jsonrpc": "2.0", "id": mid, "error": {"code": -32601, "message": "Method not found"}}
    return {"jsonrpc": "2.0", "id": mid, "result": result}


MAX_BODY_CHARS = 1_000_000


def _resp(status, body=None):
    return {"statusCode": status, "headers": {"content-type": "application/json"}, "body": json.dumps(body) if body is not None else ""}


def lambda_handler(event, context):
    global _deadline
    _deadline = time.monotonic() + OPERATION_BUDGET_S
    secret = os.environ.get("ACCESS_TOKEN", "")
    parts = [p for p in event.get("rawPath", "").split("/") if p]
    if len(parts) != 2 or not secret or not hmac.compare_digest(parts[0], secret) or parts[1] not in SERVICES:
        return _resp(404, {"error": "not found"})
    if event["requestContext"]["http"]["method"] != "POST":
        return _resp(405, {"error": "use POST"})
    auth = (event.get("headers") or {}).get("authorization", "")
    token = auth[7:].strip() if auth.lower().startswith("bearer ") else ""
    raw = event.get("body") or ""
    if len(raw) > MAX_BODY_CHARS:
        return _resp(413, {"error": "request too large"})
    try:
        if event.get("isBase64Encoded"):
            raw = base64.b64decode(raw).decode("utf-8")
        body = json.loads(raw)
    except ValueError:  # includes bad base64 and bad UTF-8
        return _resp(400, {"error": "invalid json"})
    tools = SERVICES[parts[1]]
    if isinstance(body, list):
        out = [r for r in (_rpc(m, tools, token) for m in body) if r is not None]
        return _resp(200, out) if out else _resp(202)
    out = _rpc(body, tools, token)
    return _resp(200, out) if out else _resp(202)
