interface Bar {
  label: string;
  value: number;
}

interface Props {
  title: string;
  bars: Bar[];
  color?: string;
}

/**
 * A deliberately dependency-free bar chart rendered as SVG.
 * Good enough for pipeline counts, and nothing to audit in node_modules.
 */
export default function BarChart({ title, bars, color = '#2563eb' }: Props) {
  const width = 640;
  const height = 220;
  const padding = 36;
  const max = Math.max(1, ...bars.map((b) => b.value));
  const slot = bars.length > 0 ? (width - padding * 2) / bars.length : width;
  const barWidth = Math.min(64, slot * 0.6);

  return (
    <figure className="chart">
      <figcaption>{title}</figcaption>
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}>
        <line
          x1={padding}
          y1={height - padding}
          x2={width - padding}
          y2={height - padding}
          stroke="#94a3b8"
        />
        {bars.map((bar, i) => {
          const barHeight = (bar.value / max) * (height - padding * 2);
          const x = padding + i * slot + (slot - barWidth) / 2;
          const y = height - padding - barHeight;
          return (
            <g key={bar.label}>
              <rect x={x} y={y} width={barWidth} height={barHeight} fill={color} rx={4} />
              <text x={x + barWidth / 2} y={y - 6} textAnchor="middle" fontSize={13}>
                {bar.value}
              </text>
              <text
                x={x + barWidth / 2}
                y={height - padding + 18}
                textAnchor="middle"
                fontSize={11}
                fill="#475569"
              >
                {bar.label}
              </text>
            </g>
          );
        })}
      </svg>
    </figure>
  );
}
