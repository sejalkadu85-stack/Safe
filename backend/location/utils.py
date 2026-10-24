import math


def haversine_distance(lat1, lon1, lat2, lon2):
    """
    Calculate the great circle distance between two points
    on the earth (specified in decimal degrees).
    Returns distance in meters.
    """
    # Convert decimal degrees to radians
    lat1_rad = math.radians(float(lat1))
    lon1_rad = math.radians(float(lon1))
    lat2_rad = math.radians(float(lat2))
    lon2_rad = math.radians(float(lon2))

    # Haversine formula
    dlon = lon2_rad - lon1_rad
    dlat = lat2_rad - lat1_rad

    a = math.sin(dlat / 2)**2 + math.cos(lat1_rad) * math.cos(lat2_rad) * math.sin(dlon / 2)**2
    c = 2 * math.asin(math.sqrt(a))

    # Radius of earth in meters: 6,371,000 meters
    radius_meters = 6371000
    return c * radius_meters


def is_inside_geofence(point_lat, point_lon, center_lat, center_lon, radius_meters):
    """
    Returns True if (point_lat, point_lon) is within radius_meters of (center_lat, center_lon).
    """
    distance = haversine_distance(point_lat, point_lon, center_lat, center_lon)
    return distance <= radius_meters


def decode_polyline(polyline_str):
    """
    Decodes an encoded Google Polyline string into a list of (lat, lng) tuples.
    """
    index, lat, lng = 0, 0, 0
    coordinates = []
    length = len(polyline_str)

    while index < length:
        # Decode latitude
        shift, result = 0, 0
        while True:
            byte = ord(polyline_str[index]) - 63
            index += 1
            result |= (byte & 0x1f) << shift
            shift += 5
            if byte < 0x20:
                break
        dlat = ~(result >> 1) if (result & 1) else (result >> 1)
        lat += dlat

        # Decode longitude
        shift, result = 0, 0
        while True:
            byte = ord(polyline_str[index]) - 63
            index += 1
            result |= (byte & 0x1f) << shift
            shift += 5
            if byte < 0x20:
                break
        dlng = ~(result >> 1) if (result & 1) else (result >> 1)
        lng += dlng

        coordinates.append((lat * 1e-5, lng * 1e-5))

    return coordinates


def parse_polyline_points(route_polyline):
    """
    Accepts either a JSON string of [[lat, lng], ...], a list of coordinates,
    or a Google encoded polyline string, and returns a list of (lat, lng) tuples.
    """
    if not route_polyline:
        return []
    
    if isinstance(route_polyline, list):
        parsed = []
        for pt in route_polyline:
            if isinstance(pt, (list, tuple)) and len(pt) >= 2:
                parsed.append((float(pt[0]), float(pt[1])))
        return parsed

    import json
    if isinstance(route_polyline, str):
        trimmed = route_polyline.strip()
        if trimmed.startswith('[') and trimmed.endswith(']'):
            try:
                pts = json.loads(trimmed)
                return [(float(p[0]), float(p[1])) for p in pts if len(p) >= 2]
            except Exception:
                pass
        # Try decoding as encoded polyline
        try:
            return decode_polyline(trimmed)
        except Exception:
            return []
    return []


def point_to_segment_distance(plat, plon, lat1, lon1, lat2, lon2):
    """
    Calculates shortest distance in meters from (plat, plon) to the segment
    between (lat1, lon1) and (lat2, lon2).
    """
    p_lat, p_lon = float(plat), float(plon)
    a_lat, a_lon = float(lat1), float(lon1)
    b_lat, b_lon = float(lat2), float(lon2)

    # Convert to local Cartesian coordinates centered at (a_lat, a_lon)
    # 1 deg latitude ≈ 111,320 meters
    # 1 deg longitude ≈ 111,320 * cos(lat) meters
    mid_lat_rad = math.radians((a_lat + b_lat) / 2.0)
    cos_lat = math.cos(mid_lat_rad)

    dx = (b_lon - a_lon) * 111320.0 * cos_lat
    dy = (b_lat - a_lat) * 111320.0

    seg_len_sq = dx * dx + dy * dy
    if seg_len_sq == 0:
        return haversine_distance(p_lat, p_lon, a_lat, a_lon)

    # Point relative to A
    px = (p_lon - a_lon) * 111320.0 * cos_lat
    py = (p_lat - a_lat) * 111320.0

    # Projection factor t
    t = (px * dx + py * dy) / seg_len_sq
    t = max(0.0, min(1.0, t))

    # Closest point on segment
    closest_lat = a_lat + t * (b_lat - a_lat)
    closest_lon = a_lon + t * (b_lon - a_lon)

    return haversine_distance(p_lat, p_lon, closest_lat, closest_lon)


def distance_to_polyline(plat, plon, route_polyline):
    """
    Returns the minimum distance in meters from (plat, plon) to any segment
    in the route polyline.
    """
    points = parse_polyline_points(route_polyline)
    if not points:
        return float('inf')
    if len(points) == 1:
        return haversine_distance(plat, plon, points[0][0], points[0][1])

    min_dist = float('inf')
    for i in range(len(points) - 1):
        d = point_to_segment_distance(
            plat, plon,
            points[i][0], points[i][1],
            points[i+1][0], points[i+1][1]
        )
        if d < min_dist:
            min_dist = d

    return min_dist

