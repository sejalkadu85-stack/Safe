from django.contrib import admin
from .models import DeviceToken, Notification, CheckInSchedule


@admin.register(DeviceToken)
class DeviceTokenAdmin(admin.ModelAdmin):
    list_display = ('id', 'user', 'device_type', 'device_name', 'is_active', 'updated_at')
    list_filter = ('device_type', 'is_active')
    search_fields = ('user__username', 'token', 'device_name')


@admin.register(Notification)
class NotificationAdmin(admin.ModelAdmin):
    list_display = ('id', 'recipient', 'notification_type', 'title', 'is_read', 'created_at')
    list_filter = ('notification_type', 'is_read', 'created_at')
    search_fields = ('recipient__username', 'title', 'body')


@admin.register(CheckInSchedule)
class CheckInScheduleAdmin(admin.ModelAdmin):
    list_display = ('id', 'user', 'title', 'scheduled_time', 'repeat_interval', 'is_active', 'is_sent')
    list_filter = ('repeat_interval', 'is_active', 'is_sent')
    search_fields = ('user__username', 'title', 'message')
