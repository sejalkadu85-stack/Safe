from django.contrib import admin
from .models import Volunteer, VolunteerRequest, Helpline


@admin.register(Volunteer)
class VolunteerAdmin(admin.ModelAdmin):
    list_display = ('user', 'phone_number', 'is_verified', 'is_available', 'total_helps', 'rating', 'created_at')
    list_filter = ('is_verified', 'is_available', 'created_at')
    search_fields = ('user__username', 'user__email', 'phone_number', 'skills')
    actions = ['verify_volunteers', 'unverify_volunteers']

    def verify_volunteers(self, request, queryset):
        queryset.update(is_verified=True)
    verify_volunteers.short_description = "Mark selected volunteers as Verified"

    def unverify_volunteers(self, request, queryset):
        queryset.update(is_verified=False)
    unverify_volunteers.short_description = "Mark selected volunteers as Unverified"


@admin.register(VolunteerRequest)
class VolunteerRequestAdmin(admin.ModelAdmin):
    list_display = ('id', 'requester', 'volunteer', 'status', 'emergency_type', 'rating', 'created_at')
    list_filter = ('status', 'emergency_type', 'created_at')
    search_fields = ('requester__username', 'volunteer__user__username', 'message')


@admin.register(Helpline)
class HelplineAdmin(admin.ModelAdmin):
    list_display = ('name', 'number', 'category', 'city', 'state', 'is_national', 'priority')
    list_filter = ('category', 'is_national', 'state')
    search_fields = ('name', 'number', 'description')
