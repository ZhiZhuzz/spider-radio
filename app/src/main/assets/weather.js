// Canvas weather lifecycle; independent of playback and sheet rendering.
// Keep the animation composited separately from the illustration.
const worlds = [...document.querySelectorAll(".world")],
  layers = worlds.map((w, i) => ({
    canvas: w.querySelector("canvas"),
    ctx: w.querySelector("canvas").getContext("2d"),
    i,
    particles: Array.from({ length: i === 3 ? 44 : 55 }, () => ({
      x: Math.random(),
      y: Math.random(),
      z: 0.3 + Math.random() * 0.7,
      seed: Math.random() * 100,
      speed: 0.4 + Math.random(),
    })),
  }));
function resize() {
  let d = Math.min(devicePixelRatio || 1, 1.5);
  for (let l of layers) {
    l.w = innerWidth * 1.04;
    l.h = innerHeight * 1.04;
    l.canvas.width = l.w * d;
    l.canvas.height = l.h * d;
    l.ctx.setTransform(d, 0, 0, d, 0, 0);
  }
}
addEventListener("resize", resize);
resize();
let prev = 0,
  animTime = 0;
const reduced = matchMedia("(prefers-reduced-motion: reduce)");
let animationFrame = 0,
  nativeVisible = true;
function updateAnimation() {
  cancelAnimationFrame(animationFrame);
  animationFrame = 0;
  document.body.classList.toggle("eco", !!cfg.eco);
  if (document.hidden || !nativeVisible || cfg.eco || reduced.matches) {
    layers.forEach((l) => l.ctx.clearRect(0, 0, l.w, l.h));
    return;
  }
  prev = performance.now();
  animationFrame = requestAnimationFrame(draw);
}
function nativeVisibility(visible) {
  nativeVisible = visible;
  updateAnimation();
}
document.addEventListener("visibilitychange", updateAnimation);
if (reduced.addEventListener)
  reduced.addEventListener("change", updateAnimation);
else reduced.addListener(updateAnimation);
function draw(t) {
  animationFrame = requestAnimationFrame(draw);
  let dt = Math.min((t - prev) / 1000, 0.04);
  if (t - prev < 25) return;
  prev = t;
  if (document.hidden) return;
  if (cfg.eco || reduced.matches) {
    layers.forEach((l) => l.ctx.clearRect(0, 0, l.w, l.h));
    return;
  }
  animTime += dt;
  for (let l of layers) {
    const { ctx: c, w, h, i } = l;
    c.clearRect(0, 0, w, h);
    if (i !== ch && Number(getComputedStyle(worlds[i]).opacity) < 0.02)
      continue;
    if (i === 3) {
      c.save();
      c.beginPath();
      c.rect(w * 0.05, h * 0.12, w * 0.87, h * 0.63);
      c.clip();
      for (let p of l.particles) {
        p.y = (p.y + dt * 0.036 * p.speed) % 1;
        let x = p.x * w,
          y = h * 0.08 + p.y * h * 0.68,
          len = 12 + 65 * p.z;
        c.strokeStyle = `rgba(232,217,244,${0.08 + p.z * 0.13})`;
        c.lineWidth = 0.5 + p.z;
        c.beginPath();
        c.moveTo(x, y - len);
        c.bezierCurveTo(x + 2, y - len * 0.5, x - 3, y - 8, x, y);
        c.stroke();
        c.fillStyle = "rgba(244,229,255,.2)";
        c.beginPath();
        c.ellipse(x, y, 1.2, 2.5, 0, 0, 7);
        c.fill();
      }
      let fog = c.createLinearGradient(0, h * 0.3, w, h * 0.7);
      fog.addColorStop(0, "transparent");
      fog.addColorStop(
        0.5,
        `rgba(188,160,196,${0.03 + 0.02 * Math.sin(animTime * 0.3)})`,
      );
      fog.addColorStop(1, "transparent");
      c.fillStyle = fog;
      c.fillRect(0, 0, w, h);
      c.restore();
    }
    if (i === 1) {
      c.save();
      c.beginPath();
      c.rect(w * 0.05, h * 0.12, w * 0.85, h * 0.59);
      c.clip();
      for (let p of l.particles) {
        p.y = (p.y + dt * 0.025 * p.speed) % 1;
        let x = (p.x * w + Math.sin(animTime * 0.4 + p.seed) * 12 + w) % w;
        c.fillStyle = `rgba(250,245,232,${0.25 + p.z * 0.35})`;
        c.beginPath();
        c.arc(x, h * 0.08 + p.y * h * 0.65, 0.8 + p.z * 1.7, 0, 7);
        c.fill();
      }
      c.restore();
      c.strokeStyle = "rgba(255,238,211,.1)";
      c.lineWidth = 2;
      for (let j = 0; j < 3; j++) {
        c.beginPath();
        let x = w * 0.21 + j * 4,
          y = h * 0.77;
        c.moveTo(x, y);
        c.bezierCurveTo(
          x + Math.sin(animTime + j) * 5,
          y - 14,
          x - 6,
          y - 30,
          x + Math.sin(animTime * 0.6 + j) * 7,
          y - 44,
        );
        c.stroke();
      }
    }
    if (i === 2) {
      c.save();
      c.beginPath();
      c.rect(w * 0.07, h * 0.17, w * 0.87, h * 0.4);
      c.clip();
      for (let p of l.particles) {
        p.x = (p.x + dt * 0.1 * p.speed) % 1;
        let x = p.x * w,
          y = h * (0.24 + p.y * 0.27);
        c.fillStyle = `rgba(248,225,168,${0.05 + p.z * 0.11})`;
        c.fillRect(x, y, 6 + p.z * 35, 0.8 + p.z * 2);
      }
      c.restore();
    }
    if (i === 0) {
      c.save();
      c.beginPath();
      c.rect(0, h * 0.61, w, h * 0.28);
      c.clip();
      for (let p of l.particles) {
        let x = p.x * w + Math.sin(animTime * 0.6 + p.seed) * 6,
          y = h * (0.63 + p.y * 0.22);
        c.strokeStyle = `rgba(246,191,131,${0.04 + 0.08 * (0.5 + 0.5 * Math.sin(animTime + p.seed))})`;
        c.lineWidth = 1;
        c.beginPath();
        c.moveTo(x, y);
        c.lineTo(x + 4 + 18 * p.z, y);
        c.stroke();
      }
      c.restore();
      let carX = ((animTime * 12) % (w + 80)) - 40;
      c.fillStyle = "rgba(255,217,165,.5)";
      c.fillRect(carX, h * 0.495, 5, 1.4);
    }
  }
}
updateAnimation();
