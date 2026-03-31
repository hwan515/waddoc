import { useId } from 'react';

const EcgWaveform = ({
    waveform = [],
    samplingHz = 25,
    durationSeconds = 8,
    className = '',
    compact = false,
}) => {
    const svgId = useId().replace(/:/g, '');

    if (!waveform.length) {
        return (
            <div className={`rounded-3xl border border-white/10 bg-slate-950/60 p-6 text-center text-sm text-slate-300 ${className}`}>
                측정된 ECG 파형이 없습니다.
            </div>
        );
    }

    const width = 960;
    const height = compact ? 300 : 320;
    const padding = compact ? 10 : 12;
    const plotWidth = width - padding * 2;
    const plotHeight = height - padding * 2;
    const minorGridSize = compact ? 10 : 12;
    const majorGridStep = minorGridSize * 5;
    const minValue = Math.min(...waveform);
    const maxValue = Math.max(...waveform);
    const safeMin = minValue === maxValue ? minValue - 1 : minValue;
    const safeMax = minValue === maxValue ? maxValue + 1 : maxValue;
    const amplitude = safeMax - safeMin;
    const baselineY = padding + plotHeight / 2;
    const minorPatternId = `ecg-minor-${svgId}`;
    const majorPatternId = `ecg-major-${svgId}`;

    const points = waveform.map((value, index) => {
        const x = padding + (index / Math.max(waveform.length - 1, 1)) * plotWidth;
        const normalized = (value - safeMin) / amplitude;
        const y = height - padding - normalized * plotHeight;
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');

    return (
        <div className={`rounded-3xl border border-white/10 bg-slate-950/60 ${compact ? 'p-3' : 'p-4'} shadow-inner ${className}`}>
            <div className={`mb-3 flex items-center justify-between gap-3 text-slate-300 ${compact ? 'text-[11px]' : 'text-xs'}`}>
                <span className="font-semibold tracking-[0.25em] uppercase text-[#B9D6F2]">심전도 파형</span>
                <span>{samplingHz}Hz · {durationSeconds}초 · {waveform.length}개 샘플</span>
            </div>

            <svg
                viewBox={`0 0 ${width} ${height}`}
                preserveAspectRatio="none"
                className={`w-full rounded-2xl bg-[#fcfdff] ${compact ? 'aspect-[16/5]' : 'aspect-[3/1]'}`}
            >
                <defs>
                    <pattern
                        id={minorPatternId}
                        width={minorGridSize}
                        height={minorGridSize}
                        patternUnits="userSpaceOnUse"
                    >
                        <path
                            d={`M ${minorGridSize} 0 L 0 0 0 ${minorGridSize}`}
                            fill="none"
                            stroke="rgba(59, 130, 246, 0.18)"
                            strokeWidth="1"
                        />
                    </pattern>
                    <pattern
                        id={majorPatternId}
                        width={majorGridStep}
                        height={majorGridStep}
                        patternUnits="userSpaceOnUse"
                    >
                        <rect width={majorGridStep} height={majorGridStep} fill={`url(#${minorPatternId})`} />
                        <path
                            d={`M ${majorGridStep} 0 L 0 0 0 ${majorGridStep}`}
                            fill="none"
                            stroke="rgba(37, 99, 235, 0.3)"
                            strokeWidth="1.4"
                        />
                    </pattern>
                </defs>
                <rect
                    x={padding}
                    y={padding}
                    width={plotWidth}
                    height={plotHeight}
                    rx="10"
                    fill="#fdfefe"
                    stroke="rgba(37, 99, 235, 0.12)"
                    strokeWidth="1"
                />
                <rect
                    x={padding}
                    y={padding}
                    width={plotWidth}
                    height={plotHeight}
                    rx="10"
                    fill={`url(#${majorPatternId})`}
                />
                <line
                    x1={padding}
                    y1={baselineY}
                    x2={width - padding}
                    y2={baselineY}
                    stroke="rgba(29, 78, 216, 0.24)"
                    strokeWidth="1.4"
                />
                <polyline
                    fill="none"
                    stroke="#111827"
                    strokeWidth={compact ? '2.8' : '3.2'}
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    points={points}
                />
            </svg>
        </div>
    );
};

export default EcgWaveform;
