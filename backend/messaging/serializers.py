from rest_framework import serializers
from django.contrib.auth import get_user_model
from .models import Conversation, Message, QuickMessage
from accounts.serializers import UserSerializer

User = get_user_model()


class MessageSerializer(serializers.ModelSerializer):
    sender = UserSerializer(read_only=True)
    is_me = serializers.SerializerMethodField()

    class Meta:
        model = Message
        fields = (
            'id',
            'conversation',
            'sender',
            'content',
            'message_type',
            'timestamp',
            'is_read',
            'is_me',
        )
        read_only_fields = ('id', 'timestamp')

    def get_is_me(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return obj.sender_id == request.user.id
        return False


class ConversationSerializer(serializers.ModelSerializer):
    participants = UserSerializer(many=True, read_only=True)
    last_message = serializers.SerializerMethodField()
    unread_count = serializers.SerializerMethodField()

    class Meta:
        model = Conversation
        fields = (
            'id',
            'participants',
            'last_message',
            'unread_count',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'created_at', 'updated_at')

    def get_last_message(self, obj):
        last_msg = obj.last_message
        if last_msg:
            return MessageSerializer(last_msg, context=self.context).data
        return None

    def get_unread_count(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return obj.unread_count_for_user(request.user)
        return 0


class SendMessageSerializer(serializers.Serializer):
    conversation_id = serializers.IntegerField(required=False, allow_null=True)
    recipient_id = serializers.IntegerField(required=False, allow_null=True)
    content = serializers.CharField(required=True, allow_blank=False)
    message_type = serializers.ChoiceField(
        choices=Message.MESSAGE_TYPES,
        default='text'
    )

    def validate(self, attrs):
        conversation_id = attrs.get('conversation_id')
        recipient_id = attrs.get('recipient_id')

        if not conversation_id and not recipient_id:
            raise serializers.ValidationError(
                "Either 'conversation_id' or 'recipient_id' must be provided."
            )
        return attrs


class QuickMessageSerializer(serializers.ModelSerializer):
    is_custom = serializers.SerializerMethodField()

    class Meta:
        model = QuickMessage
        fields = (
            'id',
            'title',
            'content',
            'is_default',
            'is_custom',
            'created_at',
        )
        read_only_fields = ('id', 'is_default', 'is_custom', 'created_at')

    def get_is_custom(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return obj.user_id == request.user.id
        return not obj.is_default

