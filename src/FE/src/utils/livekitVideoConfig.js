import { VideoPresets } from 'livekit-client';

// Use ideal browser constraints so cameras that cannot do 1080p can still fall back cleanly.
export const LIVEKIT_HIGH_QUALITY_VIDEO_CONSTRAINTS = {
    deviceId: 'default',
    width: { ideal: 1920, max: 1920 },
    height: { ideal: 1080, max: 1080 },
    frameRate: { ideal: 30, max: 30 },
    aspectRatio: 16 / 9,
};

export const LIVEKIT_HIGH_QUALITY_ROOM_OPTIONS = {
    adaptiveStream: true,
    dynacast: true,
    publishDefaults: {
        simulcast: true,
        videoEncoding: VideoPresets.h1080.encoding,
        videoSimulcastLayers: [VideoPresets.h360, VideoPresets.h720],
        degradationPreference: 'maintain-resolution',
    },
};
