const isFiniteNumber = (value) => typeof value === 'number' && Number.isFinite(value);

export const formatGpsCoordinate = (value) => {
    if (!isFiniteNumber(value)) return '-';
    return value.toFixed(5);
};

export const approximateGpsFromMap = (worldX, worldZ) => {
    if (!isFiniteNumber(worldX) || !isFiniteNumber(worldZ)) {
        return null;
    }

    const lat =
        35.8756013 +
        (worldX * 0.00000168155) +
        (worldZ * -0.0000024673);

    const lng =
        128.0492269 +
        (worldX * 0.000000072) +
        (worldZ * 0.000000907725);

    return { lat, lng };
};

export const getDisplayGps = ({ vehiclePose = null, vehicleLocation = null } = {}) => {
    if (vehiclePose && isFiniteNumber(vehiclePose.x) && isFiniteNumber(vehiclePose.z)) {
        return approximateGpsFromMap(vehiclePose.x, vehiclePose.z);
    }

    if (
        vehicleLocation?.source === 'pose'
        && isFiniteNumber(vehicleLocation.lat)
        && isFiniteNumber(vehicleLocation.lng)
    ) {
        return approximateGpsFromMap(vehicleLocation.lat, vehicleLocation.lng);
    }

    if (isFiniteNumber(vehicleLocation?.lat) && isFiniteNumber(vehicleLocation?.lng)) {
        return {
            lat: vehicleLocation.lat,
            lng: vehicleLocation.lng
        };
    }

    return null;
};
