from django.urls import path
from .views import (
    RegisterDeviceTokenView,
    NotificationListView,
    MarkNotificationReadView,
    MarkAllNotificationsReadView,
    CheckInScheduleListCreateView,
    CheckInScheduleDetailView,
    RespondToCheckInView,
    StartCheckInSessionView,
    RespondCheckInSessionView,
    StopCheckInSessionView,
    ActiveCheckInSessionView,
)

app_name = 'notifications'

urlpatterns = [
    # Device Token Registration
    path('token/', RegisterDeviceTokenView.as_view(), name='register_token'),

    # Notification History & Read Status
    path('', NotificationListView.as_view(), name='notifications_list'),
    path('<int:id>/read/', MarkNotificationReadView.as_view(), name='mark_read'),
    path('read-all/', MarkAllNotificationsReadView.as_view(), name='mark_all_read'),

    # Scheduled Check-In Reminders
    path('check-ins/', CheckInScheduleListCreateView.as_view(), name='check_in_schedules'),
    path('check-ins/<int:id>/', CheckInScheduleDetailView.as_view(), name='check_in_schedule_detail'),
    path('check-in/respond/', RespondToCheckInView.as_view(), name='respond_check_in'),

    # Periodic Check-In Timer (Task 4.2)
    path('session/start/', StartCheckInSessionView.as_view(), name='start_checkin_session'),
    path('session/respond/', RespondCheckInSessionView.as_view(), name='respond_checkin_session'),
    path('session/stop/', StopCheckInSessionView.as_view(), name='stop_checkin_session'),
    path('session/status/', ActiveCheckInSessionView.as_view(), name='status_checkin_session'),
]

