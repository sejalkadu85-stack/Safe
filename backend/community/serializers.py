from rest_framework import serializers
from .models import Volunteer, VolunteerRequest, Helpline
from accounts.models import CustomUser


class VolunteerUserSerializer(serializers.ModelSerializer):
    full_name = serializers.SerializerMethodField()

    class Meta:
        model = CustomUser
        fields = ['id', 'username', 'first_name', 'last_name', 'full_name', 'phone_number', 'email']

    def get_full_name(self, obj):
        name = f"{obj.first_name} {obj.last_name}".strip()
        return name if name else obj.username


class VolunteerSerializer(serializers.ModelSerializer):
    user = VolunteerUserSerializer(read_only=True)
    distance_meters = serializers.FloatField(read_only=True, required=False)

    class Meta:
        model = Volunteer
        fields = [
            'id', 'user', 'is_verified', 'is_available', 'phone_number',
            'skills', 'latitude', 'longitude', 'address', 'total_helps',
            'rating', 'rating_count', 'distance_meters', 'created_at', 'updated_at'
        ]
        read_only_fields = ['id', 'user', 'is_verified', 'total_helps', 'rating', 'rating_count', 'created_at', 'updated_at']


class VolunteerRegisterSerializer(serializers.Serializer):
    phone_number = serializers.CharField(max_length=20, required=False, allow_blank=True)
    skills = serializers.CharField(max_length=255, required=False, allow_blank=True)
    latitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=False, allow_null=True)
    longitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=False, allow_null=True)
    address = serializers.CharField(max_length=255, required=False, allow_blank=True)
    is_available = serializers.BooleanField(required=False, default=True)


class VolunteerRequestSerializer(serializers.ModelSerializer):
    requester = VolunteerUserSerializer(read_only=True)
    volunteer = VolunteerSerializer(read_only=True)

    class Meta:
        model = VolunteerRequest
        fields = [
            'id', 'requester', 'volunteer', 'status', 'emergency_type',
            'message', 'latitude', 'longitude', 'address', 'rating',
            'feedback', 'created_at', 'resolved_at'
        ]
        read_only_fields = ['id', 'requester', 'volunteer', 'status', 'created_at', 'resolved_at']


class CreateVolunteerRequestSerializer(serializers.Serializer):
    volunteer_id = serializers.IntegerField(required=False, allow_null=True)
    emergency_type = serializers.CharField(max_length=100, default='Walk Companion / Immediate Help')
    message = serializers.CharField(required=False, allow_blank=True)
    latitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=False, allow_null=True)
    longitude = serializers.DecimalField(max_digits=9, decimal_places=6, required=False, allow_null=True)
    address = serializers.CharField(max_length=255, required=False, allow_blank=True)


class RespondVolunteerRequestSerializer(serializers.Serializer):
    action = serializers.ChoiceField(choices=['accept', 'decline', 'on_the_way', 'arrived', 'complete', 'cancel'])


class RateVolunteerSerializer(serializers.Serializer):
    rating = serializers.IntegerField(min_value=1, max_value=5)
    feedback = serializers.CharField(required=False, allow_blank=True)


class HelplineSerializer(serializers.ModelSerializer):
    class Meta:
        model = Helpline
        fields = [
            'id', 'name', 'number', 'category', 'description',
            'city', 'state', 'country', 'is_national', 'is_toll_free',
            'priority', 'created_at'
        ]
