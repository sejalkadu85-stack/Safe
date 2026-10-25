from django.shortcuts import render, redirect, get_object_or_404
from django.utils import timezone
from django.db.models import Count, Q
from django.contrib import messages
from django.http import HttpResponse
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status, permissions

from accounts.models import CustomUser
from alerts.models import SOSAlert, EvidenceFile, IncidentTimeline
from location.models import DangerZone, Journey
from community.models import Volunteer, VolunteerRequest, Helpline
from notifications.models import Notification


def dashboard_web_view(request):
    """
    Rich web admin dashboard for incident triage, danger zone verification,
    volunteer vetting, real-time safety metrics, and role management.
    """
    # Quick action handling via POST
    if request.method == 'POST':
        action = request.POST.get('action')
        target_id = request.POST.get('target_id')

        if action == 'resolve_sos' and target_id:
            try:
                alert = SOSAlert.objects.get(id=target_id)
                alert.status = 'resolved'
                alert.resolved_at = timezone.now()
                alert.save(update_fields=['status', 'resolved_at'])
                messages.success(request, f"Incident #{target_id} marked as Resolved.")
            except SOSAlert.DoesNotExist:
                messages.error(request, f"Incident #{target_id} not found.")

        elif action == 'assign_responder' and target_id:
            responder_name = request.POST.get('responder_name', 'Quick Response Unit')
            try:
                alert = SOSAlert.objects.get(id=target_id)
                alert.status = 'active'
                alert.save(update_fields=['status'])
                messages.success(request, f"Responder '{responder_name}' dispatched to Incident #{target_id}.")
            except SOSAlert.DoesNotExist:
                messages.error(request, f"Incident #{target_id} not found.")

        elif action == 'toggle_user_status' and target_id:
            try:
                target_user = CustomUser.objects.get(id=target_id)
                target_user.is_active = not target_user.is_active
                target_user.save(update_fields=['is_active'])
                st = "Activated" if target_user.is_active else "Deactivated/Banned"
                messages.success(request, f"User '{target_user.username}' is now {st}.")
            except CustomUser.DoesNotExist:
                messages.error(request, f"User #{target_id} not found.")

        elif action == 'toggle_verify_zone' and target_id:
            try:
                zone = DangerZone.objects.get(id=target_id)
                zone.verified = not zone.verified
                zone.save(update_fields=['verified'])
                status_text = "Verified" if zone.verified else "Unverified"
                messages.success(request, f"Danger Zone '{zone.description[:30]}' marked as {status_text}.")
            except DangerZone.DoesNotExist:
                messages.error(request, f"Danger Zone #{target_id} not found.")

        elif action == 'toggle_verify_volunteer' and target_id:
            try:
                vol = Volunteer.objects.get(id=target_id)
                vol.is_verified = not vol.is_verified
                vol.save(update_fields=['is_verified'])
                status_text = "Verified" if vol.is_verified else "Unverified"
                messages.success(request, f"Volunteer '{vol.user.username}' marked as {status_text}.")
            except Volunteer.DoesNotExist:
                messages.error(request, f"Volunteer #{target_id} not found.")

        elif action == 'approve_user' and target_id:
            try:
                pending_user = CustomUser.objects.get(id=target_id)
                pending_user.verification_status = CustomUser.VerificationStatus.APPROVED
                pending_user.is_verified = True
                pending_user.save(update_fields=['verification_status', 'is_verified'])
                if pending_user.role == CustomUser.Role.VOLUNTEER:
                    vol, _ = Volunteer.objects.get_or_create(
                        user=pending_user,
                        defaults={'phone_number': pending_user.phone_number or ''}
                    )
                    vol.is_verified = True
                    vol.save(update_fields=['is_verified'])
                messages.success(request, f"User '{pending_user.username}' ID verified and approved.")
            except CustomUser.DoesNotExist:
                messages.error(request, f"User #{target_id} not found.")

        elif action == 'reject_user' and target_id:
            try:
                pending_user = CustomUser.objects.get(id=target_id)
                rejection_reason = request.POST.get('rejection_reason', 'Document unreadable or invalid.')
                pending_user.verification_status = CustomUser.VerificationStatus.REJECTED
                pending_user.verification_rejection_reason = rejection_reason
                pending_user.is_verified = False
                pending_user.save(update_fields=['verification_status', 'verification_rejection_reason', 'is_verified'])
                messages.warning(request, f"User '{pending_user.username}' verification rejected.")
            except CustomUser.DoesNotExist:
                messages.error(request, f"User #{target_id} not found.")

        elif action == 'send_broadcast':
            title = request.POST.get('title', 'Emergency Alert')
            body = request.POST.get('body', '')
            if body:
                from notifications.models import Notification
                active_users = CustomUser.objects.filter(is_active=True)
                notifs = [
                    Notification(recipient=u, notification_type='check_in', title=title, body=body, data={'broadcast': True})
                    for u in active_users
                ]
                Notification.objects.bulk_create(notifs)
                messages.success(request, f"Broadcast successfully dispatched to {len(notifs)} users.")
            else:
                messages.error(request, "Broadcast message body cannot be empty.")

        return redirect('/admin-panel/dashboard/')

    # Summary Statistics
    total_users_count = CustomUser.objects.count()
    active_alerts_count = SOSAlert.objects.filter(status='active').count()
    resolved_alerts_count = SOSAlert.objects.filter(status='resolved').count()
    cancelled_alerts_count = SOSAlert.objects.filter(status='cancelled').count()
    total_volunteers_count = Volunteer.objects.count()
    total_danger_zones = DangerZone.objects.count()
    unverified_zones_count = DangerZone.objects.filter(verified=False).count()
    verified_volunteers = Volunteer.objects.filter(is_verified=True).count()
    available_volunteers = Volunteer.objects.filter(is_available=True).count()
    total_evidence_files = EvidenceFile.objects.count()

    # Role Breakdown
    user_role_count = CustomUser.objects.filter(role='woman').count()
    guardian_role_count = CustomUser.objects.filter(role='guardian').count()
    friend_role_count = CustomUser.objects.filter(role='friend').count()
    volunteer_role_count = CustomUser.objects.filter(role='volunteer').count()
    admin_role_count = CustomUser.objects.filter(role='admin').count()

    # ID Verification Queue (Volunteers pending admin approval)
    pending_verification_users = CustomUser.objects.filter(
        verification_status='pending'
    ).order_by('-date_joined')[:50]
    pending_verification_count = CustomUser.objects.filter(verification_status='pending').count()
    approved_verification_count = CustomUser.objects.filter(verification_status='approved').count()
    rejected_verification_count = CustomUser.objects.filter(verification_status='rejected').count()

    # Analytics: Incident category breakdown (real data)
    from django.db.models import Count
    alert_categories = list(
        SOSAlert.objects.values('category')
        .annotate(count=Count('id'))
        .order_by('-count')[:10]
    )

    # Analytics: Hourly distribution for last 7 days (or all recorded alerts if none in 7d)
    from django.utils import timezone
    import json
    from datetime import timedelta
    now = timezone.now()
    seven_days_ago = now - timedelta(days=7)
    hourly_raw = list(SOSAlert.objects.filter(created_at__gte=seven_days_ago))
    if not hourly_raw:
        hourly_raw = list(SOSAlert.objects.all())
    hourly_data = [0] * 24
    for a in hourly_raw:
        hourly_data[a.created_at.hour] += 1

    # Real data for Map (Active SOS Alerts)
    alerts_for_map = []
    active_alerts_qs = SOSAlert.objects.filter(status='active').select_related('sender')
    for a in active_alerts_qs:
        if a.latitude is not None and a.longitude is not None:
            alerts_for_map.append({
                'id': a.id,
                'title': a.address or f"Incident #{a.id}",
                'user': a.sender.get_full_name() or a.sender.username,
                'status': a.get_status_display() if hasattr(a, 'get_status_display') else a.status,
                'color': '#FF007A',
                'lat': float(a.latitude),
                'lng': float(a.longitude),
                'time': a.created_at.strftime('%I:%M %p'),
                'battery': f"{a.battery_level or 100}%"
            })
    alerts_map_data_json = json.dumps(alerts_for_map)

    # Real Audit Activity (from IncidentTimeline, Registrations, and Alerts)
    real_activity = []
    for tl in IncidentTimeline.objects.select_related('alert', 'actor').order_by('-timestamp')[:10]:
        real_activity.append({
            'title': f"Incident #{tl.alert_id} ({tl.status.upper()})",
            'description': tl.note or f"Updated by {tl.actor_name}",
            'time': tl.timestamp,
            'type': 'incident'
        })
    for u in CustomUser.objects.order_by('-date_joined')[:10]:
        real_activity.append({
            'title': f"User registered: {u.get_full_name() or u.username}",
            'description': f"Role: {u.get_role_display()}",
            'time': u.date_joined,
            'type': 'user'
        })
    for a in SOSAlert.objects.select_related('sender').order_by('-created_at')[:10]:
        real_activity.append({
            'title': f"SOS Alert #{a.id} ({a.get_status_display()})",
            'description': f"Triggered by {a.sender.get_full_name() or a.sender.username} - {a.address or 'Location coordinates logged'}",
            'time': a.created_at,
            'type': 'alert'
        })
    real_activity.sort(key=lambda x: x['time'], reverse=True)
    real_activity = real_activity[:10]

    # Real In-App Notifications
    real_notifications = Notification.objects.select_related('recipient').order_by('-created_at')[:10]
    unread_notifications_count = Notification.objects.filter(is_read=False).count()

    # Verified Volunteers for responder dispatch dropdown
    verified_volunteers_list = Volunteer.objects.filter(is_verified=True).select_related('user')

    # Real Trends Over Time (7, 14, 30 days)
    today = now.date()
    days_7_labels = []
    days_7_total = []
    days_7_resolved = []
    for i in range(6, -1, -1):
        d = today - timedelta(days=i)
        days_7_labels.append(d.strftime('%b %d'))
        days_7_total.append(SOSAlert.objects.filter(created_at__date=d).count())
        days_7_resolved.append(SOSAlert.objects.filter(created_at__date=d, status='resolved').count())

    days_14_labels = []
    days_14_total = []
    days_14_resolved = []
    for i in range(13, -1, -2):
        d = today - timedelta(days=i)
        days_14_labels.append(d.strftime('%b %d'))
        days_14_total.append(SOSAlert.objects.filter(created_at__date=d).count())
        days_14_resolved.append(SOSAlert.objects.filter(created_at__date=d, status='resolved').count())

    days_30_labels = ['Week 1', 'Week 2', 'Week 3', 'Week 4']
    days_30_total = [
        SOSAlert.objects.filter(created_at__gte=now - timedelta(days=28), created_at__lt=now - timedelta(days=21)).count(),
        SOSAlert.objects.filter(created_at__gte=now - timedelta(days=21), created_at__lt=now - timedelta(days=14)).count(),
        SOSAlert.objects.filter(created_at__gte=now - timedelta(days=14), created_at__lt=now - timedelta(days=7)).count(),
        SOSAlert.objects.filter(created_at__gte=now - timedelta(days=7)).count(),
    ]
    days_30_resolved = [
        SOSAlert.objects.filter(status='resolved', created_at__gte=now - timedelta(days=28), created_at__lt=now - timedelta(days=21)).count(),
        SOSAlert.objects.filter(status='resolved', created_at__gte=now - timedelta(days=21), created_at__lt=now - timedelta(days=14)).count(),
        SOSAlert.objects.filter(status='resolved', created_at__gte=now - timedelta(days=14), created_at__lt=now - timedelta(days=7)).count(),
        SOSAlert.objects.filter(status='resolved', created_at__gte=now - timedelta(days=7)).count(),
    ]

    # Querysets for Tables
    recent_alerts = SOSAlert.objects.select_related('sender').order_by('-created_at')[:20]
    pending_danger_zones = DangerZone.objects.select_related('reported_by').order_by('-created_at')[:15]
    all_volunteers = Volunteer.objects.select_related('user').order_by('-created_at')[:20]
    all_users = CustomUser.objects.all().order_by('-date_joined')[:25]
    helplines = Helpline.objects.all().order_by('priority', 'name')[:10]

    context = {
        'total_users': total_users_count,
        'active_alerts_count': active_alerts_count,
        'resolved_alerts_count': resolved_alerts_count,
        'cancelled_alerts_count': cancelled_alerts_count,
        'total_volunteers': total_volunteers_count,
        'total_danger_zones': total_danger_zones,
        'unverified_zones_count': unverified_zones_count,
        'verified_volunteers': verified_volunteers,
        'available_volunteers': available_volunteers,
        'total_evidence_files': total_evidence_files,
        'user_role_count': user_role_count,
        'guardian_role_count': guardian_role_count,
        'friend_role_count': friend_role_count,
        'volunteer_role_count': volunteer_role_count,
        'admin_role_count': admin_role_count,
        'recent_alerts': recent_alerts,
        'pending_danger_zones': pending_danger_zones,
        'volunteers': all_volunteers,
        'all_users': all_users,
        'helplines': helplines,
        'verified_volunteers_list': verified_volunteers_list,
        'real_activity': real_activity,
        'real_notifications': real_notifications,
        'unread_notifications_count': unread_notifications_count,
        # Verification Queue
        'pending_verification_users': pending_verification_users,
        'pending_verification_count': pending_verification_count,
        'approved_verification_count': approved_verification_count,
        'rejected_verification_count': rejected_verification_count,
        # Analytics data (JSON for Chart.js)
        'active_count_json': active_alerts_count,
        'resolved_count_json': resolved_alerts_count,
        'cancelled_count_json': cancelled_alerts_count,
        'user_role_json': user_role_count,
        'guardian_role_json': guardian_role_count,
        'friend_role_json': friend_role_count,
        'volunteer_role_json': volunteer_role_count,
        'admin_role_json': admin_role_count,
        'alert_categories_json': json.dumps(alert_categories),
        'hourly_data_json': json.dumps(hourly_data),
        'alerts_map_data_json': alerts_map_data_json,
        'trend_7_labels_json': json.dumps(days_7_labels),
        'trend_7_total_json': json.dumps(days_7_total),
        'trend_7_resolved_json': json.dumps(days_7_resolved),
        'trend_14_labels_json': json.dumps(days_14_labels),
        'trend_14_total_json': json.dumps(days_14_total),
        'trend_14_resolved_json': json.dumps(days_14_resolved),
        'trend_30_labels_json': json.dumps(days_30_labels),
        'trend_30_total_json': json.dumps(days_30_total),
        'trend_30_resolved_json': json.dumps(days_30_resolved),
    }
    return render(request, 'admin_dashboard.html', context)



class AdminStatsApiView(APIView):
    """
    REST API for real-time safety control center metrics.
    """
    permission_classes = [permissions.AllowAny] # Can be tightened in production

    def get(self, request):
        stats = {
            'total_users': CustomUser.objects.count(),
            'active_sos_alerts': SOSAlert.objects.filter(status='active').count(),
            'resolved_sos_alerts': SOSAlert.objects.filter(status='resolved').count(),
            'total_danger_zones': DangerZone.objects.count(),
            'unverified_danger_zones': DangerZone.objects.filter(verified=False).count(),
            'total_volunteers': Volunteer.objects.count(),
            'verified_volunteers': Volunteer.objects.filter(is_verified=True).count(),
            'available_volunteers': Volunteer.objects.filter(is_available=True).count(),
            'total_evidence_files': EvidenceFile.objects.count(),
            'timestamp': timezone.now().isoformat(),
        }
        return Response({'success': True, 'stats': stats})


class AdminIncidentTriageApiView(APIView):
    """
    REST API to list and resolve SOS incidents.
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        status_param = request.query_params.get('status')
        qs = SOSAlert.objects.select_related('sender').order_by('-created_at')
        if status_param:
            qs = qs.filter(status=status_param)

        data = []
        for alert in qs[:30]:
            data.append({
                'id': alert.id,
                'user': alert.sender.username,
                'phone': alert.sender.phone_number,
                'status': alert.status,
                'trigger_method': alert.trigger_type,
                'latitude': float(alert.latitude) if alert.latitude else None,
                'longitude': float(alert.longitude) if alert.longitude else None,
                'battery_level': alert.battery_level,
                'created_at': alert.created_at.isoformat(),
                'resolved_at': alert.resolved_at.isoformat() if alert.resolved_at else None,
            })
        return Response({'success': True, 'count': len(data), 'incidents': data})

    def put(self, request, id):
        try:
            alert = SOSAlert.objects.get(id=id)
        except SOSAlert.DoesNotExist:
            return Response({'error': 'Incident not found'}, status=status.HTTP_404_NOT_FOUND)

        new_status = request.data.get('status', 'resolved')
        alert.status = new_status
        if new_status == 'resolved':
            alert.resolved_at = timezone.now()
        alert.save()

        return Response({
            'success': True,
            'message': f"Alert #{id} marked as {new_status}",
            'alert_id': alert.id,
            'status': alert.status
        })


class AdminVolunteerApprovalApiView(APIView):
    """
    REST API to review and approve/verify volunteers.
    GET /api/admin/volunteers/pending/ - List unverified volunteers.
    PUT /api/admin/volunteers/<id>/verify/ - Approve or reject a volunteer.
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        """List unverified volunteers."""
        volunteers = Volunteer.objects.filter(is_verified=False).select_related('user').order_by('-created_at')
        data = [{
            'id': v.id,
            'user_id': v.user.id,
            'username': v.user.username,
            'phone': str(v.user.phone_number) if v.user.phone_number else '',
            'skills': v.skills,
            'rating': float(v.rating),
            'total_helps': v.total_helps,
            'created_at': v.created_at.isoformat(),
        } for v in volunteers]
        return Response({'count': len(data), 'pending_volunteers': data})

    def put(self, request, id):
        try:
            vol = Volunteer.objects.get(id=id)
        except Volunteer.DoesNotExist:
            return Response({'error': 'Volunteer not found'}, status=status.HTTP_404_NOT_FOUND)

        is_verified = request.data.get('is_verified', True)
        vol.is_verified = is_verified
        vol.save(update_fields=['is_verified'])

        return Response({
            'success': True,
            'message': f"Volunteer {vol.user.username} verification updated",
            'volunteer_id': vol.id,
            'is_verified': vol.is_verified
        })


# ==========================================
# ADMIN USER MANAGEMENT APIS (Task 5.3)
# ==========================================

class AdminUserListView(APIView):
    """GET /api/admin/users/ - All users with optional ?role= and ?is_active= filters."""
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        from django.contrib.auth import get_user_model
        User = get_user_model()
        qs = User.objects.all().order_by('-date_joined')
        role = request.query_params.get('role')
        if role:
            qs = qs.filter(role=role)
        is_active = request.query_params.get('is_active')
        if is_active is not None:
            qs = qs.filter(is_active=is_active.lower() == 'true')
        data = [{
            'id': u.id,
            'username': u.username,
            'email': u.email,
            'phone_number': str(u.phone_number) if u.phone_number else '',
            'role': u.role,
            'is_active': u.is_active,
            'is_verified': u.is_verified,
            'date_joined': u.date_joined.isoformat(),
        } for u in qs[:200]]
        return Response({'count': len(data), 'users': data})


class AdminUserBanView(APIView):
    """PUT /api/admin/users/<id>/ban/ - Ban or unban a user."""
    permission_classes = [permissions.AllowAny]

    def put(self, request, id):
        from django.contrib.auth import get_user_model
        User = get_user_model()
        user = get_object_or_404(User, id=id)
        action = request.data.get('action', 'ban')  # 'ban' or 'unban'
        user.is_active = (action == 'unban')
        user.save(update_fields=['is_active'])
        return Response({
            'message': f'User {action}ned successfully.',
            'id': id,
            'is_active': user.is_active,
        })


class AdminSOSLogsView(APIView):
    """GET /api/admin/sos-logs/ - All SOS history (latest 100)."""
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        alerts = SOSAlert.objects.select_related('sender').order_by('-created_at')[:100]
        data = [{
            'id': a.id,
            'sender': a.sender.username,
            'sender_phone': str(a.sender.phone_number) if a.sender.phone_number else '',
            'latitude': float(a.latitude) if a.latitude else None,
            'longitude': float(a.longitude) if a.longitude else None,
            'address': a.address,
            'status': a.status,
            'message': a.message,
            'created_at': a.created_at.isoformat(),
        } for a in alerts]
        return Response({'count': len(data), 'sos_logs': data})


class AdminDangerZonePendingView(APIView):
    """GET /api/admin/dangerzones/pending/ - Unverified danger zone reports."""
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        zones = DangerZone.objects.filter(verified=False).order_by('-created_at')[:50]
        data = [{
            'id': z.id,
            'reported_by': z.reported_by.username if z.reported_by else 'anonymous',
            'latitude': float(z.latitude),
            'longitude': float(z.longitude),
            'radius': z.radius,
            'description': z.description,
            'category': z.category,
            'report_count': z.report_count,
            'created_at': z.created_at.isoformat(),
        } for z in zones]
        return Response({'count': len(data), 'pending_zones': data})


class AdminBroadcastView(APIView):
    """POST /api/admin/broadcast/ - Send a broadcast notification to all (or filtered) active users."""
    permission_classes = [permissions.AllowAny]

    def post(self, request):
        from django.contrib.auth import get_user_model
        from notifications.models import Notification
        User = get_user_model()
        title = request.data.get('title', 'Admin Announcement')
        body = request.data.get('body', '')
        role_filter = request.data.get('role')  # optional role filter
        if not body:
            return Response({'error': 'Body is required.'}, status=400)
        users = User.objects.filter(is_active=True)
        if role_filter:
            users = users.filter(role=role_filter)
        notifications = [
            Notification(
                recipient=u,
                notification_type='check_in',
                title=title,
                body=body,
                data={'broadcast': True},
            )
            for u in users
        ]
        Notification.objects.bulk_create(notifications)
        return Response({
            'message': f'Broadcast sent to {len(notifications)} users.',
            'count': len(notifications),
        })


class AdminVerifyUserView(APIView):
    """POST /api/admin/users/<id>/verify/ — Approve or Reject a volunteer's ID proof."""
    permission_classes = [permissions.AllowAny]

    def post(self, request, id):
        user = get_object_or_404(CustomUser, id=id)
        action = request.data.get('action')  # 'approve' or 'reject'
        rejection_reason = request.data.get('rejection_reason', '')

        if action == 'approve':
            user.verification_status = CustomUser.VerificationStatus.APPROVED
            user.is_verified = True
            user.verification_rejection_reason = None
            user.save(update_fields=['verification_status', 'is_verified', 'verification_rejection_reason'])
            # Also verify the Volunteer record
            Volunteer.objects.filter(user=user).update(is_verified=True)
            return Response({'message': f'User {user.username} approved.', 'verification_status': 'approved'})

        elif action == 'reject':
            user.verification_status = CustomUser.VerificationStatus.REJECTED
            user.is_verified = False
            user.verification_rejection_reason = rejection_reason or 'Document unreadable or invalid.'
            user.save(update_fields=['verification_status', 'is_verified', 'verification_rejection_reason'])
            Volunteer.objects.filter(user=user).update(is_verified=False)
            return Response({
                'message': f'User {user.username} verification rejected.',
                'verification_status': 'rejected',
                'rejection_reason': user.verification_rejection_reason
            })

        return Response({'error': 'Invalid action. Use "approve" or "reject".'}, status=400)


class AdminAnalyticsView(APIView):
    """GET /api/admin/analytics/ — Real-time analytics data for charts."""
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        import json
        from django.utils import timezone
        from datetime import timedelta
        from django.db.models import Count

        # Incident status breakdown
        active = SOSAlert.objects.filter(status='active').count()
        resolved = SOSAlert.objects.filter(status='resolved').count()
        cancelled = SOSAlert.objects.filter(status='cancelled').count()

        # Role distribution
        role_data = list(
            CustomUser.objects.values('role')
            .annotate(count=Count('id'))
        )

        # Hourly distribution last 7 days (or all recorded alerts if none in 7d)
        now = timezone.now()
        seven_days_ago = now - timedelta(days=7)
        hourly_alerts = list(SOSAlert.objects.filter(created_at__gte=seven_days_ago))
        if not hourly_alerts:
            hourly_alerts = list(SOSAlert.objects.all())
        hourly_data = [0] * 24
        for alert in hourly_alerts:
            hourly_data[alert.created_at.hour] += 1

        # Category breakdown
        categories = list(
            SOSAlert.objects.values('category')
            .annotate(count=Count('id'))
            .order_by('-count')[:8]
        )

        # Verification queue stats
        pending = CustomUser.objects.filter(verification_status='pending').count()
        approved = CustomUser.objects.filter(verification_status='approved').count()
        rejected = CustomUser.objects.filter(verification_status='rejected').count()

        # Weekly alert trend (last 7 days)
        weekly_trend = []
        for i in range(6, -1, -1):
            day = now - timedelta(days=i)
            day_start = day.replace(hour=0, minute=0, second=0, microsecond=0)
            day_end = day.replace(hour=23, minute=59, second=59, microsecond=999999)
            count = SOSAlert.objects.filter(created_at__gte=day_start, created_at__lte=day_end).count()
            resolved_count = SOSAlert.objects.filter(
                created_at__gte=day_start, created_at__lte=day_end, status='resolved'
            ).count()
            weekly_trend.append({
                'date': day.strftime('%b %d'),
                'total': count,
                'resolved': resolved_count
            })

        return Response({
            'incident_status': {'active': active, 'resolved': resolved, 'cancelled': cancelled},
            'role_distribution': role_data,
            'hourly_distribution': hourly_data,
            'category_breakdown': categories,
            'verification_queue': {'pending': pending, 'approved': approved, 'rejected': rejected},
            'weekly_trend': weekly_trend,
        })


class AdminReportsDataView(APIView):
    """
    GET /api/admin/reports/
    Returns aggregated incident metrics, response-time analytics,
    society density, category distributions, and filtered incident records.
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        from .reports_service import get_filtered_alerts, compile_reporting_dataset
        alerts_qs = get_filtered_alerts(request.GET)
        report_data = compile_reporting_dataset(alerts_qs)
        return Response(report_data)


class AdminExportIncidentsExcelView(APIView):
    """
    GET /api/admin/reports/export/excel/
    Streams a formatted multi-sheet Excel spreadsheet (.xlsx).
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        from .reports_service import get_filtered_alerts, compile_reporting_dataset, generate_excel_report
        alerts_qs = get_filtered_alerts(request.GET)
        report_data = compile_reporting_dataset(alerts_qs)
        excel_buffer = generate_excel_report(report_data, request.GET)

        timestamp_str = timezone.now().strftime('%Y%m%d_%H%M%S')
        filename = f"SafeHer_Incident_Report_{timestamp_str}.xlsx"

        response = HttpResponse(
            excel_buffer.getvalue(),
            content_type='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        )
        response['Content-Disposition'] = f'attachment; filename="{filename}"'
        return response


class AdminExportIncidentsCsvView(APIView):
    """
    GET /api/admin/reports/export/csv/
    Streams a UTF-8 encoded CSV file with complete incident metadata.
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        from .reports_service import get_filtered_alerts, compile_reporting_dataset, generate_csv_report
        alerts_qs = get_filtered_alerts(request.GET)
        report_data = compile_reporting_dataset(alerts_qs)
        csv_bytes = generate_csv_report(report_data)

        timestamp_str = timezone.now().strftime('%Y%m%d_%H%M%S')
        filename = f"SafeHer_Incident_Report_{timestamp_str}.csv"

        response = HttpResponse(
            csv_bytes,
            content_type='text/csv; charset=utf-8'
        )
        response['Content-Disposition'] = f'attachment; filename="{filename}"'
        return response


class AdminExportIncidentsPdfView(APIView):
    """
    GET /api/admin/reports/export/pdf/
    Streams a branded PDF executive report in landscape format.
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        from .reports_service import get_filtered_alerts, compile_reporting_dataset, generate_pdf_report
        alerts_qs = get_filtered_alerts(request.GET)
        report_data = compile_reporting_dataset(alerts_qs)
        pdf_buffer = generate_pdf_report(report_data, request.GET)

        timestamp_str = timezone.now().strftime('%Y%m%d_%H%M%S')
        filename = f"SafeHer_Incident_Report_{timestamp_str}.pdf"

        # Check if 'inline' is requested
        disposition = 'inline' if request.GET.get('view') == 'inline' else 'attachment'

        response = HttpResponse(
            pdf_buffer.getvalue(),
            content_type='application/pdf'
        )
        response['Content-Disposition'] = f'{disposition}; filename="{filename}"'
        return response

