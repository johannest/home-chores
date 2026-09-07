#!/usr/bin/env node
/*
 * Regenerates every PNG in src/main/resources/META-INF/resources/icons from the two SVG
 * masters there: icon.svg (the full-bleed artwork, derived from logo.svg) and icon-small.svg
 * (the simplified 16/32 px variant).
 *
 * The set of sizes mirrors what Vaadin's @PWA would generate itself, so the static files
 * simply replace Vaadin's runtime downscaling with proper vector renders. Splash screens
 * follow Vaadin's layout: the icon at a third of the short side, centered on the PWA
 * background colour; ours additionally rounds the icon's corners since it sits on white.
 *
 * Needs @resvg/resvg-js, which is not a project dependency. Either
 *     npm i --no-save @resvg/resvg-js && node tools/generate-icons.cjs
 * or point NODE_PATH at a directory that has it installed.
 */
const fs = require('fs');
const path = require('path');
const { Resvg } = require('@resvg/resvg-js');

const dir = path.join(__dirname, '..', 'src/main/resources/META-INF/resources/icons');
const master = fs.readFileSync(path.join(dir, 'icon.svg'), 'utf8');
const small = fs.readFileSync(path.join(dir, 'icon-small.svg'), 'utf8');
const SMALL_MAX = 48;      // sizes at or below this use icon-small.svg
const BACKGROUND = '#ffffff'; // keep in step with @PWA(backgroundColor)

const squares = [16, 32, 96, 144, 180, 192, 512];
const splashes = [
    [640, 1136], [750, 1334], [828, 1792], [1125, 2436], [1170, 2532], [1242, 2208],
    [1242, 2688], [1284, 2778], [1536, 2048], [1620, 2160], [1668, 2224], [1668, 2388],
    [2048, 2732],
];

function render(svg, width, file) {
    const png = new Resvg(svg, { fitTo: { mode: 'width', value: width } }).render().asPng();
    fs.writeFileSync(path.join(dir, file), png);
}

/** The master's own viewBox, so nesting it keeps its coordinate system. */
function viewBox(svg) {
    const m = svg.match(/<svg[^>]*\sviewBox="([^"]+)"/);
    if (!m) throw new Error('master SVG needs an explicit viewBox');
    return m[1];
}

/** Drops the XML prolog and outer <svg> element of a master so it can be nested. */
function inner(svg) {
    return svg.replace(/^[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '');
}

function splash(w, h) {
    const s = Math.floor(Math.min(w, h) / 3);
    const x = Math.floor((w - s) / 2), y = Math.floor((h - s) / 2);
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}">
  <rect width="${w}" height="${h}" fill="${BACKGROUND}"/>
  <clipPath id="tile"><rect x="${x}" y="${y}" width="${s}" height="${s}" rx="${Math.round(s * 0.22)}"/></clipPath>
  <g clip-path="url(#tile)"><svg x="${x}" y="${y}" width="${s}" height="${s}" viewBox="${viewBox(master)}">${inner(master)}</svg></g>
</svg>`;
}

for (const n of squares) {
    render(n <= SMALL_MAX ? small : master, n, `icon-${n}x${n}.png`);
}
render(master, 512, 'icon.png');
for (const [w, h] of splashes) {
    render(splash(w, h), w, `icon-${w}x${h}.png`);
    render(splash(h, w), h, `icon-${h}x${w}.png`);
}
console.log(`wrote ${squares.length + 1 + splashes.length * 2} icons to ${dir}`);
