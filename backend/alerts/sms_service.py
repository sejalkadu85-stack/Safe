"""
SafeHer — Free SMS Service
===========================
Strategy (in priority order):
  1. Fast2SMS  — Indian free-tier SMS API (₹50 free credit, no card needed)
  2. MSG91     — Indian free-tier fallback
  3. Console   — Logs to terminal if no credentials set (safe fallback)

Sign up FREE at: https://www.fast2sms.com/
After signup → Dashboard → Dev API → Copy API Key → paste in .env as FAST2SMS_API_KEY
"""

import logging
import requests
from django.conf import settings

logger = logging.getLogger(__name__)


def send_sms_via_fast2sms(phone_number: str, message: str) -> bool:
    """
    Send SMS using Fast2SMS (Free Indian SMS API).
    Free tier: ₹50 credit on signup (~66 SMS), no credit card needed.
    Sign up: https://www.fast2sms.com/
    """
    api_key = getattr(settings, 'FAST2SMS_API_KEY', '')
    if not api_key:
        return False

    # Clean phone number — strip +91 or 0 prefix, keep 10 digits
    phone = phone_number.strip().lstrip('+').lstrip('91').lstrip('0')
    if len(phone) != 10 or not phone.isdigit():
        # Try keeping the number as-is if it looks like a full international number
        phone = phone_number.strip().replace('+', '').replace(' ', '')

    try:
        url = "https://www.fast2sms.com/dev/bulkV2"
        payload = {
            "route": "q",           # Quick SMS route
            "message": message,
            "language": "english",
            "flash": 0,
            "numbers": phone,
        }
        headers = {
            "authorization": api_key,
            "Content-Type": "application/json",
        }
        response = requests.post(url, json=payload, headers=headers, timeout=10)
        result = response.json()

        if result.get("return") is True:
            logger.info(f"[Fast2SMS] ✅ SMS sent to {phone_number}")
            return True
        else:
            logger.warning(f"[Fast2SMS] ❌ Failed: {result.get('message', 'Unknown error')}")
            return False

    except requests.exceptions.Timeout:
        logger.warning(f"[Fast2SMS] ⏱️ Timeout sending SMS to {phone_number}")
        return False
    except Exception as e:
        logger.warning(f"[Fast2SMS] ⚠️ Error: {e}")
        return False


def send_sms_via_msg91(phone_number: str, message: str) -> bool:
    """
    Send SMS using MSG91 (Indian SMS API — free trial available).
    Sign up: https://msg91.com/
    Dashboard → API → Copy Auth Key → paste in .env as MSG91_AUTH_KEY
    Also need: MSG91_SENDER_ID (6-char e.g. SFTHER) and MSG91_ROUTE (default 4)
    """
    auth_key = getattr(settings, 'MSG91_AUTH_KEY', '')
    sender_id = getattr(settings, 'MSG91_SENDER_ID', 'SFTHER')
    route = getattr(settings, 'MSG91_ROUTE', '4')

    if not auth_key:
        return False

    # Clean number — MSG91 needs full international format without +
    phone = phone_number.strip().replace('+', '').replace(' ', '')
    if not phone.startswith('91') and len(phone) == 10:
        phone = '91' + phone

    try:
        url = "https://api.msg91.com/api/v5/flow/"
        payload = {
            "template_id": getattr(settings, 'MSG91_TEMPLATE_ID', ''),
            "short_url": "0",
            "mobiles": phone,
            "VAR1": message[:100],   # template variable
        }
        # Fallback: use raw SMS endpoint if no template
        if not payload["template_id"]:
            url = "https://api.msg91.com/api/sendhttp.php"
            params = {
                "authkey": auth_key,
                "mobiles": phone,
                "message": message,
                "sender": sender_id,
                "route": route,
            }
            response = requests.get(url, params=params, timeout=10)
            if response.status_code == 200:
                logger.info(f"[MSG91] ✅ SMS sent to {phone_number}")
                return True
            else:
                logger.warning(f"[MSG91] ❌ Failed: {response.text}")
                return False

        headers = {"authkey": auth_key, "Content-Type": "application/json"}
        response = requests.post(url, json=payload, headers=headers, timeout=10)
        if response.status_code == 200:
            logger.info(f"[MSG91] ✅ SMS sent to {phone_number}")
            return True
        else:
            logger.warning(f"[MSG91] ❌ Failed: {response.text}")
            return False

    except Exception as e:
        logger.warning(f"[MSG91] ⚠️ Error: {e}")
        return False


def send_sms(phone_number: str, message: str) -> bool:
    """
    Main SMS dispatcher — tries each provider in order until one succeeds.
    Priority: Fast2SMS → MSG91 → Console log (fallback)
    """
    if not phone_number or not message:
        return False

    _log_sms_console(phone_number, message)

    # 1. Try Fast2SMS (best free Indian option)
    if getattr(settings, 'FAST2SMS_API_KEY', ''):
        if send_sms_via_fast2sms(phone_number, message):
            return True

    # 2. Try MSG91
    if getattr(settings, 'MSG91_AUTH_KEY', ''):
        if send_sms_via_msg91(phone_number, message):
            return True

    # 3. Console fallback (always visible in Django terminal)
    logger.info(f"[SMS FALLBACK] No SMS provider configured — message logged only.")
    return False


def _log_sms_console(phone_number: str, message: str):
    """Always prints SMS content to Django terminal for debugging safely."""
    border = "=" * 60
    try:
        print(f"\n{border}")
        print(f"  [SMS DISPATCH] -> {phone_number}")
        print(f"{'-' * 60}")
        print(f"  {message}")
        print(f"{border}\n")
    except Exception:
        pass
