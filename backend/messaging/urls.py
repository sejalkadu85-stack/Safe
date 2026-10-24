from django.urls import path
from .views import (
    ConversationListView,
    ConversationDetailMessagesView,
    SendMessageView,
    MarkMessageReadView,
    QuickMessageTemplateListCreateView,
    QuickMessageTemplateDetailView,
)

app_name = 'messaging'

urlpatterns = [
    # Quick Message Templates
    path('quick-templates/', QuickMessageTemplateListCreateView.as_view(), name='quick_templates'),
    path('quick-templates/<int:id>/', QuickMessageTemplateDetailView.as_view(), name='quick_template_detail'),

    # List conversations
    path('conversations/', ConversationListView.as_view(), name='conversations_list'),

    # Send a message
    path('send/', SendMessageView.as_view(), name='send_message'),

    # Mark message as read
    path('<int:id>/read/', MarkMessageReadView.as_view(), name='mark_message_read'),

    # Get messages for a specific conversation
    path('<int:conversation_id>/', ConversationDetailMessagesView.as_view(), name='conversation_messages'),
]
