"""
SafeHer — Login Rate Limiting & Brute-Force Protection
========================================================
Uses Django REST Framework's built-in throttling (no extra packages needed).

Rules enforced:
  - Max 5 failed login attempts per IP per 15 minutes → 429 Too Many Requests
  - Max 5 failed login attempts per username/email per 15 minutes → 429
  - After 10 consecutive failures from same IP → 1 hour lockout
  - Successful login resets the failure counter for that user
  - All blocked attempts are logged with timestamp, IP, and identifier

No Redis required — uses Django's cache (in-memory by default).
"""

import time
import logging
from django.core.cache import cache
from rest_framework.throttling import SimpleRateThrottle

logger = logging.getLogger(__name__)

# ─── Configuration ─────────────────────────────────────────────────────────────
LOGIN_MAX_ATTEMPTS   = 5        # Failures allowed before soft block
LOGIN_WINDOW_SECONDS = 15 * 60  # 15-minute sliding window
LOGIN_HARD_LOCKOUT_ATTEMPTS = 10   # Failures before 1-hour hard lockout
LOGIN_HARD_LOCKOUT_SECONDS  = 60 * 60  # 1 hour
# ────────────────────────────────────────────────────────────────────────────────


def _get_client_ip(request) -> str:
    """Extract real client IP, respecting proxies."""
    x_forwarded_for = request.META.get('HTTP_X_FORWARDED_FOR')
    if x_forwarded_for:
        return x_forwarded_for.split(',')[0].strip()
    return request.META.get('REMOTE_ADDR', '0.0.0.0')


def _ip_cache_key(ip: str) -> str:
    return f"login_fail_ip:{ip}"


def _user_cache_key(identifier: str) -> str:
    safe = identifier.lower().replace('@', '_at_').replace('.', '_').replace('+', '_')
    return f"login_fail_user:{safe}"


def _hard_lockout_key(ip: str) -> str:
    return f"login_lockout_ip:{ip}"


# ─── Public API used by LoginView ──────────────────────────────────────────────

def _safe_ttl(key: str, default: int) -> int:
    """Get cache TTL safely — LocMemCache doesn't support .ttl(), so fall back to default."""
    try:
        ttl = cache.ttl(key)
        return ttl if ttl is not None and ttl > 0 else default
    except AttributeError:
        return default


def check_login_rate_limit(request, identifier: str) -> dict:
    """
    Call this BEFORE processing login credentials.

    Returns:
        {'allowed': True}                          — proceed normally
        {'allowed': False, 'detail': str,
         'retry_after_seconds': int}               — reject with 429
    """
    ip = _get_client_ip(request)

    # 1. Hard lockout check (1-hour block after 10 consecutive failures)
    hard_key = _hard_lockout_key(ip)
    hard_block_until = cache.get(hard_key)
    if hard_block_until:
        remaining = int(hard_block_until - time.time())
        if remaining > 0:
            logger.warning(
                f"[RATE LIMIT] Hard lockout — IP: {ip} blocked for {remaining}s"
            )
            return {
                'allowed': False,
                'detail': (
                    f"Too many failed login attempts. "
                    f"Your IP has been temporarily locked for security. "
                    f"Try again in {_format_duration(remaining)}."
                ),
                'retry_after_seconds': remaining,
            }
        else:
            cache.delete(hard_key)

    # 2. Soft IP-based rate limit (5 fails / 15 min)
    ip_key = _ip_cache_key(ip)
    ip_attempts = _get_attempts(ip_key)
    if ip_attempts >= LOGIN_MAX_ATTEMPTS:
        ttl = _safe_ttl(ip_key, LOGIN_WINDOW_SECONDS)
        logger.warning(
            f"[RATE LIMIT] IP soft block — IP: {ip}, attempts: {ip_attempts}"
        )
        return {
            'allowed': False,
            'detail': (
                f"Too many login attempts from your network. "
                f"Please wait {_format_duration(ttl)} before trying again."
            ),
            'retry_after_seconds': ttl,
        }

    # 3. Per-user/identifier rate limit (5 fails / 15 min)
    user_key = _user_cache_key(identifier)
    user_attempts = _get_attempts(user_key)
    if user_attempts >= LOGIN_MAX_ATTEMPTS:
        ttl = _safe_ttl(user_key, LOGIN_WINDOW_SECONDS)
        logger.warning(
            f"[RATE LIMIT] User soft block — identifier: {identifier}, attempts: {user_attempts}"
        )
        return {
            'allowed': False,
            'detail': (
                f"Too many failed login attempts for this account. "
                f"Please wait {_format_duration(ttl)} before trying again."
            ),
            'retry_after_seconds': ttl,
        }

    return {'allowed': True}


def record_login_failure(request, identifier: str):
    """
    Call this when login fails (wrong password / user not found).
    Increments failure counters for both IP and identifier.
    Escalates to hard lockout when threshold is crossed.
    """
    ip = _get_client_ip(request)

    ip_key   = _ip_cache_key(ip)
    user_key = _user_cache_key(identifier)

    ip_count   = _increment(ip_key,   LOGIN_WINDOW_SECONDS)
    user_count = _increment(user_key, LOGIN_WINDOW_SECONDS)

    logger.warning(
        f"[LOGIN FAILED] IP: {ip} ({ip_count} attempts) | "
        f"Identifier: {identifier} ({user_count} attempts)"
    )

    # Escalate to hard lockout if IP has too many failures
    if ip_count >= LOGIN_HARD_LOCKOUT_ATTEMPTS:
        hard_key = _hard_lockout_key(ip)
        cache.set(hard_key, time.time() + LOGIN_HARD_LOCKOUT_SECONDS, timeout=LOGIN_HARD_LOCKOUT_SECONDS)
        logger.warning(
            f"[RATE LIMIT] ⛔ Hard lockout applied to IP: {ip} for 1 hour"
        )


def record_login_success(request, identifier: str):
    """
    Call this when login succeeds.
    Resets failure counters for the user so they don't get locked out
    after recovering their password.
    """
    ip = _get_client_ip(request)
    cache.delete(_ip_cache_key(ip))
    cache.delete(_user_cache_key(identifier))
    logger.info(f"[LOGIN SUCCESS] IP: {ip} | Identifier: {identifier} — counters reset")


# ─── Helpers ───────────────────────────────────────────────────────────────────

def _get_attempts(key: str) -> int:
    return int(cache.get(key) or 0)


def _increment(key: str, window: int) -> int:
    """Atomic increment with TTL preservation (only sets TTL on first write)."""
    try:
        count = cache.incr(key)
    except ValueError:
        # Key doesn't exist yet
        cache.set(key, 1, timeout=window)
        count = 1
    return count


def _format_duration(seconds: int) -> str:
    """Human-readable duration for error messages."""
    if seconds >= 3600:
        return f"{seconds // 3600} hour(s)"
    if seconds >= 60:
        return f"{seconds // 60} minute(s)"
    return f"{seconds} second(s)"


# ─── DRF Throttle Class (for global API throttling, optional) ──────────────────

class LoginRateThrottle(SimpleRateThrottle):
    """
    DRF throttle class for the login endpoint.
    Rate: 10 requests per minute per IP (covers non-failure spam too).
    Can be applied via view's throttle_classes.
    """
    scope = 'login'

    def get_cache_key(self, request, view):
        return f"throttle_login_{self.get_ident(request)}"
