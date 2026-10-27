#!/usr/bin/env node
/*
 * Regenerates src/main/resources/META-INF/resources/icons/doodles-2.svg, the repeating tile of
 * line drawings behind the board (styles.css, body::before).
 *
 * The tile is a mask, not a picture: only its alpha is used, and styles.css paints it in
 * --app-doodle, so one file serves every palette and both schemes. Strokes are therefore black.
 *
 * Icons are scattered with Poisson-disc spacing on a torus — distances wrap around the tile's
 * edges — and an icon near an edge is drawn again on the opposite side, so the tile repeats
 * without seams. The PRNG is seeded: the same SEED gives the same tile, and a different one
 * a fresh arrangement of the same drawings.
 *
 *     node tools/generate-doodles.cjs [seed]
 *
 * The file is served under /icons/, which the CDN caches for a month (README, runbook step 5):
 * a tile that looks different gets the next number in its file name (and styles.css follows), so
 * no phone keeps the old one for a month.
 */
const fs = require('fs');
const path = require('path');

const SEED = Number(process.argv[2] ?? 7);
const TILE = 240;
const out = path.join(__dirname, '..', 'src/main/resources/META-INF/resources/icons/doodles-2.svg');

// Each drawing sits in a box of about 32 px centred on (0, 0).
const ICONS = {
    broom: '<path d="M2-18v20"/><path d="M-8 2h20l4 16h-28z"/><path d="M-6 8l-2 10M0 8v10M6 8l2 10"/>',
    mug: '<path d="M-12-6h20v10a8 8 0 0 1-8 8h-4a8 8 0 0 1-8-8z"/><path d="M8-3h3a4 4 0 0 1 0 8h-3"/><path d="M-5-14c-2 3 2 4 0 7M2-14c-2 3 2 4 0 7"/>',
    sock: '<path d="M-6-16h12v14l7 7a6 6 0 0 1-4 10H-2a8 8 0 0 1-8-8V-16z"/><path d="M-6-10h12"/>',
    star: '<path d="M0-14l3.6 8.4L12-4l-6.6 5.6L7.4 10 0 5.4-7.4 10l2-8.4L-12-4l8.4-1.6z"/>',
    house: '<path d="M-15 0L0-13 15 0"/><path d="M-11-3v16h22V-3"/><path d="M-3 13V5h6v8"/>',
    leaf: '<path d="M-12 12c0-14 9-24 24-24 0 14-10 24-24 24z"/><path d="M-12 12L4-4"/>',
    plant: '<path d="M-10-2h20l-3 16h-14z"/><path d="M-12-2h24"/><path d="M0-2c0-6 3-9 8-10M0-2c0-5-3-8-8-9"/>',
    diamond: '<path d="M0-14l8 10-8 18-8-18z"/><path d="M-8-4h16M0-14v28"/>',
    heart: '<path d="M0 11S-13 4-13-4a6.5 6.5 0 0 1 13-2 6.5 6.5 0 0 1 13 2c0 8-13 15-13 15z"/>',
    shirt: '<path d="M-6-14h12l10 6-4 8-4-2v16h-16V-2l-4 2-4-8z"/>',
    check: '<circle r="12"/><path d="M-5 0l3.5 3.5 7-8"/>',
    bucket: '<path d="M-11-6h22l-3 18h-16z"/><path d="M-13-6a13 4 0 0 1 26 0"/>',
    sun: '<circle r="6"/><path d="M0-12v-2M0 12v2M-12 0h-2M12 0h2M-8.5-8.5l-1.4-1.4M8.5 8.5l1.4 1.4M-8.5 8.5l-1.4 1.4M8.5-8.5l1.4-1.4"/>',
    bubbles: '<circle cx="-4" cy="2" r="6"/><circle cx="7" cy="-5" r="3.5"/><circle cx="6" cy="8" r="2"/>',
    sparkle: '<path d="M0-9v6M0 3v6M-9 0h6M3 0h6"/>',
    dot: '<circle r="2.2"/>',
    wave: '<path d="M-12 0c4-6 8 6 12 0s8 6 12 0"/>',
    plus: '<path d="M0-6v12M-6 0h12"/>',
};
const FILLERS = ['sparkle', 'dot', 'wave', 'plus'];
const BIG = Object.keys(ICONS).filter((k) => !FILLERS.includes(k));

// mulberry32: small, seedable, good enough for scattering.
let state = SEED >>> 0;
function random() {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
}
const between = (lo, hi) => lo + random() * (hi - lo);

function torusDistance(a, b) {
    const dx = Math.min(Math.abs(a.x - b.x), TILE - Math.abs(a.x - b.x));
    const dy = Math.min(Math.abs(a.y - b.y), TILE - Math.abs(a.y - b.y));
    return Math.hypot(dx, dy);
}

// Drawings first, about 38 px apart; fillers in the gaps, kept 25 px clear of the drawings. Packed
// close and drawn thin, the tile reads as an even texture rather than a scatter of pictures.
const placed = [];
for (const { kind, spacing, tries, pool, scale } of [
    { kind: 'big', spacing: 38, tries: 6000, pool: BIG, scale: [0.7, 0.88] },
    { kind: 'small', spacing: 18, tries: 5000, pool: FILLERS, scale: [0.7, 0.95] },
]) {
    let bag = [];
    for (let i = 0; i < tries; i++) {
        const p = { x: between(0, TILE), y: between(0, TILE) };
        if (placed.every((q) => torusDistance(p, q) >= (q.kind === kind ? spacing : 25))) {
            if (bag.length === 0) {
                bag = [...pool].sort(() => random() - 0.5);
            }
            placed.push({ ...p, kind, name: bag.pop(), rotate: between(-40, 40), scale: between(...scale) });
        }
    }
}

const groups = [];
for (const d of placed) {
    for (const ox of [-TILE, 0, TILE]) {
        for (const oy of [-TILE, 0, TILE]) {
            const x = d.x + ox;
            const y = d.y + oy;
            if (x > -24 && x < TILE + 24 && y > -24 && y < TILE + 24) {
                groups.push(`<g transform="translate(${x.toFixed(1)} ${y.toFixed(1)}) rotate(${d.rotate.toFixed(0)}) `
                    + `scale(${d.scale.toFixed(2)})">${ICONS[d.name]}</g>`);
            }
        }
    }
}

fs.writeFileSync(out,
    `<svg xmlns="http://www.w3.org/2000/svg" width="${TILE}" height="${TILE}" viewBox="0 0 ${TILE} ${TILE}" `
    + 'fill="none" stroke="#000" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round">\n'
    + `<!-- Generated by tools/generate-doodles.cjs (seed ${SEED}). A mask: styles.css supplies the colour. -->\n`
    + groups.join('\n') + '\n</svg>\n');
console.log(`${path.relative(process.cwd(), out)}: ${placed.length} drawings, seed ${SEED}`);
