from django.db import models
from django.conf import settings


class Conversation(models.Model):
    """
    A conversation between two or more participants.
    """
    participants = models.ManyToManyField(
        settings.AUTH_USER_MODEL,
        related_name='conversations'
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-updated_at']

    def __str__(self):
        participant_names = ", ".join([u.username for u in self.participants.all()[:3]])
        return f"Conversation #{self.id} ({participant_names})"

    @property
    def last_message(self):
        return self.messages.order_by('-timestamp').first()

    def unread_count_for_user(self, user):
        return self.messages.filter(is_read=False).exclude(sender=user).count()


class Message(models.Model):
    """
    Individual message sent within a conversation.
    """
    MESSAGE_TYPES = (
        ('text', 'Text'),
        ('quick', 'Quick Message'),
        ('alert', 'Alert'),
    )

    conversation = models.ForeignKey(
        Conversation,
        on_delete=models.CASCADE,
        related_name='messages'
    )
    sender = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='sent_messages'
    )
    content = models.TextField()
    message_type = models.CharField(
        max_length=20,
        choices=MESSAGE_TYPES,
        default='text'
    )
    timestamp = models.DateTimeField(auto_now_add=True)
    is_read = models.BooleanField(default=False)

    class Meta:
        ordering = ['timestamp']

    def __str__(self):
        return f"[{self.message_type.upper()}] {self.sender.username}: {self.content[:30]}"


class QuickMessage(models.Model):
    """
    Quick message templates for one-tap emergency or status updates.
    Can be a system-wide pre-loaded default or a custom template created by a user.
    """
    DEFAULT_TEMPLATES = [
        "I'm safe ✅",
        "Come pick me up 🚗",
        "Call me NOW 📞",
        "In danger, need help 🆘",
        "Reached home safely 🏠",
    ]

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name='quick_templates'
    )
    title = models.CharField(max_length=100, blank=True, default='')
    content = models.CharField(max_length=255)
    is_default = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-is_default', 'created_at']

    def __str__(self):
        prefix = "[Default]" if self.is_default else f"[{self.user}]"
        return f"{prefix} {self.content}"

