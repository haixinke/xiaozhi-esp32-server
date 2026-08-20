function createParticles(width, height) {
  return Array.from({ length: 48 }, (_, index) => ({
    x: (((index * 47) % 101) / 100) * width,
    y: (((index * 73) % 103) / 102) * height,
    speed: .035 + (index % 7) * .007,
    radius: 1.3 + (index % 4) * .55,
    drift: 4 + (index % 5) * 1.8,
    phase: (index % 12) * Math.PI / 6,
    opacity: .22 + (index % 5) * .09
  }));
}

function drawFog(context, width, height, timestamp, opacity) {
  const drift = Math.sin(timestamp / 3400) * width * .035;
  const haze = context.createLinearGradient(drift - width * .08, 0, drift + width * 1.08, height);
  haze.addColorStop(0, `rgba(228,238,236,${opacity * .86})`);
  haze.addColorStop(.5, `rgba(242,246,239,${opacity})`);
  haze.addColorStop(1, `rgba(224,236,238,${opacity * .9})`);
  context.fillStyle = haze;
  context.fillRect(-width * .1, 0, width * 1.2, height);
}

function drawRain(context, width, height, timestamp, intensity, particles) {
  const strength = Number(intensity || .72);
  const gust = Math.sin(timestamp / 2300) * 2.4;
  const baseLengths = [2.1, 3.5, 5.6];
  const lengthSteps = [.455, .7, .91];
  context.save();
  context.lineCap = 'round';
  (particles || []).slice(0, 42).forEach((particle, index) => {
    const depth = index % 3;
    const velocity = 1.05 + depth * .42;
    const y = (particle.y + timestamp * particle.speed * velocity) % (height + 44) - 30;
    const x = (particle.x + index * 2 + gust * (1 + depth * .35)) % width;
    const length = baseLengths[depth] + (index % 4) * lengthSteps[depth];
    const slant = 2.4 + strength * 2.2 + gust * .18;
    const alpha = (.1 + depth * .065 + particle.opacity * .08) * strength;
    context.lineWidth = .48 + depth * .25;
    context.strokeStyle = `rgba(196,219,227,${alpha})`;
    context.beginPath();
    context.moveTo(x, y);
    context.lineTo(x - slant, y + length);
    context.stroke();
  });
  context.restore();
}

function drawSnow(context, width, height, timestamp, particles) {
  context.save();
  context.fillStyle = 'rgba(255,255,252,.82)';
  (particles || []).slice(0, 30).forEach((particle, index) => {
    const y = (particle.y + timestamp * particle.speed * .72) % (height + 20) - 10;
    const x = particle.x + Math.sin(timestamp / 900 + index) * particle.drift;
    context.beginPath();
    context.arc(x, y, particle.radius, 0, Math.PI * 2);
    context.fill();
  });
  context.restore();
}

function drawDroplets(context, width, height, timestamp, density, particles) {
  context.save();
  context.lineCap = 'round';
  const count = Math.max(4, Math.round(10 * density));
  (particles || []).slice(0, count).forEach((particle, index) => {
    const travel = timestamp * particle.speed * .075;
    const y = (particle.y + travel + index * 17) % (height + 36) - 18;
    const x = (particle.x + Math.sin(timestamp / 2100 + particle.phase) * 1.2) % width;
    const radius = 1.1 + (index % 3) * .55;
    const alpha = .12 + particle.opacity * .16;
    context.fillStyle = `rgba(224,238,242,${alpha})`;
    context.beginPath();
    context.moveTo(x, y - radius * 1.7);
    context.bezierCurveTo(x + radius * 1.15, y - radius * .2, x + radius, y + radius, x, y + radius * 1.2);
    context.bezierCurveTo(x - radius, y + radius, x - radius * 1.15, y - radius * .2, x, y - radius * 1.7);
    context.fill();
    if (index % 5 === 0) {
      context.strokeStyle = `rgba(216,233,239,${alpha * .65})`;
      context.lineWidth = .55;
      context.beginPath();
      context.moveTo(x, y + radius * 1.8);
      context.lineTo(x - .4, y + radius * 4.4 + (index % 3) * 2);
      context.stroke();
    }
  });
  context.restore();
}

function drawSunFlecks(context, width, height, timestamp, opacity, particles) {
  context.save();
  (particles || []).slice(0, 8).forEach((particle, index) => {
    const x = (particle.x + Math.sin(timestamp / 2600 + particle.phase) * particle.drift) % width;
    const y = particle.y * .8 + Math.cos(timestamp / 3100 + particle.phase) * 3;
    const radius = 5 + (index % 4) * 3;
    const glow = context.createRadialGradient(x, y, 0, x, y, radius);
    glow.addColorStop(0, `rgba(255,248,202,${opacity})`);
    glow.addColorStop(1, 'rgba(255,248,202,0)');
    context.fillStyle = glow;
    context.beginPath();
    context.arc(x, y, radius, 0, Math.PI * 2);
    context.fill();
  });
  context.restore();
}

function drawCloudDrift(context, width, height, timestamp) {
  context.save();
  const drift = (timestamp / 90) % (width * 1.8) - width * .6;
  const cloud = context.createRadialGradient(drift, height * .28, 0, drift, height * .28, width * .62);
  cloud.addColorStop(0, 'rgba(235,241,239,.105)');
  cloud.addColorStop(1, 'rgba(235,241,239,0)');
  context.fillStyle = cloud;
  context.fillRect(0, 0, width, height);
  context.restore();
}

function drawLeaves(context, width, height, timestamp, particles) {
  context.save();
  (particles || []).slice(0, 12).forEach((particle, index) => {
    const y = (particle.y + timestamp * particle.speed * .28) % (height + 30) - 15;
    const x = (particle.x + timestamp * .006 + Math.sin(timestamp / 950 + particle.phase) * particle.drift * 2) % (width + 20) - 10;
    const size = 3.5 + (index % 4) * 1.1;
    context.save();
    context.translate(x, y);
    context.rotate(Math.sin(timestamp / 760 + particle.phase) * .9);
    context.fillStyle = index % 3 === 0 ? 'rgba(183,116,56,.54)' : 'rgba(208,153,73,.48)';
    context.beginPath();
    context.moveTo(-size, 0);
    context.quadraticCurveTo(0, -size * .75, size, 0);
    context.quadraticCurveTo(0, size * .75, -size, 0);
    context.fill();
    context.restore();
  });
  context.restore();
}

function drawIceGlints(context, width, height, timestamp, particles) {
  context.save();
  context.lineCap = 'round';
  (particles || []).slice(0, 7).forEach((particle, index) => {
    const pulse = .14 + (.5 + .5 * Math.sin(timestamp / 1500 + particle.phase)) * .18;
    const x = particle.x;
    const y = height * (.18 + (index % 4) * .15) + Math.sin(timestamp / 2600 + index) * 2;
    const radius = 2.2 + (index % 3) * 1.1;
    context.strokeStyle = `rgba(224,239,252,${pulse})`;
    context.lineWidth = .7;
    context.beginPath();
    context.moveTo(x - radius, y);
    context.lineTo(x + radius, y);
    context.moveTo(x, y - radius);
    context.lineTo(x, y + radius);
    context.stroke();
  });
  context.restore();
}

function drawLightning(context, width, height, timestamp) {
  const cycle = timestamp % 11800;
  if (cycle > 320) return;
  const firstPulse = cycle < 115 ? Math.sin(Math.PI * cycle / 115) : 0;
  const secondPulse = cycle > 185 && cycle < 300
    ? Math.sin(Math.PI * (cycle - 185) / 115) * .52
    : 0;
  const flash = Math.max(firstPulse, secondPulse);
  if (flash <= 0) return;
  context.save();
  const glow = context.createRadialGradient(width * .82, height * .08, 0, width * .82, height * .08, width * .95);
  glow.addColorStop(0, `rgba(218,231,255,${flash * .095})`);
  glow.addColorStop(.52, `rgba(205,222,249,${flash * .042})`);
  glow.addColorStop(1, 'rgba(199,219,248,0)');
  context.fillStyle = glow;
  context.fillRect(0, 0, width, height);
  context.restore();
}

function clipWindowGlass(context, width, height) {
  context.beginPath();
  context.moveTo(0, 0);
  context.lineTo(width, 0);
  context.lineTo(width, height * .98);
  context.lineTo(0, height * .88);
  context.closePath();
  context.clip();
}

function drawFrame(context, size, particles, environment, options) {
  if (!context || !size) return;
  const settings = options || {};
  const source = environment || {};
  const timestamp = Number(settings.timestamp || Date.now());
  const reducedMotion = Boolean(settings.reducedMotion);
  const weather = source.weather || 'sunny';
  const season = source.season || 'spring';
  const period = source.period || 'day';
  const fogVisible = Boolean(settings.fogVisible);
  const summerStorm = season === 'summer' && weather === 'rain' && period === 'night';
  context.clearRect(0, 0, size.width, size.height);
  context.save();
  if (settings.clipGlass) clipWindowGlass(context, size.width, size.height);
  if (fogVisible) {
    context.fillStyle = 'rgba(235,242,239,.13)';
    context.fillRect(0, 0, size.width, size.height);
  }
  if (weather === 'fog') drawFog(context, size.width, size.height, timestamp, fogVisible ? .08 : .105);
  if (weather === 'cloudy') drawCloudDrift(context, size.width, size.height, timestamp);
  if (weather === 'rain' || weather === 'storm') {
    drawRain(context, size.width, size.height, timestamp, weather === 'storm' ? 1 : .72, particles);
    drawDroplets(context, size.width, size.height, timestamp, weather === 'storm' ? .72 : .52, particles);
  }
  if (weather === 'snow') drawSnow(context, size.width, size.height, timestamp, particles);
  if (weather === 'afterRain') {
    drawDroplets(context, size.width, size.height, timestamp, .45, particles);
    if (period === 'day') drawSunFlecks(context, size.width, size.height, timestamp, .11, particles);
  }
  if (weather === 'postSnow' && period === 'day') drawSunFlecks(context, size.width, size.height, timestamp, .13, particles);
  if (weather === 'postSnow' && period === 'night') drawIceGlints(context, size.width, size.height, timestamp, particles);
  if (weather === 'sunny' && period === 'day') drawSunFlecks(context, size.width, size.height, timestamp, .09, particles);
  if (weather === 'wind' || (season === 'autumn' && weather === 'sunny')) drawLeaves(context, size.width, size.height, timestamp, particles);
  if (!reducedMotion && (weather === 'storm' || summerStorm)) drawLightning(context, size.width, size.height, timestamp);
  context.restore();
}

function needsAnimation(environment, fogVisible) {
  const source = environment || {};
  const weather = source.weather || 'sunny';
  return Boolean(fogVisible)
    || weather !== 'sunny'
    || (source.season === 'autumn' && weather === 'sunny')
    || (weather === 'sunny' && source.period === 'day');
}

module.exports = {
  createParticles,
  drawFog,
  drawRain,
  drawSnow,
  drawDroplets,
  drawSunFlecks,
  drawCloudDrift,
  drawLeaves,
  drawIceGlints,
  drawLightning,
  clipWindowGlass,
  drawFrame,
  needsAnimation
};
