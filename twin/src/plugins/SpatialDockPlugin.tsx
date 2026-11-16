import { Suspense, useEffect, useRef, useState } from "react";
import { Canvas } from "@react-three/fiber";
import { Float, RoundedBox } from "@react-three/drei";
import { motion } from "motion/react";
import VanillaTilt from "vanilla-tilt";
import { canUseWebGL } from "../twin/ShaderView";
import { usePageVisible } from "../twin/usePageVisible";
import { useReducedMotion } from "../twin/useReducedMotion";
import type { PluginPanelProps } from "../twin/types";
import { ActionRow, PluginIntro, TargetSwitch } from "./ui";

const isTest = import.meta.env.MODE === "test";
const MOTION_PRESETS = ["still", "float", "tilt", "spring"] as const;
type MotionPreset = (typeof MOTION_PRESETS)[number];

function Scene({ motionPreset }: { motionPreset: MotionPreset }) {
  const float = motionPreset === "float" || motionPreset === "spring";
  return (
    <>
      <ambientLight intensity={1.6} />
      <directionalLight position={[3, 4, 5]} intensity={2.2} color="#fafaf7" />
      <directionalLight position={[-4, -2, 2]} intensity={0.8} color="#5a5a56" />
      <Float speed={float ? 1.6 : 0} rotationIntensity={float ? 0.35 : 0} floatIntensity={float ? 0.6 : 0}>
        <group rotation={[0.12, -0.35, 0.04]}>
          <RoundedBox args={[2.8, 1.7, 0.18]} radius={0.08} smoothness={4} position={[0, 0.15, 0]}>
            <meshStandardMaterial color="#111111" roughness={0.4} metalness={0.05} />
          </RoundedBox>
          <RoundedBox args={[2.3, 0.45, 0.2]} radius={0.08} smoothness={4} position={[0, -0.65, 0.18]}>
            <meshStandardMaterial color="#e7e6e1" roughness={0.8} />
          </RoundedBox>
          <mesh position={[-0.9, 0.35, 0.2]}>
            <circleGeometry args={[0.18, 32]} />
            <meshBasicMaterial color="#fafaf7" />
          </mesh>
          <mesh position={[0.95, 0.35, 0.2]}>
            <ringGeometry args={[0.16, 0.22, 32]} />
            <meshBasicMaterial color="#fafaf7" />
          </mesh>
        </group>
      </Float>
    </>
  );
}

function TiltCards({ motionPreset }: { motionPreset: MotionPreset }) {
  const first = useRef<HTMLDivElement>(null);
  const second = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (isTest) return;
    const nodes = [first.current, second.current].filter((node): node is HTMLDivElement => Boolean(node));
    nodes.forEach((node) =>
      VanillaTilt.init(node, {
        max: motionPreset === "tilt" ? 14 : 4,
        speed: 500,
        glare: true,
        "max-glare": 0.16,
        gyroscope: false,
      }),
    );
  }, [motionPreset]);

  const transition = motionPreset === "spring" ? { type: "spring" as const, stiffness: 180, damping: 16 } : { duration: 0.25 };

  return (
    <div className="tilt-row" data-testid="spatial-tilt-cards">
      <motion.div ref={first} className="tilt-card tilt-card-plum" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={transition}>
        <small>01 / PLATE</small>
        <strong>PRINT<br />ROOM</strong>
        <span className="tilt-line" />
      </motion.div>
      <motion.div ref={second} className="tilt-card tilt-card-paper" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ ...transition, delay: 0.04 }}>
        <small>02 / SHEET</small>
        <strong>OPEN<br />STUDIO</strong>
        <span className="tilt-line" />
      </motion.div>
    </div>
  );
}

export default function SpatialDockPlugin({ target, setTarget, apply }: PluginPanelProps) {
  const [motionPreset, setMotionPreset] = useState<MotionPreset>("float");
  const pageVisible = usePageVisible();
  const reduceMotion = useReducedMotion();
  const useScene = canUseWebGL() && !isTest;

  return (
    <div className="plugin-body">
      <PluginIntro>
        A small object on a workbench. The dock is local geometry, lit by two paper lights; the cards below tilt under your pointer.
      </PluginIntro>
      <TargetSwitch value={target} onChange={setTarget} />
      <div className="spatial-dock" data-testid="spatial-dock">
        {useScene ? (
          <Suspense fallback={<div className="spatial-fallback">Preparing the object…</div>}>
            <Canvas frameloop={pageVisible && !reduceMotion ? "always" : "never"} dpr={[1, 1.5]} camera={{ position: [0, 0, 5], fov: 35 }} gl={{ antialias: true, alpha: true }}>
              <Scene motionPreset={motionPreset} />
            </Canvas>
          </Suspense>
        ) : (
          <div className="spatial-fallback" data-testid="spatial-fallback">
            <span className="spatial-fallback-object" />
            <span>Local R3F scene</span>
          </div>
        )}
      </div>
      <TiltCards motionPreset={motionPreset} />
      <div className="motion-presets" role="group" aria-label="Motion preset">
        {MOTION_PRESETS.map((preset) => (
          <button type="button" key={preset} aria-pressed={motionPreset === preset} onClick={() => setMotionPreset(preset)}>
            {preset}
          </button>
        ))}
      </div>
      <ActionRow>
        <button type="button" className="button primary" onClick={() => apply({ kind: "pattern", preset: "circuit", controls: { ink: "#111111", opacity: 0.22 } })}>
          Send a motion ground
        </button>
      </ActionRow>
    </div>
  );
}
