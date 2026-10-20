"""Tells the Ultron app whether a newer build exists and hands out a short-lived download link for it.

GET /<ACCESS_TOKEN>/latest  ->  contents of s3://BUCKET/latest.json plus `apkUrl`, a presigned link to the APK.
The bucket stays private (the APK holds connector secrets), so the link expires after 30 minutes.
The link uses S3 Transfer Acceleration (enabled on the bucket), so phones far from us-east-1 download
through the nearest AWS edge.

Env vars: BUCKET, ACCESS_TOKEN
"""
import hmac
import json
import os

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


def lambda_handler(event, context):
    parts = [p for p in event.get("rawPath", "").split("/") if p]
    token = os.environ.get("ACCESS_TOKEN", "")
    if len(parts) != 2 or parts[1] != "latest" or not token or not hmac.compare_digest(parts[0], token):
        return _resp(404, {"error": "not found"})
    try:
        info = json.loads(s3.get_object(Bucket=BUCKET, Key="latest.json")["Body"].read())
    except ClientError as e:
        if e.response["Error"]["Code"] in ("NoSuchKey", "404"):
            return _resp(404, {"error": "no release published yet"})
        raise
    info["apkUrl"] = s3_links.generate_presigned_url(
        "get_object", Params={"Bucket": BUCKET, "Key": info["apkKey"]}, ExpiresIn=1800)
    return _resp(200, info)
