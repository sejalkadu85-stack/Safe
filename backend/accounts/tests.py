from django.test import TestCase
from django.urls import reverse
from rest_framework.test import APITestCase
from rest_framework import status
from .models import CustomUser, UserProfile, OTPVerification


class AccountsAuthAPITests(APITestCase):
    def setUp(self):
        self.register_url = reverse('accounts:register')
        self.login_url = reverse('accounts:login')
        self.logout_url = reverse('accounts:logout')
        self.me_url = reverse('accounts:current_user')
        self.profile_url = reverse('accounts:profile')
        self.send_otp_url = reverse('accounts:send_otp')
        self.verify_otp_url = reverse('accounts:verify_otp')
        self.reset_password_url = reverse('accounts:reset_password')

    def test_user_registration_success(self):
        payload = {
            'username': 'aisha_khan',
            'email': 'aisha@example.com',
            'phone_number': '+919876543210',
            'first_name': 'Aisha',
            'last_name': 'Khan',
            'role': 'woman',
            'password': 'StrongPassword123!',
            'confirm_password': 'StrongPassword123!',
        }
        response = self.client.post(self.register_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn('tokens', response.data)
        self.assertIn('access', response.data['tokens'])
        self.assertIn('refresh', response.data['tokens'])
        self.assertEqual(response.data['user']['email'], 'aisha@example.com')
        self.assertEqual(response.data['user']['role'], 'woman')

    def test_user_login_with_email(self):
        CustomUser.objects.create_user(
            username='guardian_rahul',
            email='rahul@example.com',
            phone_number='+919876543211',
            role='guardian',
            password='Password123!'
        )
        payload = {
            'email_or_phone': 'rahul@example.com',
            'password': 'Password123!',
        }
        response = self.client.post(self.login_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('tokens', response.data)
        self.assertEqual(response.data['user']['role'], 'guardian')

    def test_user_login_with_phone_number(self):
        CustomUser.objects.create_user(
            username='mom_guardian',
            email='mom@example.com',
            phone_number='+919876543212',
            role='guardian',
            password='Password123!'
        )
        payload = {
            'email_or_phone': '+919876543212',
            'password': 'Password123!',
        }
        response = self.client.post(self.login_url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['user']['username'], 'mom_guardian')

    def test_current_user_profile_and_update(self):
        user = CustomUser.objects.create_user(
            username='priya_sharma',
            email='priya@example.com',
            role='woman',
            password='Password123!'
        )
        self.client.force_authenticate(user=user)

        # GET /api/auth/profile/
        response = self.client.get(self.profile_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['username'], 'priya_sharma')

        # PUT /api/auth/profile/
        update_payload = {
            'first_name': 'Priya',
            'last_name': 'Sharma',
            'blood_group': 'B+',
            'city': 'Mumbai',
            'emergency_notes': 'Asthmatic, inhaler in backpack',
        }
        update_response = self.client.put(self.profile_url, update_payload, format='json')
        self.assertEqual(update_response.status_code, status.HTTP_200_OK)
        self.assertEqual(update_response.data['user']['first_name'], 'Priya')
        self.assertEqual(update_response.data['user']['profile']['blood_group'], 'B+')
        self.assertEqual(update_response.data['user']['profile']['city'], 'Mumbai')

    def test_profile_photo_upload(self):
        import io
        from PIL import Image
        from django.core.files.uploadedfile import SimpleUploadedFile

        user = CustomUser.objects.create_user(
            username='photo_test_user',
            email='photo@test.com',
            role='woman',
            password='Password123!'
        )
        self.client.force_authenticate(user=user)

        # Create dummy image in memory
        image_file = io.BytesIO()
        image = Image.new('RGB', (100, 100), color='purple')
        image.save(image_file, 'JPEG')
        image_file.seek(0)

        uploaded_file = SimpleUploadedFile(
            name='avatar.jpg',
            content=image_file.read(),
            content_type='image/jpeg'
        )

        photo_url = reverse('accounts:profile_photo')
        response = self.client.post(photo_url, {'profile_photo': uploaded_file}, format='multipart')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn('profile_photo_url', response.data)
        user.refresh_from_db()
        self.assertTrue(bool(user.profile_photo))

    def test_get_user_detail_by_id(self):
        ward = CustomUser.objects.create_user(
            username='ward_user',
            email='ward@test.com',
            role='woman',
            password='Password123!'
        )
        ward.profile.blood_group = 'O+'
        ward.profile.save()

        guardian = CustomUser.objects.create_user(
            username='guardian_test',
            email='guardian@test.com',
            role='guardian',
            password='Password123!'
        )
        self.client.force_authenticate(user=guardian)

        detail_url = reverse('accounts:user_detail', kwargs={'id': ward.id})
        response = self.client.get(detail_url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['username'], 'ward_user')
        self.assertEqual(response.data['blood_group'], 'O+')

    def test_otp_flow_and_verification(self):
        user = CustomUser.objects.create_user(
            username='neha_patel',
            email='neha@example.com',
            phone_number='+919876543213',
            role='woman',
            password='Password123!'
        )
        self.assertFalse(user.is_verified)

        # Send OTP
        send_response = self.client.post(self.send_otp_url, {
            'phone_or_email': '+919876543213',
            'purpose': 'verification'
        }, format='json')
        self.assertEqual(send_response.status_code, status.HTTP_200_OK)
        otp_record = OTPVerification.objects.filter(phone_or_email='+919876543213').first()
        self.assertIsNotNone(otp_record)
        otp = otp_record.otp_code

        # Verify OTP
        verify_response = self.client.post(self.verify_otp_url, {
            'phone_or_email': '+919876543213',
            'otp_code': otp,
            'purpose': 'verification'
        }, format='json')
        self.assertEqual(verify_response.status_code, status.HTTP_200_OK)

        user.refresh_from_db()
        self.assertTrue(user.is_verified)

    def test_logout_blacklists_token(self):
        user = CustomUser.objects.create_user(
            username='logout_test',
            email='logout@example.com',
            role='woman',
            password='Password123!'
        )
        self.client.force_authenticate(user=user)

        from accounts.views import get_tokens_for_user
        tokens = get_tokens_for_user(user)

        response = self.client.post(self.logout_url, {'refresh': tokens['refresh']}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)


class RolePermissionsTests(TestCase):
    """
    Test RBAC (Role-Based Access Control) permission classes.
    """
    def setUp(self):
        from rest_framework.test import APIRequestFactory
        self.factory = APIRequestFactory()

        self.woman_user = CustomUser.objects.create_user(
            username='woman_perm_test',
            email='woman_perm@test.com',
            role='woman',
            password='Password123!'
        )
        self.guardian_user = CustomUser.objects.create_user(
            username='guardian_perm_test',
            email='guardian_perm@test.com',
            role='guardian',
            password='Password123!'
        )
        self.volunteer_user = CustomUser.objects.create_user(
            username='volunteer_perm_test',
            email='volunteer_perm@test.com',
            role='volunteer',
            password='Password123!'
        )
        self.admin_user = CustomUser.objects.create_superuser(
            username='admin_perm_test',
            email='admin_perm@test.com',
            role='admin',
            password='Password123!'
        )

    def test_is_woman_permission(self):
        from accounts.permissions import IsWoman
        permission = IsWoman()

        request = self.factory.get('/')
        request.user = self.woman_user
        self.assertTrue(permission.has_permission(request, None))

        request.user = self.guardian_user
        self.assertFalse(permission.has_permission(request, None))

        request.user = self.volunteer_user
        self.assertFalse(permission.has_permission(request, None))

    def test_is_guardian_permission(self):
        from accounts.permissions import IsGuardian
        permission = IsGuardian()

        request = self.factory.get('/')
        request.user = self.guardian_user
        self.assertTrue(permission.has_permission(request, None))

        request.user = self.woman_user
        self.assertFalse(permission.has_permission(request, None))

    def test_is_admin_permission(self):
        from accounts.permissions import IsAdminUser
        permission = IsAdminUser()

        request = self.factory.get('/')
        request.user = self.admin_user
        self.assertTrue(permission.has_permission(request, None))

        request.user = self.woman_user
        self.assertFalse(permission.has_permission(request, None))
