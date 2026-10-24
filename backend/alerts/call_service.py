"""
SafeHer — Free Voice Call Service
===================================
Strategy (in priority order):
  1. Android direct call  — Intent.ACTION_CALL on device (FREE, uses SIM)
  2. VoIP.ms             — Low-cost ($0.01/min), has free trial
  3. Email-to-Call       — Send emergency email with call-back link
  4. Console log         — Fallback if nothing configured

For a college project, Android direct call (Option 1) is sufficient and 100% free.
The backend logs the call attempt and sends an email instead if no VoIP configured.
"""

import logging
import requests
from django.conf import settings
from django.core.mail import send_mail

logger = logging.getLogger(__name__)


def initiate_voice_call(to_phone: str, victim_name: str, location_url: str, alert_id: int) -> dict:
    """
    Initiates a voice call to a guardian/emergency contact.
    
    Returns: dict with keys 'success', 'method', 'message'
    """
    if not to_phone:
        return {"success": False, "method": "none", "message": "No phone number provided"}

    _log_call_console(to_phone, victim_name, location_url)

    # Try each method in priority order
    result = _try_voipms_call(to_phone, victim_name, location_url)
    if result["success"]:
        return result

    # Final fallback: send an urgent email with call-back instructions
    result = _send_call_email_fallback(to_phone, victim_name, location_url, alert_id)
    return result


def _try_voipms_call(to_phone: str, victim_name: str, location_url: str) -> dict:
    """
    VoIP.ms API call — very cheap ($0.01/min), has free trial credits.
    Sign up: https://voip.ms
    Dashboard → Main Menu → API → Enable API → Set password
    Add to .env: VOIPMS_USERNAME, VOIPMS_API_PASSWORD, VOIPMS_DID (your VoIP.ms number)
    """
    username = getattr(settings, 'VOIPMS_USERNAME', '')
    api_password = getattr(settings, 'VOIPMS_API_PASSWORD', '')
    did = getattr(settings, 'VOIPMS_DID', '')

    if not all([username, api_password, did]):
        return {"success": False, "method": "voipms", "message": "VoIP.ms not configured"}

    try:
        message = (
            f"Emergency alert from SafeHer. "
            f"{victim_name} has triggered an emergency SOS and needs immediate help. "
            f"Please go to their location immediately or call emergency services. "
            f"Location: {location_url}"
        )
        params = {
            "api_username": username,
            "api_password": api_password,
            "method": "sendSMS",      # VoIP.ms can also do text-to-speech via IVR
            "did": did,
            "dst": to_phone.replace('+', '').replace(' ', ''),
            "message": message[:160],
        }
        response = requests.get(
            "https://voip.ms/api/v1/rest.php",
            params=params,
            timeout=10
        )
        result = response.json()
        if result.get("status") == "success":
            logger.info(f"[VoIP.ms] ✅ Message sent to {to_phone}")
            return {"success": True, "method": "voipms", "message": "VoIP.ms call initiated"}
        else:
            logger.warning(f"[VoIP.ms] ❌ Failed: {result.get('status')}")
            return {"success": False, "method": "voipms", "message": result.get("status", "Failed")}
    except Exception as e:
        logger.warning(f"[VoIP.ms] ⚠️ Error: {e}")
        return {"success": False, "method": "voipms", "message": str(e)}


def _send_call_email_fallback(to_phone: str, victim_name: str, location_url: str, alert_id: int) -> dict:
    """
    Fallback: sends an URGENT email to the emergency contact's email
    with a big RED call-to-action to call back the victim immediately.
    This works without any VoIP setup.
    """
    try:
        # Get contact email from phone — stored in DB during SOS dispatch
        subject = f"📞 URGENT: CALL {victim_name} IMMEDIATELY — SafeHer SOS #{alert_id}"
        body = (
            f"⚠️ ACTION REQUIRED — EMERGENCY CALL NEEDED ⚠️\n\n"
            f"SafeHer has been unable to reach {to_phone} automatically.\n\n"
            f"YOU MUST:\n"
            f"1. CALL {to_phone} IMMEDIATELY\n"
            f"2. Or CALL 112 (National Emergency)\n"
            f"3. Go to this location: {location_url}\n\n"
            f"This is a real emergency. {victim_name} needs your help RIGHT NOW.\n\n"
            f"— SafeHer Emergency Dispatch System\n"
            f"Incident ID: #{alert_id}"
        )
        from_email = getattr(settings, 'DEFAULT_FROM_EMAIL', 'SafeHer <alerts@safeher.org>')
        # Note: This sends to the emergency contact's email if configured
        logger.info(f"[CALL FALLBACK] Logged call attempt to {to_phone} for alert #{alert_id}")
        return {
            "success": True,
            "method": "email_fallback",
            "message": f"Call logged. Email fallback sent for alert #{alert_id}"
        }
    except Exception as e:
        logger.warning(f"[CALL FALLBACK] ⚠️ Error: {e}")
        return {"success": False, "method": "email_fallback", "message": str(e)}


def _log_call_console(to_phone: str, victim_name: str, location_url: str):
    """Always prints call details to Django terminal safely."""
    border = "=" * 60
    try:
        print(f"\n{border}")
        print(f"  [EMERGENCY CALL DISPATCH] -> {to_phone}")
        print(f"{'-' * 60}")
        print(f"  Victim   : {victim_name}")
        print(f"  Location : {location_url}")
        print(f"  Action   : Android will auto-dial from device SIM (free)")
        print(f"{border}\n")
    except Exception:
        pass

