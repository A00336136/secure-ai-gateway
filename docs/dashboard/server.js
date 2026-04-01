const http = require('http');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = 3333;

const server = http.createServer(async (req, res) => {
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  // ── PROXY: Forward requests to backend services ──
  if (req.url.startsWith('/proxy/')) {
    // /proxy/app/actuator/health → http://localhost:8100/actuator/health
    // /proxy/nemo/ → http://localhost:8001/
    // /proxy/presidio/health → http://localhost:5002/health
    // /proxy/ollama/api/tags → http://localhost:11434/api/tags
    const portMap = {
      'app': 8100,
      'nemo': 8001,
      'presidio': 5002,
      'ollama': 11434,
    };

    const parts = req.url.replace('/proxy/', '').split('/');
    const service = parts.shift();
    const targetPath = '/' + parts.join('/');
    const port = portMap[service];

    if (!port) { res.writeHead(400); res.end('Unknown service'); return; }

    // Collect request body for POST
    let body = '';
    req.on('data', chunk => body += chunk);
    req.on('end', () => {
      const options = {
        hostname: 'localhost',
        port: port,
        path: targetPath,
        method: req.method,
        timeout: 300000,
        headers: {
          'Content-Type': req.headers['content-type'] || 'application/json',
          'Accept': 'application/json',
        },
      };
      // Forward Authorization header
      if (req.headers['authorization']) {
        options.headers['Authorization'] = req.headers['authorization'];
      }

      const proxyReq = http.request(options, (proxyRes) => {
        const headers = {
          'Content-Type': proxyRes.headers['content-type'] || 'application/json',
          'Access-Control-Allow-Origin': '*',
        };
        // Forward rate limit headers
        ['x-rate-limit-remaining', 'x-rate-limit-capacity', 'retry-after',
         'x-pii-redacted', 'x-duration-ms', 'x-guardrails-status'].forEach(h => {
          if (proxyRes.headers[h]) headers[h] = proxyRes.headers[h];
        });
        res.writeHead(proxyRes.statusCode, headers);
        proxyRes.pipe(res);
      });

      proxyReq.on('error', (e) => {
        res.writeHead(502);
        res.end(JSON.stringify({ error: 'Service unreachable', service, message: e.message }));
      });
      proxyReq.on('timeout', () => {
        proxyReq.destroy();
        res.writeHead(504);
        res.end(JSON.stringify({ error: 'Timeout', service }));
      });

      if (body && (req.method === 'POST' || req.method === 'PUT')) {
        proxyReq.write(body);
      }
      proxyReq.end();
    });
    return;
  }

  // ── Static file serving ──
  let filePath = path.join(__dirname, req.url === '/' ? 'index.html' : req.url);
  const ext = path.extname(filePath);
  const mimeTypes = { '.html':'text/html', '.css':'text/css', '.js':'application/javascript', '.json':'application/json', '.png':'image/png', '.svg':'image/svg+xml' };

  fs.readFile(filePath, (err, data) => {
    if (err) { res.writeHead(404); res.end('Not found'); return; }
    res.writeHead(200, { 'Content-Type': mimeTypes[ext] || 'text/plain' });
    res.end(data);
  });
});

server.listen(PORT, () => {
  console.log(`
  ╔═══════════════════════════════════════════════╗
  ║  SecureAI Gateway — Interactive Demo Platform ║
  ╠═══════════════════════════════════════════════╣
  ║  Dashboard:  http://localhost:${PORT}             ║
  ║  Proxy API:  http://localhost:${PORT}/proxy/{svc} ║
  ╚═══════════════════════════════════════════════╝
  `);
});
