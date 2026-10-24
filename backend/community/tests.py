from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APIClient
from rest_framework import status
from decimal import Decimal

from accounts.models import CustomUser
from alerts.models import SOSAlert
from community.models import Volunteer, VolunteerRequest, Helpline


class VolunteerAndHelplineTests(TestCase):
    def setUp(self):
        self.client = APIClient()

        # Users
        self.woman = CustomUser.objects.create_user(
            username='user_priya',
            email='priya@example.com',
            password='Password123!',
            phone_number='+919876543210',
            role=CustomUser.Role.WOMAN
        )
        self.volunteer_user = CustomUser.objects.create_user(
            username='vol_aarav',
            email='aarav@example.com',
            password='Password123!',
            phone_number='+919876543211',
            role=CustomUser.Role.VOLUNTEER
        )

        # Pre-seed a helpline
        self.helpline = Helpline.objects.create(
            name='Women Helpline',
            number='1091',
            category='women',
            description='24/7 emergency response for women',
            is_national=True,
            priority=1
        )

    def test_volunteer_registration_and_profile(self):
        self.client.force_authenticate(user=self.volunteer_user)

        # 1. Register as volunteer
        payload = {
            'phone_number': '+919876543211',
            'skills': 'First aid certified, martial arts',
            'latitude': 18.5204,
            'longitude': 73.8567,
            'address': 'FC Road, Pune',
            'is_available': True
        }
        res = self.client.post('/api/volunteers/register/', payload, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        self.assertTrue(res.data['success'])
        self.assertTrue(Volunteer.objects.filter(user=self.volunteer_user).exists())

        # 2. Get profile
        res_prof = self.client.get('/api/volunteers/profile/')
        self.assertEqual(res_prof.status_code, status.HTTP_200_OK)
        self.assertTrue(res_prof.data['is_registered'])
        self.assertEqual(res_prof.data['volunteer']['skills'], 'First aid certified, martial arts')

    def test_nearby_volunteers(self):
        # Create volunteer near Pune station (18.5284, 73.8739)
        Volunteer.objects.create(
            user=self.volunteer_user,
            phone_number='+919876543211',
            skills='Self defense trainer',
            latitude=Decimal('18.5284'),
            longitude=Decimal('73.8739'),
            is_available=True,
            is_verified=True
        )

        self.client.force_authenticate(user=self.woman)

        # Request nearby volunteers near FC road (18.5204, 73.8567) - distance ~2km
        res = self.client.get('/api/volunteers/nearby/?lat=18.5204&lng=73.8567&radius=5000')
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertTrue(res.data['success'])
        self.assertGreaterEqual(len(res.data['volunteers']), 1)
        self.assertIn('distance_meters', res.data['volunteers'][0])

    def test_volunteer_request_workflow(self):
        vol = Volunteer.objects.create(
            user=self.volunteer_user,
            phone_number='+919876543211',
            skills='First Aid',
            latitude=Decimal('18.5204'),
            longitude=Decimal('73.8567'),
            is_available=True,
            is_verified=True
        )

        # 1. Woman creates request
        self.client.force_authenticate(user=self.woman)
        payload = {
            'volunteer_id': vol.id,
            'emergency_type': 'Night Walk Companion',
            'message': 'Walking home from metro station alone, need companion.',
            'latitude': 18.5210,
            'longitude': 73.8570,
            'address': 'Pune Metro Station 3'
        }
        res_req = self.client.post('/api/volunteers/request/', payload, format='json')
        self.assertEqual(res_req.status_code, status.HTTP_201_CREATED)
        req_id = res_req.data['request']['id']

        # 2. Volunteer views requests
        self.client.force_authenticate(user=self.volunteer_user)
        res_list = self.client.get('/api/volunteers/requests/?role=volunteer')
        self.assertEqual(res_list.status_code, status.HTTP_200_OK)
        self.assertEqual(len(res_list.data['requests']), 1)

        # 3. Volunteer accepts request
        res_accept = self.client.put(f'/api/volunteers/requests/{req_id}/respond/', {'action': 'accept'}, format='json')
        self.assertEqual(res_accept.status_code, status.HTTP_200_OK)
        self.assertEqual(res_accept.data['request']['status'], 'accepted')

        # 4. Volunteer completes assistance
        res_complete = self.client.put(f'/api/volunteers/requests/{req_id}/respond/', {'action': 'complete'}, format='json')
        self.assertEqual(res_complete.status_code, status.HTTP_200_OK)
        self.assertEqual(res_complete.data['request']['status'], 'completed')

        # 5. Woman leaves a 5-star rating
        self.client.force_authenticate(user=self.woman)
        res_rate = self.client.post(f'/api/volunteers/requests/{req_id}/rate/', {
            'rating': 5,
            'feedback': 'Aarav was very kind and escorted me safely!'
        }, format='json')
        self.assertEqual(res_rate.status_code, status.HTTP_200_OK)
        self.assertTrue(res_rate.data['success'])

        # Verify volunteer's rating updated
        vol.refresh_from_db()
        self.assertEqual(vol.total_helps, 1)
        self.assertEqual(vol.rating, Decimal('5.00'))

    def test_helpline_directory_and_search(self):
        # 1. Fetch all
        res = self.client.get('/api/helplines/')
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertGreaterEqual(res.data['count'], 1)

        # 2. Filter by category
        res_cat = self.client.get('/api/helplines/?category=women')
        self.assertEqual(res_cat.status_code, status.HTTP_200_OK)
        self.assertEqual(res_cat.data['count'], 1)

        # 3. Search query
        res_search = self.client.get('/api/helplines/?search=1091')
        self.assertEqual(res_search.status_code, status.HTTP_200_OK)
        self.assertEqual(res_search.data['count'], 1)
        self.assertEqual(res_search.data['helplines'][0]['number'], '1091')

    def test_admin_incident_and_stats_api(self):
        alert = SOSAlert.objects.create(
            sender=self.woman,
            status='active',
            trigger_type='manual',
            latitude=Decimal('18.5204'),
            longitude=Decimal('73.8567')
        )

        # 1. Get stats
        res_stats = self.client.get('/api/admin-panel/api/stats/')
        self.assertEqual(res_stats.status_code, status.HTTP_200_OK)
        self.assertEqual(res_stats.data['stats']['active_sos_alerts'], 1)

        # 2. List incidents
        res_incidents = self.client.get('/api/admin-panel/api/incidents/?status=active')
        self.assertEqual(res_incidents.status_code, status.HTTP_200_OK)
        self.assertEqual(len(res_incidents.data['incidents']), 1)

        # 3. Resolve incident
        res_resolve = self.client.put(f'/api/admin-panel/api/incidents/{alert.id}/resolve/', {'status': 'resolved'}, format='json')
        self.assertEqual(res_resolve.status_code, status.HTTP_200_OK)
        alert.refresh_from_db()
        self.assertEqual(alert.status, 'resolved')

        # 4. Web Dashboard render
        res_dash = self.client.get('/admin-panel/dashboard/')
        self.assertEqual(res_dash.status_code, status.HTTP_200_OK)
        self.assertContains(res_dash, 'SafeHer Command & Incident Center')
