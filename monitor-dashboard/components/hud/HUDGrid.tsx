"use client";

import { useEffect, useState } from "react";

export default function HUDGrid() {
  const [size, setSize] = useState({ w: 1200, h: 800 });

  useEffect(() => {
    const onResize = () => setSize({ w: window.innerWidth, h: window.innerHeight });
    onResize();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  const cx = size.w / 2;
  const cy = size.h / 2;
  const maxR = Math.max(size.w, size.h) * 0.55;

  return (
    <svg
      className="fixed inset-0 z-[1] pointer-events-none"
      width={size.w}
      height={size.h}
      style={{ opacity: 0.06 }}
    >
      <defs>
        <radialGradient id="gridFade" cx="50%" cy="50%" r="50%">
          <stop offset="0%" stopColor="#00FFFF" stopOpacity="1" />
          <stop offset="100%" stopColor="#00FFFF" stopOpacity="0" />
        </radialGradient>
      </defs>

      {/* Concentric circles */}
      {[1, 2, 3, 4].map((i) => (
        <circle
          key={`c${i}`}
          cx={cx}
          cy={cy}
          r={(maxR * i) / 4}
          fill="none"
          stroke="#00FFFF"
          strokeWidth={0.5}
          opacity={1 - i * 0.15}
        />
      ))}

      {/* Radial lines */}
      {Array.from({ length: 12 }).map((_, i) => {
        const angle = (Math.PI * 2 * i) / 12;
        const x = cx + maxR * Math.cos(angle);
        const y = cy + maxR * Math.sin(angle);
        return (
          <line
            key={`r${i}`}
            x1={cx}
            y1={cy}
            x2={x}
            y2={y}
            stroke="#00FFFF"
            strokeWidth={0.5}
            opacity={0.3}
          />
        );
      })}
    </svg>
  );
}
