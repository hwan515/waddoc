const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

export const worldToMinimap = (
    worldX,
    worldZ,
    worldWidth,
    worldHeight,
    pixelWidth,
    pixelHeight,
    options = {}
) => {
    const {
        origin = 'center',
        invertY = true,
        clampToBounds = true
    } = options;

    const normalizedX = origin === 'center'
        ? (worldX + (worldWidth / 2)) / worldWidth
        : worldX / worldWidth;

    const normalizedZ = origin === 'center'
        ? (worldZ + (worldHeight / 2)) / worldHeight
        : worldZ / worldHeight;

    const normalizedY = invertY ? 1 - normalizedZ : normalizedZ;

    const x = normalizedX * pixelWidth;
    const y = normalizedY * pixelHeight;

    return {
        x: clampToBounds ? clamp(x, 0, pixelWidth) : x,
        y: clampToBounds ? clamp(y, 0, pixelHeight) : y,
        normalizedX,
        normalizedY
    };
};

export const vehiclePoseToMinimap = (
    vehiclePose,
    worldWidth,
    worldHeight,
    pixelWidth,
    pixelHeight,
    options = {}
) => {
    if (!vehiclePose) return null;

    const minimapPoint = worldToMinimap(
        vehiclePose.x,
        vehiclePose.z,
        worldWidth,
        worldHeight,
        pixelWidth,
        pixelHeight,
        options
    );

    return {
        ...minimapPoint,
        yaw: vehiclePose.yaw ?? 0
    };
};

export const pathPointsToMinimap = (
    pathPoints = [],
    worldWidth,
    worldHeight,
    pixelWidth,
    pixelHeight,
    options = {}
) => {
    return pathPoints.map((point) => ({
        ...worldToMinimap(
            point.x,
            point.z,
            worldWidth,
            worldHeight,
            pixelWidth,
            pixelHeight,
            options
        ),
        source: point
    }));
};
