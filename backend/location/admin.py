from django.contrib import admin
from .models import LocationLog, LocationShareSession, SafeZone, SafeZoneEvent


@admin.register(LocationLog)
class LocationLogAdmin(admin.ModelAdmin):
    list_display = (
        'user', 'latitude', 'longitude', 'accuracy',
        'speed', 'battery_level', 'timestamp'
    )
    list_filter = ('timestamp', 'is_mock')
    search_fields = ('user__username', 'user__email')
    ordering = ('-timestamp',)
    readonly_fields = ('timestamp',)


@admin.register(LocationShareSession)
class LocationShareSessionAdmin(admin.ModelAdmin):
    list_display = (
        'user', 'duration_minutes', 'started_at',
        'expires_at', 'is_active', 'share_with_all_trusted'
    )
    list_filter = ('is_active', 'share_with_all_trusted')
    search_fields = ('user__username', 'user__email', 'note')
    ordering = ('-started_at',)
    filter_horizontal = ('shared_with',)


@admin.register(SafeZone)
class SafeZoneAdmin(admin.ModelAdmin):
    list_display = (
        'name', 'user', 'radius_meters', 'notify_on_exit',
        'notify_on_entry', 'is_active', 'created_at'
    )
    list_filter = ('is_active', 'notify_on_exit', 'notify_on_entry')
    search_fields = ('name', 'user__username', 'user__email')
    ordering = ('-created_at',)


@admin.register(SafeZoneEvent)
class SafeZoneEventAdmin(admin.ModelAdmin):
    list_display = (
        'safe_zone', 'user', 'event_type', 'distance_meters',
        'notified_guardians', 'timestamp'
    )
    list_filter = ('event_type', 'notified_guardians', 'timestamp')
    search_fields = ('safe_zone__name', 'user__username')
    ordering = ('-timestamp',)
    readonly_fields = ('timestamp',)
