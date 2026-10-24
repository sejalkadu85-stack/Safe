from django.urls import path
from .views import (
    RegisterOrUpdateVolunteerView,
    VolunteerProfileView,
    NearbyVolunteersView,
    CreateVolunteerRequestView,
    ListVolunteerRequestsView,
    RespondVolunteerRequestView,
    RateVolunteerRequestView,
    HelplineListView,
)

app_name = 'community'

urlpatterns = [
    # Volunteer System (Task 5.1)
    path('volunteers/register/', RegisterOrUpdateVolunteerView.as_view(), name='volunteer_register'),
    path('volunteers/profile/', VolunteerProfileView.as_view(), name='volunteer_profile'),
    path('volunteers/nearby/', NearbyVolunteersView.as_view(), name='volunteers_nearby'),
    path('volunteers/request/', CreateVolunteerRequestView.as_view(), name='volunteer_request_create'),
    path('volunteers/requests/', ListVolunteerRequestsView.as_view(), name='volunteer_requests_list'),
    path('volunteers/requests/<int:id>/respond/', RespondVolunteerRequestView.as_view(), name='volunteer_request_respond'),
    path('volunteers/requests/<int:id>/rate/', RateVolunteerRequestView.as_view(), name='volunteer_request_rate'),

    # Helpline Directory (Task 5.2)
    path('helplines/', HelplineListView.as_view(), name='helplines_list'),
]
