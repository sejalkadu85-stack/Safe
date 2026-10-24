from django.core.management.base import BaseCommand
from accounts.models import CustomUser


class Command(BaseCommand):
    help = 'Initialize a default system administrator for the Women Safety System'

    def add_arguments(self, parser):
        parser.add_argument('--username', type=str, default='admin', help='Admin username')
        parser.add_argument('--email', type=str, default='admin@womensafety.local', help='Admin email')
        parser.add_argument('--password', type=str, default='Admin@123456', help='Admin password')
        parser.add_argument('--phone', type=str, default='+919000000000', help='Admin phone number')

    def handle(self, *args, **options):
        username = options['username']
        email = options['email']
        password = options['password']
        phone = options['phone']

        if CustomUser.objects.filter(username=username).exists():
            self.stdout.write(self.style.WARNING(f"Admin user '{username}' already exists."))
            return

        user = CustomUser.objects.create_superuser(
            username=username,
            email=email,
            password=password,
            role=CustomUser.Role.ADMIN,
            phone_number=phone,
            is_verified=True,
        )

        self.stdout.write(
            self.style.SUCCESS(
                f"Successfully initialized Admin user:\n"
                f"  Username: {user.username}\n"
                f"  Email: {user.email}\n"
                f"  Role: {user.role}\n"
                f"  Is Staff: {user.is_staff}\n"
                f"  Is Superuser: {user.is_superuser}"
            )
        )
