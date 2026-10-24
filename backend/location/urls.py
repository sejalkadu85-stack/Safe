from django.urls import path
from .views import (
    UpdateLocationView,
    LiveLocationView,
    LocationHistoryView,
    StartLocationShareView,
    StopLocationShareView,
    LocationShareStatusView,
    ListCreateSafeZoneView,
    SafeZoneDetailView,
    CheckGeofenceView,
    SafeZoneEventsHistoryView,
    StartJourneyView,
    UpdateJourneyProgressView,
    CompleteJourneyView,
    ActiveJourneyView,
    ReportDangerZoneView,
    DangerZoneListView,
    NearbyDangerZonesView,
    VerifyDangerZoneView,
)


app_name = 'location'

urlpatterns = [
    # Location Tracking Pings
    path('update/', UpdateLocationView.as_view(), name='update_location'),

    # Live Queries
    path('live/<int:user_id>/', LiveLocationView.as_view(), name='live_location'),
    path('history/<int:user_id>/', LocationHistoryView.as_view(), name='location_history'),

    # Location Share Sessions
    path('share/', StartLocationShareView.as_view(), name='start_share'),
    path('share/stop/', StopLocationShareView.as_view(), name='stop_share'),
    path('share/status/', LocationShareStatusView.as_view(), name='share_status'),

    # Safe Zones & Geofencing
    path('safezone/', ListCreateSafeZoneView.as_view(), name='list_create_safezone'),
    path('safezone/<int:id>/', SafeZoneDetailView.as_view(), name='safezone_detail'),
    path('safezone/check/', CheckGeofenceView.as_view(), name='check_geofence'),
    path('safezone/events/', SafeZoneEventsHistoryView.as_view(), name='safezone_events'),

    # Safe Journey Mode (Task 4.1)
    path('journey/start/', StartJourneyView.as_view(), name='start_journey'),
    path('journey/<int:id>/update/', UpdateJourneyProgressView.as_view(), name='update_journey'),
    path('journey/<int:id>/complete/', CompleteJourneyView.as_view(), name='complete_journey'),
    path('journey/active/', ActiveJourneyView.as_view(), name='active_journey'),

    # Danger Zones (Task 4.4)
    path('dangerzones/report/', ReportDangerZoneView.as_view(), name='report_dangerzone'),
    path('dangerzones/', DangerZoneListView.as_view(), name='list_dangerzones'),
    path('dangerzones/nearby/', NearbyDangerZonesView.as_view(), name='nearby_dangerzones'),
    path('dangerzones/<int:id>/verify/', VerifyDangerZoneView.as_view(), name='verify_dangerzone'),
]

