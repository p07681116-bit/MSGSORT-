const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = 3000;
const PUBLIC_DIR = path.join(__dirname, 'public');
const DATA_DIR = path.join(__dirname, 'data');
const REQUESTS_FILE = path.join(DATA_DIR, 'deletion_requests.json');

// Ensure data directory exists
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

// Ensure requests file exists
if (!fs.existsSync(REQUESTS_FILE)) {
  fs.writeFileSync(REQUESTS_FILE, JSON.stringify([], null, 2), 'utf8');
}

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

const server = http.createServer((req, res) => {
  // CORS & Security headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
  res.setHeader('X-Content-Type-Options', 'nosniff');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;

  // Health check endpoint
  if (pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'ok', app: 'MsgSort Account Deletion Portal' }));
    return;
  }

  // API Endpoint: Submit Account Deletion Request
  if (pathname === '/api/delete-account-request' && req.method === 'POST') {
    let body = '';
    req.on('data', chunk => {
      body += chunk.toString();
      // Guard against excessively large payloads
      if (body.length > 50000) {
        req.destroy();
      }
    });

    req.on('end', () => {
      try {
        const payload = JSON.parse(body || '{}');
        const email = (payload.email || '').trim().toLowerCase();

        if (!email || !email.includes('@')) {
          res.writeHead(400, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ error: 'Valid email address is required.' }));
          return;
        }

        const ticketId = 'DEL-REQ-' + crypto.randomBytes(4).toString('hex').toUpperCase();
        const requestRecord = {
          ticketId,
          email,
          accountId: (payload.accountId || '').trim(),
          reason: payload.reason || 'not_specified',
          notes: (payload.notes || '').trim(),
          status: 'pending_verification',
          submittedAt: new Date().toISOString(),
          ip: req.headers['x-forwarded-for'] || req.socket.remoteAddress || 'unknown'
        };

        // Persist to local JSON audit storage
        try {
          const currentData = JSON.parse(fs.readFileSync(REQUESTS_FILE, 'utf8') || '[]');
          currentData.push(requestRecord);
          fs.writeFileSync(REQUESTS_FILE, JSON.stringify(currentData, null, 2), 'utf8');
        } catch (saveError) {
          console.error('Error saving deletion request record:', saveError);
        }

        console.log(`[Account Deletion] New request recorded: ${ticketId} for ${email}`);

        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
          success: true,
          ticketId,
          message: 'Account deletion request successfully logged. Ownership verification initiated.',
          supportEmail: 'sortmsg@gmail.com'
        }));
      } catch (parseError) {
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: 'Invalid JSON payload.' }));
      }
    });
    return;
  }

  // Route: Main Account Deletion Webpage
  if (
    pathname === '/' ||
    pathname === '/delete-account' ||
    pathname === '/delete-account.html' ||
    pathname === '/account-deletion' ||
    pathname === '/account-deletion.html'
  ) {
    const filePath = path.join(PUBLIC_DIR, 'delete-account.html');
    if (fs.existsSync(filePath)) {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      fs.createReadStream(filePath).pipe(res);
      return;
    }
  }

  // Static File Serving
  let safePath = path.normalize(pathname).replace(/^(\.\.[\/\\])+/, '');
  let fullPath = path.join(PUBLIC_DIR, safePath);

  if (fs.existsSync(fullPath) && fs.statSync(fullPath).isFile()) {
    const ext = path.extname(fullPath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': contentType });
    fs.createReadStream(fullPath).pipe(res);
    return;
  }

  // Fallback to delete-account.html for any unmapped route
  const fallbackPath = path.join(PUBLIC_DIR, 'delete-account.html');
  if (fs.existsSync(fallbackPath)) {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    fs.createReadStream(fallbackPath).pipe(res);
  } else {
    res.writeHead(404, { 'Content-Type': 'text/plain' });
    res.end('Page Not Found');
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`MsgSort Account Deletion Web Server running on port ${PORT}`);
});
