"""Creates the three Ultron subscription plans in Razorpay and prints their ids for infra/api.yaml.

Run once per Razorpay mode (test keys first, live keys after KYC). The keys come from the environment, never from a file:
  RAZORPAY_KEY_ID=rzp_test_xxx RAZORPAY_KEY_SECRET=xxx python scripts/razorpay-plans.py

Razorpay plans cannot be edited, so a price change means new plans and a redeploy with the new ids.
"""
import base64
import json
import os
import sys
import urllib.request

PLANS = [
    # (parameter name in infra/api.yaml, Razorpay period, interval, amount in paise, label)
    ("RazorpayPlanMonthly", "monthly", 1, 19_900, "Ultron 1 month"),
    ("RazorpayPlanHalfYearly", "monthly", 6, 99_900, "Ultron 6 months"),
    ("RazorpayPlanYearly", "yearly", 1, 179_900, "Ultron 1 year"),
]


def main():
    key_id, key_secret = os.environ.get("RAZORPAY_KEY_ID", ""), os.environ.get("RAZORPAY_KEY_SECRET", "")
    if not (key_id and key_secret):
        sys.exit("Set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET first.")
    auth = base64.b64encode(f"{key_id}:{key_secret}".encode()).decode()
    for parameter, period, interval, amount, label in PLANS:
        body = {"period": period, "interval": interval,
                "item": {"name": label, "amount": amount, "currency": "INR", "description": label}}
        request = urllib.request.Request("https://api.razorpay.com/v1/plans", data=json.dumps(body).encode(), method="POST",
                                         headers={"Authorization": f"Basic {auth}", "Content-Type": "application/json"})
        with urllib.request.urlopen(request, timeout=15) as response:
            plan = json.loads(response.read())
        print(f"{parameter}={plan['id']}")


if __name__ == "__main__":
    main()
