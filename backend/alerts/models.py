from django.db import models
from django.conf import settings
from django.utils import timezone


class TrustedContact(models.Model):
    """
    A directional trust relationship between a Woman (owner) and a
    Guardian / Friend (contact_user). The contact must explicitly
    accept the request before they can receive live-location or SOS alerts.
    """

    class Relationship(models.TextChoices):
        PARENT = 'parent', 'Parent'
        SIBLING = 'sibling', 'Sibling'
        FRIEND = 'friend', 'Friend'
        SPOUSE = 'spouse', 'Spouse'
        COLLEAGUE = 'colleague', 'Colleague'
        OTHER = 'other', 'Other'

    class Status(models.TextChoices):
        PENDING = 'pending', 'Pending'
        ACCEPTED = 'accepted', 'Accepted'
        REJECTED = 'rejected', 'Rejected'
        BLOCKED = 'blocked', 'Blocked'

    class Tier(models.TextChoices):
        PRIMARY = 'primary', 'Primary Guardian'
        SECONDARY = 'secondary', 'Secondary Guardian'
        FRIEND = 'friend', 'Friend / Emergency Contact'
        OTHER = 'other', 'Other Contact'

    # The woman who is adding a contact
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='my_contacts',
        help_text="The woman who owns / initiated this trusted-contact relationship."
    )
    # The guardian / friend being added (optional if contact is not yet a registered user)
    contact_user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name='trusted_by',
        help_text="The guardian or friend who will receive alerts."
    )
    name = models.CharField(max_length=120, blank=True)
    phone_number = models.CharField(max_length=30, blank=True, db_index=True)
    tier = models.CharField(
        max_length=20,
        choices=Tier.choices,
        default=Tier.PRIMARY,
        db_index=True
    )
    relationship = models.CharField(
        max_length=20,
        choices=Relationship.choices,
        default=Relationship.OTHER
    )
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.ACCEPTED,
        db_index=True
    )
    is_verified = models.BooleanField(default=True)
    # Optional contact nickname / alias
    nickname = models.CharField(max_length=80, blank=True)
    # Notify this contact via SMS even without app
    notify_via_sms = models.BooleanField(default=True)
    notify_via_email = models.BooleanField(default=True)
    notify_via_push = models.BooleanField(default=True)

    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'Trusted Contact'
        verbose_name_plural = 'Trusted Contacts'
        ordering = ['tier', '-created_at']

    def __str__(self):
        target = self.name or (self.contact_user.username if self.contact_user else self.phone_number)
        return (
            f"{self.owner.username} → {target}"
            f" ({self.get_tier_display()}) [{self.get_status_display()}]"
        )

    @property
    def is_accepted(self):
        return self.status == self.Status.ACCEPTED


class SOSAlert(models.Model):
    """
    Emergency SOS Alert record triggered by a woman in distress.
    Broadcasts through escalating tiers (Primary Guardian -> Secondary Guardian -> Volunteers).
    """

    class Status(models.TextChoices):
        ACTIVE = 'active', 'Active (Help Needed)'
        RESOLVED = 'resolved', 'Resolved (Safe)'
        CANCELLED = 'cancelled', 'Cancelled (False Alarm)'

    class EmergencyCategory(models.TextChoices):
        HARASSMENT = 'harassment', 'Harassment'
        STALKING = 'stalking', 'Stalking'
        MEDICAL = 'medical', 'Medical Emergency'
        ACCIDENT = 'accident', 'Accident'
        UNSAFE_SITUATION = 'unsafe_situation', 'Unsafe Situation'
        OTHER = 'other', 'Other'

    class LifecycleStatus(models.TextChoices):
        OPEN = 'open', 'Open'
        ACTIVE = 'active', 'Active'
        ESCALATED = 'escalated', 'Escalated'
        RESOLVED = 'resolved', 'Resolved'
        CLOSED = 'closed', 'Closed'

    class TriggerType(models.TextChoices):
        MANUAL = 'manual', 'Manual SOS Button'
        SHAKE = 'shake', 'Shake Gesture'
        POWER_BUTTON = 'power_button', 'Power Button Rapid Press'
        AI_SOUND = 'ai_sound', 'AI Sound Detection'
        AI_MOTION = 'ai_motion', 'AI Motion Detection'
        CHECKIN_TIMEOUT = 'checkin_timeout', 'Check-in Timeout'
        JOURNEY_DEVIATION = 'journey_deviation', 'Safe Journey Deviation'
        BATTERY_LOW = 'battery_low', 'Low Battery Auto-Alert'

    sender = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='sos_alerts',
        help_text="The user in distress who triggered the alert."
    )
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.ACTIVE,
        db_index=True
    )
    lifecycle_status = models.CharField(
        max_length=20,
        choices=LifecycleStatus.choices,
        default=LifecycleStatus.OPEN,
        db_index=True
    )
    category = models.CharField(
        max_length=40,
        choices=EmergencyCategory.choices,
        default=EmergencyCategory.UNSAFE_SITUATION,
        db_index=True
    )
    escalation_tier = models.PositiveSmallIntegerField(default=1, db_index=True) # 1: Primary, 2: Secondary, 3: Volunteer
    escalated_at = models.DateTimeField(null=True, blank=True)
    assigned_responder = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='assigned_sos_incidents'
    )
    assigned_guardian = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='assigned_guardian_sos_incidents'
    )
    trigger_type = models.CharField(
        max_length=30,
        choices=TriggerType.choices,
        default=TriggerType.MANUAL,
        db_index=True
    )
    latitude = models.DecimalField(
        max_digits=9,
        decimal_places=6,
        null=True,
        blank=True
    )
    longitude = models.DecimalField(
        max_digits=9,
        decimal_places=6,
        null=True,
        blank=True
    )
    altitude = models.FloatField(null=True, blank=True)
    accuracy_meters = models.FloatField(null=True, blank=True)
    address = models.TextField(blank=True)
    message = models.TextField(
        default="EMERGENCY! I need immediate help. Here is my current location."
    )
    battery_level = models.PositiveSmallIntegerField(
        null=True,
        blank=True,
        help_text="Device battery percentage (0-100)"
    )
    cancellation_reason = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True, db_index=True)
    resolved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        verbose_name = 'SOS Alert'
        verbose_name_plural = 'SOS Alerts'
        ordering = ['-created_at']

    def __str__(self):
        return f"SOS #{self.id} by {self.sender.username} [{self.get_status_display()}] at {self.created_at}"

    @property
    def is_active(self):
        return self.status == self.Status.ACTIVE

    @property
    def google_maps_url(self):
        if self.latitude is not None and self.longitude is not None:
            return f"https://www.google.com/maps?q={self.latitude},{self.longitude}"
        return ""


class AlertRecipient(models.Model):
    """
    Tracks notification status and acknowledgment from each guardian/recipient
    for a specific SOS alert.

    Each record is tied to exactly one TrustedContact row (trusted_contact),
    proving that every notification was authorised by the victim's own saved
    emergency-contact list.  Per-channel delivery statuses let the app show
    accurate feedback without ever exposing credentials.
    """

    class RecipientStatus(models.TextChoices):
        NOTIFIED     = 'notified',     'Notified'
        DELIVERED    = 'delivered',    'Delivered'
        ACKNOWLEDGED = 'acknowledged', 'Acknowledged (Help on the way)'

    class DeliveryStatus(models.TextChoices):
        PENDING   = 'pending',   'Pending'
        SENT      = 'sent',      'Sent'
        DELIVERED = 'delivered', 'Delivered'
        FAILED    = 'failed',    'Failed'
        SKIPPED   = 'skipped',   'Skipped (channel disabled)'

    alert = models.ForeignKey(
        SOSAlert,
        on_delete=models.CASCADE,
        related_name='recipients'
    )
    recipient = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name='received_sos_alerts'
    )
    # The exact TrustedContact row that authorised this notification.
    # NULL only for legacy rows created before this field was added.
    trusted_contact = models.ForeignKey(
        'TrustedContact',
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='alert_notifications',
        help_text="The saved emergency-contact entry that authorised this alert."
    )
    status = models.CharField(
        max_length=20,
        choices=RecipientStatus.choices,
        default=RecipientStatus.NOTIFIED,
        db_index=True
    )
    # ── Per-channel delivery outcomes ───────────────────────────────────────
    sms_status = models.CharField(
        max_length=16,
        choices=DeliveryStatus.choices,
        default=DeliveryStatus.PENDING,
        db_index=True
    )
    email_status = models.CharField(
        max_length=16,
        choices=DeliveryStatus.choices,
        default=DeliveryStatus.PENDING,
        db_index=True
    )
    call_status = models.CharField(
        max_length=16,
        choices=DeliveryStatus.choices,
        default=DeliveryStatus.PENDING,
        db_index=True
    )
    push_status = models.CharField(
        max_length=16,
        choices=DeliveryStatus.choices,
        default=DeliveryStatus.PENDING,
        db_index=True
    )
    # ── Legacy boolean convenience flags (kept for backward-compat) ─────────
    notified_via_sms   = models.BooleanField(default=False)
    notified_via_push  = models.BooleanField(default=False)
    notified_via_email = models.BooleanField(default=False)

    notified_at    = models.DateTimeField(auto_now_add=True)
    acknowledged_at = models.DateTimeField(null=True, blank=True)
    guardian_note  = models.TextField(
        blank=True,
        help_text="Optional note from guardian, e.g. 'Calling police', 'I am 5 mins away'"
    )

    class Meta:
        verbose_name = 'Alert Recipient'
        verbose_name_plural = 'Alert Recipients'
        ordering = ['notified_at']

    def __str__(self):
        target = self.recipient.username if self.recipient else (
            self.trusted_contact.name or self.trusted_contact.phone_number
            if self.trusted_contact else "Unknown"
        )
        return f"Alert #{self.alert_id} -> {target} ({self.get_status_display()})"


class EvidenceFile(models.Model):
    """
    Audio, video, or photo evidence recorded secretly or automatically
    during an active alert or high-risk journey. Supports local secure storage
    and cloud storage (S3 / Cloudinary).
    """
    class FileType(models.TextChoices):
        AUDIO = 'audio', 'Audio Recording'
        VIDEO = 'video', 'Video Recording'
        IMAGE = 'image', 'Photo / Snapshot'

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='evidence_files'
    )
    sos_alert = models.ForeignKey(
        SOSAlert,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='evidence_files',
        help_text="Optional SOS alert this evidence is attached to."
    )
    file = models.FileField(
        upload_to='evidence/%Y/%m/%d/',
        null=True,
        blank=True,
        help_text="Uploaded media file"
    )
    file_url = models.CharField(
        max_length=500,
        blank=True,
        help_text="Direct URL if stored externally (Cloudinary, AWS S3) or relative media URL."
    )
    file_type = models.CharField(
        max_length=20,
        choices=FileType.choices,
        default=FileType.AUDIO,
        db_index=True
    )
    duration_seconds = models.FloatField(
        null=True,
        blank=True,
        help_text="Duration in seconds if audio or video."
    )
    file_size_bytes = models.BigIntegerField(
        null=True,
        blank=True,
        help_text="File size in bytes."
    )
    timestamp = models.DateTimeField(
        default=timezone.now,
        db_index=True
    )

    class Meta:
        verbose_name = 'Evidence File'
        verbose_name_plural = 'Evidence Files'
        ordering = ['-timestamp']

    def __str__(self):
        return f"Evidence #{self.id} [{self.get_file_type_display()}] by {self.user.username} at {self.timestamp}"

    @property
    def url(self):
        if self.file_url:
            return self.file_url
        if self.file:
            return self.file.url
        return ""


class IncidentTimeline(models.Model):
    """
    Chronological visual timeline of an incident's lifecycle:
    OPEN -> ACTIVE -> ESCALATED -> RESOLVED -> CLOSED.
    """
    alert = models.ForeignKey(
        SOSAlert,
        on_delete=models.CASCADE,
        related_name='timeline'
    )
    status = models.CharField(max_length=30, default='open', db_index=True)
    actor = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True
    )
    actor_name = models.CharField(max_length=120, default='System')
    note = models.TextField(blank=True)
    timestamp = models.DateTimeField(default=timezone.now, db_index=True)

    class Meta:
        verbose_name = 'Incident Timeline Event'
        verbose_name_plural = 'Incident Timeline Events'
        ordering = ['timestamp']

    def __str__(self):
        return f"Incident #{self.alert_id} - {self.status.upper()} by {self.actor_name} at {self.timestamp}"


class IncidentMessage(models.Model):
    """
    Real-time incident chat between User (Woman), Guardian, and Responder/Volunteer.
    """
    alert = models.ForeignKey(
        SOSAlert,
        on_delete=models.CASCADE,
        related_name='incident_messages'
    )
    sender = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True
    )
    sender_name = models.CharField(max_length=120)
    sender_role = models.CharField(max_length=30, default='woman')  # woman, guardian, volunteer, admin
    content = models.TextField()
    timestamp = models.DateTimeField(default=timezone.now, db_index=True)

    class Meta:
        verbose_name = 'Incident Message'
        verbose_name_plural = 'Incident Messages'
        ordering = ['timestamp']

    def __str__(self):
        return f"[{self.alert_id}] {self.sender_name} ({self.sender_role}): {self.content[:30]}"


class EscalationConfig(models.Model):
    """
    Admin-configurable response time settings for automatic SOS escalation.
    """
    primary_timeout_seconds = models.PositiveIntegerField(default=60)
    secondary_timeout_seconds = models.PositiveIntegerField(default=60)
    auto_volunteer_dispatch = models.BooleanField(default=True)
    escalation_enabled = models.BooleanField(default=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'Escalation Configuration'
        verbose_name_plural = 'Escalation Configurations'

    @classmethod
    def get_config(cls):
        config, _ = cls.objects.get_or_create(id=1)
        return config


