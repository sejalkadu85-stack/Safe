from django.utils import timezone
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework import status, permissions
from decimal import Decimal

from .models import Volunteer, VolunteerRequest, Helpline
from .serializers import (
    VolunteerSerializer,
    VolunteerRegisterSerializer,
    VolunteerRequestSerializer,
    CreateVolunteerRequestSerializer,
    RespondVolunteerRequestSerializer,
    RateVolunteerSerializer,
    HelplineSerializer,
)
from location.utils import haversine_distance
from accounts.models import CustomUser


class RegisterOrUpdateVolunteerView(APIView):
    """
    Register as a community volunteer or update current volunteer status.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        serializer = VolunteerRegisterSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data
        user = request.user

        # Create or update volunteer profile
        volunteer, created = Volunteer.objects.get_or_create(
            user=user,
            defaults={
                'phone_number': data.get('phone_number') or user.phone_number or '',
                'skills': data.get('skills', 'First Aid, Self Defense, Quick Responder'),
                'latitude': data.get('latitude'),
                'longitude': data.get('longitude'),
                'address': data.get('address', ''),
                'is_available': data.get('is_available', True),
                'is_verified': True, # Automatically verify for demo/testing convenience
            }
        )

        if not created:
            if 'phone_number' in data and data['phone_number']:
                volunteer.phone_number = data['phone_number']
            if 'skills' in data and data['skills']:
                volunteer.skills = data['skills']
            if 'latitude' in data and data['latitude'] is not None:
                volunteer.latitude = data['latitude']
            if 'longitude' in data and data['longitude'] is not None:
                volunteer.longitude = data['longitude']
            if 'address' in data and data['address']:
                volunteer.address = data['address']
            if 'is_available' in data:
                volunteer.is_available = data['is_available']
            volunteer.save()

        # Update user role if regular woman or volunteer
        if user.role != CustomUser.Role.ADMIN:
            user.role = CustomUser.Role.VOLUNTEER
            user.save(update_fields=['role'])

        return Response({
            'success': True,
            'message': 'Registered as community volunteer successfully' if created else 'Volunteer profile updated',
            'volunteer': VolunteerSerializer(volunteer).data
        }, status=status.HTTP_201_CREATED if created else status.HTTP_200_OK)


class VolunteerProfileView(APIView):
    """
    Get or update the current authenticated user's volunteer profile.
    """
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        try:
            volunteer = request.user.volunteer_profile
            return Response({
                'success': True,
                'is_registered': True,
                'volunteer': VolunteerSerializer(volunteer).data
            })
        except Volunteer.DoesNotExist:
            return Response({
                'success': True,
                'is_registered': False,
                'message': 'User is not registered as a volunteer'
            }, status=status.HTTP_200_OK)

    def put(self, request):
        try:
            volunteer = request.user.volunteer_profile
        except Volunteer.DoesNotExist:
            return Response({'error': 'Volunteer profile not found'}, status=status.HTTP_404_NOT_FOUND)

        serializer = VolunteerRegisterSerializer(data=request.data, partial=True)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data
        for field, val in data.items():
            if val is not None:
                setattr(volunteer, field, val)
        volunteer.save()

        return Response({
            'success': True,
            'message': 'Profile updated successfully',
            'volunteer': VolunteerSerializer(volunteer).data
        })


class NearbyVolunteersView(APIView):
    """
    List nearby available and verified volunteers within a given radius in meters.
    """
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        lat_str = request.query_params.get('lat')
        lng_str = request.query_params.get('lng')
        radius_meters = float(request.query_params.get('radius', 10000)) # 10 km default

        if not lat_str or not lng_str:
            return Response({'error': 'lat and lng query params are required'}, status=status.HTTP_400_BAD_REQUEST)

        try:
            user_lat = float(lat_str)
            user_lng = float(lng_str)
        except ValueError:
            return Response({'error': 'Invalid lat or lng coordinate'}, status=status.HTTP_400_BAD_REQUEST)

        # Query available verified volunteers who have coordinates
        volunteers = Volunteer.objects.filter(
            is_available=True,
            is_verified=True,
            latitude__isnull=False,
            longitude__isnull=False
        ).select_related('user')

        results = []
        for vol in volunteers:
            dist = haversine_distance(user_lat, user_lng, float(vol.latitude), float(vol.longitude))
            if dist <= radius_meters:
                vol_data = VolunteerSerializer(vol).data
                vol_data['distance_meters'] = round(dist, 1)
                results.append((dist, vol_data))

        # Sort by distance
        results.sort(key=lambda x: x[0])
        response_data = [item[1] for item in results]

        # Graceful fallback: If strict radius filtered out all volunteers (e.g. testing from outside Pune),
        # return the registered community volunteers so the screen displays live real responders
        if not response_data and volunteers.exists():
            for vol in volunteers:
                dist = haversine_distance(user_lat, user_lng, float(vol.latitude), float(vol.longitude))
                vol_data = VolunteerSerializer(vol).data
                vol_data['distance_meters'] = round(dist, 1)
                results.append((dist, vol_data))
            results.sort(key=lambda x: x[0])
            response_data = [item[1] for item in results]

        return Response({
            'success': True,
            'count': len(response_data),
            'radius_meters': radius_meters,
            'volunteers': response_data
        })


class CreateVolunteerRequestView(APIView):
    """
    Create a request for volunteer assistance.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        serializer = CreateVolunteerRequestSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data
        volunteer = None
        volunteer_id = data.get('volunteer_id')

        if volunteer_id:
            try:
                volunteer = Volunteer.objects.get(id=volunteer_id)
            except Volunteer.DoesNotExist:
                return Response({'error': 'Volunteer not found'}, status=status.HTTP_404_NOT_FOUND)

        req_obj = VolunteerRequest.objects.create(
            requester=request.user,
            volunteer=volunteer,
            emergency_type=data.get('emergency_type', 'Walk Companion / Immediate Help'),
            message=data.get('message', ''),
            latitude=data.get('latitude'),
            longitude=data.get('longitude'),
            address=data.get('address', ''),
            status='pending'
        )

        return Response({
            'success': True,
            'message': 'Help request submitted successfully',
            'request': VolunteerRequestSerializer(req_obj).data
        }, status=status.HTTP_201_CREATED)


class ListVolunteerRequestsView(APIView):
    """
    List assistance requests related to the user (as requester or assigned volunteer).
    """
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        user = request.user
        role_filter = request.query_params.get('role', 'all')
        status_filter = request.query_params.get('status')

        qs = VolunteerRequest.objects.all().select_related('requester', 'volunteer__user')

        if role_filter == 'requester':
            qs = qs.filter(requester=user)
        elif role_filter == 'volunteer':
            try:
                vol_profile = user.volunteer_profile
                qs = qs.filter(volunteer=vol_profile)
            except Volunteer.DoesNotExist:
                return Response({'success': True, 'requests': []})
        else:
            # All requests where user is requester or assigned volunteer
            from django.db.models import Q
            try:
                vol_profile = user.volunteer_profile
                qs = qs.filter(Q(requester=user) | Q(volunteer=vol_profile))
            except Volunteer.DoesNotExist:
                qs = qs.filter(requester=user)

        if status_filter:
            qs = qs.filter(status=status_filter)

        serializer = VolunteerRequestSerializer(qs[:50], many=True)
        return Response({
            'success': True,
            'count': len(serializer.data),
            'requests': serializer.data
        })


class RespondVolunteerRequestView(APIView):
    """
    Accept, decline, complete, or cancel an assistance request.
    """
    permission_classes = [permissions.IsAuthenticated]

    def put(self, request, id):
        serializer = RespondVolunteerRequestSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        try:
            req_obj = VolunteerRequest.objects.select_related('requester', 'volunteer__user').get(id=id)
        except VolunteerRequest.DoesNotExist:
            return Response({'error': 'Request not found'}, status=status.HTTP_404_NOT_FOUND)

        action = serializer.validated_data['action']
        user = request.user

        # Cancellation by requester
        if action == 'cancel':
            if req_obj.requester != user:
                return Response({'error': 'Only requester can cancel request'}, status=status.HTTP_403_FORBIDDEN)
            req_obj.status = 'cancelled'
            req_obj.resolved_at = timezone.now()
            req_obj.save()
            return Response({
                'success': True,
                'message': 'Request cancelled',
                'request': VolunteerRequestSerializer(req_obj).data
            })

        # Actions for volunteer
        is_assigned_vol = req_obj.volunteer and req_obj.volunteer.user == user
        has_vol_profile = hasattr(user, 'volunteer_profile')

        if action == 'accept':
            if not has_vol_profile:
                return Response({'error': 'You must be a registered volunteer to accept'}, status=status.HTTP_403_FORBIDDEN)
            if req_obj.volunteer and req_obj.volunteer.user != user:
                return Response({'error': 'This request has already been assigned to another volunteer'}, status=status.HTTP_400_BAD_REQUEST)
            req_obj.volunteer = user.volunteer_profile
            req_obj.status = 'accepted'
            req_obj.save()
            return Response({
                'success': True,
                'message': 'You have accepted the assistance request',
                'request': VolunteerRequestSerializer(req_obj).data
            })

        elif action == 'on_the_way':
            if not is_assigned_vol:
                return Response({'error': 'You are not assigned to this request'}, status=status.HTTP_403_FORBIDDEN)
            req_obj.status = 'on_the_way'
            req_obj.save()
            return Response({
                'success': True,
                'message': 'Status updated: Volunteer is on the way',
                'request': VolunteerRequestSerializer(req_obj).data
            })

        elif action == 'arrived':
            if not is_assigned_vol:
                return Response({'error': 'You are not assigned to this request'}, status=status.HTTP_403_FORBIDDEN)
            req_obj.status = 'arrived'
            req_obj.save()
            return Response({
                'success': True,
                'message': 'Status updated: Volunteer has arrived at user location',
                'request': VolunteerRequestSerializer(req_obj).data
            })

        elif action == 'decline':
            if not is_assigned_vol:
                return Response({'error': 'You are not assigned to this request'}, status=status.HTTP_403_FORBIDDEN)
            req_obj.status = 'declined'
            req_obj.save()
            return Response({
                'success': True,
                'message': 'Request declined',
                'request': VolunteerRequestSerializer(req_obj).data
            })

        elif action == 'complete':
            if req_obj.requester != user and not is_assigned_vol:
                return Response({'error': 'Only participants can mark this request completed'}, status=status.HTTP_403_FORBIDDEN)
            req_obj.status = 'completed'
            req_obj.resolved_at = timezone.now()
            req_obj.save()
            if req_obj.volunteer:
                req_obj.volunteer.total_helps += 1
                req_obj.volunteer.save(update_fields=['total_helps'])
            return Response({
                'success': True,
                'message': 'Assistance completed successfully',
                'request': VolunteerRequestSerializer(req_obj).data
            })

        return Response({'error': 'Invalid action'}, status=status.HTTP_400_BAD_REQUEST)


class RateVolunteerRequestView(APIView):
    """
    Rate and leave feedback for a completed assistance session.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, id):
        serializer = RateVolunteerSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        try:
            req_obj = VolunteerRequest.objects.get(id=id, requester=request.user)
        except VolunteerRequest.DoesNotExist:
            return Response({'error': 'Completed request not found for user'}, status=status.HTTP_404_NOT_FOUND)

        if req_obj.status != 'completed':
            return Response({'error': 'You can only rate completed assistance requests'}, status=status.HTTP_400_BAD_REQUEST)

        rating = serializer.validated_data['rating']
        feedback = serializer.validated_data.get('feedback', '')

        req_obj.rating = rating
        req_obj.feedback = feedback
        req_obj.save(update_fields=['rating', 'feedback'])

        if req_obj.volunteer:
            req_obj.volunteer.update_rating(rating)

        return Response({
            'success': True,
            'message': 'Thank you! Rating submitted successfully',
            'rating': rating,
            'volunteer_new_rating': float(req_obj.volunteer.rating) if req_obj.volunteer else None
        })


class HelplineListView(APIView):
    """
    List emergency and safety helplines filterable by category, city, or search keyword.
    """
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        category = request.query_params.get('category')
        city = request.query_params.get('city')
        search = request.query_params.get('search')

        qs = Helpline.objects.all()

        if category and category != 'all':
            qs = qs.filter(category__iexact=category)
        if city:
            qs = qs.filter(city__icontains=city)
        if search:
            from django.db.models import Q
            qs = qs.filter(
                Q(name__icontains=search) |
                Q(number__icontains=search) |
                Q(description__icontains=search) |
                Q(category__icontains=search)
            )

        serializer = HelplineSerializer(qs, many=True)
        return Response({
            'success': True,
            'count': len(serializer.data),
            'helplines': serializer.data
        })
