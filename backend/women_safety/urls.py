"""
URL configuration for women_safety project.
"""

from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static

from location.views import (
    StartJourneyView, UpdateJourneyProgressView, CompleteJourneyView, ActiveJourneyView,
    ReportDangerZoneView, DangerZoneListView, NearbyDangerZonesView, VerifyDangerZoneView
)
from alerts.views import UploadEvidenceView, EvidenceFileListView, EvidenceFileDetailView, BatteryLowAlertView
from notifications.views import (
    StartCheckInSessionView, RespondCheckInSessionView, StopCheckInSessionView, ActiveCheckInSessionView
)
from community.views import (
    RegisterOrUpdateVolunteerView, VolunteerProfileView, NearbyVolunteersView,
    CreateVolunteerRequestView, ListVolunteerRequestsView, RespondVolunteerRequestView,
    RateVolunteerRequestView, HelplineListView
)
from admin_panel.views import (
    dashboard_web_view,
    AdminStatsApiView,
    AdminVolunteerApprovalApiView,
    AdminUserListView,
    AdminUserBanView,
    AdminSOSLogsView,
    AdminDangerZonePendingView,
    AdminBroadcastView,
    AdminReportsDataView,
    AdminExportIncidentsExcelView,
    AdminExportIncidentsCsvView,
    AdminExportIncidentsPdfView,
)

urlpatterns = [
    path('admin/', admin.site.urls),
    path('admin-panel/', include('admin_panel.urls')),
    
    # API Endpoints
    path('api/auth/', include('accounts.urls')),
    path('api/alerts/', include('alerts.urls')),
    path('api/location/', include('location.urls')),
    path('api/messages/', include('messaging.urls')),
    path('api/community/', include('community.urls')),
    path('api/admin-panel/', include(('admin_panel.urls', 'admin_panel'), namespace='admin_panel_api')),
    path('api/notifications/', include('notifications.urls')),

    # Safe Journey Mode (Task 4.1)
    path('api/journey/start/', StartJourneyView.as_view(), name='root_start_journey'),
    path('api/journey/<int:id>/update/', UpdateJourneyProgressView.as_view(), name='root_update_journey'),
    path('api/journey/<int:id>/complete/', CompleteJourneyView.as_view(), name='root_complete_journey'),
    path('api/journey/active/', ActiveJourneyView.as_view(), name='root_active_journey'),

    # Periodic Check-In Timer (Task 4.2)
    path('api/checkin/start/', StartCheckInSessionView.as_view(), name='root_start_checkin'),
    path('api/checkin/respond/', RespondCheckInSessionView.as_view(), name='root_respond_checkin'),
    path('api/checkin/stop/', StopCheckInSessionView.as_view(), name='root_stop_checkin'),
    path('api/checkin/status/', ActiveCheckInSessionView.as_view(), name='root_status_checkin'),

    # Evidence Recording (Task 4.3)
    path('api/evidence/upload/', UploadEvidenceView.as_view(), name='root_upload_evidence'),
    path('api/evidence/', EvidenceFileListView.as_view(), name='root_list_evidence'),
    path('api/evidence/<int:id>/', EvidenceFileDetailView.as_view(), name='root_detail_evidence'),

    # Danger Zones (Task 4.4)
    path('api/dangerzones/report/', ReportDangerZoneView.as_view(), name='root_report_dangerzone'),
    path('api/dangerzones/', DangerZoneListView.as_view(), name='root_list_dangerzones'),
    path('api/dangerzones/nearby/', NearbyDangerZonesView.as_view(), name='root_nearby_dangerzones'),
    path('api/dangerzones/<int:id>/verify/', VerifyDangerZoneView.as_view(), name='root_verify_dangerzone'),

    # Volunteer System (Task 5.1)
    path('api/volunteers/register/', RegisterOrUpdateVolunteerView.as_view(), name='root_volunteers_register'),
    path('api/volunteers/profile/', VolunteerProfileView.as_view(), name='root_volunteers_profile'),
    path('api/volunteers/nearby/', NearbyVolunteersView.as_view(), name='root_volunteers_nearby'),
    path('api/volunteers/request/', CreateVolunteerRequestView.as_view(), name='root_volunteers_request'),
    path('api/volunteers/requests/', ListVolunteerRequestsView.as_view(), name='root_volunteers_requests_list'),
    path('api/volunteers/requests/<int:id>/respond/', RespondVolunteerRequestView.as_view(), name='root_volunteers_respond'),
    path('api/volunteers/requests/<int:id>/rate/', RateVolunteerRequestView.as_view(), name='root_volunteers_rate'),

    # Helpline Directory (Task 5.2)
    path('api/helplines/', HelplineListView.as_view(), name='root_helplines_list'),

    # Admin Dashboard APIs (Task 5.3) – flat routes alongside admin_panel/ include
    path('api/admin/stats/', AdminStatsApiView.as_view(), name='root_admin_stats'),
    path('api/admin/users/', AdminUserListView.as_view(), name='root_admin_users'),
    path('api/admin/users/<int:id>/ban/', AdminUserBanView.as_view(), name='root_admin_ban_user'),
    path('api/admin/sos-logs/', AdminSOSLogsView.as_view(), name='root_admin_sos_logs'),
    path('api/admin/dangerzones/pending/', AdminDangerZonePendingView.as_view(), name='root_admin_pending_dangerzones'),
    path('api/admin/broadcast/', AdminBroadcastView.as_view(), name='root_admin_broadcast'),
    path('api/admin/volunteers/pending/', AdminVolunteerApprovalApiView.as_view(), name='root_admin_pending_volunteers'),
    path('api/admin/volunteers/<int:id>/verify/', AdminVolunteerApprovalApiView.as_view(), name='root_admin_verify_volunteer'),
    path('api/admin/reports/', AdminReportsDataView.as_view(), name='root_admin_reports'),
    path('api/admin/reports/export/excel/', AdminExportIncidentsExcelView.as_view(), name='root_admin_export_excel'),
    path('api/admin/reports/export/csv/', AdminExportIncidentsCsvView.as_view(), name='root_admin_export_csv'),
    path('api/admin/reports/export/pdf/', AdminExportIncidentsPdfView.as_view(), name='root_admin_export_pdf'),

    # Battery-Low Auto Alert (Task 5.4)
    path('api/alerts/battery-low/', BatteryLowAlertView.as_view(), name='root_battery_low_alert'),
]


# Serve media and static files in development
if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)
    urlpatterns += static(settings.STATIC_URL, document_root=settings.STATIC_ROOT)
