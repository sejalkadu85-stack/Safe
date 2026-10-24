from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APITestCase
from rest_framework import status
from django.contrib.auth import get_user_model
from django.utils import timezone
from datetime import timedelta

from .models import LocationLog, LocationShareSession, SafeZone, SafeZoneEvent
from alerts.models import TrustedContact, SOSAlert

User = get_user_model()


class LocationAPITests(APITestCase):
    def setUp(self):
        self.woman = User.objects.create_user(
            username='pooja',
            email='pooja@example.com',
            phone_number='+919876500031',
            role='woman',
            password='Password123!'
        )

        self.guardian = User.objects.create_user(
            username='vikram_guardian',
            email='vikram@example.com',
            phone_number='+919876500032',
            role='guardian',
            password='Password123!'
        )

        self.stranger = User.objects.create_user(
            username='stranger_user',
            email='stranger@example.com',
            role='woman',
            password='Password123!'
        )

        # Set up accepted relationship between woman and guardian
        TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='sibling',
            status=TrustedContact.Status.ACCEPTED
        )

        self.update_url = reverse('location:update_location')
        self.live_url = reverse('location:live_location', kwargs={'user_id': self.woman.id})
        self.history_url = reverse('location:location_history', kwargs={'user_id': self.woman.id})
        self.start_share_url = reverse('location:start_share')
        self.stop_share_url = reverse('location:stop_share')
        self.status_share_url = reverse('location:share_status')

    def test_location_update_records_ping(self):
        self.client.force_authenticate(user=self.woman)
        payload = {
            'latitude': '19.075983',
            'longitude': '72.877655',
            'accuracy': 8.5,
            'speed': 1.2,
            'heading': 180.0,
            'battery_level': 88,
        }
        response = self.client.post(self.update_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(LocationLog.objects.filter(user=self.woman).count(), 1)
        log = LocationLog.objects.first()
        self.assertEqual(float(log.latitude), 19.075983)
        self.assertEqual(log.battery_level, 88)

    def test_location_update_syncs_with_active_sos(self):
        self.client.force_authenticate(user=self.woman)
        # Create active SOS alert
        sos = SOSAlert.objects.create(
            sender=self.woman,
            latitude='19.000000',
            longitude='72.000000',
            status=SOSAlert.Status.ACTIVE
        )

        # Send fresh ping
        self.client.post(self.update_url, {
            'latitude': '19.075983',
            'longitude': '72.877655',
            'battery_level': 50
        }, format='json')

        sos.refresh_from_db()
        self.assertEqual(float(sos.latitude), 19.075983)
        self.assertEqual(float(sos.longitude), 72.877655)
        self.assertEqual(sos.battery_level, 50)

    def test_live_location_privacy_denies_unauthorized_user(self):
        # Ping exists
        LocationLog.objects.create(
            user=self.woman,
            latitude='19.075983',
            longitude='72.877655'
        )

        # Stranger attempts to view location
        self.client.force_authenticate(user=self.stranger)
        response = self.client.get(self.live_url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

        # Even guardian is denied when NO emergency and NO active share session exists!
        self.client.force_authenticate(user=self.guardian)
        response = self.client.get(self.live_url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_live_location_allowed_during_active_sos(self):
        LocationLog.objects.create(
            user=self.woman,
            latitude='19.075983',
            longitude='72.877655'
        )
        SOSAlert.objects.create(
            sender=self.woman,
            latitude='19.075983',
            longitude='72.877655',
            status=SOSAlert.Status.ACTIVE
        )

        # Guardian now has full emergency clearance to view
        self.client.force_authenticate(user=self.guardian)
        response = self.client.get(self.live_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertTrue(response.data['has_active_sos'])
        self.assertIsNotNone(response.data['latest_location'])
        self.assertEqual(float(response.data['latest_location']['latitude']), 19.075983)

    def test_live_location_allowed_during_active_share_session(self):
        LocationLog.objects.create(
            user=self.woman,
            latitude='19.075983',
            longitude='72.877655'
        )

        # Woman activates 60-min live sharing
        self.client.force_authenticate(user=self.woman)
        share_res = self.client.post(self.start_share_url, {
            'duration_minutes': 60,
            'note': 'Taking auto home'
        }, format='json')
        self.assertEqual(share_res.status_code, status.HTTP_201_CREATED)

        # Guardian queries live location
        self.client.force_authenticate(user=self.guardian)
        response = self.client.get(self.live_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertTrue(response.data['is_actively_sharing'])
        self.assertEqual(response.data['share_session']['note'], 'Taking auto home')

    def test_stop_location_sharing(self):
        self.client.force_authenticate(user=self.woman)
        self.client.post(self.start_share_url, {'duration_minutes': 30}, format='json')

        # Stop sharing
        stop_res = self.client.post(self.stop_share_url, format='json')
        self.assertEqual(stop_res.status_code, status.HTTP_200_OK)

        # Guardian is now denied
        self.client.force_authenticate(user=self.guardian)
        response = self.client.get(self.live_url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_location_history_trail(self):
        now = timezone.now()
        for i in range(3):
            LocationLog.objects.create(
                user=self.woman,
                latitude=f"19.075{i}00",
                longitude=f"72.877{i}00",
                timestamp=now - timedelta(minutes=i*10)
            )

        # Active share session so guardian can view history
        LocationShareSession.objects.create(
            user=self.woman,
            duration_minutes=60,
            expires_at=now + timedelta(hours=1),
            is_active=True
        )

        self.client.force_authenticate(user=self.guardian)
        response = self.client.get(self.history_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 3)


class SafeZoneAPITests(APITestCase):
    def setUp(self):
        self.woman = User.objects.create_user(
            username='simran',
            email='simran@example.com',
            phone_number='+919876500041',
            role='woman',
            password='Password123!'
        )

        self.guardian = User.objects.create_user(
            username='guardian_simran',
            email='guardian_simran@example.com',
            phone_number='+919876500042',
            role='guardian',
            password='Password123!'
        )

        TrustedContact.objects.create(
            owner=self.woman,
            contact_user=self.guardian,
            relationship='parent',
            status=TrustedContact.Status.ACCEPTED,
            notify_via_sms=True
        )

        self.safezone_url = reverse('location:list_create_safezone')
        self.check_url = reverse('location:check_geofence')
        self.events_url = reverse('location:safezone_events')
        self.update_url = reverse('location:update_location')

    def test_create_safe_zone(self):
        self.client.force_authenticate(user=self.woman)
        payload = {
            'name': 'Home',
            'latitude': '19.076000',
            'longitude': '72.877000',
            'radius_meters': 300,
            'notify_on_exit': True,
            'notify_on_entry': True,
        }
        response = self.client.post(self.safezone_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(SafeZone.objects.filter(user=self.woman).count(), 1)
        zone = SafeZone.objects.first()
        self.assertEqual(zone.name, 'Home')
        self.assertEqual(zone.radius_meters, 300)

    def test_list_safe_zones(self):
        SafeZone.objects.create(
            user=self.woman,
            name='Home',
            latitude='19.076000',
            longitude='72.877000',
            radius_meters=200
        )
        SafeZone.objects.create(
            user=self.woman,
            name='University',
            latitude='19.100000',
            longitude='72.880000',
            radius_meters=500
        )

        self.client.force_authenticate(user=self.woman)
        response = self.client.get(self.safezone_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 2)

    def test_check_geofence_inside_and_outside(self):
        SafeZone.objects.create(
            user=self.woman,
            name='Home',
            latitude='19.076000',
            longitude='72.877000',
            radius_meters=300
        )
        self.client.force_authenticate(user=self.woman)

        # Coordinate right next to center (~50 meters away)
        in_res = self.client.post(self.check_url, {
            'latitude': '19.076200',
            'longitude': '72.877200'
        }, format='json')
        self.assertEqual(in_res.status_code, status.HTTP_200_OK)
        self.assertTrue(in_res.data['is_inside_safe_zone'])

        # Coordinate 2 km away
        out_res = self.client.post(self.check_url, {
            'latitude': '19.090000',
            'longitude': '72.877000'
        }, format='json')
        self.assertEqual(out_res.status_code, status.HTTP_200_OK)
        self.assertFalse(out_res.data['is_inside_safe_zone'])

    def test_geofence_breach_detection_on_location_update(self):
        SafeZone.objects.create(
            user=self.woman,
            name='Campus',
            latitude='19.076000',
            longitude='72.877000',
            radius_meters=200,
            notify_on_exit=True
        )
        self.client.force_authenticate(user=self.woman)

        # Ping 1: Inside Campus
        self.client.post(self.update_url, {
            'latitude': '19.076050',
            'longitude': '72.877050'
        }, format='json')

        # Ping 2: Outside Campus (1.5 km away)
        update_res = self.client.post(self.update_url, {
            'latitude': '19.090000',
            'longitude': '72.877000'
        }, format='json')

        self.assertEqual(update_res.status_code, status.HTTP_201_CREATED)
        self.assertEqual(update_res.data['geofence_events_triggered'], 1)

        # Verify SafeZoneEvent of type 'exit' was created
        event = SafeZoneEvent.objects.filter(user=self.woman, event_type='exit').first()
        self.assertIsNotNone(event)
        self.assertEqual(event.safe_zone.name, 'Campus')
        self.assertTrue(event.notified_guardians)

    def test_safezone_update_and_delete(self):
        zone = SafeZone.objects.create(
            user=self.woman,
            name='Temporary Place',
            latitude='19.076000',
            longitude='72.877000',
            radius_meters=150
        )
        self.client.force_authenticate(user=self.woman)

        detail_url = reverse('location:safezone_detail', kwargs={'id': zone.id})

        # Update name and radius
        patch_res = self.client.patch(detail_url, {'name': 'Gym', 'radius_meters': 250}, format='json')
        self.assertEqual(patch_res.status_code, status.HTTP_200_OK)
        self.assertEqual(patch_res.data['name'], 'Gym')
        self.assertEqual(patch_res.data['radius_meters'], 250)

        # Delete safezone
        delete_res = self.client.delete(detail_url)
        self.assertEqual(delete_res.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(SafeZone.objects.filter(id=zone.id).exists())


class SafeJourneyTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='sarah', email='sarah@test.com', password='password123')
        self.guardian = User.objects.create_user(username='guardian_bob', email='bob@test.com', password='password123')
        TrustedContact.objects.create(
            owner=self.user,
            contact_user=self.guardian,
            status=TrustedContact.Status.ACCEPTED
        )
        self.client.force_authenticate(user=self.user)

    def test_start_journey(self):
        url = reverse('root_start_journey')
        eta = (timezone.now() + timedelta(hours=1)).isoformat()
        payload = {
            'start_location': 'Office, Downtown',
            'destination': 'Home, Uptown',
            'start_latitude': '19.076000',
            'start_longitude': '72.877000',
            'dest_latitude': '19.120000',
            'dest_longitude': '72.850000',
            'route_polyline': '[[19.076, 72.877], [19.090, 72.865], [19.120, 72.850]]',
            'estimated_arrival': eta,
            'share_with_all_trusted': True,
        }
        res = self.client.post(url, payload, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        self.assertEqual(res.data['status'], 'active')
        self.assertTrue(res.data['is_active'])

        # Verify active journey query
        active_res = self.client.get(reverse('root_active_journey'))
        self.assertEqual(active_res.status_code, status.HTTP_200_OK)
        self.assertTrue(active_res.data['has_active_journey'])

    def test_journey_deviation_triggers_sos(self):
        eta = (timezone.now() + timedelta(hours=1)).isoformat()
        start_res = self.client.post(reverse('root_start_journey'), {
            'start_location': 'Start Pt',
            'destination': 'End Pt',
            'start_latitude': '19.076000',
            'start_longitude': '72.877000',
            'dest_latitude': '19.080000',
            'dest_longitude': '72.877000',
            'route_polyline': '[[19.076000, 72.877000], [19.080000, 72.877000]]',
            'estimated_arrival': eta,
        }, format='json')
        journey_id = start_res.data['id']

        # Update position far off route (e.g. 5 km away: lat 19.130000)
        update_url = reverse('root_update_journey', kwargs={'id': journey_id})
        update_res = self.client.put(update_url, {
            'current_latitude': '19.130000',
            'current_longitude': '72.877000',
        }, format='json')

        self.assertEqual(update_res.status_code, status.HTTP_200_OK)
        self.assertTrue(update_res.data['deviation_detected'])
        self.assertTrue(update_res.data['new_deviation_triggered'])
        self.assertGreater(update_res.data['deviation_distance_meters'], 500)

        # Check SOS was automatically generated
        sos_exists = SOSAlert.objects.filter(
            sender=self.user,
            trigger_type=SOSAlert.TriggerType.JOURNEY_DEVIATION,
            status=SOSAlert.Status.ACTIVE
        ).exists()
        self.assertTrue(sos_exists)

    def test_complete_journey(self):
        eta = (timezone.now() + timedelta(hours=1)).isoformat()
        start_res = self.client.post(reverse('root_start_journey'), {
            'start_location': 'Point A',
            'destination': 'Point B',
            'start_latitude': '19.076000',
            'start_longitude': '72.877000',
            'dest_latitude': '19.080000',
            'dest_longitude': '72.877000',
            'estimated_arrival': eta,
        }, format='json')
        journey_id = start_res.data['id']

        complete_url = reverse('root_complete_journey', kwargs={'id': journey_id})
        complete_res = self.client.put(complete_url, format='json')
        self.assertEqual(complete_res.status_code, status.HTTP_200_OK)
        self.assertEqual(complete_res.data['status'], 'completed')


class DangerZoneTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='alice', email='alice@test.com', password='password123')
        self.admin = User.objects.create_superuser(username='admin', email='admin@test.com', password='password123')
        self.client.force_authenticate(user=self.user)

    def test_report_and_list_danger_zone(self):
        url = reverse('root_report_dangerzone')
        payload = {
            'title': 'Dark Alley near Station',
            'latitude': '19.076000',
            'longitude': '72.877000',
            'radius': 120.0,
            'description': 'Street lights broken for 2 weeks',
            'category': 'poor_lighting',
            'severity': 'high',
        }
        res = self.client.post(url, payload, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        self.assertEqual(res.data['title'], 'Dark Alley near Station')
        zone_id = res.data['id']

        # List danger zones
        list_res = self.client.get(reverse('root_list_dangerzones'))
        self.assertEqual(list_res.status_code, status.HTTP_200_OK)
        self.assertGreaterEqual(len(list_res.data), 1)

        # Nearby query
        nearby_url = f"{reverse('root_nearby_dangerzones')}?lat=19.076050&lng=72.877050&radius=500"
        nearby_res = self.client.get(nearby_url)
        self.assertEqual(nearby_res.status_code, status.HTTP_200_OK)
        self.assertEqual(len(nearby_res.data), 1)
        self.assertIn('distance_meters', nearby_res.data[0])

        # Admin verification
        self.client.force_authenticate(user=self.admin)
        verify_url = reverse('root_verify_dangerzone', kwargs={'id': zone_id})
        verify_res = self.client.put(verify_url, {'verified': True}, format='json')
        self.assertEqual(verify_res.status_code, status.HTTP_200_OK)
        self.assertTrue(verify_res.data['verified'])

