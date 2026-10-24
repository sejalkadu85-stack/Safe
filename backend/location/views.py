from datetime import timedelta
from decimal import Decimal
from django.utils import timezone

from django.shortcuts import get_object_or_404
from django.contrib.auth import get_user_model
from django.db.models import Q
from rest_framework import generics, status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from rest_framework.exceptions import PermissionDenied

from .models import LocationLog, LocationShareSession, SafeZone, SafeZoneEvent, Journey, DangerZone
from .serializers import (
    LocationLogSerializer,
    LocationUpdateSerializer,
    LocationShareSessionSerializer,
    StartLocationShareSerializer,
    LiveLocationResponseSerializer,
    SafeZoneSerializer,
    CreateSafeZoneSerializer,
    SafeZoneEventSerializer,
    CheckGeofenceSerializer,
    JourneySerializer,
    StartJourneySerializer,
    UpdateJourneyProgressSerializer,
    DangerZoneSerializer,
    ReportDangerZoneSerializer,
)
from .services import process_geofence_for_ping
from .utils import distance_to_polyline, haversine_distance
from alerts.models import TrustedContact, SOSAlert
from alerts.serializers import SOSAlertSerializer
from notifications.fcm_service import send_fcm_notification

User = get_user_model()



def user_can_view_location(viewer, target_user):
    """
    Checks if `viewer` is authorized to see `target_user`'s live location:
    1. Viewer is the user herself.
    2. Viewer is an admin/staff.
    3. Target user currently has an ACTIVE SOS alert AND viewer is an accepted trusted contact.
    4. Target user currently has an ACTIVE LocationShareSession AND viewer is an authorized guardian.
    """
    if viewer == target_user:
        return True

    if viewer.role == 'admin' or viewer.is_staff or viewer.is_superuser:
        return True

    # Check for active SOS alert
    has_active_sos = SOSAlert.objects.filter(
        sender=target_user,
        status=SOSAlert.Status.ACTIVE
    ).exists()

    is_accepted_contact = TrustedContact.objects.filter(
        owner=target_user,
        contact_user=viewer,
        status=TrustedContact.Status.ACCEPTED
    ).exists()

    if has_active_sos and is_accepted_contact:
        return True

    # Check for active LocationShareSession
    now = timezone.now()
    active_session = LocationShareSession.objects.filter(
        user=target_user,
        is_active=True,
        expires_at__gt=now
    ).prefetch_related('shared_with').first()

    if active_session:
        if active_session.share_with_all_trusted and is_accepted_contact:
            return True
        if viewer in active_session.shared_with.all():
            return True

    return False


class UpdateLocationView(APIView):
    """
    POST /api/location/update/
    Receives GPS ping from device background service.
    Updates active SOS alert location if one is currently active.
    Evaluates safe zones for boundary exit/entry events.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = LocationUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        log = LocationLog.objects.create(
            user=request.user,
            latitude=data['latitude'],
            longitude=data['longitude'],
            accuracy=data.get('accuracy'),
            speed=data.get('speed'),
            altitude=data.get('altitude'),
            heading=data.get('heading'),
            battery_level=data.get('battery_level'),
            is_mock=data.get('is_mock', False),
            timestamp=timezone.now()
        )

        # Sync location with any active SOS alert
        active_sos = SOSAlert.objects.filter(
            sender=request.user,
            status=SOSAlert.Status.ACTIVE
        ).first()

        if active_sos:
            active_sos.latitude = data['latitude']
            active_sos.longitude = data['longitude']
            if data.get('battery_level') is not None:
                active_sos.battery_level = data['battery_level']
            active_sos.save()

        # Check safe zone geofence transitions
        geofence_events = process_geofence_for_ping(request.user, log)

        return Response(
            {
                'message': 'Location ping recorded.',
                'location': LocationLogSerializer(log).data,
                'geofence_events_triggered': len(geofence_events),
            },
            status=status.HTTP_201_CREATED
        )


class LiveLocationView(APIView):
    """
    GET /api/location/live/<int:user_id>/
    Fetches latest live position, active SOS status, and active sharing session.
    Protected by mutual consent and active sharing / emergency rules.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request, user_id):
        target_user = get_object_or_404(User, id=user_id)

        if not user_can_view_location(request.user, target_user):
            raise PermissionDenied(
                "Access restricted: Live location is private. "
                "An active emergency SOS or live sharing session is required to view coordinates."
            )

        latest_ping = LocationLog.objects.filter(user=target_user).first()

        active_sos = SOSAlert.objects.filter(
            sender=target_user,
            status=SOSAlert.Status.ACTIVE
        ).first()

        now = timezone.now()
        share_session = LocationShareSession.objects.filter(
            user=target_user,
            is_active=True,
            expires_at__gt=now
        ).first()

        payload = {
            'user': target_user,
            'latest_location': latest_ping,
            'has_active_sos': active_sos is not None,
            'active_sos': active_sos,
            'is_actively_sharing': share_session is not None,
            'share_session': share_session,
        }

        serializer = LiveLocationResponseSerializer(payload)
        return Response(serializer.data, status=status.HTTP_200_OK)


class LocationHistoryView(generics.ListAPIView):
    """
    GET /api/location/history/<int:user_id>/?hours=6&limit=100
    Retrieves GPS breadcrumb trail for authorized guardians during an event.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = LocationLogSerializer

    def get_queryset(self):
        user_id = self.kwargs.get('user_id')
        target_user = get_object_or_404(User, id=user_id)

        if not user_can_view_location(self.request.user, target_user):
            raise PermissionDenied("Access restricted: You do not have permission to view location history.")

        hours = int(self.request.query_params.get('hours', 6))
        hours = min(max(hours, 1), 48)  # Clamp between 1 and 48 hours

        since = timezone.now() - timedelta(hours=hours)
        limit = int(self.request.query_params.get('limit', 100))
        limit = min(max(limit, 10), 500)

        return LocationLog.objects.filter(
            user=target_user,
            timestamp__gte=since
        )[:limit]


class StartLocationShareView(APIView):
    """
    POST /api/location/share/
    Initiate a temporary location sharing session (e.g. 60 mins).
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = StartLocationShareSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        duration = data['duration_minutes']
        now = timezone.now()
        expires = now + timedelta(minutes=duration)

        # Deactivate previous active sessions
        LocationShareSession.objects.filter(
            user=request.user,
            is_active=True
        ).update(is_active=False)

        session = LocationShareSession.objects.create(
            user=request.user,
            duration_minutes=duration,
            expires_at=expires,
            is_active=True,
            note=data.get('note', ''),
            share_with_all_trusted=data.get('share_with_all_trusted', True)
        )

        shared_ids = data.get('shared_with_ids', [])
        if shared_ids:
            guardians = User.objects.filter(id__in=shared_ids)
            session.shared_with.set(guardians)

        return Response(
            {
                'message': f"Location sharing activated for {duration} minutes.",
                'session': LocationShareSessionSerializer(session).data
            },
            status=status.HTTP_201_CREATED
        )


class StopLocationShareView(APIView):
    """
    POST /api/location/share/stop/
    Prematurely stop any active location sharing sessions.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        count = LocationShareSession.objects.filter(
            user=request.user,
            is_active=True
        ).update(is_active=False)

        return Response(
            {
                'message': 'Location sharing stopped successfully.',
                'sessions_stopped': count
            },
            status=status.HTTP_200_OK
        )


class LocationShareStatusView(APIView):
    """
    GET /api/location/share/status/
    Returns the current active sharing session for the authenticated user.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request):
        now = timezone.now()
        active_session = LocationShareSession.objects.filter(
            user=request.user,
            is_active=True,
            expires_at__gt=now
        ).first()

        return Response(
            {
                'is_actively_sharing': active_session is not None,
                'session': LocationShareSessionSerializer(active_session).data if active_session else None
            },
            status=status.HTTP_200_OK
        )


# ==========================================
# SAFE ZONE / GEO-FENCING VIEWS
# ==========================================

class ListCreateSafeZoneView(generics.ListCreateAPIView):
    """
    GET /api/location/safezone/ -> List user's active safe zones
    POST /api/location/safezone/ -> Create a new safe zone (e.g. Home, College)
    """
    permission_classes = (IsAuthenticated,)

    def get_serializer_class(self):
        if self.request.method == 'POST':
            return CreateSafeZoneSerializer
        return SafeZoneSerializer

    def get_queryset(self):
        return SafeZone.objects.filter(user=self.request.user)


class SafeZoneDetailView(generics.RetrieveUpdateDestroyAPIView):
    """
    GET, PUT, PATCH, DELETE /api/location/safezone/<int:id>/
    Manage an individual safe zone.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = SafeZoneSerializer
    lookup_field = 'id'

    def get_queryset(self):
        return SafeZone.objects.filter(user=self.request.user)


class CheckGeofenceView(APIView):
    """
    POST /api/location/safezone/check/
    Checks whether given coordinates are inside any of the user's active safe zones.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = CheckGeofenceSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        lat = serializer.validated_data['latitude']
        lon = serializer.validated_data['longitude']

        safe_zones = SafeZone.objects.filter(user=request.user, is_active=True)

        results = []
        inside_any = False

        for zone in safe_zones:
            dist = zone.distance_to(lat, lon)
            is_in = dist <= zone.radius_meters
            if is_in:
                inside_any = True

            results.append({
                'zone_id': zone.id,
                'zone_name': zone.name,
                'radius_meters': zone.radius_meters,
                'current_distance_meters': round(dist, 1),
                'is_inside': is_in,
            })

        return Response({
            'is_inside_safe_zone': inside_any,
            'zones': results,
        }, status=status.HTTP_200_OK)


class SafeZoneEventsHistoryView(generics.ListAPIView):
    """
    GET /api/location/safezone/events/
    List safe zone exit/entry event logs for the user.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = SafeZoneEventSerializer

    def get_queryset(self):
        return SafeZoneEvent.objects.filter(user=self.request.user)[:50]


# ==========================================
# SAFE JOURNEY VIEWS (Task 4.1)
# ==========================================

class StartJourneyView(APIView):
    """
    POST /api/journey/start/
    Start a new Safe Journey with route and ETA.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = StartJourneySerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        # Cancel any existing active journeys for this user
        Journey.objects.filter(
            user=request.user,
            status__in=[Journey.Status.ACTIVE, Journey.Status.DEVIATED]
        ).update(status=Journey.Status.CANCELLED)

        journey = Journey.objects.create(
            user=request.user,
            start_location=data['start_location'],
            destination=data['destination'],
            start_latitude=data['start_latitude'],
            start_longitude=data['start_longitude'],
            dest_latitude=data['dest_latitude'],
            dest_longitude=data['dest_longitude'],
            current_latitude=data['start_latitude'],
            current_longitude=data['start_longitude'],
            route_polyline=data.get('route_polyline', ''),
            estimated_arrival=data['estimated_arrival'],
            share_with_all_trusted=data.get('share_with_all_trusted', True),
        )

        shared_ids = data.get('shared_with_ids', [])
        if shared_ids:
            guardians = User.objects.filter(id__in=shared_ids)
            journey.shared_with.set(guardians)

        # Notify guardians
        recipients = []
        if journey.share_with_all_trusted:
            accepted_contacts = TrustedContact.objects.filter(
                owner=request.user,
                status=TrustedContact.Status.ACCEPTED
            ).select_related('contact_user')
            recipients = [c.contact_user for c in accepted_contacts]
        else:
            recipients = list(journey.shared_with.all())

        eta_formatted = journey.estimated_arrival.strftime('%I:%M %p')
        for guardian in recipients:
            send_fcm_notification(
                user=guardian,
                title="Safe Journey Started 🚗",
                body=f"{request.user.username} started traveling to {journey.destination}. Expected arrival: {eta_formatted}",
                notification_type='location_share',
                data={
                    'journey_id': str(journey.id),
                    'user_id': str(request.user.id),
                    'type': 'journey_started',
                }
            )

        return Response(JourneySerializer(journey).data, status=status.HTTP_201_CREATED)


class UpdateJourneyProgressView(APIView):
    """
    PUT /api/journey/<int:id>/update/
    Updates current location along the route.
    Detects deviation > 500m -> auto-triggers SOS alert.
    Detects overdue ETA + 10 min -> auto-triggers SOS alert.
    """
    permission_classes = (IsAuthenticated,)

    def put(self, request, id):
        journey = get_object_or_404(Journey, id=id, user=request.user)
        if not journey.is_active:
            return Response(
                {'error': f'Journey is {journey.get_status_display().lower()} and cannot be updated.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        serializer = UpdateJourneyProgressSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        lat = serializer.validated_data['current_latitude']
        lon = serializer.validated_data['current_longitude']

        journey.current_latitude = lat
        journey.current_longitude = lon

        deviation_dist = 0.0
        if journey.route_polyline:
            deviation_dist = distance_to_polyline(lat, lon, journey.route_polyline)

        # Check > 500m deviation
        new_deviation = False
        if deviation_dist > 500.0 and not journey.deviation_detected:
            journey.deviation_detected = True
            journey.status = Journey.Status.DEVIATED
            new_deviation = True

            sos = SOSAlert.objects.create(
                sender=request.user,
                status=SOSAlert.Status.ACTIVE,
                trigger_type=SOSAlert.TriggerType.JOURNEY_DEVIATION,
                latitude=lat,
                longitude=lon,
                message=f"ROUTE DEVIATION ALERT! {request.user.username} is off-course by {int(deviation_dist)} meters on journey to {journey.destination}."
            )
            recipients = list(journey.shared_with.all()) if not journey.share_with_all_trusted else [
                c.contact_user for c in TrustedContact.objects.filter(owner=request.user, status=TrustedContact.Status.ACCEPTED).select_related('contact_user')
            ]
            for guardian in recipients:
                send_fcm_notification(
                    user=guardian,
                    title="⚠️ Route Deviation Alert!",
                    body=f"{request.user.username} has deviated {int(deviation_dist)}m from the planned route to {journey.destination}!",
                    notification_type='sos_alert',
                    data={
                        'sos_id': str(sos.id),
                        'journey_id': str(journey.id),
                        'latitude': str(lat),
                        'longitude': str(lon),
                    }
                )

        # Check ETA exceeded by 10 min
        now = timezone.now()
        if now > journey.estimated_arrival + timedelta(minutes=10) and not journey.eta_exceeded_alerted:
            journey.eta_exceeded_alerted = True
            sos = SOSAlert.objects.create(
                sender=request.user,
                status=SOSAlert.Status.ACTIVE,
                trigger_type=SOSAlert.TriggerType.JOURNEY_DEVIATION,
                latitude=lat,
                longitude=lon,
                message=f"OVERDUE ETA ALERT! {request.user.username} has not confirmed arrival at {journey.destination} (10 mins overdue)."
            )
            recipients = list(journey.shared_with.all()) if not journey.share_with_all_trusted else [
                c.contact_user for c in TrustedContact.objects.filter(owner=request.user, status=TrustedContact.Status.ACCEPTED).select_related('contact_user')
            ]
            for guardian in recipients:
                send_fcm_notification(
                    user=guardian,
                    title="⏰ Overdue Journey Arrival!",
                    body=f"{request.user.username} was expected to arrive at {journey.destination} over 10 minutes ago.",
                    notification_type='sos_alert',
                    data={
                        'sos_id': str(sos.id),
                        'journey_id': str(journey.id),
                        'latitude': str(lat),
                        'longitude': str(lon),
                    }
                )

        journey.save()
        return Response({
            'journey': JourneySerializer(journey).data,
            'deviation_distance_meters': round(deviation_dist, 1),
            'deviation_detected': journey.deviation_detected,
            'new_deviation_triggered': new_deviation,
        }, status=status.HTTP_200_OK)


class CompleteJourneyView(APIView):
    """
    PUT /api/journey/<int:id>/complete/
    Mark journey as completed safely.
    """
    permission_classes = (IsAuthenticated,)

    def put(self, request, id):
        journey = get_object_or_404(Journey, id=id, user=request.user)
        journey.status = Journey.Status.COMPLETED
        journey.save()

        recipients = list(journey.shared_with.all()) if not journey.share_with_all_trusted else [
            c.contact_user for c in TrustedContact.objects.filter(owner=request.user, status=TrustedContact.Status.ACCEPTED).select_related('contact_user')
        ]
        for guardian in recipients:
            send_fcm_notification(
                user=guardian,
                title="Journey Completed Safely ✅",
                body=f"{request.user.username} arrived safely at {journey.destination}!",
                notification_type='location_share',
                data={'journey_id': str(journey.id), 'type': 'journey_completed'}
            )

        return Response(JourneySerializer(journey).data, status=status.HTTP_200_OK)


class ActiveJourneyView(APIView):
    """
    GET /api/journey/active/?user_id=<optional>
    Get active journey for self or monitored contact.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request):
        target_user_id = request.query_params.get('user_id')
        if target_user_id:
            target_user = get_object_or_404(User, id=target_user_id)
            if not user_can_view_location(request.user, target_user):
                raise PermissionDenied("You do not have permission to view this journey.")
            journey = Journey.objects.filter(
                user=target_user,
                status__in=[Journey.Status.ACTIVE, Journey.Status.DEVIATED]
            ).first()
        else:
            journey = Journey.objects.filter(
                user=request.user,
                status__in=[Journey.Status.ACTIVE, Journey.Status.DEVIATED]
            ).first()

        if not journey:
            return Response({'has_active_journey': False, 'journey': None}, status=status.HTTP_200_OK)

        return Response({
            'has_active_journey': True,
            'journey': JourneySerializer(journey).data
        }, status=status.HTTP_200_OK)


# ==========================================
# DANGER ZONE VIEWS (Task 4.4)
# ==========================================

class ReportDangerZoneView(APIView):
    """
    POST /api/dangerzones/report/
    Report an unsafe location.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = ReportDangerZoneSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        data = serializer.validated_data

        lat = data['latitude']
        lon = data['longitude']

        existing = DangerZone.objects.filter(
            latitude__range=(lat - Decimal('0.001'), lat + Decimal('0.001')),
            longitude__range=(lon - Decimal('0.001'), lon + Decimal('0.001')),
        )

        matched = None
        for zone in existing:
            if zone.distance_to(lat, lon) <= 50.0:
                matched = zone
                break

        if matched:
            matched.report_count += 1
            if matched.report_count >= 3 and not matched.verified:
                matched.verified = True
            matched.save()
            return Response(DangerZoneSerializer(matched).data, status=status.HTTP_200_OK)

        zone = DangerZone.objects.create(
            reported_by=request.user,
            title=data.get('title', 'Reported Danger Area'),
            latitude=lat,
            longitude=lon,
            radius=data.get('radius', 150.0),
            description=data['description'],
            category=data.get('category', 'harassment_hotspot'),
            severity=data.get('severity', 'medium'),
            verified=(request.user.role == 'admin' or request.user.is_staff),
        )
        return Response(DangerZoneSerializer(zone).data, status=status.HTTP_201_CREATED)


class DangerZoneListView(generics.ListAPIView):
    """
    GET /api/dangerzones/
    List danger zones for heatmap visualization.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = DangerZoneSerializer

    def get_queryset(self):
        category = self.request.query_params.get('category')
        qs = DangerZone.objects.all()
        if category:
            qs = qs.filter(category=category)
        return qs.order_by('-verified', '-report_count')[:100]


class NearbyDangerZonesView(APIView):
    """
    GET /api/dangerzones/nearby/?lat=&lng=&radius=
    Get danger zones near a specified coordinate.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request):
        lat = request.query_params.get('lat')
        lng = request.query_params.get('lng')
        radius = float(request.query_params.get('radius', 1500.0))

        if not lat or not lng:
            return Response({'error': 'lat and lng query parameters are required.'}, status=status.HTTP_400_BAD_REQUEST)

        plat = float(lat)
        plon = float(lng)

        zones = DangerZone.objects.all()
        nearby = []
        for zone in zones:
            dist = zone.distance_to(plat, plon)
            if dist <= radius:
                zone_data = DangerZoneSerializer(zone).data
                zone_data['distance_meters'] = round(dist, 1)
                nearby.append(zone_data)

        nearby.sort(key=lambda x: x['distance_meters'])
        return Response(nearby, status=status.HTTP_200_OK)


class VerifyDangerZoneView(APIView):
    """
    PUT /api/dangerzones/<int:id>/verify/
    Verify or unverify a danger zone (admin/staff).
    """
    permission_classes = (IsAuthenticated,)

    def put(self, request, id):
        if not (request.user.role == 'admin' or request.user.is_staff):
            raise PermissionDenied("Only administrators can verify danger zones.")

        zone = get_object_or_404(DangerZone, id=id)
        verified = request.data.get('verified', True)
        zone.verified = verified
        zone.save()
        return Response(DangerZoneSerializer(zone).data, status=status.HTTP_200_OK)

