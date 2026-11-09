"""Sends mail through Resend (https://resend.com).

The Lambdas build their mails in the shape Amazon SES takes (Source, Destination, Message, ReplyToAddresses), so
[ResendMail.send_email] accepts those arguments and turns them into a Resend request. Nothing else changes for the
callers: a rejected mail raises, and the callers already treat that as "emailed: false".

The API key lives in SSM Parameter Store (a SecureString named by RESEND_API_KEY_PARAM) and is read once per container.
It is never put in an environment variable, a template or a log line. This file is the same in every Lambda that sends mail.
"""
import json
import os
import urllib.error
import urllib.request

API_URL = "https://api.resend.com/emails"
TIMEOUT_SECONDS = 8

_api_key = None


def _key():
    global _api_key
    if _api_key is None:
        import boto3  # Lambda runtime has it; imported here so the offline tests run without it.
        name = os.environ["RESEND_API_KEY_PARAM"]
        _api_key = boto3.client("ssm").get_parameter(Name=name, WithDecryption=True)["Parameter"]["Value"]
    return _api_key


def to_payload(params):
    """The Resend request body for SES-style [params]."""
    body = params["Message"]["Body"]
    payload = {
        "from": params["Source"],
        "to": list(params["Destination"]["ToAddresses"]),
        "subject": params["Message"]["Subject"]["Data"],
        "text": body["Text"]["Data"],
    }
    if "Html" in body:
        payload["html"] = body["Html"]["Data"]
    if params.get("ReplyToAddresses"):
        payload["reply_to"] = list(params["ReplyToAddresses"])
    return payload


class ResendMail:
    def send_email(self, **params):
        request = urllib.request.Request(
            API_URL,
            data=json.dumps(to_payload(params)).encode(),
            headers={
                "Authorization": f"Bearer {_key()}",
                "Content-Type": "application/json",
                # Resend sits behind a filter that refuses the default Python user agent.
                "User-Agent": "ultron-api/1.0",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
                response.read()
        except urllib.error.HTTPError as error:
            raise RuntimeError(f"Resend refused the mail: HTTP {error.code} {error.read()[:300]!r}") from None
