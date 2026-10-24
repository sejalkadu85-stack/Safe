from rest_framework import generics, status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated, AllowAny
from django.shortcuts import get_object_or_404
from django.db.models import Q
from django.utils import timezone

from .models import (
    TrustedContact,
    SOSAlert,
    AlertRecipient,
    EvidenceFile,
    IncidentTimeline,
    IncidentMessage,
    EscalationConfig,
)
from .serializers import (
    TrustedContactSerializer,
    AddTrustedContactSerializer,
    RespondContactRequestSerializer,
    UpdateContactPreferencesSerializer,
    SOSAlertSerializer,
    TriggerSOSSerializer,
    CancelSOSSerializer,
    AcknowledgeSOSSerializer,
    EvidenceFileSerializer,
    UploadEvidenceSerializer,
    IncidentTimelineSerializer,
    IncidentMessageSerializer,
    EscalationConfigSerializer,
)
from .services import dispatch_sos_alert



# ==========================================
# TRUSTED CONTACT VIEWS
# ==========================================

class AddTrustedContactView(APIView):
    """
    POST /api/alerts/contacts/add/
    Add a trusted contact by email or phone number.
    Creates a relationship in 'pending' status until accepted by the contact.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = AddTrustedContactSerializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)
        contact = serializer.save()

        target_name = (
            contact.contact_user.get_full_name() or contact.contact_user.username
        ) if contact.contact_user else (contact.name or contact.phone_number)

        return Response(
            {
                'message': f"Request sent to {target_name}.",
                'contact': TrustedContactSerializer(contact).data
            },
            status=status.HTTP_201_CREATED
        )


class ListTrustedContactsView(generics.ListCreateAPIView):
    """
    GET /api/alerts/contacts/
    List all trusted contacts configured by the authenticated woman.
    Filter by query param ?status=accepted|pending|all

    POST /api/alerts/contacts/
    Add a trusted contact directly to contacts list.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = TrustedContactSerializer

    def get_queryset(self):
        status_filter = self.request.query_params.get('status')
        qs = TrustedContact.objects.filter(owner=self.request.user)

        if status_filter and status_filter != 'all':
            qs = qs.filter(status=status_filter)

        return qs.select_related('owner', 'contact_user')

    def post(self, request, *args, **kwargs):
        serializer = AddTrustedContactSerializer(data=request.data, context={'request': request})
        serializer.is_valid(raise_exception=True)
        contact = serializer.save()
        return Response(
            TrustedContactSerializer(contact).data,
            status=status.HTTP_201_CREATED
        )


class PendingContactRequestsView(generics.ListAPIView):
    """
    GET /api/alerts/contacts/pending/
    List incoming guardian requests for the authenticated user
    (i.e. where contact_user == current user and status is 'pending').
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = TrustedContactSerializer

    def get_queryset(self):
        return TrustedContact.objects.filter(
            contact_user=self.request.user,
            status=TrustedContact.Status.PENDING
        ).select_related('owner', 'contact_user')


class RespondContactRequestView(APIView):
    """
    PUT /api/alerts/contacts/<int:id>/respond/
    Guardian accepts, rejects, or blocks a trusted-contact request.
    Payload: {"action": "accept" | "reject" | "block"}
    """
    permission_classes = (IsAuthenticated,)

    def put(self, request, id):
        contact = get_object_or_404(TrustedContact, id=id, contact_user=request.user)

        serializer = RespondContactRequestSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        action = serializer.validated_data['action']

        if action == 'accept':
            contact.status = TrustedContact.Status.ACCEPTED
            msg = f"You are now a trusted guardian for {contact.owner.get_full_name() or contact.owner.username}."
        elif action == 'reject':
            contact.status = TrustedContact.Status.REJECTED
            msg = "Guardian request declined."
        elif action == 'block':
            contact.status = TrustedContact.Status.BLOCKED
            msg = "User blocked from sending safety requests."

        contact.save()
        return Response({
            'message': msg,
            'contact': TrustedContactSerializer(contact).data
        }, status=status.HTTP_200_OK)


class ContactDetailUpdateDeleteView(APIView):
    """
    GET, PUT, DELETE /api/alerts/contacts/<int:id>/
    View, update (name, phone, tier, relationship), or delete a trusted contact.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request, id):
        contact = get_object_or_404(TrustedContact, id=id, owner=request.user)
        return Response(TrustedContactSerializer(contact).data)

    def put(self, request, id):
        contact = get_object_or_404(TrustedContact, id=id, owner=request.user)
        data = request.data
        if 'name' in data:
            contact.name = data['name']
        if 'phone_number' in data:
            contact.phone_number = data['phone_number']
        if 'tier' in data:
            contact.tier = data['tier']
        if 'relationship' in data:
            contact.relationship = data['relationship']
        if 'nickname' in data:
            contact.nickname = data['nickname']
        if 'is_verified' in data:
            contact.is_verified = bool(data['is_verified'])
        contact.save()
        return Response({
            'message': 'Contact updated successfully.',
            'contact': TrustedContactSerializer(contact).data
        })

    def delete(self, request, id):
        contact = get_object_or_404(
            TrustedContact,
            Q(id=id) & (Q(owner=request.user) | Q(contact_user=request.user))
        )
        contact.delete()
        return Response(
            {'message': 'Trusted contact removed successfully.'},
            status=status.HTTP_200_OK
        )


class VerifyContactView(APIView):
    """
    POST /api/alerts/contacts/<int:id>/verify/
    Mark a trusted contact as verified.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request, id):
        contact = get_object_or_404(TrustedContact, id=id, owner=request.user)
        contact.is_verified = True
        contact.status = TrustedContact.Status.ACCEPTED
        contact.save()
        return Response({
            'message': f"Contact {contact.name or contact.phone_number} verified successfully.",
            'contact': TrustedContactSerializer(contact).data
        })


class UpdateContactPreferencesView(generics.UpdateAPIView):
    """
    PATCH /api/alerts/contacts/<int:id>/preferences/
    Update nickname, relationship or notification channels for a contact.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = UpdateContactPreferencesSerializer
    lookup_field = 'id'

    def get_queryset(self):
        return TrustedContact.objects.filter(owner=self.request.user)


# ==========================================
# EMERGENCY SOS & INCIDENT LIFECYCLE VIEWS
# ==========================================

class TriggerSOSView(APIView):
    """
    POST /api/alerts/sos/trigger/
    Trigger an emergency SOS incident.
    Creates incident in OPEN status, records initial timeline, and dispatches to Tier 1 (Primary Guardians).
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = TriggerSOSSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        category = data.get('category', SOSAlert.EmergencyCategory.UNSAFE_SITUATION)

        # Check if user already has an active alert
        existing_alert = SOSAlert.objects.filter(
            sender=request.user,
            status=SOSAlert.Status.ACTIVE
        ).first()

        if existing_alert:
            if data.get('latitude') is not None:
                existing_alert.latitude = data['latitude']
            if data.get('longitude') is not None:
                existing_alert.longitude = data['longitude']
            if data.get('address'):
                existing_alert.address = data['address']
            if data.get('battery_level') is not None:
                existing_alert.battery_level = data['battery_level']
            if category:
                existing_alert.category = category
            existing_alert.save()

            IncidentTimeline.objects.create(
                alert=existing_alert,
                status=existing_alert.lifecycle_status,
                actor=request.user,
                actor_name=request.user.get_full_name() or request.user.username,
                note=f"Location updated: lat={existing_alert.latitude}, lng={existing_alert.longitude}",
                timestamp=timezone.now()
            )

            recipients = dispatch_sos_alert(existing_alert, tier=existing_alert.escalation_tier)

            return Response({
                'message': 'Active SOS alert updated with latest location and category.',
                'alert': SOSAlertSerializer(existing_alert).data,
                'recipients_notified_count': len(recipients),
            }, status=status.HTTP_200_OK)

        # Create new incident
        alert = SOSAlert.objects.create(
            sender=request.user,
            latitude=data.get('latitude'),
            longitude=data.get('longitude'),
            altitude=data.get('altitude'),
            accuracy_meters=data.get('accuracy_meters'),
            address=data.get('address', ''),
            message=data.get('message', 'EMERGENCY! I need immediate help. Here is my current location.'),
            trigger_type=data.get('trigger_type', SOSAlert.TriggerType.MANUAL),
            category=category,
            lifecycle_status=SOSAlert.LifecycleStatus.OPEN,
            escalation_tier=1,
            battery_level=data.get('battery_level'),
            status=SOSAlert.Status.ACTIVE
        )

        recipients = dispatch_sos_alert(alert, tier=1)

        return Response({
            'message': '🚨 Emergency Incident created! Primary Guardians notified.',
            'alert': SOSAlertSerializer(alert).data,
            'recipients_notified_count': len(recipients),
        }, status=status.HTTP_201_CREATED)


class SOSIncidentDetailStatusView(APIView):
    """
    GET /api/alerts/sos/<int:id>/status/
    Returns real-time incident status, visual timeline, and escalation timeout remaining.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)
        config = EscalationConfig.get_config()

        # Calculate time elapsed since creation
        elapsed_seconds = int((timezone.now() - alert.created_at).total_seconds())
        timeout = config.primary_timeout_seconds if alert.escalation_tier == 1 else config.secondary_timeout_seconds
        remaining_seconds = max(0, timeout - elapsed_seconds)

        timeline_data = IncidentTimelineSerializer(alert.timeline.all(), many=True).data

        return Response({
            'alert': SOSAlertSerializer(alert).data,
            'timeline': timeline_data,
            'elapsed_seconds': elapsed_seconds,
            'tier_timeout_seconds': timeout,
            'seconds_until_next_escalation': remaining_seconds,
            'can_escalate': alert.status == SOSAlert.Status.ACTIVE and alert.escalation_tier < 3,
        })


class SOSIncidentEscalateView(APIView):
    """
    POST /api/alerts/sos/<int:id>/escalate/
    Manually or automatically advance the incident to the next escalation tier:
    Tier 1 -> Tier 2 (Secondary Guardians & Friends)
    Tier 2 -> Tier 3 (Volunteers / Emergency Services)
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)

        if alert.status != SOSAlert.Status.ACTIVE:
            return Response({'error': 'Cannot escalate an inactive or resolved incident.'}, status=400)

        next_tier = min(3, alert.escalation_tier + 1)
        alert.escalation_tier = next_tier
        alert.lifecycle_status = SOSAlert.LifecycleStatus.ESCALATED
        alert.escalated_at = timezone.now()
        alert.save()

        recipients = dispatch_sos_alert(alert, tier=next_tier)

        tier_label = "Secondary Guardians & Friends" if next_tier == 2 else "Nearby Volunteers & Emergency Dispatch"

        return Response({
            'message': f"Incident #{id} escalated to Tier {next_tier} ({tier_label}).",
            'escalation_tier': next_tier,
            'recipients_notified_count': len(recipients),
            'alert': SOSAlertSerializer(alert).data,
        })


class SOSIncidentRespondView(APIView):
    """
    POST /api/alerts/sos/<int:id>/respond/
    Guardian or Volunteer accepts / responds to the incident.
    Transitions lifecycle status to ACTIVE and records assignment.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)
        user = request.user
        note = request.data.get('note', 'Accepted incident mission and responding.')

        alert.lifecycle_status = SOSAlert.LifecycleStatus.ACTIVE
        alert.status = SOSAlert.Status.ACTIVE

        responder_name = user.get_full_name() or user.username

        if user.role == 'volunteer':
            alert.assigned_responder = user
            role_label = "Volunteer Responder"
        else:
            alert.assigned_guardian = user
            role_label = "Guardian"

        alert.save()

        # Record timeline event
        IncidentTimeline.objects.create(
            alert=alert,
            status='active',
            actor=user,
            actor_name=f"{responder_name} ({role_label})",
            note=note,
            timestamp=timezone.now()
        )

        return Response({
            'message': f"You have accepted Incident #{id}. Status is now ACTIVE.",
            'alert': SOSAlertSerializer(alert).data,
        })


class CancelSOSView(APIView):
    """
    PUT /api/alerts/sos/<int:id>/cancel/
    Cancel an active SOS alert.
    """
    permission_classes = (IsAuthenticated,)

    def put(self, request, id):
        alert = get_object_or_404(
            SOSAlert,
            Q(id=id) & (Q(sender=request.user) | Q(sender__role='admin') | Q(sender__is_staff=True))
        )

        serializer = CancelSOSSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        reason = serializer.validated_data.get('reason', 'Cancelled by user.')
        alert.status = SOSAlert.Status.CANCELLED
        alert.lifecycle_status = SOSAlert.LifecycleStatus.CLOSED
        alert.cancellation_reason = reason
        alert.resolved_at = timezone.now()
        alert.save()

        IncidentTimeline.objects.create(
            alert=alert,
            status='closed',
            actor=request.user,
            actor_name=request.user.get_full_name() or request.user.username,
            note=f"Cancelled: {reason}",
            timestamp=timezone.now()
        )

        return Response({
            'message': 'SOS alert has been cancelled.',
            'alert': SOSAlertSerializer(alert).data
        }, status=status.HTTP_200_OK)


class ResolveSOSView(APIView):
    """
    PUT, POST /api/alerts/sos/<int:id>/resolve/
    Mark an active SOS alert as safely resolved.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request, id):
        return self._resolve(request, id)

    def put(self, request, id):
        return self._resolve(request, id)

    def _resolve(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)

        note = request.data.get('note', 'User confirmed safe. Incident resolved.')
        alert.status = SOSAlert.Status.RESOLVED
        alert.lifecycle_status = SOSAlert.LifecycleStatus.RESOLVED
        alert.resolved_at = timezone.now()
        alert.save()

        IncidentTimeline.objects.create(
            alert=alert,
            status='resolved',
            actor=request.user,
            actor_name=request.user.get_full_name() or request.user.username,
            note=note,
            timestamp=timezone.now()
        )

        return Response({
            'message': 'SOS alert marked as safely resolved.',
            'alert': SOSAlertSerializer(alert).data
        }, status=status.HTTP_200_OK)


class SOSIncidentCloseView(APIView):
    """
    POST /api/alerts/sos/<int:id>/close/
    Formally closes and archives a resolved incident.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)
        note = request.data.get('note', 'Incident formally closed and archived.')

        alert.lifecycle_status = SOSAlert.LifecycleStatus.CLOSED
        alert.save(update_fields=['lifecycle_status'])

        IncidentTimeline.objects.create(
            alert=alert,
            status='closed',
            actor=request.user,
            actor_name=request.user.get_full_name() or request.user.username,
            note=note,
            timestamp=timezone.now()
        )

        return Response({
            'message': f"Incident #{id} is now CLOSED.",
            'alert': SOSAlertSerializer(alert).data
        })


class IncidentChatView(APIView):
    """
    GET, POST /api/alerts/sos/<int:id>/chat/
    Real-time incident chat between User, Guardian, and Responder.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)
        messages = alert.incident_messages.all().order_by('timestamp')
        return Response({
            'alert_id': alert.id,
            'count': messages.count(),
            'messages': IncidentMessageSerializer(messages, many=True).data
        })

    def post(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)
        content = request.data.get('message', '').strip()
        if not content:
            return Response({'error': 'Message content is required.'}, status=400)

        user = request.user
        sender_name = user.get_full_name() or user.username
        if user == alert.sender:
            role = 'woman'
        elif user.role in ['guardian', 'friend']:
            role = 'guardian'
        elif user.role == 'volunteer':
            role = 'volunteer'
        elif user.role == 'admin':
            role = 'admin'
        else:
            role = 'guardian'

        msg = IncidentMessage.objects.create(
            alert=alert,
            sender=user,
            sender_name=sender_name,
            sender_role=role,
            content=content,
            timestamp=timezone.now()
        )

        return Response({
            'success': True,
            'message': IncidentMessageSerializer(msg).data
        }, status=status.HTTP_201_CREATED)


class GuardianActiveAlertsView(APIView):
    """
    GET /api/guardian/alerts/active/
    Returns active SOS alerts for guardians (where guardian is in trusted contacts or assigned).
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request):
        user = request.user
        # Find all active alerts where user is trusted contact or assigned
        alerts = SOSAlert.objects.filter(
            Q(recipients__recipient=user) |
            Q(assigned_guardian=user) |
            Q(sender__my_contacts__contact_user=user)
        ).filter(status=SOSAlert.Status.ACTIVE).distinct().order_by('-created_at')

        data = []
        for a in alerts[:30]:
            elapsed = int((timezone.now() - a.created_at).total_seconds())
            mins = elapsed // 60
            secs = elapsed % 60
            data.append({
                'id': a.id,
                'user_name': a.sender.get_full_name() or a.sender.username,
                'phone': str(a.sender.phone_number) if a.sender.phone_number else '',
                'emergency_type': a.get_category_display() if hasattr(a, 'get_category_display') else a.category,
                'category': a.category,
                'status': a.lifecycle_status,
                'escalation_tier': a.escalation_tier,
                'latitude': float(a.latitude) if a.latitude else 18.5204,
                'longitude': float(a.longitude) if a.longitude else 73.8567,
                'address': a.address or 'Current GPS location',
                'google_maps_url': a.google_maps_url,
                'time_since_sos': f"{mins}m {secs}s ago",
                'elapsed_seconds': elapsed,
                'battery_level': a.battery_level,
                'created_at': a.created_at.isoformat(),
            })

        return Response({'count': len(data), 'active_alerts': data})


class AdminEscalationConfigView(APIView):
    """
    GET, PUT /api/admin/escalation-config/
    Admin configuration for automatic escalation timers and rules.
    """
    permission_classes = (AllowAny,)

    def get(self, request):
        config = EscalationConfig.get_config()
        return Response(EscalationConfigSerializer(config).data)

    def put(self, request):
        config = EscalationConfig.get_config()
        serializer = EscalationConfigSerializer(config, data=request.data, partial=True)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response({
            'message': 'Escalation configuration updated successfully.',
            'config': serializer.data
        })


class AcknowledgeSOSView(APIView):
    """
    PUT /api/alerts/sos/<int:id>/acknowledge/
    Guardian acknowledges distress alert and signals that help is on the way.
    """
    permission_classes = (IsAuthenticated,)

    def put(self, request, id):
        alert = get_object_or_404(SOSAlert, id=id)

        recipient_record = get_object_or_404(
            AlertRecipient,
            alert=alert,
            recipient=request.user
        )

        serializer = AcknowledgeSOSSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        recipient_record.status = AlertRecipient.RecipientStatus.ACKNOWLEDGED
        recipient_record.acknowledged_at = timezone.now()
        recipient_record.guardian_note = serializer.validated_data.get('note', '')
        recipient_record.save()

        # Update alert to ACTIVE if still in OPEN
        if alert.lifecycle_status == SOSAlert.LifecycleStatus.OPEN:
            alert.lifecycle_status = SOSAlert.LifecycleStatus.ACTIVE
            alert.assigned_guardian = request.user
            alert.save(update_fields=['lifecycle_status', 'assigned_guardian'])

        IncidentTimeline.objects.create(
            alert=alert,
            status='active',
            actor=request.user,
            actor_name=f"{request.user.get_full_name() or request.user.username} (Guardian)",
            note=f"Acknowledged alert: {recipient_record.guardian_note}",
            timestamp=timezone.now()
        )

        return Response({
            'message': 'Alert acknowledged. The user will be notified that you are responding.',
            'recipient': recipient_record.status,
            'alert': SOSAlertSerializer(alert).data
        }, status=status.HTTP_200_OK)


class ActiveSOSAlertsView(generics.ListAPIView):
    """
    GET /api/alerts/sos/active/
    Returns active SOS alerts relevant to the current user:
    - Woman: active alert she triggered
    - Guardian: active alerts for women who have added him as accepted contact
    - Admin: all active alerts
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = SOSAlertSerializer

    def get_queryset(self):
        user = self.request.user
        if user.role == 'admin' or user.is_staff or user.is_superuser:
            return SOSAlert.objects.filter(status=SOSAlert.Status.ACTIVE).select_related('sender').prefetch_related('recipients__recipient')

        # Active alerts user sent OR active alerts user received as guardian
        return SOSAlert.objects.filter(
            Q(sender=user, status=SOSAlert.Status.ACTIVE) |
            Q(recipients__recipient=user, status=SOSAlert.Status.ACTIVE)
        ).distinct().select_related('sender').prefetch_related('recipients__recipient')


class SOSAlertHistoryView(generics.ListAPIView):
    """
    GET /api/alerts/sos/history/
    Returns past SOS alerts for current user.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = SOSAlertSerializer

    def get_queryset(self):
        user = self.request.user
        if user.role == 'admin' or user.is_staff or user.is_superuser:
            return SOSAlert.objects.all().select_related('sender').prefetch_related('recipients__recipient')

        return SOSAlert.objects.filter(
            Q(sender=user) | Q(recipients__recipient=user)
        ).distinct().select_related('sender').prefetch_related('recipients__recipient')


class SOSAlertDetailView(generics.RetrieveAPIView):
    """
    GET /api/alerts/sos/<int:id>/
    Get detailed information for a specific SOS alert.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = SOSAlertSerializer
    lookup_field = 'id'

    def get_queryset(self):
        user = self.request.user
        if user.role == 'admin' or user.is_staff or user.is_superuser:
            return SOSAlert.objects.all()

        return SOSAlert.objects.filter(
            Q(sender=user) | Q(recipients__recipient=user)
        ).distinct()


# ==========================================
# EVIDENCE RECORDING VIEWS (Task 4.3)
# ==========================================

class UploadEvidenceView(APIView):
    """
    POST /api/evidence/upload/
    Upload audio/video/image evidence file.
    Auto-links to the most recent active SOS alert if not explicitly provided.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = UploadEvidenceSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        file_obj = data.get('file')
        file_url = data.get('file_url', '')
        file_type = data.get('file_type', 'audio')
        duration_seconds = data.get('duration_seconds')
        sos_alert_id = data.get('sos_alert_id')

        sos_alert = None
        if sos_alert_id:
            sos_alert = SOSAlert.objects.filter(id=sos_alert_id, sender=request.user).first()
        if not sos_alert:
            # Auto-link to latest active SOS alert
            sos_alert = SOSAlert.objects.filter(
                sender=request.user,
                status=SOSAlert.Status.ACTIVE
            ).order_by('-created_at').first()

        file_size = file_obj.size if file_obj else None

        evidence = EvidenceFile.objects.create(
            user=request.user,
            sos_alert=sos_alert,
            file=file_obj,
            file_url=file_url,
            file_type=file_type,
            duration_seconds=duration_seconds,
            file_size_bytes=file_size,
        )

        if sos_alert:
            duration_info = f" ({duration_seconds}s)" if duration_seconds else ""
            IncidentTimeline.objects.create(
                alert=sos_alert,
                status=sos_alert.lifecycle_status or 'active',
                actor=request.user,
                actor_name=request.user.get_full_name() or request.user.username,
                note=f"🎤 Voice Note / Audio Evidence uploaded{duration_info}. Evidence ID #{evidence.id}.",
                timestamp=timezone.now()
            )

        return Response(EvidenceFileSerializer(evidence).data, status=status.HTTP_201_CREATED)


class EvidenceFileListView(generics.ListAPIView):
    """
    GET /api/evidence/
    List evidence files for the user or guardian.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = EvidenceFileSerializer

    def get_queryset(self):
        user = self.request.user
        sos_alert_id = self.request.query_params.get('sos_alert_id')

        if user.role == 'admin' or user.is_staff or user.is_superuser:
            qs = EvidenceFile.objects.all()
        else:
            qs = EvidenceFile.objects.filter(
                Q(user=user) | Q(sos_alert__recipients__recipient=user)
            ).distinct()

        if sos_alert_id:
            qs = qs.filter(sos_alert_id=sos_alert_id)

        return qs.order_by('-timestamp')


class EvidenceFileDetailView(generics.RetrieveDestroyAPIView):
    """
    GET, DELETE /api/evidence/<int:id>/
    Retrieve or delete evidence file.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = EvidenceFileSerializer
    lookup_field = 'id'

    def get_queryset(self):
        user = self.request.user
        if user.role == 'admin' or user.is_staff:
            return EvidenceFile.objects.all()
        return EvidenceFile.objects.filter(user=user)



# ==========================================
# BATTERY-LOW AUTO ALERT (Task 5.4)
# ==========================================

class BatteryLowAlertView(APIView):
    """
    POST /api/alerts/battery-low/
    Triggered by Android when battery drops below 10%.
    Sends the user's last known location to all accepted trusted contacts
    via in-app notification and FCM push notification.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        battery_level = request.data.get('battery_level', 0)
        latitude = request.data.get('latitude')
        longitude = request.data.get('longitude')
        address = request.data.get('address', '')

        # Fetch all accepted trusted contacts that have registered accounts
        trusted = TrustedContact.objects.filter(
            owner=request.user, status=TrustedContact.Status.ACCEPTED
        ).select_related('contact_user')
        contacts = [tc.contact_user for tc in trusted if tc.contact_user]

        display_name = request.user.get_full_name() or request.user.username
        message = (
            f"⚠️ Low Battery Alert! {display_name}'s phone battery is at {battery_level}%. "
            f"Last known location: lat={latitude}, lng={longitude}."
        )

        from notifications.models import Notification
        from notifications.fcm_service import send_fcm_notification

        for contact in contacts:
            Notification.objects.create(
                recipient=contact,
                notification_type='location_share',
                title=f'🔋 Low Battery: {display_name}',
                body=message,
                data={
                    'battery_level': battery_level,
                    'latitude': str(latitude),
                    'longitude': str(longitude),
                },
            )
            try:
                send_fcm_notification(
                    user=contact,
                    title='🔋 Low Battery Alert',
                    body=message,
                    notification_type='location_share',
                    data={
                        'battery_level': str(battery_level),
                        'latitude': str(latitude or ''),
                        'longitude': str(longitude or ''),
                    },
                    save_history=False,
                )
            except Exception:
                pass  # FCM failure must not abort the whole request

        return Response({
            'message': f'Battery low alert sent to {len(contacts)} trusted contacts.',
            'battery_level': battery_level,
            'contacts_notified': len(contacts),
        }, status=status.HTTP_200_OK)
