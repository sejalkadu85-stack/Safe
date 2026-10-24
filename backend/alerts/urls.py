from django.urls import path
from .views import (
    AddTrustedContactView,
    ListTrustedContactsView,
    PendingContactRequestsView,
    RespondContactRequestView,
    ContactDetailUpdateDeleteView,
    VerifyContactView,
    UpdateContactPreferencesView,
    TriggerSOSView,
    CancelSOSView,
    ResolveSOSView,
    SOSIncidentCloseView,
    SOSIncidentRespondView,
    SOSIncidentEscalateView,
    SOSIncidentDetailStatusView,
    IncidentChatView,
    GuardianActiveAlertsView,
    AdminEscalationConfigView,
    AcknowledgeSOSView,
    ActiveSOSAlertsView,
    SOSAlertHistoryView,
    SOSAlertDetailView,
    UploadEvidenceView,
    EvidenceFileListView,
    EvidenceFileDetailView,
    BatteryLowAlertView,
)

app_name = 'alerts'

urlpatterns = [
    # Trusted Contacts Management
    path('contacts/add/', AddTrustedContactView.as_view(), name='add_contact'),
    path('contacts/', ListTrustedContactsView.as_view(), name='list_contacts'),
    path('contacts/pending/', PendingContactRequestsView.as_view(), name='pending_contacts'),
    path('contacts/<int:id>/respond/', RespondContactRequestView.as_view(), name='respond_contact'),
    path('contacts/<int:id>/verify/', VerifyContactView.as_view(), name='verify_contact'),
    path('contacts/<int:id>/preferences/', UpdateContactPreferencesView.as_view(), name='update_preferences'),
    path('contacts/<int:id>/', ContactDetailUpdateDeleteView.as_view(), name='contact_detail_update_delete'),
    path('contacts/<int:id>/delete/', ContactDetailUpdateDeleteView.as_view(), name='delete_contact'),

    # Emergency SOS & Incident Lifecycle Operations
    path('sos/trigger/', TriggerSOSView.as_view(), name='trigger_sos'),
    path('sos/active/', ActiveSOSAlertsView.as_view(), name='active_sos'),
    path('sos/history/', SOSAlertHistoryView.as_view(), name='sos_history'),
    path('sos/<int:id>/', SOSAlertDetailView.as_view(), name='sos_detail'),
    path('sos/<int:id>/status/', SOSIncidentDetailStatusView.as_view(), name='sos_incident_status'),
    path('sos/<int:id>/escalate/', SOSIncidentEscalateView.as_view(), name='sos_incident_escalate'),
    path('sos/<int:id>/respond/', SOSIncidentRespondView.as_view(), name='sos_incident_respond'),
    path('sos/<int:id>/resolve/', ResolveSOSView.as_view(), name='resolve_sos'),
    path('sos/<int:id>/close/', SOSIncidentCloseView.as_view(), name='close_sos'),
    path('sos/<int:id>/cancel/', CancelSOSView.as_view(), name='cancel_sos'),
    path('sos/<int:id>/acknowledge/', AcknowledgeSOSView.as_view(), name='acknowledge_sos'),

    # Emergency Incident Chat
    path('sos/<int:id>/chat/', IncidentChatView.as_view(), name='sos_incident_chat'),

    # Guardian Dashboard Active Alerts
    path('guardian/active/', GuardianActiveAlertsView.as_view(), name='guardian_active_alerts'),

    # Admin Escalation Settings
    path('admin/escalation-config/', AdminEscalationConfigView.as_view(), name='admin_escalation_config'),

    # Evidence Recording
    path('evidence/upload/', UploadEvidenceView.as_view(), name='upload_evidence'),
    path('evidence/', EvidenceFileListView.as_view(), name='list_evidence'),
    path('evidence/<int:id>/', EvidenceFileDetailView.as_view(), name='detail_evidence'),

    # Battery-Low Auto Alert
    path('battery-low/', BatteryLowAlertView.as_view(), name='battery_low_alert'),
]
