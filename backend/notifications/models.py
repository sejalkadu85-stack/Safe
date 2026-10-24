from django.db import models
from django.conf import settings
from django.utils import timezone


class DeviceToken(models.Model):
    """
    Stores FCM registration tokens for devices associated with a user.
    """
    DEVICE_TYPES = (
        ('android', 'Android'),
        ('ios', 'iOS'),
        ('web', 'Web'),
    )

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='device_tokens'
    )
    token = models.CharField(max_length=500, unique=True)
    device_type = models.CharField(max_length=20, choices=DEVICE_TYPES, default='android')
    device_name = models.CharField(max_length=100, blank=True, default='')
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-updated_at']

    def __str__(self):
        return f"{self.user.username} ({self.device_type}) - {self.token[:20]}..."


class Notification(models.Model):
    """
    In-app notification history and delivery record.
    """
    NOTIFICATION_TYPES = (
        ('sos_alert', 'SOS Alert'),
        ('location_share', 'Location Share'),
        ('new_message', 'New Message'),
        ('contact_request', 'Contact Request'),
        ('check_in', 'Check-In'),
    )

    recipient = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='notifications'
    )
    notification_type = models.CharField(
        max_length=30,
        choices=NOTIFICATION_TYPES
    )
    title = models.CharField(max_length=255)
    body = models.TextField()
    data = models.JSONField(default=dict, blank=True)
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        return f"[{self.get_notification_type_display()}] -> {self.recipient.username}: {self.title}"


class CheckInSchedule(models.Model):
    """
    Scheduled safety check-in reminders for users.
    """
    REPEAT_CHOICES = (
        ('none', 'One-time'),
        ('hourly', 'Hourly'),
        ('daily', 'Daily'),
    )

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='check_in_schedules'
    )
    title = models.CharField(max_length=150, default="Safety Check-In Reminder")
    message = models.TextField(default="Are you safe? Please tap here to check in.")
    scheduled_time = models.DateTimeField(help_text="Next scheduled time for the check-in reminder")
    repeat_interval = models.CharField(max_length=20, choices=REPEAT_CHOICES, default='none')
    is_active = models.BooleanField(default=True)
    is_sent = models.BooleanField(default=False)
    last_sent_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['scheduled_time']

    def __str__(self):
        return f"Check-In for {self.user.username} @ {self.scheduled_time} ({self.repeat_interval})"


class CheckInSession(models.Model):
    """
    Periodic Safety Check-In Timer (e.g. prompt every 15, 30, or 60 minutes).
    If the user fails to respond within 60 seconds of `next_due`, an emergency SOS
    is automatically triggered to their trusted contacts.
    """
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='checkin_sessions'
    )
    interval_minutes = models.PositiveIntegerField(
        default=15,
        help_text="Check-in frequency in minutes (e.g. 15, 30, 60 min)."
    )
    last_checkin = models.DateTimeField(
        default=timezone.now,
        help_text="Timestamp of the last confirmed safe response."
    )
    next_due = models.DateTimeField(
        db_index=True,
        help_text="Next time the user is required to respond 'I'm safe'."
    )
    is_active = models.BooleanField(
        default=True,
        db_index=True
    )
    alert_triggered = models.BooleanField(
        default=False,
        help_text="Whether SOS alert was triggered due to missed check-in."
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'Check-In Session'
        verbose_name_plural = 'Check-In Sessions'
        ordering = ['-created_at']

    def __str__(self):
        status = "Active" if self.is_active else "Stopped"
        return f"CheckInSession #{self.id} for {self.user.username} [{status}] interval: {self.interval_minutes}m, next due: {self.next_due}"

