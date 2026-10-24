from rest_framework import serializers
from django.contrib.auth import get_user_model
from django.db.models import Q
from .models import TrustedContact, SOSAlert, AlertRecipient
from accounts.serializers import PublicUserSummarySerializer

User = get_user_model()


class TrustedContactSerializer(serializers.ModelSerializer):
    owner = PublicUserSummarySerializer(read_only=True)
    contact_user = PublicUserSummarySerializer(read_only=True)
    relationship_display = serializers.CharField(source='get_relationship_display', read_only=True)
    status_display = serializers.CharField(source='get_status_display', read_only=True)
    tier_display = serializers.CharField(source='get_tier_display', read_only=True)

    class Meta:
        model = TrustedContact
        fields = (
            'id',
            'owner',
            'contact_user',
            'name',
            'phone_number',
            'tier',
            'tier_display',
            'relationship',
            'relationship_display',
            'status',
            'status_display',
            'is_verified',
            'nickname',
            'notify_via_sms',
            'notify_via_email',
            'notify_via_push',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'created_at', 'updated_at')


class AddTrustedContactSerializer(serializers.Serializer):
    name = serializers.CharField(required=False, allow_blank=True, default='')
    phone_number = serializers.CharField(required=False, allow_blank=True, default='')
    email_or_phone = serializers.CharField(
        required=False,
        allow_blank=True,
        default='',
        help_text="Email address or phone number of the contact"
    )
    tier = serializers.ChoiceField(
        choices=TrustedContact.Tier.choices,
        default=TrustedContact.Tier.PRIMARY
    )
    relationship = serializers.ChoiceField(
        choices=TrustedContact.Relationship.choices,
        default=TrustedContact.Relationship.OTHER
    )
    nickname = serializers.CharField(
        max_length=80,
        required=False,
        allow_blank=True,
        default=''
    )
    notify_via_sms = serializers.BooleanField(default=True)
    notify_via_email = serializers.BooleanField(default=True)
    notify_via_push = serializers.BooleanField(default=True)

    def to_internal_value(self, data):
        if hasattr(data, 'copy'):
            data = data.copy()
        else:
            data = dict(data)
        if 'tier' in data and isinstance(data['tier'], str):
            data['tier'] = data['tier'].lower()
        if 'relationship' in data and isinstance(data['relationship'], str):
            rel = data['relationship'].lower()
            if rel in ['mother', 'father', 'mom', 'dad']:
                rel = 'parent'
            data['relationship'] = rel
        return super().to_internal_value(data)

    def validate(self, attrs):
        request = self.context.get('request')
        owner = request.user if request else None
        identifier = (attrs.get('phone_number') or attrs.get('email_or_phone') or '').strip()
        name = attrs.get('name', '').strip()

        if not identifier and not name:
            raise serializers.ValidationError("Either phone number, email, or contact name is required.")

        target_user = None
        if identifier:
            target_user = User.objects.filter(
                Q(email__iexact=identifier) | Q(phone_number=identifier)
            ).first()

        # Cannot add self as contact
        if owner and target_user and target_user == owner:
            raise serializers.ValidationError({"email_or_phone": "You cannot add yourself as an emergency contact."})

        # If email specified but user does not exist in SafeHer system
        if '@' in identifier and not target_user:
            raise serializers.ValidationError({"email_or_phone": "No registered user found with this email address."})

        # Cannot add duplicate contact
        if owner:
            existing = TrustedContact.objects.filter(owner=owner)
            if target_user:
                existing = existing.filter(contact_user=target_user)
            elif identifier:
                existing = existing.filter(phone_number=identifier)
            else:
                existing = existing.none()

            if existing.exists():
                raise serializers.ValidationError({"email_or_phone": "This person is already in your emergency contacts list."})

        attrs['target_user'] = target_user
        attrs['resolved_identifier'] = identifier
        return attrs

    def create(self, validated_data):
        owner = self.context['request'].user
        target_user = validated_data.get('target_user')
        identifier = validated_data.get('resolved_identifier') or (validated_data.get('phone_number') or validated_data.get('email_or_phone') or '').strip()
        name = validated_data.get('name', '').strip()

        contact_name = name or (target_user.get_full_name() or target_user.username if target_user else identifier)
        phone = (target_user.phone_number if target_user and target_user.phone_number else (identifier if '@' not in identifier else ''))

        return TrustedContact.objects.create(
            owner=owner,
            contact_user=target_user,
            name=contact_name,
            phone_number=phone,
            tier=validated_data.get('tier', TrustedContact.Tier.PRIMARY),
            relationship=validated_data.get('relationship', TrustedContact.Relationship.OTHER),
            nickname=validated_data.get('nickname', ''),
            notify_via_sms=validated_data.get('notify_via_sms', True),
            notify_via_email=validated_data.get('notify_via_email', True),
            notify_via_push=validated_data.get('notify_via_push', True),
            status=TrustedContact.Status.PENDING,
            is_verified=False,
        )


class RespondContactRequestSerializer(serializers.Serializer):
    action = serializers.ChoiceField(
        choices=['accept', 'reject', 'block'],
        required=True,
        help_text="'accept' to confirm being a guardian, 'reject' or 'block' to decline"
    )


class UpdateContactPreferencesSerializer(serializers.ModelSerializer):
    class Meta:
        model = TrustedContact
        fields = ('name', 'phone_number', 'tier', 'nickname', 'relationship', 'status', 'is_verified', 'notify_via_sms', 'notify_via_email', 'notify_via_push')


# ==========================================
# SOS ALERT SERIALIZERS
# ==========================================

class AlertRecipientSerializer(serializers.ModelSerializer):
    recipient = PublicUserSummarySerializer(read_only=True)
    status_display = serializers.CharField(source='get_status_display', read_only=True)
    sms_status_display = serializers.CharField(source='get_sms_status_display', read_only=True)
    email_status_display = serializers.CharField(source='get_email_status_display', read_only=True)
    call_status_display = serializers.CharField(source='get_call_status_display', read_only=True)
    push_status_display = serializers.CharField(source='get_push_status_display', read_only=True)

    class Meta:
        model = AlertRecipient
        fields = (
            'id',
            'recipient',
            'trusted_contact',
            'status',
            'status_display',
            'sms_status',
            'sms_status_display',
            'email_status',
            'email_status_display',
            'call_status',
            'call_status_display',
            'push_status',
            'push_status_display',
            'notified_via_sms',
            'notified_via_push',
            'notified_via_email',
            'notified_at',
            'acknowledged_at',
            'guardian_note',
        )
        read_only_fields = ('id', 'notified_at')


class IncidentTimelineSerializer(serializers.ModelSerializer):
    actor_name = serializers.CharField(read_only=True)

    class Meta:
        from .models import IncidentTimeline
        model = IncidentTimeline
        fields = ('id', 'status', 'actor_name', 'note', 'timestamp')


class IncidentMessageSerializer(serializers.ModelSerializer):
    class Meta:
        from .models import IncidentMessage
        model = IncidentMessage
        fields = ('id', 'sender_name', 'sender_role', 'content', 'timestamp')


class EscalationConfigSerializer(serializers.ModelSerializer):
    class Meta:
        from .models import EscalationConfig
        model = EscalationConfig
        fields = ('primary_timeout_seconds', 'secondary_timeout_seconds', 'auto_volunteer_dispatch', 'escalation_enabled', 'updated_at')


class SOSAlertSerializer(serializers.ModelSerializer):
    sender = PublicUserSummarySerializer(read_only=True)
    status_display = serializers.CharField(source='get_status_display', read_only=True)
    trigger_type_display = serializers.CharField(source='get_trigger_type_display', read_only=True)
    category_display = serializers.CharField(source='get_category_display', read_only=True)
    recipients = AlertRecipientSerializer(many=True, read_only=True)
    timeline = IncidentTimelineSerializer(many=True, read_only=True)
    google_maps_url = serializers.CharField(read_only=True)
    assigned_responder_name = serializers.SerializerMethodField()
    assigned_guardian_name = serializers.SerializerMethodField()

    class Meta:
        model = SOSAlert
        fields = (
            'id',
            'sender',
            'status',
            'status_display',
            'lifecycle_status',
            'category',
            'category_display',
            'escalation_tier',
            'escalated_at',
            'assigned_responder',
            'assigned_responder_name',
            'assigned_guardian',
            'assigned_guardian_name',
            'trigger_type',
            'trigger_type_display',
            'latitude',
            'longitude',
            'altitude',
            'accuracy_meters',
            'address',
            'message',
            'battery_level',
            'google_maps_url',
            'cancellation_reason',
            'created_at',
            'resolved_at',
            'recipients',
            'timeline',
        )
        read_only_fields = ('id', 'created_at', 'resolved_at')

    def get_assigned_responder_name(self, obj):
        if obj.assigned_responder:
            return obj.assigned_responder.get_full_name() or obj.assigned_responder.username
        return None

    def get_assigned_guardian_name(self, obj):
        if obj.assigned_guardian:
            return obj.assigned_guardian.get_full_name() or obj.assigned_guardian.username
        return None


class TriggerSOSSerializer(serializers.Serializer):
    latitude = serializers.DecimalField(
        max_digits=9,
        decimal_places=6,
        required=False,
        allow_null=True
    )
    longitude = serializers.DecimalField(
        max_digits=9,
        decimal_places=6,
        required=False,
        allow_null=True
    )
    altitude = serializers.FloatField(required=False, allow_null=True)
    accuracy_meters = serializers.FloatField(required=False, allow_null=True)
    address = serializers.CharField(required=False, allow_blank=True, default='')
    message = serializers.CharField(
        required=False,
        default="EMERGENCY! I need immediate help. Here is my current location."
    )
    category = serializers.CharField(
        required=False,
        default=SOSAlert.EmergencyCategory.UNSAFE_SITUATION
    )
    trigger_type = serializers.ChoiceField(
        choices=SOSAlert.TriggerType.choices,
        default=SOSAlert.TriggerType.MANUAL
    )
    battery_level = serializers.IntegerField(
        required=False,
        min_value=0,
        max_value=100,
        allow_null=True
    )


class CancelSOSSerializer(serializers.Serializer):
    reason = serializers.CharField(
        required=False,
        allow_blank=True,
        default="False alarm / I am safe now."
    )


class AcknowledgeSOSSerializer(serializers.Serializer):
    note = serializers.CharField(
        required=False,
        allow_blank=True,
        default="I have seen your alert and help is on the way!"
    )


# ==========================================
# EVIDENCE RECORDING SERIALIZERS (Task 4.3)
# ==========================================

class EvidenceFileSerializer(serializers.ModelSerializer):
    user = PublicUserSummarySerializer(read_only=True)
    file_type_display = serializers.CharField(source='get_file_type_display', read_only=True)
    file_url = serializers.CharField(source='url', read_only=True)

    class Meta:
        from .models import EvidenceFile
        model = EvidenceFile
        fields = (
            'id',
            'user',
            'sos_alert',
            'file',
            'file_url',
            'file_type',
            'file_type_display',
            'duration_seconds',
            'file_size_bytes',
            'timestamp',
        )
        read_only_fields = ('id', 'timestamp')


class UploadEvidenceSerializer(serializers.Serializer):
    file = serializers.FileField(required=False, allow_null=True)
    file_url = serializers.CharField(required=False, allow_blank=True, default='')
    file_type = serializers.ChoiceField(
        choices=[('audio', 'Audio Recording'), ('video', 'Video Recording'), ('image', 'Photo / Snapshot')],
        default='audio'
    )
    sos_alert_id = serializers.IntegerField(required=False, allow_null=True)
    duration_seconds = serializers.FloatField(required=False, allow_null=True)

