"""
Django settings for women_safety project.
"""

from pathlib import Path
from datetime import timedelta
from decouple import config, Csv

# Build paths inside the project like this: BASE_DIR / 'subdir'.
BASE_DIR = Path(__file__).resolve().parent.parent

# SECURITY WARNING: keep the secret key used in production secret!
SECRET_KEY = config('SECRET_KEY', default='django-insecure-pl$4u9*7kuacj9bz%b*@vg%t40%m&xu%ywkm7k*!t4*cq0e__n')

# SECURITY WARNING: don't run with debug turned on in production!
DEBUG = config('DEBUG', default=True, cast=bool)

ALLOWED_HOSTS = config(
    'ALLOWED_HOSTS',
    default='localhost,127.0.0.1,10.0.2.2,*',
    cast=Csv()
)

# Application definition
INSTALLED_APPS = [
    # Daphne for ASGI / WebSockets
    'daphne',

    # Django core apps
    'django.contrib.admin',
    'django.contrib.auth',
    'django.contrib.contenttypes',
    'django.contrib.sessions',
    'django.contrib.messages',
    'django.contrib.staticfiles',

    # Third-party apps
    'rest_framework',
    'rest_framework_simplejwt',
    'rest_framework_simplejwt.token_blacklist',
    'corsheaders',
    'channels',

    # Custom safety project apps
    'accounts.apps.AccountsConfig',
    'alerts.apps.AlertsConfig',
    'location.apps.LocationConfig',
    'messaging.apps.MessagingConfig',
    'community.apps.CommunityConfig',
    'admin_panel.apps.AdminPanelConfig',
    'notifications.apps.NotificationsConfig',
]

MIDDLEWARE = [
    'corsheaders.middleware.CorsMiddleware',  # CORS middleware at top
    'django.middleware.security.SecurityMiddleware',
    'django.contrib.sessions.middleware.SessionMiddleware',
    'django.middleware.common.CommonMiddleware',
    'django.middleware.csrf.CsrfViewMiddleware',
    'django.contrib.auth.middleware.AuthenticationMiddleware',
    'django.contrib.messages.middleware.MessageMiddleware',
    'django.middleware.clickjacking.XFrameOptionsMiddleware',
]

ROOT_URLCONF = 'women_safety.urls'

TEMPLATES = [
    {
        'BACKEND': 'django.template.backends.django.DjangoTemplates',
        'DIRS': [BASE_DIR / 'templates'],
        'APP_DIRS': True,
        'OPTIONS': {
            'context_processors': [
                'django.template.context_processors.request',
                'django.contrib.auth.context_processors.auth',
                'django.contrib.messages.context_processors.messages',
            ],
        },
    },
]

WSGI_APPLICATION = 'women_safety.wsgi.application'

# Database Configuration
# Supports PostgreSQL with fallback to SQLite for local development
DB_ENGINE = config('DB_ENGINE', default='sqlite')

if DB_ENGINE == 'postgresql':
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.postgresql',
            'NAME': config('DB_NAME', default='women_safety_db'),
            'USER': config('DB_USER', default='postgres'),
            'PASSWORD': config('DB_PASSWORD', default='postgres'),
            'HOST': config('DB_HOST', default='localhost'),
            'PORT': config('DB_PORT', default='5432'),
        }
    }
else:
    DATABASES = {
        'default': {
            'ENGINE': 'django.db.backends.sqlite3',
            'NAME': BASE_DIR / 'db.sqlite3',
        }
    }

# Password validation
AUTH_PASSWORD_VALIDATORS = [
    {
        'NAME': 'django.contrib.auth.password_validation.UserAttributeSimilarityValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.MinimumLengthValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.CommonPasswordValidator',
    },
    {
        'NAME': 'django.contrib.auth.password_validation.NumericPasswordValidator',
    },
]

# Internationalization
LANGUAGE_CODE = 'en-us'
TIME_ZONE = 'UTC'
USE_I18N = True
USE_TZ = True

# Static files (CSS, JavaScript, Images)
STATIC_URL = '/static/'
STATIC_ROOT = BASE_DIR / 'staticfiles'
STATICFILES_DIRS = [
    BASE_DIR / 'static',
]

# Media files (User profile pictures, audio/video evidence)
MEDIA_URL = '/media/'
MEDIA_ROOT = BASE_DIR / 'media'

# Default primary key field type
DEFAULT_AUTO_FIELD = 'django.db.models.BigAutoField'

# Custom User Model
AUTH_USER_MODEL = 'accounts.CustomUser'

# REST Framework Configuration
REST_FRAMEWORK = {
    'DEFAULT_AUTHENTICATION_CLASSES': (
        'rest_framework_simplejwt.authentication.JWTAuthentication',
    ),
    'DEFAULT_PERMISSION_CLASSES': (
        'rest_framework.permissions.IsAuthenticated',
    ),
    # ── Rate Limiting / Throttling ─────────────────────────────────────────────
    # Global throttle applied to all API endpoints
    'DEFAULT_THROTTLE_CLASSES': [
        'rest_framework.throttling.AnonRateThrottle',   # unauthenticated requests
        'rest_framework.throttling.UserRateThrottle',   # authenticated requests
    ],
    'DEFAULT_THROTTLE_RATES': {
        'anon': '60/minute',    # Anonymous IPs: 60 requests/min
        'user': '300/minute',   # Logged-in users: 300 requests/min
        'login': '10/minute',   # Login endpoint: 10 attempts/min per IP
        'otp':   '3/10min',     # OTP endpoint: 3 requests per 10 minutes
    },
}


# Simple JWT Configuration
JWT_ACCESS_MINUTES = config('JWT_ACCESS_TOKEN_LIFETIME_MINUTES', default=60, cast=int)
JWT_REFRESH_DAYS = config('JWT_REFRESH_TOKEN_LIFETIME_DAYS', default=7, cast=int)

SIMPLE_JWT = {
    'ACCESS_TOKEN_LIFETIME': timedelta(minutes=JWT_ACCESS_MINUTES),
    'REFRESH_TOKEN_LIFETIME': timedelta(days=JWT_REFRESH_DAYS),
    'ROTATE_REFRESH_TOKENS': True,
    'BLACKLIST_AFTER_ROTATION': True,
    'UPDATE_LAST_LOGIN': True,
    'AUTH_HEADER_TYPES': ('Bearer',),
    'AUTH_HEADER_NAME': 'HTTP_AUTHORIZATION',
    'USER_ID_FIELD': 'id',
    'USER_ID_CLAIM': 'user_id',
}

# CORS Configuration
CORS_ALLOW_ALL_ORIGINS = config('CORS_ALLOW_ALL_ORIGINS', default=True, cast=bool)
CORS_ALLOW_CREDENTIALS = True

# ASGI & Channels Configuration
ASGI_APPLICATION = 'women_safety.asgi.application'

REDIS_HOST = config('REDIS_HOST', default='127.0.0.1')
REDIS_PORT = config('REDIS_PORT', default='6379')
REDIS_URL = config('REDIS_URL', default=f"redis://{REDIS_HOST}:{REDIS_PORT}/0")
USE_REDIS = config('USE_REDIS', default=False, cast=bool)

if USE_REDIS:
    CHANNEL_LAYERS = {
        'default': {
            'BACKEND': 'channels_redis.core.RedisChannelLayer',
            'CONFIG': {
                'hosts': [REDIS_URL],
            },
        },
    }
else:
    # In-memory channel layer for local development without standalone Redis server
    CHANNEL_LAYERS = {
        'default': {
            'BACKEND': 'channels.layers.InMemoryChannelLayer',
        },
    }
# Firebase Cloud Messaging (FCM) Configuration
FIREBASE_CREDENTIALS_PATH = config('FIREBASE_CREDENTIALS_PATH', default='')

# Email Configuration (Gmail SMTP — free, 500 emails/day)
# Set EMAIL_BACKEND=smtp in .env and provide Gmail App Password
EMAIL_BACKEND = config('EMAIL_BACKEND', default='django.core.mail.backends.console.EmailBackend')
EMAIL_HOST = config('EMAIL_HOST', default='smtp.gmail.com')
EMAIL_PORT = config('EMAIL_PORT', default=587, cast=int)
EMAIL_USE_TLS = config('EMAIL_USE_TLS', default=True, cast=bool)
EMAIL_HOST_USER = config('EMAIL_HOST_USER', default='')
EMAIL_HOST_PASSWORD = config('EMAIL_HOST_PASSWORD', default='')
DEFAULT_FROM_EMAIL = config('DEFAULT_FROM_EMAIL', default='SafeHer Emergency <alerts@safeher.org>')

# ─── Free SMS Providers ────────────────────────────────────────
# Fast2SMS — Indian free-tier SMS API (no credit card, sign up: fast2sms.com)
FAST2SMS_API_KEY = config('FAST2SMS_API_KEY', default='')

# MSG91 — Indian SMS API (free trial, sign up: msg91.com)
MSG91_AUTH_KEY = config('MSG91_AUTH_KEY', default='')
MSG91_SENDER_ID = config('MSG91_SENDER_ID', default='SFTHER')
MSG91_ROUTE = config('MSG91_ROUTE', default='4')

# ─── Free/Cheap Voice Call Provider ───────────────────────────
# VoIP.ms — very cheap ($0.01/min), free trial credits available
# Note: Android device calls via Intent.ACTION_CALL are FREE (uses SIM)
VOIPMS_USERNAME = config('VOIPMS_USERNAME', default='')
VOIPMS_API_PASSWORD = config('VOIPMS_API_PASSWORD', default='')
VOIPMS_DID = config('VOIPMS_DID', default='')


