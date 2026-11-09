"""Tells the Ultron app whether a newer build exists and hands out a short-lived download link for it.

GET /v1/releases/latest (JWT_AUTH=1, behind API Gateway) or GET /<ACCESS_TOKEN>/latest (legacy Function URL)  ->  contents of s3://BUCKET/latest.json plus `apkUrl`, a presigned link to the APK.
The bucket stays private and the link expires after 30 minutes.
The link uses S3 Transfer Acceleration (enabled on the bucket), so phones far from us-east-1 download
through the nearest AWS edge.

GET /v1/releases/download (public, throttled) redirects a browser to the same presigned link, for the website button.

Env vars: BUCKET, ACCESS_TOKEN
"""
import hmac
import json
import os

import appcheck
import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

BUCKET = os.environ["BUCKET"]
REGION = os.environ.get("AWS_REGION", "us-east-1")
s3 = boto3.client("s3", region_name=REGION, config=Config(signature_version="s3v4"))
# Download links only: the accelerated endpoint routes the phone through the nearest AWS edge.
s3_links = boto3.client("s3", region_name=REGION, config=Config(signature_version="s3v4", s3={"use_accelerate_endpoint": True}))


def _resp(status, body):
    return {"statusCode": status, "headers": {"content-type": "application/json"}, "body": json.dumps(body)}


def _routed(event):
    """Behind API Gateway the Firebase token was already checked there, so only the route matters (JWT_AUTH=1).
    Without it the older Function URL mode still needs the secret first path segment."""
    path = event.get("rawPath", "")
    if os.environ.get("JWT_AUTH") == "1":
        return path == "/v1/releases/latest"
    parts = [p for p in path.split("/") if p]
    token = os.environ.get("ACCESS_TOKEN", "")
    return len(parts) == 2 and parts[1] == "latest" and bool(token) and hmac.compare_digest(parts[0], token)


def _download(event):
    """Public browser download for the website. No sign-in: the app itself still needs an activation key to work.
    Answers with a redirect to a 30 minute presigned link, so the website needs no JavaScript or CORS."""
    try:
        info = json.loads(s3.get_object(Bucket=BUCKET, Key="latest.json")["Body"].read())
    except ClientError as e:
        if e.response["Error"]["Code"] in ("NoSuchKey", "404"):
            return _resp(404, {"error": "no release published yet"})
        raise
    url = s3_links.generate_presigned_url(
        "get_object",
        Params={"Bucket": BUCKET, "Key": info["apkKey"],
                "ResponseContentDisposition": 'attachment; filename="Ultron.apk"',
                "ResponseContentType": "application/vnd.android.package-archive"},
        ExpiresIn=1800)
    return {"statusCode": 302, "headers": {"location": url, "cache-control": "no-store"}, "body": ""}


def lambda_handler(event, context):
    if os.environ.get("JWT_AUTH") == "1" and event.get("rawPath", "") == "/v1/releases/download":
        return _download(event)
    if not _routed(event):
        return _resp(404, {"error": "not found"})
    if os.environ.get("JWT_AUTH") == "1":
        refused = appcheck.check(event)
        if refused:
            return refused
    try:
        info = json.loads(s3.get_object(Bucket=BUCKET, Key="latest.json")["Body"].read())
    except ClientError as e:
        if e.response["Error"]["Code"] in ("NoSuchKey", "404"):
            return _resp(404, {"error": "no release published yet"})
        raise
    info["apkUrl"] = s3_links.generate_presigned_url(
        "get_object", Params={"Bucket": BUCKET, "Key": info["apkKey"]}, ExpiresIn=1800)
    return _resp(200, info)
