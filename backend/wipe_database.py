import psycopg
from decouple import config

def wipe_data():
    conn = psycopg.connect(
        dbname=config('DB_NAME'),
        user=config('DB_USER'),
        password=config('DB_PASSWORD'),
        host=config('DB_HOST'),
        port=config('DB_PORT', cast=int),
    )
    with conn.cursor() as cur:
        print("=== WIPING TEST / DEMO ACTIVITY DATA ===")
        
        # 1. Alerts & incidents
        cur.execute("DELETE FROM alerts_evidencefile")
        cur.execute("DELETE FROM alerts_incidentmessage")
        cur.execute("DELETE FROM alerts_incidenttimeline")
        cur.execute("DELETE FROM alerts_alertrecipient")
        cur.execute("DELETE FROM alerts_sosalert")
        cur.execute("DELETE FROM alerts_trustedcontact")
        print("[OK] Wiped all alerts, evidence, timelines, recipients, and trusted contacts")

        # 2. Location & tracking
        cur.execute("DELETE FROM location_journey_shared_with")
        cur.execute("DELETE FROM location_journey")
        cur.execute("DELETE FROM location_locationlog")
        cur.execute("DELETE FROM location_locationsharesession_shared_with")
        cur.execute("DELETE FROM location_locationsharesession")
        cur.execute("DELETE FROM location_safezoneevent")
        cur.execute("DELETE FROM location_safezone")
        cur.execute("DELETE FROM location_dangerzone")
        print("[OK] Wiped all journeys, location updates, safe zones, and danger zones")

        # 3. Community & volunteer
        cur.execute("DELETE FROM community_volunteerrequest")
        cur.execute("DELETE FROM community_volunteer")
        print("[OK] Wiped all community volunteer requests and profiles")

        # 4. Messaging & conversations
        cur.execute("DELETE FROM messaging_message")
        cur.execute("DELETE FROM messaging_conversation_participants")
        cur.execute("DELETE FROM messaging_conversation")
        print("[OK] Wiped all messaging conversations and incident chats")

        # 5. Notifications & device tokens
        cur.execute("DELETE FROM notifications_checkinsession")
        cur.execute("DELETE FROM notifications_checkinschedule")
        cur.execute("DELETE FROM notifications_notification")
        cur.execute("DELETE FROM notifications_devicetoken")
        print("[OK] Wiped all notifications and device tokens")

        # 6. Auth tokens and OTPs
        cur.execute("DELETE FROM accounts_otpverification")
        cur.execute("DELETE FROM token_blacklist_blacklistedtoken")
        cur.execute("DELETE FROM token_blacklist_outstandingtoken")
        print("[OK] Wiped all OTP records and active JWT tokens")

        # 7. Non-admin test users
        cur.execute("DELETE FROM accounts_userprofile WHERE user_id NOT IN (SELECT id FROM accounts_customuser WHERE is_staff=TRUE OR is_superuser=TRUE)")
        cur.execute("DELETE FROM accounts_customuser WHERE is_staff=FALSE AND is_superuser=FALSE")
        print("[OK] Wiped all non-admin test users (women/guardian/volunteer test accounts)")

        conn.commit()

        # Summary verification
        print("\n=== POST-WIPE VERIFICATION ===")
        cur.execute("SELECT table_name FROM information_schema.tables WHERE table_schema='public'")
        tables = [r[0] for r in cur.fetchall()]
        for t in sorted(tables):
            if not t.startswith('django_') and not t.startswith('auth_'):
                cur.execute(f'SELECT COUNT(*) FROM "{t}"')
                cnt = cur.fetchone()[0]
                print(f"  {t}: {cnt}")

        cur.execute('SELECT id, username, email, role, is_staff, is_superuser FROM accounts_customuser')
        users = cur.fetchall()
        print('\n=== REMAINING ADMIN USERS ===')
        for u in users:
            print(f'  ID={u[0]}, user={u[1]}, email={u[2]}, role={u[3]} (Admin Superuser)')

    conn.close()

if __name__ == '__main__':
    wipe_data()
