"""Creates Ultron activation keys in the ultron-licenses table and prints them. Each key is shown only once: the table
stores just its SHA-256, so keep the printed key (or send it to the buyer) straight away.

The website store does the same PutItem when an order is paid; this script is for testing, giveaways and support.

  python scripts/create-license-key.py --plan yearly --days 365
  python scripts/create-license-key.py --plan lifetime --days 0 --count 5
  python scripts/create-license-key.py --plan monthly --days 30 --name "Asha Verma" --email asha@example.com --phone "+91 98765 43210"
  python scripts/create-license-key.py --revoke ULTR-XXXX-XXXX-XXXX-XXXX

Ultronapp (the ultronapp/ web app) does the same from a browser and also lists and edits keys.

Needs AWS credentials that can write the table (the jitin-admin profile does).
"""
import argparse
import hashlib
import secrets

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
TABLE = "ultron-licenses"


def new_key():
    body = "".join(secrets.choice(ALPHABET) for _ in range(16))
    return "-".join(["ULTR"] + [body[i:i + 4] for i in range(0, 16, 4)])


def key_hash(key):
    return hashlib.sha256(key.encode()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--plan", default="yearly", help="label stored with the key, such as monthly or yearly")
    parser.add_argument("--days", type=int, default=365, help="days from first activation; 0 never expires")
    parser.add_argument("--count", type=int, default=1)
    parser.add_argument("--email", default="", help="buyer email, kept for support")
    parser.add_argument("--name", default="", help="buyer name, shown in the app")
    parser.add_argument("--phone", default="", help="buyer phone, shown in the app")
    parser.add_argument("--revoke", metavar="KEY", help="switch an existing key off instead of creating new ones")
    parser.add_argument("--region", default="us-east-1")
    args = parser.parse_args()

    import boto3
    table = boto3.resource("dynamodb", region_name=args.region).Table(TABLE)

    if args.revoke:
        table.update_item(Key={"keyHash": key_hash(args.revoke.strip().upper())},
                          UpdateExpression="SET #s = :r", ConditionExpression="attribute_exists(keyHash)",
                          ExpressionAttributeNames={"#s": "status"}, ExpressionAttributeValues={":r": "revoked"})
        print(f"Revoked {args.revoke}")
        return

    for _ in range(args.count):
        key = new_key()
        item = {"keyHash": key_hash(key), "status": "active", "plan": args.plan, "durationDays": args.days, "expiresAt": 0}
        item["hint"] = key[-4:]
        for field in ("name", "email", "phone"):
            if getattr(args, field):
                item[field] = getattr(args, field)
        table.put_item(Item=item, ConditionExpression="attribute_not_exists(keyHash)")
        print(key)


if __name__ == "__main__":
    main()
