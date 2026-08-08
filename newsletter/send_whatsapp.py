"""Send message chunks to WhatsApp via the free CallMeBot API.

Setup (one-time, do this yourself in WhatsApp):
 1. Save +34 644 84 71 66 as a contact (e.g. "CallMeBot").
 2. Send it the WhatsApp message: "I allow callmebot to send me messages"
 3. You'll receive an API key back. Store it as the CALLMEBOT_APIKEY GitHub secret,
    and your own phone number (with country code, e.g. +4915123456789) as CALLMEBOT_PHONE.
"""
import os
import time

import requests

CALLMEBOT_URL = "https://api.callmebot.com/whatsapp.php"


def send_messages(messages, delay_seconds=8):
    """Send all chunks and return how many were actually delivered.

    The caller uses the return value to decide whether the run counts as
    "sent today" - see newsletter/main.py.
    """
    phone = os.environ.get("CALLMEBOT_PHONE")
    apikey = os.environ.get("CALLMEBOT_APIKEY")
    if not phone or not apikey:
        raise RuntimeError(
            "CALLMEBOT_PHONE and CALLMEBOT_APIKEY must be set as GitHub secrets "
            "(Settings -> Secrets and variables -> Actions -> New repository secret). "
            "If they are missing, the Actions log shows them empty instead of '***'."
        )

    sent = 0
    for i, message in enumerate(messages):
        params = {"phone": phone, "text": message, "apikey": apikey}
        try:
            resp = requests.get(CALLMEBOT_URL, params=params, timeout=20)
            print(f"[whatsapp] message {i + 1}/{len(messages)} -> status {resp.status_code}: {resp.text[:200]}")
            if resp.status_code < 400:
                sent += 1
            else:
                print(f"[warn] CallMeBot rejected message {i + 1}/{len(messages)} (HTTP {resp.status_code}).")
        except Exception as exc:
            print(f"[warn] failed to send WhatsApp message {i + 1}/{len(messages)}: {exc}")

        if i < len(messages) - 1:
            time.sleep(delay_seconds)

    return sent
