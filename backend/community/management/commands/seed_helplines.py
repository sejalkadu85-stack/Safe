from django.core.management.base import BaseCommand
from community.models import Helpline


class Command(BaseCommand):
    help = 'Seeds initial Indian national and state emergency helplines'

    def handle(self, *args, **options):
        helplines_data = [
            {
                'name': 'National Emergency Number (ERSS)',
                'number': '112',
                'category': 'emergency',
                'description': 'All-in-one unified emergency response system across India for Police, Fire, and Ambulance.',
                'is_national': True,
                'priority': 1,
            },
            {
                'name': 'Women Helpline (Domestic Abuse & Distress)',
                'number': '1091',
                'category': 'women',
                'description': 'Dedicated 24x7 toll-free emergency response for women in distress or physical danger.',
                'is_national': True,
                'priority': 2,
            },
            {
                'name': 'Women in Distress (Ministry of WCD)',
                'number': '181',
                'category': 'women',
                'description': 'Toll-free 24-hour telephonic helpline to provide support, rescue and referral services to women affected by violence.',
                'is_national': True,
                'priority': 3,
            },
            {
                'name': 'Police Control Room',
                'number': '100',
                'category': 'police',
                'description': 'Immediate local police dispatch, crime reporting, and emergency patrol assistance.',
                'is_national': True,
                'priority': 4,
            },
            {
                'name': 'Emergency Medical Ambulance',
                'number': '108',
                'category': 'medical',
                'description': 'Free 24/7 medical emergency ambulance service equipped with first responders.',
                'is_national': True,
                'priority': 5,
            },
            {
                'name': 'Women Power Line',
                'number': '1090',
                'category': 'women',
                'description': '24/7 assistance specifically against stalking, harassment, obscene calls, and cyber bullying.',
                'is_national': True,
                'priority': 6,
            },
            {
                'name': 'National Cyber Crime Reporting',
                'number': '1930',
                'category': 'cyber',
                'description': 'Citizen financial fraud helpline & cyber stalking / extortion emergency complaints.',
                'is_national': True,
                'priority': 7,
            },
            {
                'name': 'Childline India',
                'number': '1098',
                'category': 'child',
                'description': '24/7 emergency phone outreach service for children in distress or danger.',
                'is_national': True,
                'priority': 8,
            },
            {
                'name': 'Pregnancy & Infant Medical Ambulance',
                'number': '102',
                'category': 'medical',
                'description': 'Free maternal and newborn health transport to nearest medical care.',
                'is_national': True,
                'priority': 9,
            },
            {
                'name': 'National Disaster Management (NDMA)',
                'number': '1078',
                'category': 'disaster',
                'description': 'Emergency relief and search & rescue during natural disasters and severe floods.',
                'is_national': True,
                'priority': 10,
            },
        ]

        count = 0
        for item in helplines_data:
            obj, created = Helpline.objects.update_or_create(
                number=item['number'],
                defaults=item
            )
            if created:
                count += 1

        self.stdout.write(self.style.SUCCESS(f'Successfully seeded {count} new helplines (total {len(helplines_data)}).'))
