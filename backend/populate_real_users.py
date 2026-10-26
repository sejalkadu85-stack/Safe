import os
import sys
import django

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'women_safety.settings')
django.setup()

from accounts.models import CustomUser, UserProfile
from alerts.models import TrustedContact
from community.models import Volunteer

DEFAULT_PASSWORD = "SafePass@123"

def create_or_update_users():
    print("=== POPULATING REAL USER ACCOUNTS ===")

    # 1. Sejal Kadu - Role: Woman (Citizen / Distress User)
    sejal, created = CustomUser.objects.get_or_create(
        email="sejalkadu31@gmail.com",
        defaults={
            "username": "sejal_kadu",
            "first_name": "Sejal",
            "last_name": "Kadu",
            "phone_number": "+919021380010",
            "role": CustomUser.Role.WOMAN,
            "is_verified": True,
            "verification_status": CustomUser.VerificationStatus.APPROVED,
        }
    )
    sejal.username = "sejal_kadu"
    sejal.first_name = "Sejal"
    sejal.last_name = "Kadu"
    sejal.phone_number = "+919021380010"
    sejal.role = CustomUser.Role.WOMAN
    sejal.is_verified = True
    sejal.verification_status = CustomUser.VerificationStatus.APPROVED
    sejal.set_password(DEFAULT_PASSWORD)
    sejal.save()

    profile, _ = UserProfile.objects.get_or_create(user=sejal)
    profile.blood_group = "B+"
    profile.medical_notes = "No known drug allergies. Asthmatic inhaler in handbag."
    profile.emergency_notes = "Call Primary Guardian immediately in case of emergency."
    profile.home_address = "Pune, Maharashtra, India"
    profile.save()
    print(f"[OK] Woman User: {sejal.get_full_name()} ({sejal.email}) - Phone: {sejal.phone_number}")

    # 2. Kripa Gayakwad - Role: Guardian (Primary Trusted Contact)
    kripa, created = CustomUser.objects.get_or_create(
        email="krijuu04@gmail.com",
        defaults={
            "username": "kripa_g",
            "first_name": "Kripa",
            "last_name": "Gayakwad",
            "phone_number": "+919545813587",
            "role": CustomUser.Role.GUARDIAN,
            "is_verified": True,
            "verification_status": CustomUser.VerificationStatus.APPROVED,
        }
    )
    kripa.username = "kripa_g"
    kripa.first_name = "Kripa"
    kripa.last_name = "Gayakwad"
    kripa.phone_number = "+919545813587"
    kripa.role = CustomUser.Role.GUARDIAN
    kripa.is_verified = True
    kripa.verification_status = CustomUser.VerificationStatus.APPROVED
    kripa.set_password(DEFAULT_PASSWORD)
    kripa.save()

    kripa_prof, _ = UserProfile.objects.get_or_create(user=kripa)
    kripa_prof.home_address = "Pune, Maharashtra, India"
    kripa_prof.save()
    print(f"[OK] Guardian User: {kripa.get_full_name()} ({kripa.email}) - Phone: {kripa.phone_number}")

    # 3. Tanushri Darshanwar - Role: Volunteer (Community Emergency Responder)
    tanushri, created = CustomUser.objects.get_or_create(
        email="sejalkadu0@gmail.com",
        defaults={
            "username": "tanushri_d",
            "first_name": "Tanushri",
            "last_name": "Darshanwar",
            "phone_number": "+917798273397",
            "role": CustomUser.Role.VOLUNTEER,
            "is_verified": True,
            "verification_status": CustomUser.VerificationStatus.APPROVED,
        }
    )
    tanushri.username = "tanushri_d"
    tanushri.first_name = "Tanushri"
    tanushri.last_name = "Darshanwar"
    tanushri.phone_number = "+917798273397"
    tanushri.role = CustomUser.Role.VOLUNTEER
    tanushri.is_verified = True
    tanushri.verification_status = CustomUser.VerificationStatus.APPROVED
    tanushri.set_password(DEFAULT_PASSWORD)
    tanushri.save()

    tanushri_prof, _ = UserProfile.objects.get_or_create(user=tanushri)
    tanushri_prof.home_address = "Shivaji Nagar / Deccan Gymkhana, Pune"
    tanushri_prof.save()

    # Community Volunteer profile for Tanushri
    vol, _ = Volunteer.objects.get_or_create(
        user=tanushri,
        defaults={
            "is_verified": True,
            "is_available": True,
            "phone_number": "+917798273397",
            "skills": "First Aid Certified, Safe Escort, Quick Responder",
            "latitude": 18.5204,
            "longitude": 73.8567,
            "address": "Shivaji Nagar, Pune",
            "total_helps": 5,
        }
    )
    vol.is_verified = True
    vol.is_available = True
    vol.phone_number = "+917798273397"
    vol.skills = "First Aid Certified, Safe Escort, Quick Responder"
    vol.latitude = 18.5204
    vol.longitude = 73.8567
    vol.address = "Shivaji Nagar, Pune"
    vol.save()
    print(f"[OK] Volunteer User: {tanushri.get_full_name()} ({tanushri.email}) - Phone: {tanushri.phone_number}")

    # 4. Link Trusted Contacts for Sejal Kadu
    # Contact 1: Kripa Gayakwad (Tier 1 Primary Guardian)
    c1, _ = TrustedContact.objects.get_or_create(
        owner=sejal,
        contact_user=kripa,
        defaults={
            "name": "Kripa Gayakwad",
            "phone_number": "+919545813587",
            "tier": TrustedContact.Tier.PRIMARY,
            "relationship": TrustedContact.Relationship.FRIEND,
            "status": TrustedContact.Status.ACCEPTED,
            "is_verified": True,
            "notify_via_sms": True,
            "notify_via_email": True,
            "notify_via_push": True,
        }
    )
    c1.name = "Kripa Gayakwad"
    c1.phone_number = "+919545813587"
    c1.tier = TrustedContact.Tier.PRIMARY
    c1.status = TrustedContact.Status.ACCEPTED
    c1.is_verified = True
    c1.notify_via_sms = True
    c1.notify_via_email = True
    c1.notify_via_push = True
    c1.save()
    print(f"[OK] Linked Primary Guardian: {c1.name} (Phone: {c1.phone_number}, Email: {kripa.email}) for {sejal.get_full_name()}")

    # Contact 2: Tanushri Darshanwar (Tier 1 Co-Guardian / Emergency Responder Contact)
    # Using Tier 1 so both Kripa and Tanushri receive immediate emergency alerts on SOS trigger!
    c2, _ = TrustedContact.objects.get_or_create(
        owner=sejal,
        contact_user=tanushri,
        defaults={
            "name": "Tanushri Darshanwar",
            "phone_number": "+917798273397",
            "tier": TrustedContact.Tier.PRIMARY,
            "relationship": TrustedContact.Relationship.FRIEND,
            "status": TrustedContact.Status.ACCEPTED,
            "is_verified": True,
            "notify_via_sms": True,
            "notify_via_email": True,
            "notify_via_push": True,
        }
    )
    c2.name = "Tanushri Darshanwar"
    c2.phone_number = "+917798273397"
    c2.tier = TrustedContact.Tier.PRIMARY
    c2.status = TrustedContact.Status.ACCEPTED
    c2.is_verified = True
    c2.notify_via_sms = True
    c2.notify_via_email = True
    c2.notify_via_push = True
    c2.save()
    print(f"[OK] Linked Primary Co-Guardian / Responder: {c2.name} (Phone: {c2.phone_number}, Email: {tanushri.email}) for {sejal.get_full_name()}")

    print("\n=== SETUP COMPLETED SUCCESSFULLY ===")
    print(f"Default Password for all 3 users: {DEFAULT_PASSWORD}")

if __name__ == "__main__":
    create_or_update_users()
