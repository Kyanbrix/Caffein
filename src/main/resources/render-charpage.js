
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

// Size of the Flash object on account.aq.com/CharPage
const WIDTH = 715;
const HEIGHT = 455;
// Ruffle draws the vector art at this multiple of the Flash size, so the PNG
// comes out sharp instead of a soft 715x455 that Discord then rescales.
// Headless Chromium only has software WebGL, and gear with big blur/glow filters
// (e.g. shadow capes) can take over 30s per frame at 3x. The screenshot then
// times out or catches the still-black canvas, so fall back to smaller scales.
const SCALES = [
    { scale: 3, screenshotTimeoutMs: 10000 },
    { scale: 2, screenshotTimeoutMs: 25000 },
    { scale: 1, screenshotTimeoutMs: 15000 },
];

const LOAD_TIMEOUT_MS = 20000;
const SETTLE_MS = 1500;       // time for item SWFs to attach after the network goes quiet
// A frame counts as drawn once enough sampled pixels are brighter than the black page
const MIN_LIT_FRACTION = 0.05;

// Neither host is real: both are answered by page.route below, so Ruffle
// and the host page are served straight from disk.
const PAGE_URL = 'https://charpage.local/';
const RUFFLE_ORIGIN = 'https://ruffle.local';
const RUFFLE_DIR = path.dirname(require.resolve('@ruffle-rs/ruffle/package.json'));
const MIME = { '.js': 'text/javascript', '.wasm': 'application/wasm', '.map': 'application/json' };

// The char page movie shows a placeholder sword until a weapon SWF replaces it,
// and it never loads one when the weapon is "none", empty or fails to download.
// This empty movie (FileAttributes, ShowFrame, End) stands in for the weapon then.
const NO_WEAPON_FILE = 'caffein-no-weapon.swf';
const EMPTY_SWF = Buffer.from([
    0x46, 0x57, 0x53, 0x0a, 23, 0, 0, 0,  // "FWS", version 10, file length
    0x00,                                 // zero-sized stage rect
    0x00, 0x18, 0x01, 0x00,               // 24 fps, 1 frame
    0x44, 0x11, 0x08, 0x00, 0x00, 0x00,   // FileAttributes: ActionScript 3
    0x40, 0x00,                           // ShowFrame
    0x00, 0x00,                           // End
]);

const PAGE_HTML = `<!DOCTYPE html>
<html><head><meta charset="UTF-8">
<style>html,body{margin:0;padding:0;background:#000;overflow:hidden}#player{width:${WIDTH}px;height:${HEIGHT}px}</style>
<script src="${RUFFLE_ORIGIN}/ruffle.js"></script>
</head><body><div id="player"></div></body></html>`;

async function readStdin() {
    let data = '';
    process.stdin.setEncoding('utf8');
    for await (const chunk of process.stdin) {
        data += chunk;
    }
    return data;
}

async function route(route) {
    const url = new URL(route.request().url());

    if (url.href === PAGE_URL) {
        return route.fulfill({ contentType: 'text/html', body: PAGE_HTML });
    }

    if (url.origin === RUFFLE_ORIGIN) {
        const file = path.join(RUFFLE_DIR, path.basename(url.pathname));
        if (!fs.existsSync(file)) {
            return route.fulfill({ status: 404 });
        }
        return route.fulfill({
            contentType: MIME[path.extname(file)] || 'application/octet-stream',
            body: fs.readFileSync(file),
        });
    }

    const isWeapon = decodeURIComponent(url.pathname).endsWith('/' + weaponFile);
    const noWeapon = { status: 200, contentType: 'application/x-shockwave-flash', body: EMPTY_SWF };
    if (url.pathname.endsWith('/' + NO_WEAPON_FILE)) {
        return route.fulfill(noWeapon);
    }

    // The char page SWF and every item SWF it loads come from game.aq.com, which
    // sends no CORS headers. Fetch them outside the page and add one so Ruffle can read them.
    // Responses are kept so a retry at a smaller scale doesn't download everything again.
    let cached = fetched.get(url.href);
    if (!cached) {
        try {
            const response = await route.fetch();
            cached = { status: response.status(), headers: response.headers(), body: await response.body() };
        } catch (err) {
            return isWeapon ? route.fulfill(noWeapon) : route.abort();
        }
        fetched.set(url.href, cached);
    }
    if (isWeapon && cached.status >= 400) {
        return route.fulfill(noWeapon);
    }
    return route.fulfill({
        status: cached.status,
        headers: { ...cached.headers, 'access-control-allow-origin': '*' },
        body: cached.body,
    });
}

const fetched = new Map();
let weaponFile = null;

/**
 * Points the weapon the movie will show at the empty SWF when there's nothing to load.
 * Like the movie, a cosmetic weapon wins whenever its name is set at all.
 */
function fixWeapon(flashvars) {
    const params = new URLSearchParams(flashvars);
    const prefix = params.has('strCustWeaponName') ? 'strCustWeapon' : 'strWeapon';
    const file = (params.get(prefix + 'File') || '').trim();
    if (file === '' || file.toLowerCase() === 'none') {
        params.set(prefix + 'File', NO_WEAPON_FILE);
        params.set(prefix + 'Type', '');   // a "Dagger" would also load an off-hand copy
        weaponFile = NO_WEAPON_FILE;
    } else {
        weaponFile = file;
    }
    return params.toString();
}

/** Plays the movie at one scale and returns the screenshot, or throws if it doesn't draw in time. */
async function renderAt(browser, { scale, screenshotTimeoutMs }, swf, flashvars) {
    const page = await browser.newPage({
        viewport: { width: WIDTH, height: HEIGHT },
        deviceScaleFactor: scale,
    });
    await page.route('**/*', route);
    await page.goto(PAGE_URL);

    await page.evaluate(async ({ swf, flashvars }) => {
        window.RufflePlayer.config = {
            autoplay: 'on',
            unmuteOverlay: 'hidden',
            splashScreen: false,
            contextMenu: 'off',
            letterbox: 'on',
            // The only renderer that draws filters, like the dark glow that keeps
            // the labels readable. 'canvas' also leaves seams between shapes.
            preferredRenderer: 'wgpu-webgl',
            warnOnUnsupportedContent: false,
            logLevel: 'error',
        };
        const player = window.RufflePlayer.newest().createPlayer();
        player.style.width = '100%';
        player.style.height = '100%';
        // Headless Chromium only has software WebGL, so Ruffle covers the movie with a
        // "hardware acceleration is disabled" notice. It has no config switch; hide it.
        const style = document.createElement('style');
        style.textContent = '#hardware-acceleration-modal { display: none !important; }';
        player.shadowRoot.appendChild(style);
        document.getElementById('player').appendChild(player);
        await player.ruffle().load({ url: swf, parameters: flashvars, allowScriptAccess: true });
    }, { swf, flashvars });

    await page.waitForLoadState('networkidle', { timeout: LOAD_TIMEOUT_MS }).catch(() => {});
    await page.waitForTimeout(SETTLE_MS);

    const png = await page.screenshot({ type: 'png', timeout: screenshotTimeoutMs });
    if (!(await isDrawn(browser, png))) {
        throw new Error(`nothing drawn at ${scale}x`);
    }
    return png;
}

/**
 * Whether the screenshot shows the movie rather than the black page behind it.
 * Decoded in a blank page, since the render page may still be busy drawing.
 */
async function isDrawn(browser, png) {
    const page = await browser.newPage();
    try {
        const lit = await page.evaluate(async (dataUrl) => {
            const img = new Image();
            img.src = dataUrl;
            await img.decode();
            const canvas = document.createElement('canvas');
            canvas.width = 64;
            canvas.height = 40;
            const ctx = canvas.getContext('2d');
            ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
            const { data } = ctx.getImageData(0, 0, canvas.width, canvas.height);
            let count = 0;
            for (let i = 0; i < data.length; i += 4) {
                if (data[i] + data[i + 1] + data[i + 2] > 48) {
                    count++;
                }
            }
            return count / (data.length / 4);
        }, 'data:image/png;base64,' + png.toString('base64'));
        return lit >= MIN_LIT_FRACTION;
    } finally {
        await page.close().catch(() => {});
    }
}

(async () => {
    const outputPath = process.argv[2];
    if (!outputPath) {
        console.error('Usage: node render-charpage.js <output-png-path>   (stdin: {"swf": "...", "flashvars": "..."})');
        process.exit(1);
    }

    const input = JSON.parse(await readStdin());
    const swf = input.swf;
    const flashvars = fixWeapon(input.flashvars);

    let lastError;
    for (const attempt of SCALES) {
        // A fresh browser each time: a frame that timed out keeps the shared
        // GPU process busy and would slow down the next attempt.
        const browser = await chromium.launch();
        try {
            fs.writeFileSync(outputPath, await renderAt(browser, attempt, swf, flashvars));
            return;
        } catch (err) {
            console.error(`Render at ${attempt.scale}x failed: ${err.message.split('\n')[0]}`);
            lastError = err;
        } finally {
            await browser.close();
        }
    }
    throw lastError;
})().catch((err) => {
    console.error(err);
    process.exit(1);
});
