from django.urls import path
from rest_framework_simplejwt.views import TokenRefreshView
from .views import (
    RegisterView,
    LoginView,
    LogoutView,
    CurrentUserView,
    ProfileView,
    ProfilePhotoUploadView,
    UserDetailByIdView,
    SendOTPView,
    VerifyOTPView,
    ResetPasswordView,
    ChangePasswordView,
)

app_name = 'accounts'

urlpatterns = [
    # Registration & Authentication
    path('register/', RegisterView.as_view(), name='register'),
    path('login/', LoginView.as_view(), name='login'),
    path('token/refresh/', TokenRefreshView.as_view(), name='token_refresh'),
    path('logout/', LogoutView.as_view(), name='logout'),

    # Profile Management
    path('me/', CurrentUserView.as_view(), name='current_user'),
    path('profile/', ProfileView.as_view(), name='profile'),
    path('profile/photo/', ProfilePhotoUploadView.as_view(), name='profile_photo'),
    path('profile/<int:id>/', UserDetailByIdView.as_view(), name='user_detail'),

    # OTP Operations (Verification & 2FA)
    path('otp/send/', SendOTPView.as_view(), name='send_otp'),
    path('otp/verify/', VerifyOTPView.as_view(), name='verify_otp'),

    # Password Management
    path('password/reset/', ResetPasswordView.as_view(), name='reset_password'),
    path('password/change/', ChangePasswordView.as_view(), name='change_password'),
]
