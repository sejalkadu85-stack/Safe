import json
from channels.generic.websocket import AsyncWebsocketConsumer
from channels.db import database_sync_to_async
from django.contrib.auth import get_user_model
from .models import Conversation, Message

User = get_user_model()


class ChatConsumer(AsyncWebsocketConsumer):
    """
    WebSocket consumer for real-time conversation messaging.
    URI: ws://<host>/api/ws/chat/<conversation_id>/
    """

    async def connect(self):
        self.conversation_id = self.scope['url_route']['kwargs']['conversation_id']
        self.room_group_name = f"chat_{self.conversation_id}"
        self.user = self.scope.get('user')

        # Verify user is authenticated
        if not self.user or not self.user.is_authenticated:
            await self.close(code=4001)
            return

        # Verify user is a participant of this conversation
        is_participant = await self.check_participant(self.conversation_id, self.user.id)
        if not is_participant:
            await self.close(code=4003)
            return

        # Join conversation room group
        await self.channel_layer.group_add(
            self.room_group_name,
            self.channel_name
        )

        await self.accept()

    async def disconnect(self, close_code):
        # Leave room group
        if hasattr(self, 'room_group_name'):
            await self.channel_layer.group_discard(
                self.room_group_name,
                self.channel_name
            )

    async def receive(self, text_data):
        """
        Receive message from WebSocket client.
        Supported events: 'message', 'typing', 'read'
        """
        try:
            data = json.loads(text_data)
        except json.JSONDecodeError:
            await self.send(text_data=json.dumps({"error": "Invalid JSON"}))
            return

        event_type = data.get('type', 'message')

        if event_type == 'message':
            content = data.get('content', '').strip()
            message_type = data.get('message_type', 'text')

            if not content:
                return

            # Save message to database
            message = await self.save_message(
                conversation_id=self.conversation_id,
                user_id=self.user.id,
                content=content,
                message_type=message_type
            )

            # Broadcast message to room group
            await self.channel_layer.group_send(
                self.room_group_name,
                {
                    'type': 'chat_message',
                    'message_id': message['id'],
                    'conversation_id': self.conversation_id,
                    'sender_id': self.user.id,
                    'sender_username': self.user.username,
                    'content': message['content'],
                    'message_type': message['message_type'],
                    'timestamp': message['timestamp'],
                    'is_read': False,
                }
            )

        elif event_type == 'typing':
            is_typing = data.get('is_typing', True)
            await self.channel_layer.group_send(
                self.room_group_name,
                {
                    'type': 'typing_indicator',
                    'sender_id': self.user.id,
                    'sender_username': self.user.username,
                    'is_typing': is_typing,
                }
            )

        elif event_type == 'read':
            message_id = data.get('message_id')
            if message_id:
                await self.mark_read(message_id, self.user.id)
                await self.channel_layer.group_send(
                    self.room_group_name,
                    {
                        'type': 'message_read',
                        'message_id': message_id,
                        'conversation_id': self.conversation_id,
                        'read_by_id': self.user.id,
                    }
                )

    # Handlers for events sent from channel layer
    async def chat_message(self, event):
        """Receive chat message from room group and forward to WebSocket."""
        await self.send(text_data=json.dumps({
            'event': 'new_message',
            'data': event
        }))

    async def typing_indicator(self, event):
        """Receive typing indicator from room group and forward to WebSocket."""
        # Don't echo typing back to sender
        if event.get('sender_id') != self.user.id:
            await self.send(text_data=json.dumps({
                'event': 'typing',
                'data': event
            }))

    async def message_read(self, event):
        """Receive read receipt from room group and forward to WebSocket."""
        await self.send(text_data=json.dumps({
            'event': 'message_read',
            'data': event
        }))

    # Database helpers
    @database_sync_to_async
    def check_participant(self, conversation_id, user_id):
        return Conversation.objects.filter(
            id=conversation_id,
            participants__id=user_id
        ).exists()

    @database_sync_to_async
    def save_message(self, conversation_id, user_id, content, message_type):
        conversation = Conversation.objects.get(id=conversation_id)
        sender = User.objects.get(id=user_id)
        msg = Message.objects.create(
            conversation=conversation,
            sender=sender,
            content=content,
            message_type=message_type
        )
        conversation.save(update_fields=['updated_at'])
        return {
            'id': msg.id,
            'content': msg.content,
            'message_type': msg.message_type,
            'timestamp': msg.timestamp.isoformat(),
        }

    @database_sync_to_async
    def mark_read(self, message_id, user_id):
        Message.objects.filter(
            id=message_id,
            conversation_id=self.conversation_id
        ).exclude(sender_id=user_id).update(is_read=True)
