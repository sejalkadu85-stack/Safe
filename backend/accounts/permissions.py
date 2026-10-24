from rest_framework import permissions


class IsWoman(permissions.BasePermission):
    """
    Permission check: Allows access only to authenticated users with the 'woman' role.
    Used for features like SOS trigger, Safe Journey initiation, and sound monitoring.
    """
    message = "Access restricted: This feature is only accessible to users with the 'woman' role."

    def has_permission(self, request, view):
        return bool(
            request.user and
            request.user.is_authenticated and
            request.user.role == 'woman'
        )


class IsGuardian(permissions.BasePermission):
    """
    Permission check: Allows access to authenticated users with the 'guardian' role.
    Used for viewing live location streams and acknowledging distress alerts.
    """
    message = "Access restricted: This feature is only accessible to designated Guardians."

    def has_permission(self, request, view):
        return bool(
            request.user and
            request.user.is_authenticated and
            request.user.role == 'guardian'
        )


class IsVolunteer(permissions.BasePermission):
    """
    Permission check: Allows access to verified community volunteers.
    """
    message = "Access restricted: This feature is only accessible to registered Volunteers."

    def has_permission(self, request, view):
        return bool(
            request.user and
            request.user.is_authenticated and
            request.user.role == 'volunteer'
        )


class IsAdminUser(permissions.BasePermission):
    """
    Permission check: Allows access only to system Administrators (role='admin' or staff/superuser).
    """
    message = "Access restricted: Administrator privileges are required for this action."

    def has_permission(self, request, view):
        return bool(
            request.user and
            request.user.is_authenticated and
            (request.user.role == 'admin' or request.user.is_staff or request.user.is_superuser)
        )


class IsWomanOrGuardian(permissions.BasePermission):
    """
    Permission check: Allows access to either a Woman or a Guardian.
    """
    message = "Access restricted: Must be a registered Woman user or Guardian."

    def has_permission(self, request, view):
        return bool(
            request.user and
            request.user.is_authenticated and
            request.user.role in ['woman', 'guardian', 'admin']
        )


class IsOwnerOrAdmin(permissions.BasePermission):
    """
    Object-level permission check: Only the owner of the object or an Admin can modify it.
    """
    message = "You do not have permission to modify this resource."

    def has_object_permission(self, request, view, obj):
        # Admin always has permission
        if request.user.role == 'admin' or request.user.is_staff or request.user.is_superuser:
            return True

        # Check if the object has user or owner attribute
        owner = getattr(obj, 'user', None) or getattr(obj, 'owner', None)
        return owner == request.user
