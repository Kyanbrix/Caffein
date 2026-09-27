
const { chromium } = require('playwright');

// Try these card widths (px) from narrow to wide until the card is "square enough".
const WIDTHS = [560, 640, 720, 800, 900, 1000, 1100];
const MAX_ASPECT = 1.1;   // max height / width before we try a wider card
const MAX_HEADER_SCALE = 1.5;

function escapeHtml(str) {
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

async function readStdin() {
    let data = '';
    process.stdin.setEncoding('utf8');
    for await (const chunk of process.stdin) {
        data += chunk;
    }
    return data;
}

// Message font size (px) by length: shorter text = bigger, longer text = smaller.
function fontSizeFor(len) {
    if (len <= 100) return 38;
    if (len <= 250) return 30;
    return 28;
}

const CARD_HTML = (messageHtml, fontSize) => `<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<style>
  @import url('https://fonts.googleapis.com/css2?family=Inter:wght@800;900&family=Lilita+One&display=swap');

  :root {
    --w: 560px;   /* card width, set by script */
    --s: 1;       /* header scale, set by script */
    --fs: ${fontSize}px;
  }

  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; background: transparent; }

  body {
    font-family: 'Inter', 'Helvetica Neue', Arial, 'Noto Color Emoji', sans-serif;
    display: inline-block;
  }

  .card {
    width: var(--w);
    background: #ffffff;
    border-radius: calc(44px * var(--s));
    overflow: hidden;
  }

  /* ---------- Header ---------- */
  .header {
    background: linear-gradient(160deg, #3fa9f5 0%, #4a86f7 40%, #6a3df5 100%);
    padding: calc(36px * var(--s)) calc(24px * var(--s)) calc(34px * var(--s));
    text-align: center;
  }

  .header-title {
    margin: 0;
    font-size: calc(40px * var(--s));
    font-weight: 800;
    color: #ffffff;
    letter-spacing: -0.01em;
    line-height: 1.1;
  }

  /* Bubbly sticker-style "Confessions" wordmark */
  .wordmark {
    position: relative;
    display: inline-block;
    margin-top: calc(22px * var(--s));
    font-family: 'Lilita One', 'Inter', sans-serif;
    font-size: calc(58px * var(--s));
    line-height: 1;
    letter-spacing: 0.01em;
    filter: drop-shadow(0 calc(4px * var(--s)) 0 rgba(20, 20, 90, 0.45));
  }

  /* white outer sticker border (behind) */
  .wordmark::before {
    content: attr(data-text);
    position: absolute;
    left: 0; top: 0;
    color: #ffffff;
    -webkit-text-stroke: calc(14px * var(--s)) #ffffff;
    z-index: 0;
  }

  /* navy outline + white fill (front) */
  .wordmark span {
    position: relative;
    z-index: 1;
    color: #ffffff;
    -webkit-text-stroke: calc(6px * var(--s)) #1a2a8f;
    paint-order: stroke fill;
  }

  /* golden halo above the last "s" */
  .halo {
    position: absolute;
    top: calc(-14px * var(--s));
    right: calc(-6px * var(--s));
    width: calc(34px * var(--s));
    height: calc(12px * var(--s));
    border: calc(3px * var(--s)) solid #f6c945;
    border-radius: 50%;
    transform: rotate(-8deg);
    z-index: 2;
  }

  /* ---------- Body ---------- */
  .body {
    padding: calc(48px * var(--s)) calc(40px * var(--s)) calc(52px * var(--s));
    display: flex;
    align-items: center;
    justify-content: center;
    min-height: calc(240px * var(--s));
  }

  .message {
    width: 100%;
    font-size: var(--fs);
    font-weight: 800;
    line-height: 1.25;
    color: #000000;
    text-align: center;
    overflow-wrap: break-word;
    word-break: break-word;
    white-space: pre-wrap;
    letter-spacing: -0.01em;
    text-wrap: pretty;   /* avoids orphan words on the last line */
  }
</style>
</head>
<body>
  <div class="card" id="card">
    <div class="header">
      <p class="header-title">Anonymous</p>
      <div class="wordmark" data-text="Confession">
        <span>Confession</span>
        <i class="halo"></i>
      </div>
    </div>
    <div class="body">
      <div class="message">${messageHtml}</div>
    </div>
  </div>
</body>
</html>`;

(async () => {
    const outputPath = process.argv[2];
    if (!outputPath) {
        console.error('Usage: node render-confession.js <output-png-path>');
        process.exit(1);
    }

    const rawMessage = (await readStdin()).trim();
    const messageHtml = escapeHtml(rawMessage);
    const fontSize = fontSizeFor(rawMessage.length);

    const browser = await chromium.launch();
    try {
        const page = await browser.newPage({
            viewport: { width: 1200, height: 1600 },
            // slightly lower scale for big cards to keep file size reasonable
            deviceScaleFactor: rawMessage.length > 250 ? 1.5 : 2
        });
        await page.setContent(CARD_HTML(messageHtml, fontSize), { waitUntil: 'networkidle' });
        await page.evaluate(() => document.fonts.ready);

        // Widen the card until its shape is reasonable (or we hit the max width).
        await page.evaluate(({ widths, maxAspect, maxScale }) => {
            const root = document.documentElement;
            const card = document.getElementById('card');
            for (const w of widths) {
                root.style.setProperty('--w', w + 'px');
                root.style.setProperty('--s', String(Math.min(w / 560, maxScale)));
                if (card.offsetHeight / w <= maxAspect) break;
            }
        }, { widths: WIDTHS, maxAspect: MAX_ASPECT, maxScale: MAX_HEADER_SCALE });

        const card = await page.$('#card');
        // omitBackground keeps the area outside the rounded corners transparent
        await card.screenshot({ path: outputPath, omitBackground: true });
    } finally {
        await browser.close();
    }
})().catch((err) => {
    console.error(err);
    process.exit(1);
});