from rest_framework import serializers
from django.contrib.auth import authenticate
from django.contrib.auth.password_validation import validate_password
from django.db.models import Q
from .models import CustomUser, UserProfile, OTPVerification


class UserProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = UserProfile
        fields = (
            'id',
            'date_of_birth',
            'blood_group',
            'address',
            'city',
            'country',
            'emergency_notes',
            'created_at',
            'updated_at',
        )
        read_only_fields = ('id', 'created_at', 'updated_at')


class UserSerializer(serializers.ModelSerializer):
    profile = UserProfileSerializer(read_only=True)
    role_display = serializers.CharField(source='get_role_display', read_only=True)

    class Meta:
        model = CustomUser
        fields = (
            'id',
            'username',
            'email',
            'phone_number',
            'first_name',
            'last_name',
            'role',
            'role_display',
            'profile_photo',
            'id_proof_type',
            'id_proof_document',
            'verification_status',
            'verification_rejection_reason',
            'is_verified',
            'profile',
            'date_joined',
        )
        read_only_fields = ('id', 'is_verified', 'verification_status', 'date_joined')


class RegisterSerializer(serializers.ModelSerializer):
    password = serializers.CharField(
        write_only=True,
        required=True,
        style={'input_type': 'password'}
    )
    confirm_password = serializers.CharField(
        write_only=True,
        required=True,
        style={'input_type': 'password'}
    )
    id_proof_type = serializers.CharField(
        required=False,
        allow_blank=True,
        allow_null=True
    )
    id_proof_document = serializers.FileField(
        required=False,
        allow_null=True
    )

    class Meta:
        model = CustomUser
        fields = (
            'username',
            'email',
            'phone_number',
            'first_name',
            'last_name',
            'role',
            'password',
            'confirm_password',
            'id_proof_type',
            'id_proof_document',
        )

    def validate_email(self, value):
        if CustomUser.objects.filter(email__iexact=value).exists():
            raise serializers.ValidationError("A user with this email address already exists.")
        return value.lower()

    def validate_phone_number(self, value):
        if value and CustomUser.objects.filter(phone_number=value).exists():
            raise serializers.ValidationError("A user with this phone number already exists.")
        return value

    def validate(self, attrs):
        if attrs['password'] != attrs['confirm_password']:
            raise serializers.ValidationError({"confirm_password": "Passwords do not match."})
        validate_password(attrs['password'])
        return attrs

    def create(self, validated_data):
        validated_data.pop('confirm_password')
        password = validated_data.pop('password')
        role = validated_data.get('role', CustomUser.Role.WOMAN)
        if role == CustomUser.Role.ADMIN:
            validated_data['is_staff'] = True
            validated_data['is_superuser'] = True

        if role == CustomUser.Role.VOLUNTEER:
            validated_data['verification_status'] = CustomUser.VerificationStatus.PENDING
            validated_data['is_verified'] = False
        else:
            # Immediate safety access for women/individuals
            validated_data['verification_status'] = CustomUser.VerificationStatus.APPROVED
            validated_data['is_verified'] = True

        user = CustomUser.objects.create_user(
            password=password,
            **validated_data
        )

        if role == CustomUser.Role.VOLUNTEER:
            from community.models import Volunteer
            Volunteer.objects.get_or_create(
                user=user,
                defaults={
                    'is_verified': False,
                    'phone_number': user.phone_number or ''
                }
            )

        return user


class LoginSerializer(serializers.Serializer):
    email_or_phone = serializers.CharField(
        required=True,
        help_text="User's email, phone number, or username"
    )
    password = serializers.CharField(
        write_only=True,
        required=True,
        style={'input_type': 'password'}
    )

    def validate(self, attrs):
        identifier = attrs.get('email_or_phone').strip()
        password = attrs.get('password')

        # Find user by email, phone_number, or username
        user_query = CustomUser.objects.filter(
            Q(email__iexact=identifier) |
            Q(phone_number=identifier) |
            Q(username__iexact=identifier)
        ).first()

        if not user_query:
            raise serializers.ValidationError({"detail": "Invalid credentials. User not found."})

        # Authenticate using standard password check
        if not user_query.check_password(password):
            raise serializers.ValidationError({"detail": "Invalid password. Please try again."})

        if not user_query.is_active:
            raise serializers.ValidationError({"detail": "This account is inactive or disabled."})

        attrs['user'] = user_query
        return attrs


class SendOTPSerializer(serializers.Serializer):
    phone_or_email = serializers.CharField(required=True)
    purpose = serializers.ChoiceField(
        choices=[('verification', 'Account Verification'), ('password_reset', 'Password Reset')],
        default='verification'
    )


class VerifyOTPSerializer(serializers.Serializer):
    phone_or_email = serializers.CharField(required=True)
    otp_code = serializers.CharField(max_length=6, min_length=6, required=True)
    purpose = serializers.ChoiceField(
        choices=[('verification', 'Account Verification'), ('password_reset', 'Password Reset')],
        default='verification'
    )


class ResetPasswordSerializer(serializers.Serializer):
    phone_or_email = serializers.CharField(required=True)
    otp_code = serializers.CharField(max_length=6, min_length=6, required=True)
    new_password = serializers.CharField(
        write_only=True,
        required=True,
        style={'input_type': 'password'}
    )
    confirm_password = serializers.CharField(
        write_only=True,
        required=True,
        style={'input_type': 'password'}
    )

    def validate(self, attrs):
        if attrs['new_password'] != attrs['confirm_password']:
            raise serializers.ValidationError({"confirm_password": "Passwords do not match."})
        validate_password(attrs['new_password'])
        return attrs


class ChangePasswordSerializer(serializers.Serializer):
    old_password = serializers.CharField(required=True, write_only=True)
    new_password = serializers.CharField(required=True, write_only=True)

    def validate_new_password(self, value):
        validate_password(value)
        return value


class UserProfileUpdateSerializer(serializers.ModelSerializer):
    email = serializers.EmailField(required=False)
    date_of_birth = serializers.DateField(source='profile.date_of_birth', required=False, allow_null=True)
    blood_group = serializers.CharField(source='profile.blood_group', required=False, allow_blank=True)
    address = serializers.CharField(source='profile.address', required=False, allow_blank=True)
    city = serializers.CharField(source='profile.city', required=False, allow_blank=True)
    country = serializers.CharField(source='profile.country', required=False, allow_blank=True)
    emergency_notes = serializers.CharField(source='profile.emergency_notes', required=False, allow_blank=True)

    class Meta:
        model = CustomUser
        fields = (
            'first_name',
            'last_name',
            'email',
            'phone_number',
            'date_of_birth',
            'blood_group',
            'address',
            'city',
            'country',
            'emergency_notes',
        )

    def validate_email(self, value):
        if value:
            existing = CustomUser.objects.filter(email__iexact=value).exclude(pk=self.instance.pk).exists()
            if existing:
                raise serializers.ValidationError("A user with this email already exists.")
        return value

    def validate_phone_number(self, value):
        if value:
            existing = CustomUser.objects.filter(phone_number=value).exclude(pk=self.instance.pk).exists()
            if existing:
                raise serializers.ValidationError("A user with this phone number already exists.")
        return value

    def update(self, instance, validated_data):
        profile_data = validated_data.pop('profile', {})

        # Update User fields
        for attr, value in validated_data.items():
            setattr(instance, attr, value)
        instance.save()

        # Update Profile fields
        profile = getattr(instance, 'profile', None)
        if not profile:
            profile = UserProfile.objects.create(user=instance)

        for attr, value in profile_data.items():
            setattr(profile, attr, value)
        profile.save()

        return instance


class ProfilePhotoUploadSerializer(serializers.ModelSerializer):
    class Meta:
        model = CustomUser
        fields = ('profile_photo',)

    def validate_profile_photo(self, value):
        max_size = 5 * 1024 * 1024  # 5MB
        if value.size > max_size:
            raise serializers.ValidationError("Profile photo size cannot exceed 5MB.")
        return value


class PublicUserSummarySerializer(serializers.ModelSerializer):
    blood_group = serializers.CharField(source='profile.blood_group', read_only=True)
    emergency_notes = serializers.CharField(source='profile.emergency_notes', read_only=True)
    role_display = serializers.CharField(source='get_role_display', read_only=True)

    class Meta:
        model = CustomUser
        fields = (
            'id',
            'username',
            'first_name',
            'last_name',
            'phone_number',
            'role',
            'role_display',
            'profile_photo',
            'is_verified',
            'blood_group',
            'emergency_notes',
            'date_joined',
        )
