import os
import logging
from django.conf import settings
from django.utils import timezone
from .models import DeviceToken, Notification

logger = logging.getLogger(__name__)

# Firebase App Initialization
_firebase_initialized = False

def initialize_firebase():
    global _firebase_initialized
    if _firebase_initialized:
        return True

    try:
        import firebase_admin
        from firebase_admin import credentials

        # Check for service account file
        cred_path = getattr(settings, 'FIREBASE_CREDENTIALS_PATH', None)
        if cred_path and os.path.exists(cred_path):
            if not firebase_admin._apps:
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            logger.info("Firebase Admin SDK initialized with credentials file: %s", cred_path)
            return True
        elif os.environ.get('GOOGLE_CLOUD_PROJECT'):
            if not firebase_admin._apps:
                firebase_admin.initialize_app()
            _firebase_initialized = True
            logger.info("Firebase Admin SDK initialized with GOOGLE_CLOUD_PROJECT.")
            return True
    except Exception as e:
        logger.warning("Could not initialize Firebase Admin SDK: %s", e)

    return False



def send_fcm_notification(user, title, body, notification_type, data=None, save_history=True):
    """
    Send push notification to all active devices of a user and record in Notification history.
    """
    data = data or {}

    # 1. Create database notification record
    notification_record = None
    if save_history:
        notification_record = Notification.objects.create(
            recipient=user,
            notification_type=notification_type,
            title=title,
            body=body,
            data=data
        )

    # 2. Collect user's active device tokens
    device_tokens = list(
        DeviceToken.objects.filter(user=user, is_active=True).values_list('token', flat=True)
    )

    if not device_tokens:
        logger.info("No active device tokens found for user %s (%s). Notification saved to history.", user.username, user.id)
        return {
            "success": True,
            "tokens_sent": 0,
            "notification_id": notification_record.id if notification_record else None,
            "mode": "stored_in_db"
        }

    # 3. Send via Firebase Cloud Messaging
    is_fcm_ready = initialize_firebase()
    if is_fcm_ready:
        try:
            from firebase_admin import messaging

            # Ensure all values in data payload are strings for FCM
            fcm_data = {str(k): str(v) for k, v in data.items()}
            fcm_data['notification_type'] = str(notification_type)
            if notification_record:
                fcm_data['notification_id'] = str(notification_record.id)

            message = messaging.MulticastMessage(
                notification=messaging.Notification(
                    title=title,
                    body=body,
                ),
                data=fcm_data,
                tokens=device_tokens,
            )

            response = messaging.send_each_for_multicast(message)

            # Deactivate any invalid/expired tokens
            if response.failure_count > 0:
                for idx, resp in enumerate(response.responses):
                    if not resp.success and resp.exception:
                        failed_token = device_tokens[idx]
                        logger.warning("FCM token failed for %s: %s", failed_token[:15], resp.exception)
                        DeviceToken.objects.filter(token=failed_token).update(is_active=False)

            return {
                "success": True,
                "tokens_sent": response.success_count,
                "failure_count": response.failure_count,
                "notification_id": notification_record.id if notification_record else None,
                "mode": "fcm_live"
            }
        except Exception as e:
            logger.error("Failed to dispatch FCM message: %s", e)

    # Simulated/logged delivery fallback when credentials are not configured
    logger.info(
        "[FCM Simulation] To: %s | Title: %s | Type: %s | Devices: %d",
        user.username, title, notification_type, len(device_tokens)
    )
    return {
        "success": True,
        "tokens_sent": len(device_tokens),
        "notification_id": notification_record.id if notification_record else None,
        "mode": "simulated"
    }


def process_check_in_schedule(schedule):
    """
    Processes a single due CheckInSchedule:
    Sends push notification to the user, updates last_sent_at, and schedules next run.
    """
    from datetime import timedelta

    title = schedule.title or "Safety Check-In Reminder"
    body = schedule.message or "Are you safe? Please tap here to check in."

    res = send_fcm_notification(
        user=schedule.user,
        title=title,
        body=body,
        notification_type='check_in',
        data={
            'schedule_id': str(schedule.id),
            'action': 'check_in_prompt',
        }
    )

    now = timezone.now()
    schedule.last_sent_at = now

    if schedule.repeat_interval == 'hourly':
        schedule.scheduled_time = now + timedelta(hours=1)
        schedule.is_sent = False
    elif schedule.repeat_interval == 'daily':
        schedule.scheduled_time = now + timedelta(days=1)
        schedule.is_sent = False
    else:
        schedule.is_sent = True
        schedule.is_active = False

    schedule.save()
    return res
