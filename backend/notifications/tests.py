from datetime import timedelta
from django.contrib.auth import get_user_model
from django.urls import reverse
from django.utils import timezone
from rest_framework import status
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import RefreshToken

from .models import DeviceToken, Notification, CheckInSchedule
from .fcm_service import send_fcm_notification, process_check_in_schedule

User = get_user_model()


class NotificationSystemTests(APITestCase):

    def setUp(self):
        self.user = User.objects.create_user(
            username='safetester',
            email='safetester@example.com',
            password='password123',
            role='woman'
        )
        token = RefreshToken.for_user(self.user)
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token.access_token}')

    def test_register_device_token(self):
        url = reverse('notifications:register_token')
        payload = {
            'token': 'fcm_sample_token_xyz_12345',
            'device_type': 'android',
            'device_name': 'Pixel 7 Pro'
        }
        response = self.client.post(url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['token'], 'fcm_sample_token_xyz_12345')
        self.assertEqual(response.data['device_name'], 'Pixel 7 Pro')

        # Registering again updates device_name without duplicate token error
        payload['device_name'] = 'Pixel 7 Pro Updated'
        update_resp = self.client.post(url, payload, format='json')
        self.assertEqual(update_resp.status_code, status.HTTP_200_OK)
        self.assertEqual(update_resp.data['device_name'], 'Pixel 7 Pro Updated')
        self.assertEqual(DeviceToken.objects.filter(token='fcm_sample_token_xyz_12345').count(), 1)

    def test_notification_history_listing_and_filter(self):
        # Create notifications
        Notification.objects.create(
            recipient=self.user,
            notification_type='sos_alert',
            title='Emergency Alert Triggered',
            body='SOS alert activated near Central Park',
            is_read=False
        )
        Notification.objects.create(
            recipient=self.user,
            notification_type='location_share',
            title='Live Location Active',
            body='Sharing live location with 2 emergency contacts',
            is_read=True
        )

        url = reverse('notifications:notifications_list')
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['total_unread'], 1)
        self.assertEqual(len(response.data['notifications']), 2)

        # Filter by unread
        unread_resp = self.client.get(f"{url}?unread=true")
        self.assertEqual(len(unread_resp.data['notifications']), 1)
        self.assertEqual(unread_resp.data['notifications'][0]['notification_type'], 'sos_alert')

    def test_mark_notification_as_read(self):
        notif = Notification.objects.create(
            recipient=self.user,
            notification_type='new_message',
            title='New message from Guardian',
            body='Are you on your way?',
            is_read=False
        )

        url = reverse('notifications:mark_read', kwargs={'id': notif.id})
        response = self.client.put(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertTrue(response.data['is_read'])

        notif.refresh_from_db()
        self.assertTrue(notif.is_read)

    def test_mark_all_notifications_as_read(self):
        for i in range(3):
            Notification.objects.create(
                recipient=self.user,
                notification_type='contact_request',
                title=f'Request #{i}',
                body='Contact request received',
                is_read=False
            )

        url = reverse('notifications:mark_all_read')
        response = self.client.post(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['count'], 3)
        self.assertEqual(Notification.objects.filter(recipient=self.user, is_read=False).count(), 0)

    def test_scheduled_check_in_flow(self):
        # 1. Create scheduled check-in
        create_url = reverse('notifications:check_in_schedules')
        sched_time = (timezone.now() + timedelta(hours=2)).isoformat()
        payload = {
            'title': 'Night Walk Check-In',
            'message': 'Confirm safe arrival at transit hub',
            'scheduled_time': sched_time,
            'repeat_interval': 'none'
        }
        res = self.client.post(create_url, payload, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        schedule_id = res.data['id']

        # 2. List schedules
        list_res = self.client.get(create_url)
        self.assertEqual(list_res.status_code, status.HTTP_200_OK)
        self.assertEqual(len(list_res.data), 1)

        # 3. Respond to check-in
        respond_url = reverse('notifications:respond_check_in')
        resp_res = self.client.post(respond_url, {
            'schedule_id': schedule_id,
            'status': "Reached transit hub safely! ✅"
        }, format='json')
        self.assertEqual(resp_res.status_code, status.HTTP_200_OK)

        # Check notification record was saved
        check_in_notifs = Notification.objects.filter(recipient=self.user, notification_type='check_in')
        self.assertTrue(check_in_notifs.exists())

        # 4. Delete schedule
        del_url = reverse('notifications:check_in_schedule_detail', kwargs={'id': schedule_id})
        del_res = self.client.delete(del_url)
        self.assertEqual(del_res.status_code, status.HTTP_200_OK)
        self.assertEqual(CheckInSchedule.objects.filter(id=schedule_id).count(), 0)

    def test_fcm_dispatch_service_and_due_scheduler(self):
        # Attach a token to user
        DeviceToken.objects.create(
            user=self.user,
            token='device_token_abc_999',
            device_type='android',
            is_active=True
        )

        # Test send_fcm_notification
        dispatch_result = send_fcm_notification(
            user=self.user,
            title='Test Push Alert',
            body='Push alert content',
            notification_type='sos_alert',
            data={'emergency': 'true'}
        )
        self.assertTrue(dispatch_result['success'])
        self.assertGreaterEqual(dispatch_result['tokens_sent'], 1)

        # Test process_check_in_schedule
        due_schedule = CheckInSchedule.objects.create(
            user=self.user,
            title='Due Reminder',
            message='Time to check in',
            scheduled_time=timezone.now() - timedelta(minutes=5),
            repeat_interval='hourly',
            is_active=True,
            is_sent=False
        )

        proc_res = process_check_in_schedule(due_schedule)
        self.assertTrue(proc_res['success'])
        due_schedule.refresh_from_db()
        self.assertIsNotNone(due_schedule.last_sent_at)
        # For hourly recurring, scheduled_time should have rolled forward
        self.assertGreater(due_schedule.scheduled_time, timezone.now())

    def test_periodic_checkin_timer_session(self):
        # 1. Start check-in session with 15-minute interval
        start_url = reverse('root_start_checkin')
        res = self.client.post(start_url, {'interval_minutes': 15}, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        self.assertEqual(res.data['interval_minutes'], 15)
        self.assertTrue(res.data['is_active'])

        # 2. Check active status
        status_url = reverse('root_status_checkin')
        status_res = self.client.get(status_url)
        self.assertEqual(status_res.status_code, status.HTTP_200_OK)
        self.assertTrue(status_res.data['is_active'])

        # 3. Respond safe
        respond_url = reverse('root_respond_checkin')
        resp_res = self.client.post(respond_url, format='json')
        self.assertEqual(resp_res.status_code, status.HTTP_200_OK)

        # 4. Stop session
        stop_url = reverse('root_stop_checkin')
        stop_res = self.client.delete(stop_url)
        self.assertEqual(stop_res.status_code, status.HTTP_200_OK)

        status_res2 = self.client.get(status_url)
        self.assertFalse(status_res2.data['is_active'])

