import http from 'node:http'
import fs from 'node:fs'
import path from 'node:path'

const sessionId = 'structure-empty-annotations'
const outdir = path.resolve('.dbg')
const host = '127.0.0.1'
const startPort = 7777
const maxRetries = 10
const idleMs = 1200 * 1000

fs.mkdirSync(outdir, { recursive: true })

const logFile = path.join(outdir, `trae-debug-log-${sessionId}.ndjson`)
const envFile = path.join(outdir, `${sessionId}.env`)
fs.writeFileSync(logFile, '', 'utf8')

let lastActivityAt = Date.now()
let idleTimer = null

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
}

const resetIdleTimer = (server) => {
  if (idleTimer) clearTimeout(idleTimer)
  idleTimer = setTimeout(() => {
    server.close(() => process.exit(0))
  }, idleMs)
}

const createServer = () => {
  const server = http.createServer((req, res) => {
    lastActivityAt = Date.now()
    resetIdleTimer(server)

    if (req.method === 'OPTIONS' && req.url === '/event') {
      res.writeHead(204, corsHeaders)
      res.end()
      return
    }

    if (req.method === 'POST' && req.url === '/event') {
      let body = ''
      req.on('data', (chunk) => {
        body += chunk
      })
      req.on('end', () => {
        try {
          const event = JSON.parse(body || '{}')
          if (!event.ts) {
            event.ts = Date.now()
          }
          fs.appendFileSync(logFile, `${JSON.stringify(event)}\n`, 'utf8')
          res.writeHead(200, { ...corsHeaders, 'Content-Type': 'text/plain; charset=utf-8' })
          res.end('ok')
        } catch {
          res.writeHead(400, { ...corsHeaders, 'Content-Type': 'text/plain; charset=utf-8' })
          res.end('bad request')
        }
      })
      return
    }

    if (req.method === 'GET' && req.url === '/health') {
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', ...corsHeaders })
      res.end(JSON.stringify({ status: 'ok', sessionId, now: Date.now(), lastActivityAt }))
      return
    }

    if (req.method === 'DELETE' && req.url === '/logs') {
      fs.writeFileSync(logFile, '', 'utf8')
      res.writeHead(200, { 'Content-Type': 'text/plain; charset=utf-8', ...corsHeaders })
      res.end('cleared')
      return
    }

    res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8', ...corsHeaders })
    res.end('not found')
  })
  return server
}

const listen = (port, attempt = 0) => {
  const server = createServer()
  server.once('error', (error) => {
    if (error && error.code === 'EADDRINUSE' && attempt < maxRetries) {
      listen(port + 1, attempt + 1)
      return
    }
    console.error(error)
    process.exit(1)
  })

  server.listen(port, host, () => {
    const apiUrl = `http://${host}:${port}/event`
    fs.writeFileSync(envFile, `DEBUG_SERVER_URL=${apiUrl}\nDEBUG_SESSION_ID=${sessionId}\n`, 'utf8')
    console.log('@@DEBUG_SERVER_INFO')
    console.log(JSON.stringify({
      api_url: apiUrl,
      session_id: sessionId,
      log_dir: outdir,
      log_file: logFile,
      env_file: envFile,
    }, null, 2))
    console.log('@@END_DEBUG_SERVER_INFO')
    resetIdleTimer(server)
  })
}

listen(startPort)
