import random
from datetime import timedelta
from django.utils import timezone
from django.conf import settings
from django.db.models import Q
from rest_framework import generics, status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenRefreshView

from .models import CustomUser, UserProfile, OTPVerification
from .rate_limit import check_login_rate_limit, record_login_failure, record_login_success
from .serializers import (
    UserSerializer,
    RegisterSerializer,
    LoginSerializer,
    SendOTPSerializer,
    VerifyOTPSerializer,
    ResetPasswordSerializer,
    ChangePasswordSerializer,
    UserProfileSerializer,
    UserProfileUpdateSerializer,
    ProfilePhotoUploadSerializer,
    PublicUserSummarySerializer,
)


def get_tokens_for_user(user):
    """
    Generate JWT access and refresh token pair for a user.
    """
    refresh = RefreshToken.for_user(user)
    # Add custom claims to the token payload
    refresh['role'] = user.role
    refresh['username'] = user.username
    refresh['email'] = user.email
    return {
        'refresh': str(refresh),
        'access': str(refresh.access_token),
    }


class RegisterView(generics.CreateAPIView):
    """
    Register a new user with role selection (woman, guardian, volunteer, admin).
    Returns user data along with access and refresh JWT tokens.
    """
    permission_classes = (AllowAny,)
    authentication_classes = ()
    parser_classes = (MultiPartParser, FormParser, JSONParser)
    serializer_class = RegisterSerializer

    def post(self, request, *args, **kwargs):
        data = request.data.copy() if hasattr(request.data, 'copy') else dict(request.data)
        if 'id_proof_document' in request.FILES:
            data['id_proof_document'] = request.FILES['id_proof_document']

        serializer = self.get_serializer(data=data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        tokens = get_tokens_for_user(user)
        user_data = UserSerializer(user, context={'request': request}).data

        if user.role == CustomUser.Role.VOLUNTEER and user.verification_status == CustomUser.VerificationStatus.PENDING:
            msg = 'Volunteer registration submitted! Your Government ID proof is currently under review by the Administrator.'
        else:
            msg = 'Account created successfully!'

        return Response(
            {
                'message': msg,
                'user': user_data,
                'tokens': tokens,
            },
            status=status.HTTP_201_CREATED
        )


class LoginView(APIView):
    """
    Authenticate user via Email, Phone Number, or Username + Password.
    Returns JWT access and refresh tokens along with user profile and role.

    Rate limiting:
      - Max 5 failed attempts per IP per 15 minutes → HTTP 429
      - Max 5 failed attempts per account per 15 minutes → HTTP 429
      - After 10 consecutive IP failures → 1-hour hard lockout
      - Successful login resets failure counter
    """
    permission_classes = (AllowAny,)
    authentication_classes = ()

    def post(self, request, *args, **kwargs):
        # Extract identifier early (for rate-limit key) before validation
        identifier = (
            request.data.get('email') or
            request.data.get('phone_number') or
            request.data.get('username') or
            'unknown'
        ).strip().lower()

        # ── 1. Check rate limit BEFORE touching credentials ──────────────
        rate_check = check_login_rate_limit(request, identifier)
        if not rate_check['allowed']:
            response = Response(
                {
                    'detail': rate_check['detail'],
                    'retry_after_seconds': rate_check.get('retry_after_seconds', 900),
                },
                status=status.HTTP_429_TOO_MANY_REQUESTS
            )
            response['Retry-After'] = str(rate_check.get('retry_after_seconds', 900))
            return response

        # ── 2. Validate credentials ───────────────────────────────────────
        serializer = LoginSerializer(data=request.data)
        if not serializer.is_valid():
            # Bad request format — count as a failure attempt
            record_login_failure(request, identifier)
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        user = serializer.validated_data.get('user')
        if not user:
            # Credentials were wrong
            record_login_failure(request, identifier)
            return Response(
                {'detail': 'Invalid credentials. Please check your email and password.'},
                status=status.HTTP_401_UNAUTHORIZED
            )

        # ── 3. Check verification gate for Volunteer role ─────────────────
        if user.role == CustomUser.Role.VOLUNTEER:
            if user.verification_status == CustomUser.VerificationStatus.PENDING:
                return Response(
                    {
                        'detail': 'Your volunteer account is pending Administrator review. Your uploaded Government ID must be verified by the admin before you can log in.',
                        'verification_status': 'pending'
                    },
                    status=status.HTTP_403_FORBIDDEN
                )
            elif user.verification_status == CustomUser.VerificationStatus.REJECTED:
                reason = user.verification_rejection_reason or "Document unreadable or invalid."
                return Response(
                    {
                        'detail': f'Your volunteer registration was rejected by the administrator. Reason: {reason}',
                        'verification_status': 'rejected',
                        'rejection_reason': reason
                    },
                    status=status.HTTP_403_FORBIDDEN
                )

        # ── 4. Login successful — reset counters ─────────────────────────
        record_login_success(request, identifier)

        tokens = get_tokens_for_user(user)
        user_data = UserSerializer(user, context={'request': request}).data

        return Response(
            {
                'message': 'Login successful!',
                'user': user_data,
                'tokens': tokens,
            },
            status=status.HTTP_200_OK
        )



class LogoutView(APIView):
    """
    Blacklist the refresh token to securely log out the user.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        refresh_token = request.data.get('refresh')
        if not refresh_token:
            return Response(
                {'detail': 'Refresh token is required.'},
                status=status.HTTP_400_BAD_REQUEST
            )
        try:
            token = RefreshToken(refresh_token)
            token.blacklist()
            return Response(
                {'message': 'Logged out successfully.'},
                status=status.HTTP_200_OK
            )
        except Exception as e:
            return Response(
                {'detail': 'Invalid or expired token.'},
                status=status.HTTP_400_BAD_REQUEST
            )


class CurrentUserView(generics.RetrieveUpdateAPIView):
    """
    Retrieve or update the currently authenticated user's profile.
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = UserSerializer

    def get_object(self):
        return self.request.user


class ProfileView(APIView):
    """
    GET: Retrieve full profile (user details + safety info).
    PUT / PATCH: Update user personal details and safety profile.
    """
    permission_classes = (IsAuthenticated,)

    def get(self, request):
        serializer = UserSerializer(request.user, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

    def put(self, request):
        serializer = UserProfileUpdateSerializer(
            request.user,
            data=request.data,
            partial=True
        )
        serializer.is_valid(raise_exception=True)
        serializer.save()

        updated_user = UserSerializer(request.user, context={'request': request}).data
        return Response(
            {
                'message': 'Profile updated successfully.',
                'user': updated_user,
            },
            status=status.HTTP_200_OK
        )

    def patch(self, request):
        return self.put(request)


class ProfilePhotoUploadView(APIView):
    """
    POST: Upload or update profile picture.
    Supports multipart fields 'profile_photo' or 'photo'.
    """
    permission_classes = (IsAuthenticated,)
    parser_classes = (MultiPartParser, FormParser)

    def post(self, request):
        data = request.data.copy() if hasattr(request.data, 'copy') else dict(request.data)
        if 'photo' in request.FILES and 'profile_photo' not in request.FILES:
            data['profile_photo'] = request.FILES['photo']
        elif 'photo' in data and 'profile_photo' not in data:
            data['profile_photo'] = data['photo']

        serializer = ProfilePhotoUploadSerializer(
            request.user,
            data=data,
            partial=True
        )
        serializer.is_valid(raise_exception=True)
        serializer.save()

        photo_url = request.user.profile_photo.url if request.user.profile_photo else None
        if photo_url and not photo_url.startswith('http'):
            photo_url = request.build_absolute_uri(photo_url)

        updated_user = UserSerializer(request.user, context={'request': request}).data
        return Response(
            {
                'message': 'Profile photo uploaded successfully.',
                'profile_photo_url': photo_url,
                'user': updated_user,
            },
            status=status.HTTP_200_OK
        )


class UserDetailByIdView(generics.RetrieveAPIView):
    """
    GET: Retrieve public/safety summary of another user (e.g., Guardian checking ward's info).
    """
    permission_classes = (IsAuthenticated,)
    serializer_class = PublicUserSummarySerializer
    queryset = CustomUser.objects.all()
    lookup_field = 'id'


class SendOTPView(APIView):
    """
    Generate and dispatch a 6-digit OTP for phone/email verification or password reset.
    Valid for 10 minutes.

    Rate limiting: Max 3 OTP requests per identifier per 10 minutes.
    """
    permission_classes = (AllowAny,)
    authentication_classes = ()

    def post(self, request):
        serializer = SendOTPSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        identifier = serializer.validated_data['phone_or_email'].strip()
        purpose = serializer.validated_data['purpose']

        # ── OTP Rate Limit: 3 requests per identifier per 10 minutes ──────
        from django.core.cache import cache
        otp_rate_key = f"otp_req:{identifier.lower().replace('@','_').replace('.','_')}"
        otp_request_count = cache.get(otp_rate_key, 0)
        if otp_request_count >= 3:
            ttl = cache.ttl(otp_rate_key) or 600
            return Response(
                {
                    'detail': (
                        f"Too many OTP requests. Please wait "
                        f"{ttl // 60} minute(s) before requesting again."
                    ),
                    'retry_after_seconds': ttl,
                },
                status=status.HTTP_429_TOO_MANY_REQUESTS
            )
        # Increment OTP request counter
        try:
            cache.incr(otp_rate_key)
        except ValueError:
            cache.set(otp_rate_key, 1, timeout=600)  # 10-minute window

        # Generate a random 6-digit OTP
        otp_code = f"{random.randint(100000, 999999)}"
        expires_at = timezone.now() + timedelta(minutes=10)

        # Invalidate existing unused OTPs for this identifier and purpose
        OTPVerification.objects.filter(
            phone_or_email=identifier,
            purpose=purpose,
            is_used=False
        ).update(is_used=True)

        OTPVerification.objects.create(
            phone_or_email=identifier,
            otp_code=otp_code,
            purpose=purpose,
            expires_at=expires_at
        )

        # Prominently print OTP to terminal with flush=True to guarantee visibility in VS Code
        import sys
        import logging
        otp_banner = (
            f"\n"
            f"====================================================\n"
            f"🔑 [SAFEHER OTP]: {otp_code}\n"
            f"📱 Phone/Email : {identifier}\n"
            f"⏰ Valid for   : 10 minutes\n"
            f"====================================================\n"
        )
        sys.stdout.write(otp_banner)
        sys.stdout.flush()
        sys.stderr.write(otp_banner)
        sys.stderr.flush()
        logging.getLogger("django").warning(otp_banner)

        response_payload = {
            'message': f'OTP sent successfully to {identifier}.',
            'expires_in_minutes': 10,
            'debug_otp': otp_code,
        }

        return Response(response_payload, status=status.HTTP_200_OK)


class VerifyOTPView(APIView):
    """
    Verify a 6-digit OTP. Marks the user's account as verified if purpose is 'verification'.
    """
    permission_classes = (AllowAny,)
    authentication_classes = ()

    def post(self, request):
        serializer = VerifyOTPSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        identifier = serializer.validated_data['phone_or_email'].strip()
        code = serializer.validated_data['otp_code']
        purpose = serializer.validated_data['purpose']

        otp_record = OTPVerification.objects.filter(
            phone_or_email=identifier,
            otp_code=code,
            purpose=purpose,
            is_used=False
        ).first()

        if not otp_record:
            return Response(
                {'detail': 'Invalid OTP or already used.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        if otp_record.is_expired:
            return Response(
                {'detail': 'This OTP has expired. Please request a new one.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        # Mark OTP as used
        otp_record.is_used = True
        otp_record.save()

        # If verification purpose, mark user as verified if they exist
        user = CustomUser.objects.filter(
            Q(phone_number=identifier) | Q(email__iexact=identifier)
        ).first()
        if user:
            user.is_verified = True
            user.save()

        return Response(
            {
                'message': 'OTP verified successfully.',
                'verified': True,
            },
            status=status.HTTP_200_OK
        )


class ResetPasswordView(APIView):
    """
    Reset user password using a verified OTP.
    """
    permission_classes = (AllowAny,)
    authentication_classes = ()

    def post(self, request):
        serializer = ResetPasswordSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        identifier = serializer.validated_data['phone_or_email'].strip()
        code = serializer.validated_data['otp_code']
        new_password = serializer.validated_data['new_password']

        otp_record = OTPVerification.objects.filter(
            phone_or_email=identifier,
            otp_code=code,
            purpose='password_reset',
            is_used=False
        ).first()

        if not otp_record or otp_record.is_expired:
            return Response(
                {'detail': 'Invalid or expired OTP for password reset.'},
                status=status.HTTP_400_BAD_REQUEST
            )

        user = CustomUser.objects.filter(
            Q(email__iexact=identifier) | Q(phone_number=identifier)
        ).first()

        if not user:
            return Response(
                {'detail': 'User with this email or phone number was not found.'},
                status=status.HTTP_404_NOT_FOUND
            )

        user.set_password(new_password)
        user.save()

        otp_record.is_used = True
        otp_record.save()

        return Response(
            {'message': 'Password has been reset successfully. You can now log in.'},
            status=status.HTTP_200_OK
        )


class ChangePasswordView(APIView):
    """
    Change password for an authenticated user.
    """
    permission_classes = (IsAuthenticated,)

    def post(self, request):
        serializer = ChangePasswordSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        user = request.user
        old_password = serializer.validated_data['old_password']
        new_password = serializer.validated_data['new_password']

        if not user.check_password(old_password):
            return Response(
                {'old_password': ['Current password is incorrect.']},
                status=status.HTTP_400_BAD_REQUEST
            )

        user.set_password(new_password)
        user.save()

        return Response(
            {'message': 'Password changed successfully.'},
            status=status.HTTP_200_OK
        )
