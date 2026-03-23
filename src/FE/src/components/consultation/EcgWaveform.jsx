const EcgWaveform = ({
    waveform = [],
    samplingHz = 25,
    durationSeconds = 8,
    className = '',
}) => {
    if (!waveform.length) {
        return (
            <div className={`rounded-3xl border border-white/10 bg-slate-950/60 p-6 text-center text-sm text-slate-300 ${className}`}>
                측정된 ECG 파형이 없습니다.
            </div>
        );
    }

    const width = 960;
    const height = 240;
    const padding = 16;
    const minValue = Math.min(...waveform);
    const maxValue = Math.max(...waveform);
    const safeMin = minValue === maxValue ? minValue - 1 : minValue;
    const safeMax = minValue === maxValue ? maxValue + 1 : maxValue;
    const amplitude = safeMax - safeMin;

    const points = waveform.map((value, index) => {
        const x = padding + (index / Math.max(waveform.length - 1, 1)) * (width - padding * 2);
        const normalized = (value - safeMin) / amplitude;
        const y = height - padding - normalized * (height - padding * 2);
        return `${x.toFixed(1)},${y.toFixed(1)}`;
    }).join(' ');

    return (
        <div className={`rounded-3xl border border-white/10 bg-slate-950/60 p-4 shadow-inner ${className}`}>
            <div className="mb-3 flex items-center justify-between gap-3 text-xs text-slate-300">
                <span className="font-semibold tracking-[0.25em] uppercase text-[#B9D6F2]">심전도 파형</span>
                <span>{samplingHz}Hz · {durationSeconds}초 · {waveform.length}개 샘플</span>
            </div>

            <svg viewBox={`0 0 ${width} ${height}`} className="h-56 w-full rounded-2xl bg-[#020817]">
                {Array.from({ length: 7 }).map((_, index) => {
                    const x = padding + (index / 6) * (width - padding * 2);
                    return (
                        <line
                            key={`grid-x-${index}`}
                            x1={x}
                            y1={padding}
                            x2={x}
                            y2={height - padding}
                            stroke="rgba(148, 163, 184, 0.12)"
                            strokeWidth="1"
                        />
                    );
                })}
                {Array.from({ length: 5 }).map((_, index) => {
                    const y = padding + (index / 4) * (height - padding * 2);
                    return (
                        <line
                            key={`grid-y-${index}`}
                            x1={padding}
                            y1={y}
                            x2={width - padding}
                            y2={y}
                            stroke="rgba(148, 163, 184, 0.12)"
                            strokeWidth="1"
                        />
                    );
                })}
                <polyline
                    fill="none"
                    stroke="#67E8F9"
                    strokeWidth="3"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    points={points}
                />
            </svg>
        </div>
    );
};

export default EcgWaveform;
