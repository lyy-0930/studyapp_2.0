"use client";

import { useRef } from "react";
import { Canvas, useFrame } from "@react-three/fiber";
import * as THREE from "three";

const STARS = 2000;

function Stars() {
  const ref = useRef<THREE.Points>(null!);
  const positions = new Float32Array(STARS * 3);
  for (let i = 0; i < STARS * 3; i++) {
    positions[i] = (Math.random() - 0.5) * 200;
  }
  return (
    <points ref={ref}>
      <bufferGeometry>
        <bufferAttribute
          attach="attributes-position"
          args={[positions, 3]}
          count={STARS}
          array={positions}
          itemSize={3}
        />
      </bufferGeometry>
      <pointsMaterial size={0.15} color="#64748B" transparent opacity={0.6} sizeAttenuation />
    </points>
  );
}

function RotatingSphere() {
  const meshRef = useRef<THREE.Mesh>(null!);
  const glowRef = useRef<THREE.Mesh>(null!);

  useFrame((state) => {
    const t = state.clock.elapsedTime;
    meshRef.current.rotation.y = t * 0.3;
    meshRef.current.rotation.x = Math.sin(t * 0.08) * 0.1;
    glowRef.current.rotation.y = t * 0.2;
  });

  return (
    <group>
      {/* Main sphere */}
      <mesh ref={meshRef}>
        <sphereGeometry args={[1.8, 64, 64]} />
        <meshPhongMaterial
          color="#0088FF"
          emissive="#0044AA"
          emissiveIntensity={0.3}
          shininess={30}
          specular="#00D4FF"
          transparent
          opacity={0.85}
        />
      </mesh>
      {/* Atmosphere glow */}
      <mesh ref={glowRef}>
        <sphereGeometry args={[1.95, 48, 48]} />
        <meshBasicMaterial
          color="#00D4FF"
          transparent
          opacity={0.12}
          side={THREE.BackSide}
        />
      </mesh>
      {/* Outer ring 1 - horizontal */}
      <mesh rotation={[0, 0, 0]}>
        <ringGeometry args={[2.1, 2.15, 80]} />
        <meshBasicMaterial color="#00D4FF" transparent opacity={0.25} side={THREE.DoubleSide} />
      </mesh>
      {/* Outer ring 2 - tilted */}
      <mesh rotation={[Math.PI / 3, 0, 0]}>
        <ringGeometry args={[2.3, 2.35, 80]} />
        <meshBasicMaterial color="#00D4FF" transparent opacity={0.15} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}

interface ThreeSceneProps {
  className?: string;
}

export default function ThreeScene({ className }: ThreeSceneProps) {
  return (
    <div className={className ?? "w-full h-full"}>
      <Canvas
        camera={{ position: [0, 2, 5.5], fov: 50 }}
        gl={{ antialias: true, alpha: true }}
        style={{ background: "transparent" }}
      >
        <ambientLight intensity={0.4} />
        <directionalLight position={[5, 5, 5]} intensity={1.2} />
        <directionalLight position={[-3, -2, 4]} intensity={0.4} color="#0080FF" />
        <Stars />
        <RotatingSphere />
      </Canvas>
    </div>
  );
}
