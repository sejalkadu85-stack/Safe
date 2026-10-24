from datetime import timedelta
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.shortcuts import get_object_or_404
from django.utils import timezone

from .models import DeviceToken, Notification, CheckInSchedule, CheckInSession
from .serializers import (
    DeviceTokenSerializer,
    RegisterDeviceTokenSerializer,
    NotificationSerializer,
    CheckInScheduleSerializer,
    CheckInSessionSerializer,
    StartCheckInSessionSerializer,
)
from .fcm_service import send_fcm_notification



class RegisterDeviceTokenView(APIView):
    """
    POST /api/notifications/token/
    Register or update an FCM device token for the authenticated user.
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = RegisterDeviceTokenSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        token = serializer.validated_data['token']
        device_type = serializer.validated_data.get('device_type', 'android')
        device_name = serializer.validated_data.get('device_name', '')

        device_token, created = DeviceToken.objects.update_or_create(
            token=token,
            defaults={
                'user': request.user,
                'device_type': device_type,
                'device_name': device_name,
                'is_active': True,
            }
        )

        return Response(
            DeviceTokenSerializer(device_token).data,
            status=status.HTTP_201_CREATED if created else status.HTTP_200_OK
        )


class NotificationListView(APIView):
    """
    GET /api/notifications/
    List notification history for the authenticated user.
    Supports query parameters:
      - ?unread=true (filter to unread only)
      - ?type=sos_alert (filter by notification_type)
    """
    permission_classes = [IsAuthenticated]

    def get(self, request):
        notifications = Notification.objects.filter(recipient=request.user)

        # Filters
        unread_only = request.query_params.get('unread')
        if unread_only and unread_only.lower() in ('true', '1'):
            notifications = notifications.filter(is_read=False)

        type_filter = request.query_params.get('type')
        if type_filter:
            notifications = notifications.filter(notification_type=type_filter)

        total_unread = Notification.objects.filter(recipient=request.user, is_read=False).count()
        serializer = NotificationSerializer(notifications[:50], many=True)

        return Response({
            "total_unread": total_unread,
            "notifications": serializer.data
        }, status=status.HTTP_200_OK)


class MarkNotificationReadView(APIView):
    """
    PUT /api/notifications/<int:id>/read/
    Mark a notification as read.
    """
    permission_classes = [IsAuthenticated]

    def put(self, request, id):
        notification = get_object_or_404(Notification, id=id, recipient=request.user)
        if not notification.is_read:
            notification.is_read = True
            notification.save(update_fields=['is_read'])

        return Response({
            "message": "Notification marked as read.",
            "id": notification.id,
            "is_read": True
        }, status=status.HTTP_200_OK)

    def patch(self, request, id):
        return self.put(request, id)


class MarkAllNotificationsReadView(APIView):
    """
    POST /api/notifications/read-all/
    Mark all unread notifications for the user as read.
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        count = Notification.objects.filter(
            recipient=request.user,
            is_read=False
        ).update(is_read=True)

        return Response({
            "message": f"Marked {count} notifications as read.",
            "count": count
        }, status=status.HTTP_200_OK)


class CheckInScheduleListCreateView(APIView):
    """
    GET /api/notifications/check-ins/
    List scheduled check-ins for the authenticated user.

    POST /api/notifications/check-ins/
    Create a new scheduled check-in reminder.
    """
    permission_classes = [IsAuthenticated]

    def get(self, request):
        schedules = CheckInSchedule.objects.filter(user=request.user).order_by('scheduled_time')
        serializer = CheckInScheduleSerializer(schedules, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request):
        serializer = CheckInScheduleSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        schedule = serializer.save(user=request.user)
        return Response(CheckInScheduleSerializer(schedule).data, status=status.HTTP_201_CREATED)


class CheckInScheduleDetailView(APIView):
    """
    DELETE /api/notifications/check-ins/<int:id>/
    Cancel/delete a scheduled check-in.
    """
    permission_classes = [IsAuthenticated]

    def delete(self, request, id):
        schedule = get_object_or_404(CheckInSchedule, id=id, user=request.user)
        schedule.delete()
        return Response({"message": "Check-in schedule removed successfully."}, status=status.HTTP_200_OK)


class RespondToCheckInView(APIView):
    """
    POST /api/notifications/check-in/respond/
    Submit check-in confirmation ("I am safe!").
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        schedule_id = request.data.get('schedule_id')
        status_msg = request.data.get('status', "I'm safe! ✅")

        # Mark corresponding check-in notification as read if id provided
        notification_id = request.data.get('notification_id')
        if notification_id:
            Notification.objects.filter(id=notification_id, recipient=request.user).update(is_read=True)

        # Record check-in event in notification history
        Notification.objects.create(
            recipient=request.user,
            notification_type='check_in',
            title='Check-In Completed',
            body=f"Checked in successfully: {status_msg}",
            data={'status': status_msg, 'timestamp': timezone.now().isoformat()},
            is_read=True
        )

        return Response({
            "message": "Check-in recorded successfully.",
            "status": status_msg,
            "timestamp": timezone.now().isoformat()
        }, status=status.HTTP_200_OK)


# ==========================================
# PERIODIC CHECK-IN TIMER (Task 4.2)
# ==========================================

class StartCheckInSessionView(APIView):
    """
    POST /api/checkin/start/
    Start a periodic check-in timer session with interval (minutes).
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = StartCheckInSessionSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        interval = serializer.validated_data['interval_minutes']

        # Deactivate any previous active sessions
        CheckInSession.objects.filter(user=request.user, is_active=True).update(is_active=False)

        now = timezone.now()
        due = now + timedelta(minutes=interval)
        session = CheckInSession.objects.create(
            user=request.user,
            interval_minutes=interval,
            last_checkin=now,
            next_due=due,
            is_active=True,
        )

        return Response(CheckInSessionSerializer(session).data, status=status.HTTP_201_CREATED)


class RespondCheckInSessionView(APIView):
    """
    POST /api/checkin/respond/
    User responds "I'm safe". Resets next due timer.
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        session = CheckInSession.objects.filter(user=request.user, is_active=True).first()
        if not session:
            return Response(
                {"error": "No active check-in session found."},
                status=status.HTTP_400_BAD_REQUEST
            )

        now = timezone.now()
        session.last_checkin = now
        session.next_due = now + timedelta(minutes=session.interval_minutes)
        session.alert_triggered = False
        session.save()

        # Record check-in notification history
        Notification.objects.create(
            recipient=request.user,
            notification_type='check_in',
            title='Check-In Confirmed ✅',
            body="You marked yourself safe.",
            data={'timestamp': now.isoformat()},
            is_read=True
        )

        return Response({
            "message": "You are marked safe! Next check-in scheduled.",
            "session": CheckInSessionSerializer(session).data
        }, status=status.HTTP_200_OK)


class StopCheckInSessionView(APIView):
    """
    DELETE /api/checkin/stop/
    Stop the current active check-in timer session.
    """
    permission_classes = [IsAuthenticated]

    def delete(self, request):
        count = CheckInSession.objects.filter(user=request.user, is_active=True).update(is_active=False)
        return Response({
            "message": "Periodic check-in timer stopped.",
            "sessions_stopped": count
        }, status=status.HTTP_200_OK)


class ActiveCheckInSessionView(APIView):
    """
    GET /api/checkin/status/
    Get current active check-in session status.
    """
    permission_classes = [IsAuthenticated]

    def get(self, request):
        session = CheckInSession.objects.filter(user=request.user, is_active=True).first()
        if not session:
            return Response({"is_active": False, "session": None}, status=status.HTTP_200_OK)

        return Response({
            "is_active": True,
            "session": CheckInSessionSerializer(session).data
        }, status=status.HTTP_200_OK)

