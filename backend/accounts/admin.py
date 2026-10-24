from django.contrib import admin
from django.contrib.auth.admin import UserAdmin
from .models import CustomUser, UserProfile


class UserProfileInline(admin.StackedInline):
    model = UserProfile
    can_delete = False
    verbose_name_plural = 'Profile Details'
    fk_name = 'user'


@admin.register(CustomUser)
class CustomUserAdmin(UserAdmin):
    inlines = (UserProfileInline,)
    list_display = (
        'username',
        'email',
        'phone_number',
        'role',
        'is_verified',
        'is_staff',
        'date_joined',
    )
    list_filter = ('role', 'is_verified', 'is_staff', 'is_active')
    search_fields = ('username', 'email', 'phone_number')
    ordering = ('-date_joined',)

    fieldsets = UserAdmin.fieldsets + (
        (
            'Safety System Roles & Contact',
            {
                'fields': ('role', 'phone_number', 'profile_photo', 'is_verified'),
            },
        ),
    )
    add_fieldsets = UserAdmin.add_fieldsets + (
        (
            'Safety System Roles & Contact',
            {
                'fields': ('role', 'email', 'phone_number'),
            },
        ),
    )


@admin.register(UserProfile)
class UserProfileAdmin(admin.ModelAdmin):
    list_display = ('user', 'blood_group', 'city', 'country', 'created_at')
    search_fields = ('user__username', 'user__email', 'city')
    list_filter = ('blood_group', 'city')


from .models import OTPVerification

@admin.register(OTPVerification)
class OTPVerificationAdmin(admin.ModelAdmin):
    list_display = ('phone_or_email', 'otp_code', 'purpose', 'is_used', 'created_at', 'expires_at')
    list_filter = ('purpose', 'is_used')
    search_fields = ('phone_or_email', 'otp_code')
    ordering = ('-created_at',)
