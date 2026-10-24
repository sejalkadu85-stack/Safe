from django.contrib import admin
from .models import Conversation, Message, QuickMessage


class MessageInline(admin.TabularInline):
    model = Message
    extra = 0
    readonly_fields = ('sender', 'content', 'message_type', 'timestamp', 'is_read')
    can_delete = True


@admin.register(Conversation)
class ConversationAdmin(admin.ModelAdmin):
    list_display = ('id', 'participant_list', 'created_at', 'updated_at')
    search_fields = ('participants__username', 'participants__email')
    inlines = [MessageInline]

    def participant_list(self, obj):
        return ", ".join([u.username for u in obj.participants.all()])
    participant_list.short_description = 'Participants'


@admin.register(Message)
class MessageAdmin(admin.ModelAdmin):
    list_display = ('id', 'conversation', 'sender', 'message_type', 'is_read', 'timestamp')
    list_filter = ('message_type', 'is_read', 'timestamp')
    search_fields = ('sender__username', 'content')


@admin.register(QuickMessage)
class QuickMessageAdmin(admin.ModelAdmin):
    list_display = ('id', 'content', 'user', 'is_default', 'created_at')
    list_filter = ('is_default', 'created_at')
    search_fields = ('content', 'title', 'user__username')

