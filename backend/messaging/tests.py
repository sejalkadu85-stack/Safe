from django.contrib.auth import get_user_model
from django.urls import reverse
from rest_framework import status
from rest_framework.test import APITestCase
from rest_framework_simplejwt.tokens import RefreshToken

from messaging.models import Conversation, Message

User = get_user_model()


class MessagingAPITests(APITestCase):

    def setUp(self):
        # Create users
        self.user1 = User.objects.create_user(
            username='alice',
            email='alice@example.com',
            password='password123',
            role='woman'
        )
        self.user2 = User.objects.create_user(
            username='bob',
            email='bob@example.com',
            password='password123',
            role='guardian'
        )
        self.user3 = User.objects.create_user(
            username='charlie',
            email='charlie@example.com',
            password='password123',
            role='volunteer'
        )

        # JWT tokens
        token1 = RefreshToken.for_user(self.user1)
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token1.access_token}')

        # Pre-create conversation between user1 and user2
        self.conversation = Conversation.objects.create()
        self.conversation.participants.add(self.user1, self.user2)

        self.initial_message = Message.objects.create(
            conversation=self.conversation,
            sender=self.user2,
            content="Hello Alice! Are you safe?",
            message_type="quick",
            is_read=False
        )

    def test_list_conversations(self):
        url = reverse('messaging:conversations_list')
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)
        self.assertEqual(response.data[0]['id'], self.conversation.id)
        self.assertEqual(response.data[0]['unread_count'], 1)
        self.assertIsNotNone(response.data[0]['last_message'])

    def test_get_conversation_messages(self):
        url = reverse('messaging:conversation_messages', kwargs={'conversation_id': self.conversation.id})
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data['conversation_id'], self.conversation.id)
        self.assertEqual(len(response.data['messages']), 1)
        self.assertEqual(response.data['messages'][0]['content'], "Hello Alice! Are you safe?")
        self.assertFalse(response.data['messages'][0]['is_me'])

    def test_send_message_in_existing_conversation(self):
        url = reverse('messaging:send_message')
        payload = {
            'conversation_id': self.conversation.id,
            'content': "Yes, I am heading home now.",
            'message_type': 'text'
        }
        response = self.client.post(url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['content'], "Yes, I am heading home now.")
        self.assertTrue(response.data['is_me'])

        # Verify DB
        self.assertEqual(Message.objects.filter(conversation=self.conversation).count(), 2)

    def test_send_message_creates_new_conversation(self):
        url = reverse('messaging:send_message')
        payload = {
            'recipient_id': self.user3.id,
            'content': "Hi Charlie, thanks for volunteering!",
            'message_type': 'text'
        }
        response = self.client.post(url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)

        # Check that a conversation between alice and charlie was created
        new_conv = Conversation.objects.filter(participants=self.user1).filter(participants=self.user3).first()
        self.assertIsNotNone(new_conv)
        self.assertEqual(response.data['conversation'], new_conv.id)

    def test_mark_message_read(self):
        url = reverse('messaging:mark_message_read', kwargs={'id': self.initial_message.id})
        response = self.client.put(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertTrue(response.data['is_read'])

        self.initial_message.refresh_from_db()
        self.assertTrue(self.initial_message.is_read)

    def test_unauthorized_user_cannot_access_conversation(self):
        # Authenticate as user3 (not in conversation)
        token3 = RefreshToken.for_user(self.user3)
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {token3.access_token}')

        url = reverse('messaging:conversation_messages', kwargs={'conversation_id': self.conversation.id})
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_403_FORBIDDEN)

    def test_get_quick_templates_seeds_and_returns_defaults(self):
        url = reverse('messaging:quick_templates')
        response = self.client.get(url)
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        contents = [t['content'] for t in response.data]
        self.assertIn("I'm safe ✅", contents)
        self.assertIn("Come pick me up 🚗", contents)
        self.assertIn("Call me NOW 📞", contents)
        self.assertIn("In danger, need help 🆘", contents)
        self.assertIn("Reached home safely 🏠", contents)
        self.assertEqual(len(response.data), 5)

    def test_create_custom_quick_message_template(self):
        url = reverse('messaging:quick_templates')
        payload = {
            'content': "At metro station, waiting for train 🚇",
            'title': "Metro Update"
        }
        response = self.client.post(url, payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['content'], "At metro station, waiting for train 🚇")
        self.assertTrue(response.data['is_custom'])
        self.assertFalse(response.data['is_default'])

        # Listing templates now includes both default and custom
        list_response = self.client.get(url)
        self.assertEqual(len(list_response.data), 6)

    def test_delete_custom_template(self):
        # Create a custom template first
        create_url = reverse('messaging:quick_templates')
        create_resp = self.client.post(create_url, {'content': 'Battery dying soon 🪫'}, format='json')
        template_id = create_resp.data['id']

        # Delete it
        detail_url = reverse('messaging:quick_template_detail', kwargs={'id': template_id})
        del_resp = self.client.delete(detail_url)
        self.assertEqual(del_resp.status_code, status.HTTP_200_OK)

    def test_cannot_delete_system_default_template(self):
        # Seed defaults
        self.client.get(reverse('messaging:quick_templates'))
        from messaging.models import QuickMessage
        default_tpl = QuickMessage.objects.filter(is_default=True).first()

        detail_url = reverse('messaging:quick_template_detail', kwargs={'id': default_tpl.id})
        del_resp = self.client.delete(detail_url)
        self.assertEqual(del_resp.status_code, status.HTTP_403_FORBIDDEN)



from channels.testing import WebsocketCommunicator
from women_safety.asgi import application
from django.test import TransactionTestCase


class WebSocketChatConsumerTests(TransactionTestCase):

    def setUp(self):
        self.user1 = User.objects.create_user(
            username='ws_alice',
            email='ws_alice@example.com',
            password='password123',
            role='woman'
        )
        self.user2 = User.objects.create_user(
            username='ws_bob',
            email='ws_bob@example.com',
            password='password123',
            role='guardian'
        )
        self.conversation = Conversation.objects.create()
        self.conversation.participants.add(self.user1, self.user2)
        self.token = str(RefreshToken.for_user(self.user1).access_token)

    async def test_websocket_chat_flow(self):
        communicator = WebsocketCommunicator(
            application,
            f"/api/ws/chat/{self.conversation.id}/?token={self.token}"
        )
        connected, _ = await communicator.connect()
        self.assertTrue(connected)

        # Send a message via WebSocket
        await communicator.send_json_to({
            "type": "message",
            "content": "Emergency! Need assistance.",
            "message_type": "alert"
        })

        # Receive broadcast message from WebSocket
        response = await communicator.receive_json_from()
        self.assertEqual(response['event'], 'new_message')
        self.assertEqual(response['data']['content'], "Emergency! Need assistance.")
        self.assertEqual(response['data']['message_type'], "alert")

        await communicator.disconnect()

