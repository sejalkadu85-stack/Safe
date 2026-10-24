from django.core.management.base import BaseCommand
from django.utils import timezone
from notifications.models import CheckInSchedule
from notifications.fcm_service import process_check_in_schedule


class Command(BaseCommand):
    help = "Process due scheduled safety check-in reminders and send notifications"

    def handle(self, *args, **options):
        now = timezone.now()
        due_schedules = CheckInSchedule.objects.filter(
            is_active=True,
            is_sent=False,
            scheduled_time__lte=now
        )

        total_due = due_schedules.count()
        self.stdout.write(f"Found {total_due} due check-in reminders at {now.isoformat()}")

        processed = 0
        for schedule in due_schedules:
            try:
                res = process_check_in_schedule(schedule)
                processed += 1
                self.stdout.write(
                    self.style.SUCCESS(
                        f"Dispatched reminder to {schedule.user.username} (ID: {schedule.id}) - Result: {res.get('mode')}"
                    )
                )
            except Exception as e:
                self.stdout.write(
                    self.style.ERROR(
                        f"Error processing schedule {schedule.id} for user {schedule.user.username}: {e}"
                    )
                )

        self.stdout.write(self.style.SUCCESS(f"Finished processing {processed}/{total_due} check-in reminders."))
