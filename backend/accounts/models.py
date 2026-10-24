from django.contrib.auth.models import AbstractUser
from django.db import models
from django.db.models.signals import post_save
from django.dispatch import receiver


class CustomUser(AbstractUser):
    """
    Custom User Model for Women Safety System supporting 3 primary roles
    (Woman, Guardian, Admin) + Community Volunteer role.
    """
    class Role(models.TextChoices):
        WOMAN = 'woman', 'Woman / Individual'
        GUARDIAN = 'guardian', 'Guardian / Trusted Contact'
        FRIEND = 'friend', 'Friend / Emergency Contact'
        VOLUNTEER = 'volunteer', 'Community Volunteer'
        ADMIN = 'admin', 'Administrator'

    class VerificationStatus(models.TextChoices):
        PENDING = 'pending', 'Pending Review'
        APPROVED = 'approved', 'Approved'
        REJECTED = 'rejected', 'Rejected'

    email = models.EmailField(unique=True, db_index=True)
    phone_number = models.CharField(
        max_length=20,
        unique=True,
        null=True,
        blank=True,
        db_index=True,
        help_text="Phone number with country code, e.g. +919876543210"
    )
    role = models.CharField(
        max_length=20,
        choices=Role.choices,
        default=Role.WOMAN,
        db_index=True
    )
    profile_photo = models.ImageField(
        upload_to='profiles/',
        null=True,
        blank=True
    )
    id_proof_type = models.CharField(
        max_length=50,
        blank=True,
        null=True,
        choices=[
            ('aadhaar', 'Aadhaar Card'),
            ('driving_license', 'Driving License'),
            ('voter_id', 'Voter ID'),
            ('passport', 'Passport'),
            ('other', 'Other Government ID'),
        ],
        help_text="Type of official government identity proof uploaded."
    )
    id_proof_document = models.FileField(
        upload_to='id_proofs/',
        null=True,
        blank=True,
        help_text="Uploaded ID document (PDF/Image) for verification."
    )
    verification_status = models.CharField(
        max_length=20,
        choices=VerificationStatus.choices,
        default=VerificationStatus.APPROVED,
        db_index=True,
        help_text="Status of account ID verification by admin."
    )
    verification_rejection_reason = models.TextField(
        blank=True,
        null=True,
        help_text="Reason provided by administrator if verification is rejected."
    )
    is_verified = models.BooleanField(
        default=False,
        help_text="Designates whether the user's phone/email has been verified."
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    REQUIRED_FIELDS = ['email']

    class Meta:
        verbose_name = 'User'
        verbose_name_plural = 'Users'
        ordering = ['-date_joined']

    def __str__(self):
        return f"{self.username} ({self.get_role_display()})"

    @property
    def is_woman(self):
        return self.role == self.Role.WOMAN

    @property
    def is_guardian(self):
        return self.role == self.Role.GUARDIAN

    @property
    def is_volunteer(self):
        return self.role == self.Role.VOLUNTEER

    @property
    def is_admin_role(self):
        return self.role == self.Role.ADMIN or self.is_staff or self.is_superuser


class UserProfile(models.Model):
    """
    Extended user profile for additional safety & emergency details.
    """
    BLOOD_GROUP_CHOICES = [
        ('A+', 'A+'), ('A-', 'A-'),
        ('B+', 'B+'), ('B-', 'B-'),
        ('AB+', 'AB+'), ('AB-', 'AB-'),
        ('O+', 'O+'), ('O-', 'O-'),
        ('UNKNOWN', 'Unknown'),
    ]

    user = models.OneToOneField(
        CustomUser,
        on_delete=models.CASCADE,
        related_name='profile'
    )
    date_of_birth = models.DateField(null=True, blank=True)
    blood_group = models.CharField(
        max_length=10,
        choices=BLOOD_GROUP_CHOICES,
        default='UNKNOWN',
        blank=True
    )
    address = models.TextField(blank=True)
    city = models.CharField(max_length=100, blank=True)
    country = models.CharField(max_length=100, default='India')
    emergency_notes = models.TextField(
        blank=True,
        help_text="Allergies, medical conditions, or critical instructions for emergency contacts/first responders."
    )
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"Profile for {self.user.username}"


# Signals to auto-create and save UserProfile when a CustomUser is created
@receiver(post_save, sender=CustomUser)
def create_user_profile(sender, instance, created, **kwargs):
    if created:
        UserProfile.objects.create(user=instance)


@receiver(post_save, sender=CustomUser)
def save_user_profile(sender, instance, **kwargs):
    if hasattr(instance, 'profile'):
        instance.profile.save()


class OTPVerification(models.Model):
    """
    Stores 6-digit OTP codes for phone/email verification and password resets.
    """
    phone_or_email = models.CharField(max_length=150, db_index=True)
    otp_code = models.CharField(max_length=6)
    purpose = models.CharField(max_length=30, default='verification')
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField()
    is_used = models.BooleanField(default=False)

    class Meta:
        verbose_name = 'OTP Verification'
        verbose_name_plural = 'OTP Verifications'
        ordering = ['-created_at']

    def __str__(self):
        return f"OTP for {self.phone_or_email} ({self.purpose})"

    @property
    def is_expired(self):
        from django.utils import timezone
        return timezone.now() > self.expires_at
