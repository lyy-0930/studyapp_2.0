import { ReactNode } from "react";

export default function GlowText({
  children,
  color = "#00FFFF",
  className = "",
}: {
  children: ReactNode;
  color?: string;
  className?: string;
}) {
  return (
    <span
      className={className}
      style={{
        color,
        textShadow: `0 0 10px ${color}, 0 0 20px ${color}44`,
      }}
    >
      {children}
    </span>
  );
}
