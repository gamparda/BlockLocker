// Run against an isolated BlueMap 5.29 server with BerryIntegrations enabled.
// Requires Playwright and its Chromium browser: node player-name-layout.cjs URL OUTPUT_DIR
const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const fs = require('node:fs/promises');
const path = require('node:path');

(async () => {
    const base = process.argv[2] || 'http://127.0.0.1:18101/';
    const output = process.argv[3] || 'layout-results';
    await fs.mkdir(output, { recursive: true });
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1280, height: 760 } });
    try {
        const settings = await (await page.request.get(new URL('settings.json', base).href)).json();
        assert(settings.styles.includes('assets/berrynames/player-names-v1.css'), 'server must register stylesheet');
        const css = await page.request.get(new URL('assets/berrynames/player-names-v1.css', base).href);
        assert.equal(css.status(), 200);
        await page.goto(base);
        await page.waitForFunction(() => window.BlueMap?.PlayerMarker && [...document.styleSheets].some(
            sheet => sheet.href?.endsWith('/assets/berrynames/player-names-v1.css')));
        await page.evaluate(() => {
            const panel = document.createElement('section');
            panel.id = 'layout-fixture';
            panel.style.cssText = 'position:absolute;inset:0;background:#14262a;z-index:99999;overflow:hidden;color:white';
            const heading = document.createElement('h2');
            heading.textContent = 'BlueMap 5.29 · Player name layout';
            heading.style.cssText = 'margin:30px;font:22px sans-serif';
            panel.append(heading);
            document.querySelector('#map-container').append(panel);
            window.layoutMarkers = ['감파르다', '베리일', '베리십일', '베리백', '베리천이백삼십사',
                '한글이름열여섯글자표시테스트', 'BerryPlayer_01'].map((name, i) => {
                const marker = new BlueMap.PlayerMarker('layout-' + i, 'layout-' + i);
                marker.updateFromData({ name, foreign: false });
                marker.element.setAttribute('distance-data', 'near');
                // Use PlayerMarker's real CSS2DObject wrapper and its absolute/translated layout.
                marker.elementObject.element.style.transform = `translate(${220 + (i % 3) * 400}px,${220 + Math.floor(i / 3) * 200}px)`;
                panel.append(marker.elementObject.element);
                return marker;
            });
        });
        const measure = () => page.evaluate(() => window.layoutMarkers.map(marker => {
            const label = marker.playerNameElement;
            const range = document.createRange(); range.selectNodeContents(label);
            return { name: label.textContent, lines: new Set([...range.getClientRects()].map(r => Math.round(r.top))).size,
                whiteSpace: getComputedStyle(label).whiteSpace, width: label.offsetWidth,
                height: label.offsetHeight, opacity: getComputedStyle(label).opacity };
        }));
        // First reproduce the reported Hangul wrapping with unmodified BlueMap styling.
        await page.evaluate(() => [...document.styleSheets].find(s => s.href?.endsWith('/assets/berrynames/player-names-v1.css')).disabled = true);
        const before = await measure();
        assert(before.find(row => row.name === '감파르다').lines > 1, 'must reproduce the original vertical wrapping');
        await page.screenshot({ path: path.join(output, 'before.png') });
        await page.evaluate(() => [...document.styleSheets].find(s => s.href?.endsWith('/assets/berrynames/player-names-v1.css')).disabled = false);
        const desktop = await measure();
        for (const row of desktop) assert.equal(row.lines, 1, row.name);
        await page.screenshot({ path: path.join(output, 'after.png') });
        await page.setViewportSize({ width: 360, height: 900 });
        await page.evaluate(() => window.layoutMarkers.forEach((marker, i) => {
            marker.elementObject.element.style.transform = `translate(180px,${160 + i * 110}px)`;
        }));
        const mobile = await measure();
        for (const row of mobile) assert.equal(row.lines, 1, row.name + ' mobile');
        await page.screenshot({ path: path.join(output, 'mobile.png') });
        await page.evaluate(() => window.layoutMarkers[0].element.setAttribute('distance-data', 'med'));
        await page.waitForFunction(() => getComputedStyle(window.layoutMarkers[0].playerNameElement).opacity === '0');
        const distanceHide = (await measure())[0].opacity;
        assert.equal(distanceHide, '0', 'native distance hiding must still work');
        const report = { browser: await browser.version(), blueMap: settings.version, registeredStyle: true,
            before, desktop, mobile, distanceHide, result: 'PASS' };
        await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
        console.log(JSON.stringify(report));
    } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
