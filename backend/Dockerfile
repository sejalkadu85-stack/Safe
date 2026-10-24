# ============================================================
# SafeHer Backend — Dockerfile
# Python 3.14 / Django 6.1 / Daphne ASGI
# ============================================================

FROM python:3.12-slim

# System deps
RUN apt-get update && apt-get install -y \
    build-essential \
    libpq-dev \
    gcc \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Working directory
WORKDIR /app

# Install Python dependencies first (layer caching)
COPY requirements.txt .
RUN pip install --upgrade pip && pip install -r requirements.txt

# Copy project files
COPY . .

# Collect static files
RUN python manage.py collectstatic --noinput || true

# Create media directory
RUN mkdir -p /app/media

# Expose ASGI port
EXPOSE 8000

# Default command — run via Daphne (ASGI for WebSocket support)
CMD ["daphne", "-b", "0.0.0.0", "-p", "8000", "women_safety.asgi:application"]
