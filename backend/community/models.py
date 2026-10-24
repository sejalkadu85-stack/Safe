from django.db import models
from django.conf import settings
from decimal import Decimal


class Volunteer(models.Model):
    """
    Community volunteer profile linked to a CustomUser.
    Volunteers can respond to nearby SOS alerts and walk companion requests.
    """
    user = models.OneToOneField(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='volunteer_profile'
    )
    is_verified = models.BooleanField(
        default=False,
        help_text="Whether this volunteer has been vetted and background-verified by admin"
    )
    is_available = models.BooleanField(
        default=True,
        help_text="Whether the volunteer is currently active and ready to receive requests"
    )
    phone_number = models.CharField(max_length=20, blank=True)
    skills = models.CharField(
        max_length=255,
        blank=True,
        default='First Aid, Self Defense, Quick Responder'
    )
    latitude = models.DecimalField(
        max_digits=9,
        decimal_places=6,
        null=True,
        blank=True
    )
    longitude = models.DecimalField(
        max_digits=9,
        decimal_places=6,
        null=True,
        blank=True
    )
    address = models.CharField(max_length=255, blank=True)
    total_helps = models.IntegerField(default=0)
    rating = models.DecimalField(max_digits=3, decimal_places=2, default=Decimal('5.00'))
    rating_count = models.IntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-rating', '-total_helps']

    def __str__(self):
        return f"Volunteer: {self.user.username} (Rating: {self.rating}★, Helps: {self.total_helps})"

    def update_rating(self, new_rating: int):
        """Updates volunteer rolling rating with a new review."""
        if 1 <= new_rating <= 5:
            current_total = float(self.rating) * self.rating_count
            self.rating_count += 1
            new_avg = (current_total + new_rating) / self.rating_count
            self.rating = Decimal(str(round(new_avg, 2)))
            self.save(update_fields=['rating', 'rating_count', 'updated_at'])


class VolunteerRequest(models.Model):
    """
    Direct or broadcast assistance request from a user to community volunteers.
    """
    STATUS_CHOICES = (
        ('pending', 'Pending'),
        ('accepted', 'Accepted'),
        ('on_the_way', 'On the Way'),
        ('arrived', 'Arrived'),
        ('completed', 'Completed'),
        ('declined', 'Declined'),
        ('cancelled', 'Cancelled'),
    )

    requester = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name='help_requests'
    )
    volunteer = models.ForeignKey(
        Volunteer,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='received_requests'
    )
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    emergency_type = models.CharField(max_length=100, default='Walk Companion / Immediate Help')
    message = models.TextField(blank=True)
    latitude = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    longitude = models.DecimalField(max_digits=9, decimal_places=6, null=True, blank=True)
    address = models.CharField(max_length=255, blank=True)
    rating = models.IntegerField(null=True, blank=True)
    feedback = models.TextField(blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    resolved_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ['-created_at']

    def __str__(self):
        vol_name = self.volunteer.user.username if self.volunteer else 'Unassigned'
        return f"HelpRequest #{self.id} by {self.requester.username} -> {vol_name} ({self.status})"


class Helpline(models.Model):
    """
    Emergency and women safety helpline directory numbers.
    """
    CATEGORY_CHOICES = (
        ('emergency', 'National Emergency'),
        ('women', 'Women Safety'),
        ('police', 'Police'),
        ('medical', 'Medical / Ambulance'),
        ('child', 'Child Safety'),
        ('cyber', 'Cyber Crime'),
        ('disaster', 'Disaster / Emergency'),
        ('other', 'Other Helplines'),
    )

    name = models.CharField(max_length=120)
    number = models.CharField(max_length=30)
    category = models.CharField(max_length=30, choices=CATEGORY_CHOICES, default='women')
    description = models.TextField(blank=True)
    city = models.CharField(max_length=100, blank=True)
    state = models.CharField(max_length=100, blank=True)
    country = models.CharField(max_length=100, default='India')
    is_national = models.BooleanField(default=True)
    is_toll_free = models.BooleanField(default=True)
    priority = models.IntegerField(default=10)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['priority', 'name']

    def __str__(self):
        return f"{self.name} ({self.number}) - {self.category}"
