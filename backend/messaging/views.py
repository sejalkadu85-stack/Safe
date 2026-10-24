from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import IsAuthenticated
from django.shortcuts import get_object_or_404
from django.contrib.auth import get_user_model
from asgiref.sync import async_to_sync
from channels.layers import get_channel_layer

from django.db.models import Q
from .models import Conversation, Message, QuickMessage
from .serializers import (
    ConversationSerializer,
    MessageSerializer,
    SendMessageSerializer,
    QuickMessageSerializer,
)

User = get_user_model()


class ConversationListView(APIView):
    """
    GET /api/messages/conversations/
    List all conversations the authenticated user participates in.
    """
    permission_classes = [IsAuthenticated]

    def get(self, request):
        conversations = Conversation.objects.filter(
            participants=request.user
        ).prefetch_related('participants', 'messages').order_by('-updated_at')
        serializer = ConversationSerializer(conversations, many=True, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)


class ConversationDetailMessagesView(APIView):
    """
    GET /api/messages/<int:conversation_id>/
    Get all messages for a specific conversation.
    """
    permission_classes = [IsAuthenticated]

    def get(self, request, conversation_id):
        conversation = get_object_or_404(
            Conversation.objects.prefetch_related('participants'),
            id=conversation_id
        )

        # Check if the user is a participant
        if not conversation.participants.filter(id=request.user.id).exists():
            return Response(
                {"error": "You are not a participant in this conversation."},
                status=status.HTTP_403_FORBIDDEN
            )

        messages = conversation.messages.select_related('sender').order_by('timestamp')
        
        # Optional query params for pagination
        limit = request.query_params.get('limit')
        if limit and limit.isdigit():
            messages = messages[max(0, len(messages) - int(limit)):]

        serializer = MessageSerializer(messages, many=True, context={'request': request})
        return Response({
            "conversation_id": conversation.id,
            "participants": [
                {"id": p.id, "username": p.username, "role": p.role}
                for p in conversation.participants.all()
            ],
            "messages": serializer.data
        }, status=status.HTTP_200_OK)


class SendMessageView(APIView):
    """
    POST /api/messages/send/
    Send a message within an existing conversation or start a new conversation with a recipient.
    """
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = SendMessageSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        conversation_id = serializer.validated_data.get('conversation_id')
        recipient_id = serializer.validated_data.get('recipient_id')
        content = serializer.validated_data.get('content')
        message_type = serializer.validated_data.get('message_type', 'text')

        conversation = None

        if conversation_id:
            conversation = get_object_or_404(Conversation, id=conversation_id)
            if not conversation.participants.filter(id=request.user.id).exists():
                return Response(
                    {"error": "You are not a participant in this conversation."},
                    status=status.HTTP_403_FORBIDDEN
                )
        elif recipient_id:
            if recipient_id == request.user.id:
                return Response(
                    {"error": "You cannot start a conversation with yourself."},
                    status=status.HTTP_400_BAD_REQUEST
                )

            recipient = get_object_or_404(User, id=recipient_id)

            # Check if a 1-on-1 conversation already exists between these two users
            conversation = Conversation.objects.filter(
                participants=request.user
            ).filter(
                participants=recipient
            ).distinct().first()

            if not conversation:
                conversation = Conversation.objects.create()
                conversation.participants.add(request.user, recipient)

        # Create the message
        message = Message.objects.create(
            conversation=conversation,
            sender=request.user,
            content=content,
            message_type=message_type,
        )

        # Touch conversation updated_at
        conversation.save(update_fields=['updated_at'])

        # Broadcast via Django Channels layer to WebSocket group
        try:
            channel_layer = get_channel_layer()
            if channel_layer:
                group_name = f"chat_{conversation.id}"
                async_to_sync(channel_layer.group_send)(
                    group_name,
                    {
                        "type": "chat_message",
                        "message_id": message.id,
                        "conversation_id": conversation.id,
                        "sender_id": request.user.id,
                        "sender_username": request.user.username,
                        "content": message.content,
                        "message_type": message.message_type,
                        "timestamp": message.timestamp.isoformat(),
                        "is_read": message.is_read,
                    }
                )
        except Exception:
            pass  # Fail gracefully if channel layer is not running

        msg_serializer = MessageSerializer(message, context={'request': request})
        return Response(msg_serializer.data, status=status.HTTP_201_CREATED)


class MarkMessageReadView(APIView):
    """
    PUT /api/messages/<int:id>/read/
    Mark a message as read.
    """
    permission_classes = [IsAuthenticated]

    def put(self, request, id):
        message = get_object_or_404(Message.objects.select_related('conversation'), id=id)

        # Ensure user is part of the conversation
        if not message.conversation.participants.filter(id=request.user.id).exists():
            return Response(
                {"error": "You are not a participant in this conversation."},
                status=status.HTTP_403_FORBIDDEN
            )

        if not message.is_read and message.sender_id != request.user.id:
            message.is_read = True
            message.save(update_fields=['is_read'])

            # Broadcast read receipt via Channels
            try:
                channel_layer = get_channel_layer()
                if channel_layer:
                    group_name = f"chat_{message.conversation_id}"
                    async_to_sync(channel_layer.group_send)(
                        group_name,
                        {
                            "type": "message_read",
                            "message_id": message.id,
                            "conversation_id": message.conversation_id,
                            "read_by_id": request.user.id,
                        }
                    )
            except Exception:
                pass

        return Response({
            "message": "Message marked as read",
            "id": message.id,
            "is_read": message.is_read
        }, status=status.HTTP_200_OK)

    def patch(self, request, id):
        return self.put(request, id)


class QuickMessageTemplateListCreateView(APIView):
    """
    GET /api/messages/quick-templates/
    List default pre-loaded templates and user's custom templates.

    POST /api/messages/quick-templates/
    Create a custom quick message template for the authenticated user.
    """
    permission_classes = [IsAuthenticated]

    # Pre-loaded default templates
    DEFAULT_TEMPLATES = [
        {"content": "I'm safe ✅", "title": "Safe"},
        {"content": "Come pick me up 🚗", "title": "Pick Up"},
        {"content": "Call me NOW 📞", "title": "Call Now"},
        {"content": "In danger, need help 🆘", "title": "Need Help"},
        {"content": "Reached home safely 🏠", "title": "Reached Home"},
    ]

    def _ensure_default_templates(self):
        """Seed default templates if not already in the database."""
        for item in self.DEFAULT_TEMPLATES:
            QuickMessage.objects.get_or_create(
                content=item["content"],
                is_default=True,
                defaults={"title": item["title"]}
            )

    def get(self, request):
        self._ensure_default_templates()
        templates = QuickMessage.objects.filter(
            Q(is_default=True) | Q(user=request.user)
        ).order_by('-is_default', 'created_at')

        serializer = QuickMessageSerializer(templates, many=True, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request):
        content = request.data.get('content', '').strip()
        title = request.data.get('title', '').strip()

        if not content:
            return Response(
                {"content": ["This field is required and cannot be blank."]},
                status=status.HTTP_400_BAD_REQUEST
            )

        if len(content) > 255:
            return Response(
                {"content": ["Ensure this field has no more than 255 characters."]},
                status=status.HTTP_400_BAD_REQUEST
            )

        template = QuickMessage.objects.create(
            user=request.user,
            content=content,
            title=title,
            is_default=False
        )

        serializer = QuickMessageSerializer(template, context={'request': request})
        return Response(serializer.data, status=status.HTTP_201_CREATED)


class QuickMessageTemplateDetailView(APIView):
    """
    DELETE /api/messages/quick-templates/<int:id>/
    Delete a custom quick message template created by the user.
    """
    permission_classes = [IsAuthenticated]

    def delete(self, request, id):
        template = get_object_or_404(QuickMessage, id=id)

        if template.is_default:
            return Response(
                {"error": "System default templates cannot be deleted."},
                status=status.HTTP_403_FORBIDDEN
            )

        if template.user_id != request.user.id:
            return Response(
                {"error": "You do not have permission to delete this template."},
                status=status.HTTP_403_FORBIDDEN
            )

        template.delete()
        return Response(
            {"message": "Quick message template deleted successfully."},
            status=status.HTTP_200_OK
        )

