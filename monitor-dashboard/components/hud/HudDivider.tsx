"use client";

import { motion } from "framer-motion";

export default function HudDivider({ className = "" }: { className?: string }) {
  return (
    <motion.div
      className={`relative h-[1px] ${className}`}
      style={{ background: "linear-gradient(90deg, transparent, rgba(0,255,255,0.3), transparent)" }}
      animate={{ opacity: [0.2, 0.5, 0.2] }}
      transition={{ duration: 1.5, repeat: Infinity, ease: "easeInOut" }}
    >
      <motion.div
        className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 w-1 h-1 rounded-full bg-[#00FFFF]"
        animate={{ opacity: [0.3, 0.7, 0.3], boxShadow: ["0 0 2px #00FFFF", "0 0 8px #00FFFF", "0 0 2px #00FFFF"] }}
        transition={{ duration: 1.5, repeat: Infinity, ease: "easeInOut" }}
      />
    </motion.div>
  );
}
