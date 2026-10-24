from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APITestCase
from rest_framework import status
from django.contrib.auth import get_user_model
from .models import TrustedContact, SOSAlert, AlertRecipient

User = get_user_model()


class TrustedContactsAPITests(APITestCase):
    def setUp(self):
        # Create Woman user
        self.woman = User.objects.create_user(
            username='ananya',
            email='ananya@example.com',
            phone_number='+919876500001',
            role='woman',
            password='Password123!'
        )

        # Create Guardian user (e.g. father/mother)
        self.guardian = User.objects.create_user(
            username='papa_guardian',
            email='papa@example.com',
            phone_number='+919876500002',
            role='guardian',
            password='Password123!'
        )

        # Create Friend user
        self.friend = User.objects.create_user(
            username='priya_friend',
            email='priya@example.com',
            phone_number='+919876500003',
            role='woman',
            password='Password123!'
        )

        self.add_url = reverse('alerts:add_contact')
        self.list_url = reverse('alerts:list_contacts')
        self.pending_url = reverse('alerts:pending_contacts')

    def test_add_trusted_contact_by_email(self):
        self.client.force_authenticate(user=self.woman)
        payload = {
            'email_or_phone': 'papa@example.com',
            'relationship': 'parent',
            'nickname': 'Dad',
            'notify_via_sms': True,
        }
        response = self.client.post(self.add_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['contact']['status'], 'pending')
        self.assertEqual(response.data['contact']['nickname'], 'Dad')
        self.assertEqual(response.data['contact']['contact_user']['username'], 'papa_guardian')

    def test_add_trusted_contact_by_phone(self):
        self.client.force_authenticate(user=self.woman)
        payload = {
            'email_or_phone': '+919876500003',
            'relationship': 'friend',
            'nickname': 'Bestie',
        }
        response = self.client.post(self.add_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['contact']['contact_user']['username'], 'priya_friend')

    def test_cannot_add_self_as_contact(self):
        self.client.force_authenticate(user=self.woman)
        payload = {'email_or_phone': 'ananya@example.com'}
        response = self.client.post(self.add_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_cannot_add_nonexistent_user(self):
        self.client.force_authenticate(user=self.woman)
        payload = {'email_or_phone': 'nonexistent@example.com'}
        response = self.client.post(self.add_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_cannot_add_duplicate_contact(self):
        self.client.force_authenticate(user=self.woman)
        TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent'
        )
        payload = {'email_or_phone': 'papa@example.com'}
        response = self.client.post(self.add_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_list_contacts(self):
        self.client.force_authenticate(user=self.woman)
        TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent',
            status='accepted'
        )
        TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.friend,
            relationship='friend',
            status='pending'
        )

        response = self.client.get(self.list_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

        # Filter by status=accepted
        filtered_response = self.client.get(f"{self.list_url}?status=accepted")
        self.assertEqual(filtered_response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(filtered_response.data), 1)
        self.assertEqual(filtered_response.data[0]['status'], 'accepted')

    def test_guardian_views_pending_requests(self):
        TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent',
            status='pending'
        )
        self.client.force_authenticate(user=self.guardian)

        response = self.client.get(self.pending_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['owner']['username'], 'ananya')

    def test_guardian_accepts_request(self):
        contact = TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent',
            status='pending'
        )
        self.client.force_authenticate(user=self.guardian)

        respond_url = reverse('alerts:respond_contact', kwargs={'id': contact.id})
        response = self.client.put(respond_url, {'action': 'accept'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['contact']['status'], 'accepted')

        contact.refresh_from_db()
        self.assertEqual(contact.status, TrustedContact.Status.ACCEPTED)

    def test_guardian_rejects_request(self):
        contact = TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent',
            status='pending'
        )
        self.client.force_authenticate(user=self.guardian)

        respond_url = reverse('alerts:respond_contact', kwargs={'id': contact.id})
        response = self.client.put(respond_url, {'action': 'reject'}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['contact']['status'], 'rejected')

    def test_delete_trusted_contact(self):
        contact = TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent',
            status='accepted'
        )
        self.client.force_authenticate(user=self.woman)

        delete_url = reverse('alerts:delete_contact', kwargs={'id': contact.id})
        response = self.client.delete(delete_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertFalse(TrustedContact.objects.filter(id=contact.id).exists())

    def test_update_contact_preferences(self):
        contact = TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent',
            status='accepted',
            notify_via_sms=True
        )
        self.client.force_authenticate(user=self.woman)

        pref_url = reverse('alerts:update_preferences', kwargs={'id': contact.id})
        response = self.client.patch(pref_url, {
            'nickname': 'Super Dad',
            'notify_via_sms': False
        }, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['nickname'], 'Super Dad')
        self.assertFalse(response.data['notify_via_sms'])


class SOSAlertsAPITests(APITestCase):
    def setUp(self):
        self.woman = User.objects.create_user(
            username='kavita',
            email='kavita@example.com',
            phone_number='+919876500010',
            role='woman',
            password='Password123!'
        )

        self.guardian = User.objects.create_user(
            username='rahul_guardian',
            email='rahul@example.com',
            phone_number='+919876500020',
            role='guardian',
            password='Password123!'
        )

        self.stranger = User.objects.create_user(
            username='stranger',
            email='stranger@example.com',
            role='woman',
            password='Password123!'
        )

        # Set up accepted trusted contact relationship
        self.contact = TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='brother',
            status=TrustedContact.Status.ACCEPTED,
            notify_via_sms=True,
            notify_via_push=True
        )

        self.trigger_url = reverse('alerts:trigger_sos')
        self.active_url = reverse('alerts:active_sos')
        self.history_url = reverse('alerts:sos_history')

    def test_trigger_sos_creates_alert_and_notifies_guardian(self):
        self.client.force_authenticate(user=self.woman)
        payload = {
            'latitude': '19.076090',
            'longitude': '72.877426',
            'battery_level': 45,
            'trigger_type': 'manual',
            'message': 'Need help immediately!',
            'address': 'Dadar, Mumbai'
        }
        response = self.client.post(self.trigger_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['alert']['status'], 'active')
        self.assertEqual(response.data['recipients_notified_count'], 1)
        self.assertIn('https://www.google.com/maps', response.data['alert']['google_maps_url'])

        alert_id = response.data['alert']['id']
        recipient_record = AlertRecipient.objects.filter(alert_id=alert_id, recipient=self.guardian).first()
        self.assertIsNotNone(recipient_record)
        self.assertEqual(recipient_record.status, AlertRecipient.RecipientStatus.NOTIFIED)

    def test_trigger_sos_updates_existing_active_alert(self):
        self.client.force_authenticate(user=self.woman)
        # First trigger
        self.client.post(self.trigger_url, {'latitude': '19.076090', 'longitude': '72.877426'}, format='json')

        # Second trigger (moved location)
        response = self.client.post(self.trigger_url, {
            'latitude': '19.080000',
            'longitude': '72.880000',
            'battery_level': 40
        }, format='json')

        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(SOSAlert.objects.filter(sender=self.woman, status='active').count(), 1)
        alert = SOSAlert.objects.get(sender=self.woman, status='active')
        self.assertEqual(float(alert.latitude), 19.080000)

    def test_cancel_sos_alert(self):
        self.client.force_authenticate(user=self.woman)
        create_res = self.client.post(self.trigger_url, {'latitude': '19.076090', 'longitude': '72.877426'}, format='json')
        alert_id = create_res.data['alert']['id']

        cancel_url = reverse('alerts:cancel_sos', kwargs={'id': alert_id})
        cancel_res = self.client.put(cancel_url, {'reason': 'False alarm, reached home.'}, format='json')
        self.assertEqual(cancel_res.status_code, status.HTTP_200_OK)
        self.assertEqual(cancel_res.data['alert']['status'], 'cancelled')
        self.assertEqual(cancel_res.data['alert']['cancellation_reason'], 'False alarm, reached home.')

    def test_guardian_acknowledges_alert(self):
        self.client.force_authenticate(user=self.woman)
        create_res = self.client.post(self.trigger_url, {'latitude': '19.076090', 'longitude': '72.877426'}, format='json')
        alert_id = create_res.data['alert']['id']

        # Guardian acknowledges
        self.client.force_authenticate(user=self.guardian)
        ack_url = reverse('alerts:acknowledge_sos', kwargs={'id': alert_id})
        ack_res = self.client.put(ack_url, {'note': 'Calling police and driving towards you.'}, format='json')
        self.assertEqual(ack_res.status_code, status.HTTP_200_OK)

        recipient_record = AlertRecipient.objects.get(alert_id=alert_id, recipient=self.guardian)
        self.assertEqual(recipient_record.status, AlertRecipient.RecipientStatus.ACKNOWLEDGED)
        self.assertIsNotNone(recipient_record.acknowledged_at)

    def test_active_alerts_visibility(self):
        self.client.force_authenticate(user=self.woman)
        self.client.post(self.trigger_url, {'latitude': '19.076090', 'longitude': '72.877426'}, format='json')

        # Woman sees her active alert
        res_woman = self.client.get(self.active_url)
        self.assertEqual(len(res_woman.data), 1)

        # Guardian sees active alert for his ward
        self.client.force_authenticate(user=self.guardian)
        res_guardian = self.client.get(self.active_url)
        self.assertEqual(len(res_guardian.data), 1)

        # Stranger sees 0 active alerts
        self.client.force_authenticate(user=self.stranger)
        res_stranger = self.client.get(self.active_url)
        self.assertEqual(len(res_stranger.data), 0)


class EvidenceFileTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='clara', email='clara@test.com', password='password123')
        self.client.force_authenticate(user=self.user)

    def test_upload_and_list_evidence(self):
        from django.core.files.uploadedfile import SimpleUploadedFile
        fake_audio = SimpleUploadedFile("threat_audio.mp3", b"audio_content_bytes", content_type="audio/mpeg")
        upload_url = reverse('root_upload_evidence')
        res = self.client.post(upload_url, {
            'file': fake_audio,
            'file_type': 'audio',
            'duration_seconds': 14.5
        }, format='multipart')

        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        self.assertEqual(res.data['file_type'], 'audio')
        self.assertEqual(res.data['duration_seconds'], 14.5)

        # List evidence
        list_url = reverse('root_list_evidence')
        list_res = self.client.get(list_url)
        self.assertEqual(list_res.status_code, status.HTTP_200_OK)
        self.assertEqual(len(list_res.data), 1)


class EmergencyContactSOSIsolationTests(APITestCase):
    """
    Tests proving that an SOS alert CANNOT be sent to a person who is not
    in the authenticated user's saved, active emergency contacts list.
    """

    def setUp(self):
        # User A (Woman in distress)
        self.woman_a = User.objects.create_user(
            username='woman_a',
            email='woman_a@safeher.org',
            phone_number='+919111111111',
            role='woman',
            password='Password123!'
        )

        # Guardian A (User A's accepted emergency contact)
        self.guardian_a = User.objects.create_user(
            username='guardian_a',
            email='guardian_a@safeher.org',
            phone_number='+919111111112',
            role='guardian',
            password='Password123!'
        )

        # User B (Another independent woman)
        self.woman_b = User.objects.create_user(
            username='woman_b',
            email='woman_b@safeher.org',
            phone_number='+919222222221',
            role='woman',
            password='Password123!'
        )

        # Guardian B (User B's emergency contact - stranger to User A)
        self.guardian_b = User.objects.create_user(
            username='guardian_b',
            email='guardian_b@safeher.org',
            phone_number='+919222222222',
            role='guardian',
            password='Password123!'
        )

        # Complete stranger (not in any emergency contacts)
        self.stranger = User.objects.create_user(
            username='stranger_unrelated',
            email='stranger@safeher.org',
            phone_number='+919999999999',
            role='guardian',
            password='Password123!'
        )

        # Connect Guardian A to Woman A
        self.contact_a = TrustedContact.objects.create(
            owner=self.woman_a,
            contact_user=self.guardian_a,
            name='Papa Guardian',
            phone_number='+919111111112',
            tier=TrustedContact.Tier.PRIMARY,
            status=TrustedContact.Status.ACCEPTED,
            notify_via_sms=True,
            notify_via_email=True,
            notify_via_push=True
        )

        # Connect Guardian B to Woman B
        self.contact_b = TrustedContact.objects.create(
            owner=self.woman_b,
            contact_user=self.guardian_b,
            name='Brother Guardian B',
            phone_number='+919222222222',
            tier=TrustedContact.Tier.PRIMARY,
            status=TrustedContact.Status.ACCEPTED,
            notify_via_sms=True,
            notify_via_email=True,
            notify_via_push=True
        )

        self.trigger_url = reverse('alerts:trigger_sos')

    def test_sos_only_dispatches_to_authenticated_users_saved_contacts(self):
        """
        When Woman A triggers an SOS, only Guardian A receives the alert.
        Guardian B and Stranger must NEVER receive any alert record.
        """
        self.client.force_authenticate(user=self.woman_a)
        response = self.client.post(self.trigger_url, {
            'latitude': 18.520430,
            'longitude': 73.856744,
            'message': 'Distress alert from Woman A'
        }, format='json')

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        alert_id = response.data['alert']['id']

        # 1. Guardian A received the alert
        recipient_a = AlertRecipient.objects.filter(alert_id=alert_id, recipient=self.guardian_a).first()
        self.assertIsNotNone(recipient_a, "Guardian A must be notified")
        self.assertEqual(recipient_a.trusted_contact, self.contact_a)
        self.assertIn(recipient_a.sms_status, [
            AlertRecipient.DeliveryStatus.SENT,
            AlertRecipient.DeliveryStatus.DELIVERED,
            AlertRecipient.DeliveryStatus.FAILED,
            AlertRecipient.DeliveryStatus.PENDING,
            AlertRecipient.DeliveryStatus.SKIPPED
        ])

        # 2. Guardian B NEVER received Woman A's alert
        recipient_b = AlertRecipient.objects.filter(alert_id=alert_id, recipient=self.guardian_b).first()
        self.assertIsNone(recipient_b, "Guardian B must NEVER receive Woman A's alert")

        # 3. Stranger NEVER received Woman A's alert
        recipient_stranger = AlertRecipient.objects.filter(alert_id=alert_id, recipient=self.stranger).first()
        self.assertIsNone(recipient_stranger, "Stranger must NEVER receive Woman A's alert")

    def test_deleted_contact_is_not_notified_on_subsequent_sos(self):
        """
        If Woman A removes a contact, that contact is immediately excluded from future SOS alerts.
        """
        # Delete contact_a
        self.contact_a.delete()

        # Trigger SOS after deletion
        self.client.force_authenticate(user=self.woman_a)
        response = self.client.post(self.trigger_url, {
            'latitude': 18.520430,
            'longitude': 73.856744,
        }, format='json')

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        alert_id = response.data['alert']['id']

        # Guardian A was deleted, so must not receive an alert
        recipient_a = AlertRecipient.objects.filter(alert_id=alert_id, recipient=self.guardian_a).first()
        self.assertIsNone(recipient_a, "Deleted contact must not receive SOS notifications")

    def test_deactivated_or_pending_contacts_are_not_notified(self):
        """
        Contacts with status PENDING, REJECTED, or BLOCKED must NOT receive SOS alerts.
        """
        # Change status to REJECTED
        self.contact_a.status = TrustedContact.Status.REJECTED
        self.contact_a.save()

        self.client.force_authenticate(user=self.woman_a)
        response = self.client.post(self.trigger_url, {
            'latitude': 18.520430,
            'longitude': 73.856744,
        }, format='json')

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        alert_id = response.data['alert']['id']

        recipient_a = AlertRecipient.objects.filter(alert_id=alert_id, recipient=self.guardian_a).first()
        self.assertIsNone(recipient_a, "Deactivated/rejected contact must NOT receive alerts")

    def test_duplicate_sos_request_does_not_send_duplicate_notifications(self):
        """
        When the app retries an SOS request or updates location on an active alert,
        it must not create duplicate recipient records or duplicate deliveries.
        """
        self.client.force_authenticate(user=self.woman_a)

        # Initial SOS
        res1 = self.client.post(self.trigger_url, {
            'latitude': 18.520430,
            'longitude': 73.856744,
        }, format='json')
        self.assertEqual(res1.status_code, status.HTTP_201_CREATED)
        alert_id = res1.data['alert']['id']

        initial_count = AlertRecipient.objects.filter(alert_id=alert_id).count()
        self.assertEqual(initial_count, 1)

        # Retried SOS (same active alert with updated GPS)
        res2 = self.client.post(self.trigger_url, {
            'latitude': 18.525000,
            'longitude': 73.860000,
        }, format='json')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)

        # Count of recipients must stay exactly 1 (idempotent)
        final_count = AlertRecipient.objects.filter(alert_id=alert_id).count()
        self.assertEqual(final_count, 1, "Must not create duplicate notification records on retry")

    def test_contact_channel_toggles_respected(self):
        """
        If contact has notify_via_email=False, email_status must be SKIPPED.
        """
        self.contact_a.notify_via_email = False
        self.contact_a.notify_via_sms = True
        self.contact_a.save()

        self.client.force_authenticate(user=self.woman_a)
        response = self.client.post(self.trigger_url, {
            'latitude': 18.520430,
            'longitude': 73.856744,
        }, format='json')

        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        alert_id = response.data['alert']['id']

        recipient_a = AlertRecipient.objects.get(alert_id=alert_id, recipient=self.guardian_a)
        self.assertEqual(recipient_a.email_status, AlertRecipient.DeliveryStatus.SKIPPED)
        self.assertIn(recipient_a.sms_status, [AlertRecipient.DeliveryStatus.SENT, AlertRecipient.DeliveryStatus.FAILED])

