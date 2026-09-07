const fs = require('node:fs')
const path = require('node:path')
const net = require('node:net')
const { spawn } = require('node:child_process')

const root = path.resolve(__dirname, '..')
const frontend = path.join(root, 'frontend')
const windows = process.platform === 'win32'
const portal = 'http://127.0.0.1:5173'

async function json(url) {
  const response = await fetch(url, { signal: AbortSignal.timeout(3000) })
  if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`)
  return response.json()
}

async function occupied(port) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(port, '127.0.0.1')
    socket.setTimeout(2000)
    socket.once('connect', () => { socket.destroy(); resolve(true) })
    socket.once('timeout', () => { socket.destroy(); reject(new Error(`Port ${port} probe timed out`)) })
    socket.once('error', error => error.code === 'ECONNREFUSED' ? resolve(false) : reject(error))
  })
}

async function backendReady() {
  const health = await json('http://127.0.0.1:8080/actuator/health')
  const stats = await json('http://127.0.0.1:8080/api/public/statistics')
  if (health.status !== 'UP' || typeof stats.totalAssets !== 'number') throw new Error('Backend is not ready or is an older application version')
}

async function frontendReady() {
  const response = await fetch(portal, { signal: AbortSignal.timeout(3000) })
  if (!response.ok || !(await response.text()).includes('/src/main.ts')) throw new Error('Port 5173 is not the expected Vite application')
  const stats = await json(`${portal}/api/public/statistics`)
  if (typeof stats.totalAssets !== 'number') throw new Error('Frontend API proxy is not ready')
}

function run(command, cwd) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, { cwd, shell: true, windowsHide: true, stdio: 'inherit' })
    child.once('error', reject)
    child.once('exit', code => code === 0 ? resolve() : reject(new Error(`${command} failed (${code})`)))
  })
}

function background(executable, args, name, cwd) {
  const logPath = path.join(root, `target/local-${name}.log`)
  const log = fs.openSync(logPath, 'a')
  const child = spawn(executable, args, { cwd, detached: true, windowsHide: true, stdio: ['ignore', log, log] })
  fs.closeSync(log)
  child.on('error', error => console.error(`${name}: ${error.message}`))
  child.unref()
  console.log(`${name}: PID ${child.pid}; log: ${logPath}`)
}

async function waitFor(check, name) {
  const deadline = Date.now() + 60000
  let lastError
  while (Date.now() < deadline) {
    try { await check(); return } catch (error) { lastError = error }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  throw new Error(`${name} did not become ready: ${lastError.message}`)
}

async function main() {
  fs.mkdirSync(path.join(root, 'target'), { recursive: true })
  if (await occupied(8080)) {
    await backendReady()
    console.log('Backend already running; reused.')
  } else {
    if (!(await occupied(5432))) throw new Error('PostgreSQL is not listening on localhost:5432. Start the existing database service first.')
    console.log('Building backend...')
    await run(windows ? 'mvn.cmd package -DskipTests' : 'mvn package -DskipTests', root)
    const localStorage = path.join(root, 'data/asset-files')
    const originalStorage = path.resolve(root, '../ai-asset-platform/data/asset-files')
    const storage = process.env.ASSET_STORAGE_ROOT || (fs.existsSync(originalStorage) ? originalStorage : localStorage)
    fs.mkdirSync(storage, { recursive: true })
    const socketDir = path.join(root, 'target/disabled-unix-sockets/not-created')
    if (windows && fs.existsSync(socketDir)) throw new Error(`Socket fallback path must not exist: ${socketDir}`)
    // Failed Unix socket binding triggers the JDK's TCP pipe fallback on this Windows host.
    const args = windows ? [`-Djdk.net.unixdomain.tmpdir=${socketDir}`] : []
    args.push('-jar', path.join(root, 'target/ai-asset-platform-0.1.0-SNAPSHOT.jar'), '--server.address=127.0.0.1', `--app.storage.root=${storage}`)
    background('java', args, 'backend', root)
    await waitFor(backendReady, 'Backend')
  }
  if (await occupied(5173)) {
    await frontendReady()
    console.log('Frontend already running; reused.')
  } else {
    const vite = path.join(frontend, 'node_modules/vite/bin/vite.js')
    if (!fs.existsSync(vite)) await run(windows ? 'npm.cmd ci' : 'npm ci', frontend)
    background(process.execPath, [vite, '--host', '127.0.0.1', '--port', '5173', '--strictPort'], 'frontend', frontend)
    await waitFor(frontendReady, 'Frontend')
  }
  console.log(`Ready.\nPortal: ${portal}/\nAdmin:  ${portal}/admin`)
  if (windows && !process.argv.includes('--no-browser')) {
    await run('start "" http://127.0.0.1:5173/', root)
    await run('start "" http://127.0.0.1:5173/admin', root)
  }
}

main().catch(error => { console.error(error.message); process.exitCode = 1 })
