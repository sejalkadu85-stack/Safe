from django.contrib import admin
from .models import TrustedContact, SOSAlert, AlertRecipient


class AlertRecipientInline(admin.TabularInline):
    model = AlertRecipient
    extra = 0
    readonly_fields = ('recipient', 'status', 'notified_at', 'acknowledged_at', 'guardian_note')


@admin.register(TrustedContact)
class TrustedContactAdmin(admin.ModelAdmin):
    list_display = (
        'owner', 'contact_user', 'relationship',
        'status', 'notify_via_sms', 'created_at',
    )
    list_filter = ('status', 'relationship', 'notify_via_sms')
    search_fields = (
        'owner__username', 'owner__email',
        'contact_user__username', 'contact_user__email',
    )
    ordering = ('-created_at',)
    readonly_fields = ('created_at', 'updated_at')


@admin.register(SOSAlert)
class SOSAlertAdmin(admin.ModelAdmin):
    list_display = (
        'id', 'sender', 'status', 'trigger_type',
        'latitude', 'longitude', 'battery_level', 'created_at'
    )
    list_filter = ('status', 'trigger_type', 'created_at')
    search_fields = ('sender__username', 'sender__email', 'address', 'message')
    ordering = ('-created_at',)
    readonly_fields = ('created_at', 'resolved_at')
    inlines = [AlertRecipientInline]


@admin.register(AlertRecipient)
class AlertRecipientAdmin(admin.ModelAdmin):
    list_display = ('alert', 'recipient', 'status', 'notified_at', 'acknowledged_at')
    list_filter = ('status', 'notified_at')
    search_fields = ('recipient__username', 'alert__sender__username')
    ordering = ('-notified_at',)
