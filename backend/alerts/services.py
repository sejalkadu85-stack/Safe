import logging
from django.utils import timezone
from .models import TrustedContact, AlertRecipient, IncidentTimeline, SOSAlert

logger = logging.getLogger(__name__)


# ─── helpers ────────────────────────────────────────────────────────────────

def _resolve_contacts(alert, tier):
    """
    Return ONLY the TrustedContact rows that belong to alert.sender,
    have status=ACCEPTED, and match the requested escalation tier.

    This is the single authoritative gate: no notification ever reaches
    a person who is not in the victim's own saved emergency-contact list.
    """
    base_qs = TrustedContact.objects.filter(
        owner=alert.sender,           # ← ownership enforced
        status=TrustedContact.Status.ACCEPTED,
    ).select_related('contact_user')

    if tier == 1:
        contacts = base_qs.filter(tier=TrustedContact.Tier.PRIMARY)
        # Graceful fallback: if no primary contact saved, try any accepted contact
        if not contacts.exists():
            contacts = base_qs
        timeline_note = (
            f"SOS triggered. Tier 1: dispatching to Primary Guardian(s) "
            f"via SMS, Email & Push."
        )
    elif tier == 2:
        contacts = base_qs.filter(
            tier__in=[TrustedContact.Tier.SECONDARY, TrustedContact.Tier.FRIEND]
        )
        timeline_note = (
            "Escalated to Tier 2: dispatching to Secondary Guardian(s) & Friends."
        )
    else:
        contacts = TrustedContact.objects.none()
        timeline_note = "Escalated to Tier 3: dispatching to nearby Volunteers."

    return contacts, timeline_note


def _get_or_create_recipient(alert, contact):
    """
    Get-or-create the AlertRecipient row for this (alert, contact) pair.
    Returns (recipient_record, is_new).

    Idempotency rule: if the row already exists for this alert and contact,
    skip all notification channels — the SOS was already dispatched to this contact.
    """
    recipient_record = AlertRecipient.objects.filter(
        alert=alert,
        trusted_contact=contact
    ).first()

    if recipient_record:
        return recipient_record, False

    # Also check if recipient user already has a record for this alert (backward compat)
    if contact.contact_user:
        recipient_record = AlertRecipient.objects.filter(
            alert=alert,
            recipient=contact.contact_user
        ).first()
        if recipient_record:
            if not recipient_record.trusted_contact:
                recipient_record.trusted_contact = contact
                recipient_record.save(update_fields=['trusted_contact'])
            return recipient_record, False

    recipient_record = AlertRecipient.objects.create(
        alert=alert,
        recipient=contact.contact_user,
        trusted_contact=contact,
        notified_via_sms=contact.notify_via_sms,
        notified_via_push=contact.notify_via_push,
        notified_via_email=contact.notify_via_email,
        notified_at=timezone.now(),
    )
    return recipient_record, True


def _build_messages(alert, sender_name, location_str, category_name):
    """Build the SMS body and email subject/body from alert data."""
    sender_phone = (
        str(alert.sender.phone_number)
        if hasattr(alert.sender, 'phone_number') and alert.sender.phone_number
        else 'Not available'
    )
    sender_email = alert.sender.email or 'Not available'

    sms_body = (
        f"🚨 SafeHer EMERGENCY ALERT: {sender_name} triggered an SOS "
        f"for [{category_name}]! "
        f"{alert.message} "
        f"Live Google Maps: {location_str}"
    )

    email_subject = (
        f"🚨 [URGENT EMERGENCY] SafeHer SOS: {sender_name} "
        f"Needs Immediate Assistance! [{category_name}]"
    )
    email_body = (
        f"🚨 EMERGENCY DISTRESS ALERT — IMMEDIATE ACTION REQUIRED 🚨\n\n"
        f"A woman has triggered an emergency SOS signal through the SafeHer Safety Network.\n\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"VICTIM & INCIDENT DETAILS:\n"
        f"• Name: {sender_name}\n"
        f"• Phone Number: {sender_phone}\n"
        f"• Email: {sender_email}\n"
        f"• Incident ID: #{alert.id}\n"
        f"• Emergency Category: {category_name}\n"
        f"• Distress Message: {alert.message}\n"
        f"• Device Battery: {alert.battery_level or 'N/A'}%\n"
        f"• Time Triggered: {timezone.now().strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
        f"📍 LIVE GPS LOCATION & MAPS:\n"
        f"• Latitude: {alert.latitude}\n"
        f"• Longitude: {alert.longitude}\n"
        f"• Google Maps URL: {location_str}\n\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
        f"🚨 WHAT YOU MUST DO IMMEDIATELY:\n"
        f"1. CALL 112 (National Emergency Helpline) immediately.\n"
        f"2. CALL {sender_name} directly at {sender_phone}.\n"
        f"3. OPEN the Google Maps link above to locate her.\n"
        f"4. Coordinate with nearby responders via the SafeHer App.\n"
        f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
        f"— SafeHer Automated Emergency Dispatch System\n"
    )
    return sms_body, email_subject, email_body


# ─── main public function ────────────────────────────────────────────────────

def dispatch_sos_alert(alert, tier=None):
    """
    Broadcasts an SOS alert to the victim's own saved emergency contacts.

    Security guarantees enforced here:
      • contacts = owner (alert.sender) + status=ACCEPTED only
      • Each AlertRecipient row is linked to its authorising TrustedContact
      • Idempotency: if a recipient record already exists (SOS retry),
        no duplicate notification is sent
      • Delivery status (SENT / FAILED / SKIPPED) written back to DB
      • No hardcoded numbers, no all-user broadcasts
    """
    if tier is None:
        tier = alert.escalation_tier or 1

    sender_name   = alert.sender.get_full_name() or alert.sender.username
    location_str  = (
        alert.google_maps_url
        or (
            f"https://www.google.com/maps?q={alert.latitude},{alert.longitude}"
            if alert.latitude else "Location unavailable"
        )
    )
    category_name = (
        alert.get_category_display()
        if hasattr(alert, 'get_category_display')
        else 'Emergency Distress'
    )

    sms_body, email_subject, email_body = _build_messages(
        alert, sender_name, location_str, category_name
    )

    # ── 1. Resolve contacts (ownership-gated) ────────────────────────────
    contacts, timeline_note = _resolve_contacts(alert, tier)

    # ── 2. Record timeline event ─────────────────────────────────────────
    IncidentTimeline.objects.create(
        alert=alert,
        status='escalated' if tier > 1 else 'open',
        actor=alert.sender,
        actor_name=sender_name if tier == 1 else "Auto-Escalation Engine",
        note=timeline_note,
        timestamp=timezone.now()
    )

    if tier == 3:
        dispatch_to_volunteers(alert)
        return []

    recipients_created = []

    # ── 3. Dispatch to each authorised contact ───────────────────────────
    for contact in contacts:
        recipient_record, is_new = _get_or_create_recipient(alert, contact)
        recipients_created.append(recipient_record)

        if not is_new:
            # Already notified in a previous (duplicate) request — skip all channels
            contact_label = contact.name or (
                recipient_record.recipient.username if recipient_record.recipient else contact.phone_number
            )
            logger.info(
                f"[SOS #{alert.id}] Duplicate: {contact_label} "
                f"already notified. Skipping."
            )
            continue

        # Resolve phone/email from the contact record (never from raw request data)
        phone = contact.phone_number or (
            str(contact.contact_user.phone_number)
            if contact.contact_user and contact.contact_user.phone_number
            else None
        )
        dest_email = None
        if contact.contact_user and contact.contact_user.email:
            dest_email = contact.contact_user.email

        # ── SMS ─────────────────────────────────────────────────────────
        if contact.notify_via_sms and phone:
            sms_ok = send_sms_notification(phone, sms_body)
            if recipient_record:
                recipient_record.sms_status = (
                    AlertRecipient.DeliveryStatus.SENT
                    if sms_ok
                    else AlertRecipient.DeliveryStatus.FAILED
                )
                recipient_record.notified_via_sms = sms_ok
        elif recipient_record:
            recipient_record.sms_status = AlertRecipient.DeliveryStatus.SKIPPED

        # ── Email ────────────────────────────────────────────────────────
        if contact.notify_via_email and dest_email:
            email_ok = send_email_notification(dest_email, email_subject, email_body)
            if recipient_record:
                recipient_record.email_status = (
                    AlertRecipient.DeliveryStatus.SENT
                    if email_ok
                    else AlertRecipient.DeliveryStatus.FAILED
                )
                recipient_record.notified_via_email = email_ok
        elif recipient_record:
            recipient_record.email_status = AlertRecipient.DeliveryStatus.SKIPPED

        # ── Voice Call (backend log + VoIP fallback) ─────────────────────
        # Primary call is handled on-device by Android Intent.ACTION_CALL (free/SIM)
        if phone:
            call_result = make_emergency_call(
                phone_number=phone,
                victim_name=sender_name,
                location_url=location_str,
                alert_id=alert.id
            )
            if recipient_record:
                recipient_record.call_status = (
                    AlertRecipient.DeliveryStatus.SENT
                    if (call_result and call_result.get('success'))
                    else AlertRecipient.DeliveryStatus.FAILED
                )
        elif recipient_record:
            recipient_record.call_status = AlertRecipient.DeliveryStatus.SKIPPED

        # ── Push Notification ────────────────────────────────────────────
        if contact.notify_via_push and contact.contact_user:
            push_ok = send_push_notification(
                user=contact.contact_user,
                title=f"🚨 SOS: {sender_name} [{category_name}]",
                body=alert.message,
                data={
                    'alert_id':    str(alert.id),
                    'sender_id':   str(alert.sender_id),
                    'sender_name': sender_name,
                    'category':    getattr(alert, 'category', 'unsafe_situation'),
                    'latitude':    str(alert.latitude)  if alert.latitude  else '',
                    'longitude':   str(alert.longitude) if alert.longitude else '',
                    'maps_url':    alert.google_maps_url,
                    'tier':        str(tier),
                }
            )
            if recipient_record:
                recipient_record.push_status = (
                    AlertRecipient.DeliveryStatus.SENT
                    if push_ok
                    else AlertRecipient.DeliveryStatus.FAILED
                )
                recipient_record.notified_via_push = push_ok
        elif recipient_record:
            recipient_record.push_status = AlertRecipient.DeliveryStatus.SKIPPED

        # Persist delivery statuses
        if recipient_record and is_new:
            recipient_record.save(update_fields=[
                'sms_status', 'email_status', 'call_status', 'push_status',
                'notified_via_sms', 'notified_via_email', 'notified_via_push',
            ])

    # ── 4. Confirmation email to victim herself ──────────────────────────
    if alert.sender.email:
        confirmation_body = (
            f"Dear {sender_name},\n\n"
            f"Your SOS Emergency Alert has been broadcast to your emergency response network!\n\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n"
            f"• Incident ID: #{alert.id}\n"
            f"• Category: {category_name}\n"
            f"• Location: {location_str}\n"
            f"• Dispatched: SMS, Email, Push & Call to your saved contacts\n"
            f"• Timestamp: {timezone.now().strftime('%Y-%m-%d %H:%M:%S UTC')}\n"
            f"━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n"
            f"Guardians and nearby responders are alerted.\n"
            f"National Emergency Helpline: 112 (Toll Free, 24x7)\n"
        )
        send_email_notification(
            to_email=alert.sender.email,
            subject=f"SafeHer SOS Confirmation: Incident #{alert.id} Dispatched",
            body=confirmation_body
        )

    logger.info(
        f"[SOS #{alert.id}] Tier {tier} dispatched to "
        f"{len(recipients_created)} contact(s)."
    )
    return recipients_created


# ─── volunteer broadcast (Tier 3 only) ──────────────────────────────────────

def dispatch_to_volunteers(alert):
    """
    Notifies nearby online community volunteers of an escalated incident.
    Volunteers are NOT in the victim's contact list — they are community
    responders. No personal contact data is used here.
    """
    try:
        from community.models import Volunteer
        volunteers = Volunteer.objects.filter(
            is_available=True, is_verified=True
        ).select_related('user')
        sender_name = alert.sender.get_full_name() or alert.sender.username

        for vol in volunteers:
            send_push_notification(
                user=vol.user,
                title=f"🚨 Nearby SOS Alert: {sender_name}",
                body=(
                    f"High priority emergency assistance needed at "
                    f"{alert.address or 'User coordinates'}."
                ),
                data={
                    'alert_id':          str(alert.id),
                    'latitude':          str(alert.latitude)  if alert.latitude  else '',
                    'longitude':         str(alert.longitude) if alert.longitude else '',
                    'is_volunteer_mission': 'true',
                }
            )
    except Exception as e:
        logger.warning("Could not dispatch to volunteers: %s", e)


# ─── notification channel implementations ────────────────────────────────────

import sys


def safe_console_print(text):
    try:
        print(text)
    except Exception:
        try:
            enc = sys.stdout.encoding or 'ascii'
            print(text.encode(enc, errors='replace').decode(enc))
        except Exception:
            pass


def send_sms_notification(phone_number: str, message_text: str) -> bool:
    """
    Dispatches SMS to a phone number from the victim's saved contact list.
    Uses free providers in order: Fast2SMS → MSG91 → console log.
    Returns True if any provider accepted the message, False otherwise.
    """
    from .sms_service import send_sms
    logger.info(f"[SMS DISPATCH] To: {phone_number}")
    return send_sms(phone_number, message_text)


def make_emergency_call(phone_number: str, victim_name: str,
                        location_url: str, alert_id: int) -> dict:
    """
    Logs a backend call-attempt and optionally uses VoIP.ms.
    The primary call is handled on-device via Android Intent.ACTION_CALL (free/SIM).
    """
    from .call_service import initiate_voice_call
    logger.info(f"[CALL DISPATCH] To: {phone_number} for alert #{alert_id}")
    return initiate_voice_call(phone_number, victim_name, location_url, alert_id)


def send_push_notification(user, title: str, body: str, data=None) -> bool:
    """
    Sends a Firebase Cloud Messaging push notification to the user's device.
    Returns True if FCM accepted the request.
    """
    logger.info(
        f"[PUSH DISPATCH] To: {user.username} | Title: {title}"
    )
    try:
        from notifications.fcm_service import send_fcm_notification
        res = send_fcm_notification(
            user=user,
            title=title,
            body=body,
            notification_type='sos_alert',
            data=data or {},
            save_history=True
        )
        if isinstance(res, dict):
            return bool(res.get('success', False))
        return bool(res)
    except Exception as e:
        logger.warning("FCM dispatch failed: %s", e)
        return False



def send_email_notification(to_email: str, subject: str, body: str) -> bool:
    """
    Delivers an emergency email via Gmail SMTP (or console in dev).
    Returns True if Django's send_mail did not raise an exception.
    """
    logger.info(f"[EMAIL DISPATCH] To: {to_email} | Subject: {subject}")
    safe_console_print("\n==========================================")
    safe_console_print(f"[EMERGENCY EMAIL DISPATCH TO: {to_email}]")
    safe_console_print(f"Subject: {subject}")
    safe_console_print("------------------------------------------")
    safe_console_print(body)
    safe_console_print("==========================================\n")
    try:
        from django.core.mail import send_mail
        from django.conf import settings as django_settings
        send_mail(
            subject=subject,
            message=body,
            from_email=getattr(
                django_settings,
                'DEFAULT_FROM_EMAIL',
                'SafeHer Emergency <alerts@safeher.org>'
            ),
            recipient_list=[to_email],
            fail_silently=False
        )
        logger.info(f"[EMAIL] Delivered to {to_email}")
        return True
    except Exception as e:
        logger.warning(
            f"[EMAIL] SMTP failed ({e}) — "
            f"check EMAIL_HOST_USER and EMAIL_HOST_PASSWORD in .env"
        )
        return False
