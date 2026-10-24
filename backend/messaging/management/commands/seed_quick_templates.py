from django.core.management.base import BaseCommand
from messaging.models import QuickMessage


class Command(BaseCommand):
    help = "Seed pre-loaded default quick message templates"

    DEFAULT_TEMPLATES = [
        {"content": "I'm safe ✅", "title": "Safe"},
        {"content": "Come pick me up 🚗", "title": "Pick Up"},
        {"content": "Call me NOW 📞", "title": "Call Now"},
        {"content": "In danger, need help 🆘", "title": "Need Help"},
        {"content": "Reached home safely 🏠", "title": "Reached Home"},
    ]

    def handle(self, *args, **options):
        count = 0
        for item in self.DEFAULT_TEMPLATES:
            obj, created = QuickMessage.objects.get_or_create(
                content=item["content"],
                is_default=True,
                defaults={"title": item["title"]}
            )
            if created:
                count += 1
                self.stdout.write(self.style.SUCCESS(f"Created template: {item['title']}"))
            else:
                self.stdout.write(f"Template already exists: {item['title']}")

        self.stdout.write(self.style.SUCCESS(f"Successfully processed default quick message templates (created {count} new)."))
