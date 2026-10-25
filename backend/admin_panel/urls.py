from django.urls import path
from .views import (
    dashboard_web_view,
    AdminStatsApiView,
    AdminIncidentTriageApiView,
    AdminVolunteerApprovalApiView,
    AdminUserListView,
    AdminUserBanView,
    AdminSOSLogsView,
    AdminDangerZonePendingView,
    AdminBroadcastView,
    AdminVerifyUserView,
    AdminAnalyticsView,
    AdminReportsDataView,
    AdminExportIncidentsExcelView,
    AdminExportIncidentsCsvView,
    AdminExportIncidentsPdfView,
    admin_report_html_view,
)

app_name = 'admin_panel'

urlpatterns = [
    # Web Dashboard
    path('', dashboard_web_view, name='admin_dashboard_root'),
    path('dashboard/', dashboard_web_view, name='admin_dashboard'),

    # REST APIs – Stats & Incident Triage
    path('api/stats/', AdminStatsApiView.as_view(), name='admin_stats_api'),
    path('api/incidents/', AdminIncidentTriageApiView.as_view(), name='admin_incidents_api'),
    path('api/incidents/<int:id>/resolve/', AdminIncidentTriageApiView.as_view(), name='admin_resolve_incident_api'),

    # REST APIs – Volunteer Management
    path('api/volunteers/pending/', AdminVolunteerApprovalApiView.as_view(), name='admin_pending_volunteers_api'),
    path('api/volunteers/<int:id>/verify/', AdminVolunteerApprovalApiView.as_view(), name='admin_verify_volunteer_api'),

    # REST APIs – User Management
    path('api/users/', AdminUserListView.as_view(), name='admin_users'),
    path('api/users/<int:id>/ban/', AdminUserBanView.as_view(), name='admin_ban_user'),
    path('api/users/<int:id>/verify/', AdminVerifyUserView.as_view(), name='admin_verify_user'),

    # REST APIs – SOS Logs
    path('api/sos-logs/', AdminSOSLogsView.as_view(), name='admin_sos_logs'),

    # REST APIs – Danger Zones
    path('api/dangerzones/pending/', AdminDangerZonePendingView.as_view(), name='admin_pending_dangerzones'),

    # REST APIs – Broadcast
    path('api/broadcast/', AdminBroadcastView.as_view(), name='admin_broadcast'),

    # REST APIs – Analytics
    path('api/analytics/', AdminAnalyticsView.as_view(), name='admin_analytics'),

    # REST APIs – Reporting Dashboards & Exports
    path('api/reports/', AdminReportsDataView.as_view(), name='admin_reports_api'),
    path('api/reports/export/excel/', AdminExportIncidentsExcelView.as_view(), name='admin_export_excel'),
    path('api/reports/export/csv/', AdminExportIncidentsCsvView.as_view(), name='admin_export_csv'),
    path('api/reports/export/pdf/', AdminExportIncidentsPdfView.as_view(), name='admin_export_pdf'),
    path('reports/view/', admin_report_html_view, name='admin_report_html_view'),
]
