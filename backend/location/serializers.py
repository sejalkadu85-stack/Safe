from rest_framework import serializers
from django.contrib.auth import get_user_model
from .models import LocationLog, LocationShareSession, SafeZone, SafeZoneEvent
from accounts.serializers import PublicUserSummarySerializer
from alerts.serializers import SOSAlertSerializer

User = get_user_model()


class LocationLogSerializer(serializers.ModelSerializer):
    user = PublicUserSummarySerializer(read_only=True)
    google_maps_url = serializers.CharField(read_only=True)

    class Meta:
        model = LocationLog
        fields = (
            'id',
            'user',
            'latitude',
            'longitude',
            'accuracy',
            'speed',
            'altitude',
            'heading',
            'battery_level',
            'is_mock',
            'timestamp',
            'google_maps_url',
        )
        read_only_fields = ('id', 'timestamp')


class LocationUpdateSerializer(serializers.Serializer):
    latitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=True)
    longitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=True)
    accuracy = serializers.FloatField(required=False, allow_null=True)
    speed = serializers.FloatField(required=False, allow_null=True)
    altitude = serializers.FloatField(required=False, allow_null=True)
    heading = serializers.FloatField(required=False, allow_null=True)
    battery_level = serializers.IntegerField(required=False, min_value=0, max_value=100, allow_null=True)
    is_mock = serializers.BooleanField(required=False, default=False)


class LocationShareSessionSerializer(serializers.ModelSerializer):
    user = PublicUserSummarySerializer(read_only=True)
    shared_with = PublicUserSummarySerializer(many=True, read_only=True)
    is_currently_active = serializers.BooleanField(read_only=True)

    class Meta:
        model = LocationShareSession
        fields = (
            'id',
            'user',
            'shared_with',
            'share_with_all_trusted',
            'duration_minutes',
            'started_at',
            'expires_at',
            'is_active',
            'is_currently_active',
            'note',
        )
        read_only_fields = ('id', 'started_at', 'expires_at')


class StartLocationShareSerializer(serializers.Serializer):
    duration_minutes = serializers.IntegerField(
        default=60,
        min_value=5,
        max_value=1440,
        help_text="Duration in minutes (from 5 mins to 24 hours)."
    )
    note = serializers.CharField(
        max_length=200,
        required=False,
        allow_blank=True,
        default=''
    )
    share_with_all_trusted = serializers.BooleanField(default=True)
    shared_with_ids = serializers.ListField(
        child=serializers.IntegerField(),
        required=False,
        default=list
    )


class LiveLocationResponseSerializer(serializers.Serializer):
    user = PublicUserSummarySerializer()
    latest_location = LocationLogSerializer(allow_null=True)
    has_active_sos = serializers.BooleanField()
    active_sos = SOSAlertSerializer(allow_null=True)
    is_actively_sharing = serializers.BooleanField()
    share_session = LocationShareSessionSerializer(allow_null=True)


# ==========================================
# SAFE ZONE SERIALIZERS
# ==========================================

class SafeZoneSerializer(serializers.ModelSerializer):
    user = PublicUserSummarySerializer(read_only=True)

    class Meta:
        model = SafeZone
        fields = (
            'id',
            'user',
            'name',
            'latitude',
            'longitude',
            'radius_meters',
            'notify_on_exit',
            'notify_on_entry',
            'is_active',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'created_at', 'updated_at')


class CreateSafeZoneSerializer(serializers.ModelSerializer):
    radius_meters = serializers.IntegerField(
        default=200,
        min_value=50,
        max_value=5000,
        help_text="Boundary radius in meters (50m to 5000m)"
    )

    class Meta:
        model = SafeZone
        fields = (
            'name',
            'latitude',
            'longitude',
            'radius_meters',
            'notify_on_exit',
            'notify_on_entry',
            'is_active',
        )

    def create(self, validated_data):
        user = self.context['request'].user
        return SafeZone.objects.create(user=user, **validated_data)


class SafeZoneEventSerializer(serializers.ModelSerializer):
    safe_zone_name = serializers.CharField(source='safe_zone.name', read_only=True)
    event_type_display = serializers.CharField(source='get_event_type_display', read_only=True)

    class Meta:
        model = SafeZoneEvent
        fields = (
            'id',
            'safe_zone',
            'safe_zone_name',
            'event_type',
            'event_type_display',
            'latitude',
            'longitude',
            'distance_meters',
            'notified_guardians',
            'timestamp',
        )
        read_only_fields = ('id', 'timestamp')


class CheckGeofenceSerializer(serializers.Serializer):
    latitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=True)
    longitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=True)


# ==========================================
# SAFE JOURNEY SERIALIZERS (Task 4.1)
# ==========================================

class JourneySerializer(serializers.ModelSerializer):
    user = PublicUserSummarySerializer(read_only=True)
    shared_with = PublicUserSummarySerializer(many=True, read_only=True)
    status_display = serializers.CharField(source='get_status_display', read_only=True)
    is_active = serializers.BooleanField(read_only=True)
    is_overdue = serializers.BooleanField(read_only=True)

    class Meta:
        from .models import Journey
        model = Journey
        fields = (
            'id',
            'user',
            'start_location',
            'destination',
            'start_latitude',
            'start_longitude',
            'dest_latitude',
            'dest_longitude',
            'current_latitude',
            'current_longitude',
            'route_polyline',
            'estimated_arrival',
            'status',
            'status_display',
            'shared_with',
            'share_with_all_trusted',
            'deviation_detected',
            'eta_exceeded_alerted',
            'is_active',
            'is_overdue',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'created_at', 'updated_at', 'deviation_detected', 'eta_exceeded_alerted')


class StartJourneySerializer(serializers.Serializer):
    start_location = serializers.CharField(max_length=255)
    destination = serializers.CharField(max_length=255)
    start_latitude = serializers.DecimalField(max_digits=9, decimal_places=6)
    start_longitude = serializers.DecimalField(max_digits=9, decimal_places=6)
    dest_latitude = serializers.DecimalField(max_digits=9, decimal_places=6)
    dest_longitude = serializers.DecimalField(max_digits=9, decimal_places=6)
    route_polyline = serializers.CharField(required=False, allow_blank=True, default='')
    estimated_arrival = serializers.DateTimeField()
    share_with_all_trusted = serializers.BooleanField(default=True)
    shared_with_ids = serializers.ListField(
        child=serializers.IntegerField(),
        required=False,
        default=list
    )


class UpdateJourneyProgressSerializer(serializers.Serializer):
    current_latitude = serializers.DecimalField(max_digits=9, decimal_places=6)
    current_longitude = serializers.DecimalField(max_digits=9, decimal_places=6)


# ==========================================
# DANGER ZONE SERIALIZERS (Task 4.4)
# ==========================================

class DangerZoneSerializer(serializers.ModelSerializer):
    reported_by = PublicUserSummarySerializer(read_only=True)
    category_display = serializers.CharField(source='get_category_display', read_only=True)
    severity_display = serializers.CharField(source='get_severity_display', read_only=True)
    distance_meters = serializers.FloatField(read_only=True, required=False)

    class Meta:
        from .models import DangerZone
        model = DangerZone
        fields = (
            'id',
            'reported_by',
            'title',
            'latitude',
            'longitude',
            'radius',
            'description',
            'category',
            'category_display',
            'severity',
            'severity_display',
            'verified',
            'report_count',
            'distance_meters',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'created_at', 'updated_at', 'verified', 'report_count')


class ReportDangerZoneSerializer(serializers.Serializer):
    title = serializers.CharField(max_length=150, default="Reported Danger Area")
    latitude = serializers.DecimalField(max_digits=9, decimal_places=6)
    longitude = serializers.DecimalField(max_digits=9, decimal_places=6)
    radius = serializers.FloatField(default=150.0, min_value=30.0, max_value=2000.0)
    description = serializers.CharField()
    category = serializers.ChoiceField(
        choices=[
            ('poor_lighting', 'Poor Lighting / Dark Street'),
            ('harassment_hotspot', 'Harassment Hotspot'),
            ('isolated_area', 'Isolated / Deserted Area'),
            ('theft', 'Frequent Theft / Snatching'),
            ('suspicious_activity', 'Suspicious Activity'),
            ('other', 'Other Safety Hazard'),
        ],
        default='harassment_hotspot'
    )
    severity = serializers.ChoiceField(
        choices=[('low', 'Low Risk'), ('medium', 'Medium Risk'), ('high', 'High Risk')],
        default='medium'
    )

