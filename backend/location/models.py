from django.db import models
from django.conf import settings
from django.utils import timezone
from .utils import haversine_distance, is_inside_geofence


class LocationLog(models.Model):
    """
    Stores historical GPS location pings recorded from device sensors.
    """
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='location_logs'
    )
    latitude = models.DecimalField(
        max_digits=9,
        decimal_places=6
    )
    longitude = models.DecimalField(
        max_digits=9,
        decimal_places=6
    )
    accuracy = models.FloatField(
        null=True,
        blank=True,
        help_text="Horizontal accuracy radius in meters."
    )
    speed = models.FloatField(
        null=True,
        blank=True,
        help_text="Speed over ground in meters/second."
    )
    altitude = models.FloatField(
        null=True,
        blank=True,
        help_text="Altitude in meters above sea level."
    )
    heading = models.FloatField(
        null=True,
        blank=True,
        help_text="Bearing/Heading in degrees (0.0 to 360.0)."
    )
    battery_level = models.PositiveSmallIntegerField(
        null=True,
        blank=True,
        help_text="Battery percentage at the time of ping (0-100)."
    )
    is_mock = models.BooleanField(
        default=False,
        help_text="True if location ping was detected as a mock/spoofed provider."
    )
    timestamp = models.DateTimeField(
        default=timezone.now,
        db_index=True
    )

    class Meta:
        verbose_name = 'Location Log'
        verbose_name_plural = 'Location Logs'
        ordering = ['-timestamp']
        indexes = [
            models.Index(fields=['user', '-timestamp']),
        ]

    def __str__(self):
        return f"{self.user.username} @ ({self.latitude}, {self.longitude}) at {self.timestamp}"

    @property
    def google_maps_url(self):
        return f"https://www.google.com/maps?q={self.latitude},{self.longitude}"


class LocationShareSession(models.Model):
    """
    Temporary live location sharing session (e.g., 'Share my location for 2 hours').
    Permitted guardians can query live coordinates while this session is active.
    """
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='share_sessions'
    )
    shared_with = models.ManyToManyField(
        settings.AUTH_USER_MODEL,
        related_name='shared_locations_with_me',
        blank=True,
        help_text="Specific guardians allowed to track this session. If empty, all accepted contacts can track."
    )
    share_with_all_trusted = models.BooleanField(
        default=True,
        help_text="If True, all accepted contacts can view location."
    )
    duration_minutes = models.PositiveIntegerField(
        default=60,
        help_text="Duration of sharing in minutes."
    )
    started_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(db_index=True)
    is_active = models.BooleanField(
        default=True,
        db_index=True
    )
    note = models.CharField(
        max_length=200,
        blank=True,
        help_text="Optional context, e.g. 'Traveling home via Uber'."
    )

    class Meta:
        verbose_name = 'Location Share Session'
        verbose_name_plural = 'Location Share Sessions'
        ordering = ['-started_at']

    def __str__(self):
        status_str = "Active" if self.is_currently_active else "Expired/Stopped"
        return f"Share #{self.id} by {self.user.username} [{status_str}] until {self.expires_at}"

    @property
    def is_currently_active(self):
        return self.is_active and timezone.now() < self.expires_at


class SafeZone(models.Model):
    """
    User-defined geofenced safe perimeter (Home, Campus, Office).
    Alerts guardians when entering or breaching the designated radius.
    """
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='safe_zones'
    )
    name = models.CharField(
        max_length=100,
        help_text="Name of the safe zone (e.g. Home, University, Office)"
    )
    latitude = models.DecimalField(
        max_digits=9,
        decimal_places=6
    )
    longitude = models.DecimalField(
        max_digits=9,
        decimal_places=6
    )
    radius_meters = models.PositiveIntegerField(
        default=200,
        help_text="Safe boundary radius in meters (50 to 5000 meters)."
    )
    notify_on_exit = models.BooleanField(
        default=True,
        help_text="Send notification to trusted contacts when leaving this zone."
    )
    notify_on_entry = models.BooleanField(
        default=False,
        help_text="Send notification to trusted contacts when arriving in this zone."
    )
    is_active = models.BooleanField(
        default=True,
        db_index=True
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'Safe Zone'
        verbose_name_plural = 'Safe Zones'
        ordering = ['-created_at']

    def __str__(self):
        return f"SafeZone '{self.name}' ({self.radius_meters}m) for {self.user.username}"

    def distance_to(self, lat, lon):
        return haversine_distance(lat, lon, self.latitude, self.longitude)

    def is_inside(self, lat, lon):
        return is_inside_geofence(lat, lon, self.latitude, self.longitude, self.radius_meters)


class SafeZoneEvent(models.Model):
    """
    Logs entry and exit events across safe zones.
    """
    class EventType(models.TextChoices):
        ENTRY = 'entry', 'Entered Safe Zone'
        EXIT = 'exit', 'Exited Safe Zone (Breach)'

    safe_zone = models.ForeignKey(
        SafeZone,
        on_delete=models.CASCADE,
        related_name='events'
    )
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='safezone_events'
    )
    event_type = models.CharField(
        max_length=20,
        choices=EventType.choices
    )
    latitude = models.DecimalField(
        max_digits=9,
        decimal_places=6
    )
    longitude = models.DecimalField(
        max_digits=9,
        decimal_places=6
    )
    distance_meters = models.FloatField(
        help_text="Distance from center at time of event"
    )
    notified_guardians = models.BooleanField(default=False)
    timestamp = models.DateTimeField(auto_now_add=True, db_index=True)

    class Meta:
        verbose_name = 'Safe Zone Event'
        verbose_name_plural = 'Safe Zone Events'
        ordering = ['-timestamp']

    def __str__(self):
        return f"{self.user.username} {self.get_event_type_display()} '{self.safe_zone.name}' at {self.timestamp}"


class Journey(models.Model):
    """
    Safe Journey Mode: Tracks user transit from start to destination along
    a specified route with estimated arrival time. Auto-detects route deviations
    and overdue ETAs to alert trusted guardians.
    """
    class Status(models.TextChoices):
        ACTIVE = 'active', 'Active (In Transit)'
        COMPLETED = 'completed', 'Completed Safely'
        DEVIATED = 'deviated', 'Route Deviation Detected'
        CANCELLED = 'cancelled', 'Cancelled'

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='journeys'
    )
    start_location = models.CharField(max_length=255)
    destination = models.CharField(max_length=255)
    start_latitude = models.DecimalField(max_digits=9, decimal_places=6)
    start_longitude = models.DecimalField(max_digits=9, decimal_places=6)
    dest_latitude = models.DecimalField(max_digits=9, decimal_places=6)
    dest_longitude = models.DecimalField(max_digits=9, decimal_places=6)
    current_latitude = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    current_longitude = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    route_polyline = models.TextField(
        blank=True,
        help_text="Encoded polyline string or JSON array of [[lat, lng], ...]"
    )
    estimated_arrival = models.DateTimeField(db_index=True)
    status = models.CharField(
        max_length=20,
        choices=Status.choices,
        default=Status.ACTIVE,
        db_index=True
    )
    shared_with = models.ManyToManyField(
        settings.AUTH_USER_MODEL,
        related_name='shared_journeys_with_me',
        blank=True,
        help_text="Trusted contacts allowed to monitor this journey."
    )
    share_with_all_trusted = models.BooleanField(
        default=True,
        help_text="If True, all accepted trusted contacts can monitor this journey."
    )
    deviation_detected = models.BooleanField(default=False)
    eta_exceeded_alerted = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'Journey'
        verbose_name_plural = 'Journeys'
        ordering = ['-created_at']

    def __str__(self):
        return f"Journey #{self.id} by {self.user.username} [{self.get_status_display()}]: {self.start_location} -> {self.destination}"

    @property
    def is_active(self):
        return self.status in [self.Status.ACTIVE, self.Status.DEVIATED]

    @property
    def is_overdue(self):
        return self.is_active and timezone.now() > self.estimated_arrival


class DangerZone(models.Model):
    """
    Community crowdsourced / admin verified danger zone for danger heatmap.
    """
    class Category(models.TextChoices):
        POOR_LIGHTING = 'poor_lighting', 'Poor Lighting / Dark Street'
        HARASSMENT_HOTSPOT = 'harassment_hotspot', 'Harassment Hotspot'
        ISOLATED_AREA = 'isolated_area', 'Isolated / Deserted Area'
        THEFT = 'theft', 'Frequent Theft / Snatching'
        SUSPICIOUS_ACTIVITY = 'suspicious_activity', 'Suspicious Activity'
        OTHER = 'other', 'Other Safety Hazard'

    class Severity(models.TextChoices):
        LOW = 'low', 'Low Risk'
        MEDIUM = 'medium', 'Medium Risk'
        HIGH = 'high', 'High Risk'

    reported_by = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='reported_danger_zones'
    )
    title = models.CharField(max_length=150, default="Reported Danger Area")
    latitude = models.DecimalField(max_digits=9, decimal_places=6)
    longitude = models.DecimalField(max_digits=9, decimal_places=6)
    radius = models.FloatField(
        default=150.0,
        help_text="Radius of danger zone in meters (50 to 1000 meters)."
    )
    description = models.TextField()
    category = models.CharField(
        max_length=30,
        choices=Category.choices,
        default=Category.HARASSMENT_HOTSPOT,
        db_index=True
    )
    severity = models.CharField(
        max_length=20,
        choices=Severity.choices,
        default=Severity.MEDIUM
    )
    verified = models.BooleanField(
        default=False,
        db_index=True,
        help_text="Verified by admin or trusted volunteers."
    )
    report_count = models.PositiveIntegerField(
        default=1,
        help_text="Total number of users who reported or upvoted this danger zone."
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        verbose_name = 'Danger Zone'
        verbose_name_plural = 'Danger Zones'
        ordering = ['-created_at']

    def __str__(self):
        status = "Verified" if self.verified else "Unverified"
        return f"DangerZone '{self.title}' [{self.get_severity_display()}, {status}] @ ({self.latitude}, {self.longitude})"

    def distance_to(self, lat, lon):
        return haversine_distance(lat, lon, self.latitude, self.longitude)

