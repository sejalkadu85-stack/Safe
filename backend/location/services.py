import logging
from .models import SafeZone, SafeZoneEvent, LocationLog
from alerts.models import TrustedContact
from alerts.services import send_sms_notification, send_push_notification

logger = logging.getLogger(__name__)


def process_geofence_for_ping(user, new_ping):
    """
    Evaluates safe zones for a user upon receiving a new location ping.
    Detects boundary exit (breach) and entry transitions.
    """
    active_zones = SafeZone.objects.filter(user=user, is_active=True)
    if not active_zones.exists():
        return []

    # Get the previous location ping for comparison
    previous_ping = LocationLog.objects.filter(
        user=user
    ).exclude(id=new_ping.id).order_by('-timestamp').first()

    triggered_events = []

    for zone in active_zones:
        current_distance = zone.distance_to(new_ping.latitude, new_ping.longitude)
        is_currently_inside = current_distance <= zone.radius_meters

        if previous_ping:
            prev_distance = zone.distance_to(previous_ping.latitude, previous_ping.longitude)
            was_previously_inside = prev_distance <= zone.radius_meters
        else:
            # First ping: establish baseline without generating false exit
            was_previously_inside = is_currently_inside

        # 1. Exit Event (Safe Zone Breach)
        if was_previously_inside and not is_currently_inside:
            event = SafeZoneEvent.objects.create(
                safe_zone=zone,
                user=user,
                event_type=SafeZoneEvent.EventType.EXIT,
                latitude=new_ping.latitude,
                longitude=new_ping.longitude,
                distance_meters=round(current_distance, 1),
                notified_guardians=zone.notify_on_exit
            )
            triggered_events.append(event)

            if zone.notify_on_exit:
                notify_guardians_geofence(
                    user=user,
                    title="⚠️ Safe Zone Perimeter Alert",
                    message=f"{user.get_full_name() or user.username} has stepped outside safe zone '{zone.name}' (current distance: {int(current_distance)}m)."
                )

        # 2. Entry Event (Safe Arrival)
        elif not was_previously_inside and is_currently_inside:
            event = SafeZoneEvent.objects.create(
                safe_zone=zone,
                user=user,
                event_type=SafeZoneEvent.EventType.ENTRY,
                latitude=new_ping.latitude,
                longitude=new_ping.longitude,
                distance_meters=round(current_distance, 1),
                notified_guardians=zone.notify_on_entry
            )
            triggered_events.append(event)

            if zone.notify_on_entry:
                notify_guardians_geofence(
                    user=user,
                    title="✅ Safe Zone Arrival Notice",
                    message=f"{user.get_full_name() or user.username} has safely arrived at safe zone '{zone.name}'."
                )

    return triggered_events


def notify_guardians_geofence(user, title, message):
    """
    Sends geofence event alerts to all accepted trusted contacts.
    """
    accepted_contacts = TrustedContact.objects.filter(
        owner=user,
        status=TrustedContact.Status.ACCEPTED
    ).select_related('contact_user')

    for contact in accepted_contacts:
        guardian = contact.contact_user

        if contact.notify_via_sms and guardian.phone_number:
            send_sms_notification(guardian.phone_number, f"{title}: {message}")

        if contact.notify_via_push:
            send_push_notification(
                user=guardian,
                title=title,
                body=message,
                data={'event_type': 'geofence', 'user_id': str(user.id)}
            )
