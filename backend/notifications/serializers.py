from rest_framework import serializers
from .models import DeviceToken, Notification, CheckInSchedule


class DeviceTokenSerializer(serializers.ModelSerializer):
    class Meta:
        model = DeviceToken
        fields = (
            'id',
            'token',
            'device_type',
            'device_name',
            'is_active',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'is_active', 'created_at', 'updated_at')


class RegisterDeviceTokenSerializer(serializers.Serializer):
    token = serializers.CharField(max_length=500, required=True)
    device_type = serializers.ChoiceField(
        choices=DeviceToken.DEVICE_TYPES,
        default='android'
    )
    device_name = serializers.CharField(
        max_length=100,
        required=False,
        allow_blank=True,
        default=''
    )


class NotificationSerializer(serializers.ModelSerializer):
    notification_type_display = serializers.CharField(
        source='get_notification_type_display',
        read_only=True
    )

    class Meta:
        model = Notification
        fields = (
            'id',
            'notification_type',
            'notification_type_display',
            'title',
            'body',
            'data',
            'is_read',
            'created_at',
        )
        read_only_fields = ('id', 'notification_type_display', 'created_at')


class CheckInScheduleSerializer(serializers.ModelSerializer):
    class Meta:
        model = CheckInSchedule
        fields = (
            'id',
            'title',
            'message',
            'scheduled_time',
            'repeat_interval',
            'is_active',
            'is_sent',
            'last_sent_at',
            'created_at',
        )
        read_only_fields = ('id', 'is_sent', 'last_sent_at', 'created_at')


class CheckInSessionSerializer(serializers.ModelSerializer):
    seconds_remaining = serializers.SerializerMethodField()

    class Meta:
        from .models import CheckInSession
        model = CheckInSession
        fields = (
            'id',
            'interval_minutes',
            'last_checkin',
            'next_due',
            'is_active',
            'alert_triggered',
            'seconds_remaining',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'last_checkin', 'created_at', 'updated_at', 'alert_triggered')

    def get_seconds_remaining(self, obj):
        from django.utils import timezone
        if not obj.is_active:
            return 0
        diff = (obj.next_due - timezone.now()).total_seconds()
        return max(0, int(diff))


class StartCheckInSessionSerializer(serializers.Serializer):
    interval_minutes = serializers.IntegerField(
        default=15,
        min_value=1,
        max_value=1440,
        help_text="Frequency in minutes for safety check-ins"
    )

