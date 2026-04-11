const puppeteer = require('puppeteer');
const path = require('path');

(async () => {
  const browser = await puppeteer.launch({
    headless: 'new',
    args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-gpu']
  });

  const page = await browser.newPage();

  const htmlPath = path.resolve(__dirname, 'ebook.html');
  const pdfPath = path.resolve(__dirname, 'Secure-AI-Gateway-Complete-Study-Guide.pdf');

  console.log('Loading HTML file...');
  await page.goto(`file://${htmlPath}`, {
    waitUntil: 'networkidle0',
    timeout: 60000
  });

  // Wait for Google Fonts to load and render
  await new Promise(r => setTimeout(r, 4000));

  console.log('Generating PDF...');
  await page.pdf({
    path: pdfPath,
    format: 'A4',
    printBackground: true,
    preferCSSPageSize: false,
    margin: { top: '15mm', right: '15mm', bottom: '18mm', left: '15mm' },
    displayHeaderFooter: true,
    headerTemplate: '<div></div>',
    footerTemplate: '<div style="font-size: 8px; color: #999; width: 100%; text-align: center; font-family: sans-serif;">Secure AI Gateway: A Complete Study Guide &mdash; TUS Midlands 2026 &nbsp;&nbsp;|&nbsp;&nbsp; Page <span class="pageNumber"></span> of <span class="totalPages"></span></div>',
    timeout: 120000
  });

  console.log(`PDF saved to: ${pdfPath}`);
  await browser.close();
  console.log('Done!');
})();
