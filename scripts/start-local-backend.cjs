const fs = require('node:fs')
const path = require('node:path')
const net = require('node:net')
const { spawn } = require('node:child_process')

const root = path.resolve(__dirname, '..')
const jar = path.join(root, 'target/ai-asset-platform-0.1.0-SNAPSHOT.jar')
const storage = process.env.ASSET_STORAGE_ROOT || path.resolve(root, '../ai-asset-platform/data/asset-files')
const unavailableSocketDir = path.join(root, 'target/disabled-unix-sockets/not-created')
if (!fs.existsSync(jar)) throw new Error('Run mvn.cmd package -DskipTests first')
if (!fs.existsSync(storage)) throw new Error('Set ASSET_STORAGE_ROOT to the existing attachment directory')
if (fs.existsSync(unavailableSocketDir)) throw new Error('Unix socket fallback path must not exist')

const probe = net.connect(8080, '127.0.0.1')
probe.on('connect', () => {
  probe.end()
  console.log('Port 8080 is already in use; no process started.')
})
probe.on('error', err => {
  if (err.code !== 'ECONNREFUSED') throw err
  const log = fs.openSync(path.join(root, 'target/local-backend.log'), 'a')
  // A missing socket directory makes this Windows JDK fall back to TCP pipes.
  const args = process.platform === 'win32' ? [`-Djdk.net.unixdomain.tmpdir=${unavailableSocketDir}`] : []
  args.push('-jar', jar, '--server.address=127.0.0.1', `--app.storage.root=${storage}`)
  const child = spawn('java', args, { cwd: root, detached: true, windowsHide: true, stdio: ['ignore', log, log] })
  child.on('error', error => { console.error(error.message); process.exitCode = 1 })
  child.unref()
  fs.closeSync(log)
  console.log(`Backend PID: ${child.pid}; check http://127.0.0.1:8080/actuator/health`)
})
