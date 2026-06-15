"use client";

import { motion } from "framer-motion";

export default function HudCorner() {
  return (
    <div className="fixed inset-0 z-[3] pointer-events-none">
      {/* Top-Left */}
      <motion.div
        className="absolute top-3 left-3"
        animate={{ opacity: [0.15, 0.35, 0.15] }}
        transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut" }}
      >
        <svg width="40" height="40">
          <line x1="0" y1="30" x2="0" y2="0" stroke="#00FFFF" strokeWidth={1} />
          <line x1="0" y1="0" x2="30" y2="0" stroke="#00FFFF" strokeWidth={1} />
        </svg>
      </motion.div>

      {/* Top-Right */}
      <motion.div
        className="absolute top-3 right-3"
        animate={{ opacity: [0.15, 0.35, 0.15] }}
        transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut", delay: 0.3 }}
      >
        <svg width="40" height="40">
          <line x1="40" y1="30" x2="40" y2="0" stroke="#00FFFF" strokeWidth={1} />
          <line x1="10" y1="0" x2="40" y2="0" stroke="#00FFFF" strokeWidth={1} />
        </svg>
      </motion.div>

      {/* Bottom-Left */}
      <motion.div
        className="absolute bottom-3 left-3"
        animate={{ opacity: [0.15, 0.35, 0.15] }}
        transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut", delay: 0.6 }}
      >
        <svg width="40" height="40">
          <line x1="0" y1="10" x2="0" y2="40" stroke="#00FFFF" strokeWidth={1} />
          <line x1="0" y1="40" x2="30" y2="40" stroke="#00FFFF" strokeWidth={1} />
        </svg>
      </motion.div>

      {/* Bottom-Right */}
      <motion.div
        className="absolute bottom-3 right-3"
        animate={{ opacity: [0.15, 0.35, 0.15] }}
        transition={{ duration: 2.5, repeat: Infinity, ease: "easeInOut", delay: 0.9 }}
      >
        <svg width="40" height="40">
          <line x1="40" y1="10" x2="40" y2="40" stroke="#00FFFF" strokeWidth={1} />
          <line x1="10" y1="40" x2="40" y2="40" stroke="#00FFFF" strokeWidth={1} />
        </svg>
      </motion.div>
    </div>
  );
}
