// Mibot DSH plugin host — stage 1.
//
// One Node process per plugin. Loads the plugin's cordis.patch.yml rows into a
// real Cordis root context (@deepseek-ai/cordis, vendored in ./node_modules)
// that carries a small set of Mibot services, and talks to Kotlin
// (DshHostBridge.kt) with NDJSON JSON-RPC 2.0 on stdin/stdout.
//
//   node --import <kit>/bootstrap.mjs <kit>/host.mjs <spec.json>
//
// spec.json: { id, pkg, profile, dataDir, locale, config? }
//   profile  — DSH-style profile dir; the plugin lives in <profile>/node_modules/<pkg>
//              and `baseUrl` (seen by `!!js` rows) is file://<profile>/
//
// Kotlin → host requests: ping, status, commands.list, commands.run {name,args},
//                         tools.list, tools.execute {name,args,callId,timeoutMs?},
//                         config.get, shutdown
// host → Kotlin notifications: log {level,name,text}, status {…}, tools/change {tools:[…]}
//
// Services provided: logger (Cordis built-in, exported to Kotlin),
// locale, config, commands (stage 1), tools (stage 2, D1). Every other service a plugin injects is recorded by
// the stub registry and reported as missing (the fiber stays PENDING, which is
// Cordis' own behaviour for unmet required injections).
import fs from 'node:fs'
import path from 'node:path'
import readline from 'node:readline'
import { pathToFileURL } from 'node:url'
import { createRequire } from 'node:module'
import { Context, Logger, Service } from '@deepseek-ai/cordis'
import YAML from 'yaml'

const KIT_VERSION = 3
const STATE = ['PENDING', 'LOADING', 'ACTIVE', 'FAILED', 'DISPOSED', 'UNLOADING']

// ------------------------------------------------------------------ RPC
const out = (obj) => { try { fs.writeSync(1, JSON.stringify(obj) + '\n') } catch {} }
const notify = (method, params) => out({ jsonrpc: '2.0', method, params })
const log = (level, name, text) => notify('log', { level, name, text: String(text).slice(0, 4000), ts: Date.now() })

// Plugin code must not write to our RPC channel: route console.* to the log.
for (const [k, lv] of [['log', 'info'], ['info', 'info'], ['warn', 'warn'], ['error', 'error'], ['debug', 'debug']]) {
  console[k] = (...a) => log(lv, 'console', a.map((x) => typeof x === 'string' ? x : (x instanceof Error ? x.stack : safeJson(x))).join(' '))
}
process.stdout.write = (chunk, ...rest) => { log('info', 'stdout', String(chunk).replace(/\n$/, '')); const cb = rest.find((x) => typeof x === 'function'); cb?.(); return true }
process.on('uncaughtException', (e) => log('error', 'host', 'uncaught: ' + (e?.stack || e)))
process.on('unhandledRejection', (e) => log('error', 'host', 'unhandled rejection: ' + (e?.stack || e)))

function safeJson(x) { try { return JSON.stringify(x) } catch { return String(x) } }

// ------------------------------------------------------------------ spec
const specPath = process.argv[2]
if (!specPath) { log('error', 'host', 'usage: host.mjs <spec.json>'); process.exit(2) }
const spec = JSON.parse(fs.readFileSync(specPath, 'utf8'))
const profile = path.resolve(spec.profile)
const baseUrl = pathToFileURL(profile + '/').href
const pkgDir = path.join(profile, 'node_modules', spec.pkg)
const dataDir = spec.dataDir ? path.resolve(spec.dataDir) : path.join(profile, '.data')
fs.mkdirSync(dataDir, { recursive: true })
const requireFromProfile = createRequire(baseUrl)

// ------------------------------------------------------------------ status
const status = {
  kit: KIT_VERSION,
  node: process.versions.node,
  cordis: readVersion('@deepseek-ai/cordis'),
  id: spec.id,
  pkg: spec.pkg,
  phase: 'starting',          // starting | loaded | failed
  state: 'PENDING',           // aggregate: ACTIVE | PENDING | FAILED | SKILLS
  rows: [],                   // { id, name, kind, state, missing[], error? }
  provided: [],
  requested: [],              // every service any row asked for (stub registry)
  missing: [],                // required services the host does not provide
  optional: [],               // services requested later via ctx.inject() that are not provided
  missingPackages: [],
  foreignOverrides: [],       // patch rows that rewrite other plugins' rows (DSH internals)
  skills: [],                 // { provider, dir, entries: [dir with SKILL.md] }
  commands: [],
  tools: [],                  // names of the tools the plugin registered (visible ones)
  error: null,
}
function readVersion(name) {
  try { return JSON.parse(fs.readFileSync(new URL(`./node_modules/${name}/package.json`, import.meta.url), 'utf8')).version } catch { return null }
}
const pushStatus = () => notify('status', status)

// ------------------------------------------------------------------ services
const root = new Context()
root.logger.exporter({
  colors: false,
  levels: { default: 3 },
  export(message) { log(message.type, message.name, Logger.format(this, message)) },
})

const BUILTIN = new Set(['logger', 'registry', 'reflect', 'events', 'fiber', 'root', 'baseUrl', 'loader'])
const provided = new Set()
function provide(name, impl) { root.provide(name, impl); provided.add(name) }

// locale — Mibot app language. register(ns|locale, dict), bind(ns) → t(key, params)
const localeImpl = (() => {
  let current = spec.locale || 'zh-CN'
  const dicts = new Map() // locale → Map(key → text)
  const subs = new Set()
  const fmt = (s, p) => typeof s === 'string' && p ? s.replace(/\{(\w+)\}/g, (_, k) => (p[k] ?? `{${k}}`)) : s
  function flatten(obj, prefix, into) {
    for (const [k, v] of Object.entries(obj || {})) {
      const key = prefix ? `${prefix}.${k}` : k
      if (v && typeof v === 'object') flatten(v, key, into); else into.set(key, v)
    }
  }
  function lookup(key) {
    const tries = [current, current.split('-')[0], 'zh-CN', 'zh', 'en-US', 'en']
    for (const l of tries) { const d = dicts.get(l); if (d?.has(key)) return d.get(key) }
    return key
  }
  return {
    getLocale: () => current,
    get locale() { return current },
    register(locale, messages) {
      // register('zh-CN', {...}) or register({ 'zh-CN': {...}, en: {...} })
      const pairs = typeof locale === 'string' ? [[locale, messages]] : Object.entries(locale || {})
      for (const [l, m] of pairs) { const d = dicts.get(l) ?? new Map(); flatten(m, '', d); dicts.set(l, d) }
      return () => {}
    },
    bind(ns) { return (key, params) => fmt(lookup(ns ? `${ns}.${key}` : key), params) },
    t: (key, params) => fmt(lookup(key), params),
    subscribe(fn) { subs.add(fn); return () => subs.delete(fn) },
    _set(l) { current = l; for (const fn of subs) try { fn(l) } catch {} ; root.emit?.('locale/change', l) },
  }
})()
provide('locale', localeImpl)

// config — per-plugin JSON store under the plugin's data dir.
const configImpl = (() => {
  const file = path.join(dataDir, 'config.json')
  let data = {}
  try { data = JSON.parse(fs.readFileSync(file, 'utf8')) } catch {}
  if (spec.config && typeof spec.config === 'object') data = { ...data, ...spec.config }
  const save = () => { const tmp = file + '.tmp'; fs.writeFileSync(tmp, JSON.stringify(data, null, 2)); fs.renameSync(tmp, file) }
  return {
    get: (key) => key == null ? structuredClone(data) : data[key],
    set(key, value) { data[key] = value; save(); return value },
    delete(key) { delete data[key]; save() },
  }
})()
provide('config', configImpl)

// commands — register / list / run. Stage 1: callable from the plugin page; chat
// slash commands come in stage 2.
const commandsImpl = (() => {
  const map = new Map()
  return {
    register(def, maybeFn) {
      const d = typeof def === 'string' ? { name: def, execute: maybeFn } : { ...def }
      const name = String(d.name || '').replace(/^\//, '')
      if (!name) throw new Error('command needs a name')
      map.set(name, d)
      status.commands = [...map.keys()]
      pushStatus()
      return () => { map.delete(name); status.commands = [...map.keys()]; pushStatus() }
    },
    list: () => [...map.values()].map((d) => {
      const o = { name: String(d.name).replace(/^\//, ''), description: d.description ?? d.summary ?? '' }
      const schema = d.parameters ?? d.args ?? d.schema
      if (schema && typeof schema === 'object' || typeof schema === 'function') { try { o.parameters = normalizeToolSchema(schema) } catch {} }
      return o
    }),
    async run(name, args = {}) {
      const d = map.get(String(name).replace(/^\//, ''))
      if (!d) throw new Error(`no such command: ${name}`)
      const fn = d.execute ?? d.handler ?? d.run ?? d.action
      if (typeof fn !== 'function') throw new Error(`command ${name} has no handler`)
      return await fn(args, { locale: localeImpl.getLocale() })
    },
  }
})()
provide('commands', commandsImpl)

// tools — Cordis service (stage 2, D1). Plugins register tools
//   ctx.tools.register({ name, description, parameters, risk?, annotations?, timeoutMs?, output?, execute(args, exec) }) → dispose
// `parameters` is a JSON Schema object (or a Schemastery schema, converted).
// Declared risk: def.risk ∈ read|write|external, else annotations.readOnlyHint → read,
// annotations.openWorldHint → external, else write.
// Kotlin mirrors the list (`tools/change` notification, `tools.list`) and calls
// `tools.execute {name, args, callId, timeoutMs?}` → {ok, output (≤ 64 KB), images?, truncated?}.
// guard()/restrict() only ever see the calling plugin's own tools: the owner of a
// tool or guard is the top-level fiber (patch row) it was registered from.
const TOOL_OUTPUT_CAP = 64 * 1024
const TOOL_TIMEOUT_DEFAULT = 60_000
const TOOL_TIMEOUT_MAX = 600_000
const TOOL_NAME_RE = /^[A-Za-z0-9_.:-]{1,128}$/
const SCHEMA_CAP = 32 * 1024
const RISKS = new Set(['read', 'write', 'external'])

function schemasteryToJson(s, depth = 0) {
  if (depth > 32) return {}
  const meta = s?.meta ?? {}
  const out = {}
  const put = (o) => {
    if (meta.description != null) o.description = typeof meta.description === 'string' ? meta.description : (meta.description['zh-CN'] ?? meta.description.en ?? Object.values(meta.description)[0])
    if (meta.default !== undefined && typeof meta.default !== 'function') o.default = meta.default
    return o
  }
  switch (s?.type) {
    case 'string': return put({ type: 'string', ...(meta.pattern?.source ? { pattern: meta.pattern.source } : {}) })
    case 'number': return put({ type: meta.step === 1 ? 'integer' : 'number', ...(meta.min != null ? { minimum: meta.min } : {}), ...(meta.max != null ? { maximum: meta.max } : {}) })
    case 'boolean': return put({ type: 'boolean' })
    case 'const': return put({ const: s.value })
    case 'array': return put({ type: 'array', items: schemasteryToJson(s.inner, depth + 1) })
    case 'tuple': return put({ type: 'array', prefixItems: (s.list ?? []).map((x) => schemasteryToJson(x, depth + 1)) })
    case 'dict': return put({ type: 'object', additionalProperties: schemasteryToJson(s.inner, depth + 1) })
    case 'object': {
      const properties = {}; const required = []
      for (const [k, v] of Object.entries(s.dict ?? {})) {
        properties[k] = schemasteryToJson(v, depth + 1)
        if (v?.meta?.required) required.push(k)
      }
      return put({ type: 'object', properties, ...(required.length ? { required } : {}) })
    }
    case 'union': {
      const list = s.list ?? []
      if (list.length && list.every((x) => x?.type === 'const' && typeof x.value === 'string')) return put({ type: 'string', enum: list.map((x) => x.value) })
      return put({ anyOf: list.map((x) => schemasteryToJson(x, depth + 1)) })
    }
    case 'intersect': return put({ allOf: (s.list ?? []).map((x) => schemasteryToJson(x, depth + 1)) })
    case 'transform': return schemasteryToJson(s.inner, depth + 1)
    default: return put(out)
  }
}

/** Normalise a tool's parameter schema to a JSON-serialisable JSON Schema object (type: object). Throws on junk. */
function normalizeToolSchema(p) {
  if (p == null) return { type: 'object', properties: {} }
  if (p[Symbol.for('schemastery')] || (typeof p === 'function' && typeof p.type === 'string')) p = schemasteryToJson(p)
  if (typeof p !== 'object' || Array.isArray(p)) throw new TypeError('tool parameters must be a JSON Schema object')
  let json
  try { json = JSON.stringify(p) } catch (e) { throw new TypeError('tool parameters are not JSON-serialisable: ' + e.message) }
  if (json.length > SCHEMA_CAP) throw new TypeError(`tool parameters schema too large (${json.length} > ${SCHEMA_CAP})`)
  const s = JSON.parse(json)
  if (s.type == null && s.properties) s.type = 'object'
  if (s.type !== 'object') throw new TypeError(`tool parameters must have type "object" (got ${JSON.stringify(s.type)})`)
  if (s.properties != null && (typeof s.properties !== 'object' || Array.isArray(s.properties))) throw new TypeError('tool parameters.properties must be an object')
  s.properties ??= {}
  if (s.required != null && (!Array.isArray(s.required) || s.required.some((r) => typeof r !== 'string'))) throw new TypeError('tool parameters.required must be a string array')
  return s
}

function toolRisk(def) {
  const r = String(def.risk ?? def.annotations?.risk ?? '').toLowerCase()
  if (RISKS.has(r)) return r
  if (def.annotations?.readOnlyHint === true) return 'read'
  if (def.annotations?.openWorldHint === true) return 'external'
  return 'write'
}

/** UTF-8 byte clip with a visible marker. */
function clipOutput(s, cap = TOOL_OUTPUT_CAP) {
  const buf = Buffer.from(s, 'utf8')
  if (buf.length <= cap) return { text: s, truncated: false }
  const marker = `\n…[output truncated: ${buf.length} bytes, limit ${cap}]`
  let cut = cap - Buffer.byteLength(marker)
  while (cut > 0 && (buf[cut] & 0xc0) === 0x80) cut--  // never split a UTF-8 sequence
  return { text: buf.subarray(0, cut).toString('utf8') + marker, truncated: true }
}

/** Turn whatever execute() returned into {output, images}. */
function renderToolResult(def, args, value) {
  const images = []
  const parts = []
  const fromContent = (content) => {
    for (const c of content) {
      if (c == null) continue
      if (typeof c === 'string') parts.push(c)
      else if (c.type === 'text') parts.push(String(c.text ?? ''))
      else if (c.type === 'image' && (c.path || c.file)) images.push(String(c.path || c.file))
      else parts.push(safeJson(c))
    }
  }
  let rendered = null
  if (typeof def.output?.render === 'function') {
    try { rendered = def.output.render(args, value) } catch (e) { parts.push(`[render failed: ${e?.message || e}] `) }
  }
  if (Array.isArray(rendered)) fromContent(rendered)
  else if (typeof rendered === 'string') parts.push(rendered)
  else if (value == null) parts.push('')
  else if (typeof value === 'string') parts.push(value)
  else if (Array.isArray(value?.content)) { fromContent(value.content); if (Array.isArray(value.images)) images.push(...value.images.map(String)) }
  else if (typeof value === 'object' && typeof value.output === 'string') { parts.push(value.output); if (Array.isArray(value.images)) images.push(...value.images.map(String)) }
  else if (typeof value === 'object') { try { parts.push(JSON.stringify(value, null, 2)) } catch { parts.push(String(value)) } }
  else parts.push(String(value))
  return { output: parts.join('\n'), images }
}

class ToolsService extends Service {
  constructor(ctx) {
    super(ctx, 'tools')
    // Shared state lives on a plain object: `this` is re-bound per caller by Cordis' tracer.
    this._s = { tools: new Map(), guards: [], restricts: [], timer: null }
  }

  /** The top-level fiber (patch row) a context belongs to; root for host code. */
  _owner(ctx) {
    let f = (ctx ?? this.ctx)?.fiber
    const rootFiber = root.fiber
    while (f && f.parent?.fiber && f.parent.fiber !== rootFiber && f.parent.fiber !== f) f = f.parent.fiber
    return f ?? rootFiber
  }

  _changed() {
    const s = this._s
    status.tools = this._list().map((t) => t.name)
    if (s.timer) return
    s.timer = setTimeout(() => {   // coalesce a burst of register() calls into one notification
      s.timer = null
      const tools = this._list()
      status.tools = tools.map((t) => t.name)
      notify('tools/change', { tools })
    }, 10)
  }

  _visible(entry) {
    for (const r of this._s.restricts) if (r.owner === entry.owner && !r.allow(entry)) return false
    return true
  }

  _list() {
    return [...this._s.tools.values()].filter((e) => this._visible(e))
      .map((e) => ({ name: e.name, description: e.description, parameters: e.parameters, risk: e.risk, ...(e.timeoutMs ? { timeoutMs: e.timeoutMs } : {}) }))
  }

  register(def, opts = {}) {
    if (!def || typeof def !== 'object') throw new TypeError('tools.register needs a tool definition')
    const name = String(def.name ?? '')
    if (!TOOL_NAME_RE.test(name)) throw new TypeError(`invalid tool name ${JSON.stringify(name)}`)
    const execute = def.execute ?? def.handler ?? def.run
    if (typeof execute !== 'function') throw new TypeError(`tool ${name} has no execute()`)
    const s = this._s
    if (s.tools.has(name) && !opts.override) throw new Error(`tool ${name} is already registered`)
    const caller = this.ctx
    const owner = this._owner(caller)
    if (s.tools.has(name) && s.tools.get(name).owner !== owner) throw new Error(`tool ${name} belongs to another plugin`)
    const entry = {
      name, def, execute, owner,
      description: String(def.description ?? '').slice(0, 4000),
      parameters: normalizeToolSchema(def.parameters ?? def.inputSchema ?? def.input),
      risk: toolRisk(def),
      timeoutMs: Number.isFinite(def.timeoutMs) && def.timeoutMs > 0 ? Math.min(def.timeoutMs, TOOL_TIMEOUT_MAX) : null,
    }
    const add = () => { s.tools.set(name, entry); this._changed(); return () => { if (s.tools.get(name) === entry) { s.tools.delete(name); this._changed() } } }
    return typeof caller?.fiber?.effect === 'function' && caller.fiber !== root.fiber ? caller.fiber.effect(add, `tools.register(${name})`) : add()
  }

  /** guard(fn | {guard|check}) — fn(exec) → undefined/true allows; false / string / {allow:false|deny:true, reason} denies. Own tools only. */
  guard(g) {
    const fn = typeof g === 'function' ? g : (g?.guard ?? g?.check)
    if (typeof fn !== 'function') throw new TypeError('tools.guard needs a function')
    const s = this._s
    const caller = this.ctx
    const rec = { owner: this._owner(caller), fn }
    const add = () => { s.guards.push(rec); return () => { const i = s.guards.indexOf(rec); if (i >= 0) s.guards.splice(i, 1) } }
    return typeof caller?.fiber?.effect === 'function' && caller.fiber !== root.fiber ? caller.fiber.effect(add, 'tools.guard') : add()
  }

  /** restrict(names[] | predicate(def)) — hide this plugin's own tools that do not pass. */
  restrict(filter) {
    const allow = Array.isArray(filter) ? ((e) => filter.includes(e.name)) : typeof filter === 'function' ? ((e) => !!filter(e.def)) : null
    if (!allow) throw new TypeError('tools.restrict needs a name list or a predicate')
    const s = this._s
    const caller = this.ctx
    const rec = { owner: this._owner(caller), allow }
    const add = () => { s.restricts.push(rec); this._changed(); return () => { const i = s.restricts.indexOf(rec); if (i >= 0) { s.restricts.splice(i, 1); this._changed() } } }
    return typeof caller?.fiber?.effect === 'function' && caller.fiber !== root.fiber ? caller.fiber.effect(add, 'tools.restrict') : add()
  }

  definitions() { return this._list() }
  get(name) { const e = this._s.tools.get(name); return e && this._visible(e) ? e.def : undefined }

  async _execute({ name, args, callId, timeoutMs } = {}) {
    const entry = this._s.tools.get(String(name))
    if (!entry || !this._visible(entry)) return { ok: false, output: `unknown tool: ${name}` }
    const a = args && typeof args === 'object' && !Array.isArray(args) ? args : {}
    const limit = Math.max(1_000, Math.min(Number(timeoutMs) || entry.timeoutMs || TOOL_TIMEOUT_DEFAULT, TOOL_TIMEOUT_MAX))
    const ac = new AbortController()
    const exec = { name: entry.name, args: a, callId: callId ?? null, signal: ac.signal, risk: entry.risk, tool: entry.def }
    const deny = (why) => ({ ok: false, denied: true, output: clipOutput(`tool ${entry.name} refused by its plugin: ${why}`).text })
    // 1. guards registered by the same plugin
    for (const g of this._s.guards) {
      if (g.owner !== entry.owner) continue
      let d
      try { d = await g.fn(exec) } catch (e) { return deny(e?.message || String(e)) }
      if (d === false) return deny('guard')
      if (typeof d === 'string') return deny(d)
      if (d && typeof d === 'object' && (d.allow === false || d.deny === true || d.decision === 'deny')) return deny(d.reason ?? d.message ?? 'guard')
    }
    let timer
    try {
      const run = async () => {
        // 2. tools/pre-execute middleware (waterfall): a listener that does not call next() vetoes.
        const pre = await root.waterfall('tools/pre-execute', exec, () => ({ allow: true }))
        if (pre && (pre.allow === false || pre.deny === true || pre.decision === 'deny')) return deny(pre.reason ?? 'pre-execute')
        const value = await entry.execute.call(entry.def, a, exec)
        const { output, images } = renderToolResult(entry.def, a, value)
        try { await root.parallel('tools/post-execute', exec, { ok: true, value }) } catch (e) { log('warn', 'tools', `post-execute: ${e?.message || e}`) }
        const c = clipOutput(output)
        return { ok: true, output: c.text, ...(images.length ? { images } : {}), ...(c.truncated ? { truncated: true } : {}) }
      }
      const runP = run()
      const raceP = Promise.race([
        runP,
        new Promise((resolve) => { timer = setTimeout(() => { ac.abort(new Error('timeout')); resolve({ ok: false, timeout: true, output: `tool ${entry.name} timed out after ${limit} ms` }) }, limit) }),
      ])
      runP.catch(() => {})   // [fork/mibot-destiny] late reject after timeout must not become unhandled
      return raceP
    } catch (e) {
      try { await root.parallel('tools/post-execute', exec, { ok: false, error: e }) } catch {}
      return { ok: false, output: clipOutput(String(e?.message || e)).text }
    } finally { clearTimeout(timer) }
  }
}
const toolsImpl = new ToolsService(root)
provided.add('tools')
status.provided = [...provided].sort()

// ------------------------------------------------------------------ patch rows
class JsExpr { constructor(src) { this.src = src } }
const yamlTags = [{ tag: 'tag:yaml.org,2002:js', resolve: (src) => new JsExpr(String(src)) }, { tag: '!js', resolve: (src) => new JsExpr(String(src)) }]

function evalJs(expr) {
  // Same scope DSH's loader gives `!!js` rows: baseUrl, ctx, process.
  // eslint-disable-next-line no-new-func
  return new Function('baseUrl', 'ctx', 'process', `return (${expr.src})`)(baseUrl, root, process)
}
function materialize(v) {
  if (v instanceof JsExpr) return evalJs(v)
  if (Array.isArray(v)) return v.map(materialize)
  if (v && typeof v === 'object') return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, materialize(x)]))
  return v
}

// Cordis 4 semantics (Inject.resolve): every declared name is required.
function normalizeInject(inject) {
  const required = new Set()
  if (!inject) return { required }
  if (Array.isArray(inject)) inject.forEach((n) => required.add(String(n)))
  else if (typeof inject === 'object') {
    for (let o = inject; o && o !== Object.prototype; o = Object.getPrototypeOf(o)) Object.keys(o).forEach((n) => required.add(n))
  }
  return { required }
}
// Stub registry: also record services requested at runtime through
// ctx.inject([...], cb) (optional features); unmet ones stay PENDING.
const runtimeRequested = new Set()
{
  const proto = Object.getPrototypeOf(root.registry)
  const orig = proto.inject
  proto.inject = function (inject, cb) {
    for (const n of normalizeInject(inject).required) runtimeRequested.add(topName(n))
    return orig.call(this, inject, cb)
  }
}
const topName = (n) => String(n).split('.')[0]

function scanSkills(dir) {
  const found = []
  const walk = (d, depth) => {
    let ents; try { ents = fs.readdirSync(d, { withFileTypes: true }) } catch { return }
    if (ents.some((e) => e.isFile() && e.name === 'SKILL.md')) { found.push(d); return }
    if (depth >= 3) return
    for (const e of ents) {
      if (!e.isDirectory() || e.name.startsWith('.')) continue
      if (e.name === 'node_modules' && depth > 0) continue
      walk(path.join(d, e.name), depth + 1)
    }
  }
  walk(dir, 0)
  return found
}

function resolveEntry(name) {
  try { return requireFromProfile.resolve(name) } catch {}
  // ESM-only conditional exports (no `require` condition): read package.json.
  const [scopeOrName, ...rest] = name.split('/')
  const pkgName = scopeOrName.startsWith('@') ? `${scopeOrName}/${rest.shift()}` : scopeOrName
  const sub = rest.length ? './' + rest.join('/') : '.'
  const dir = path.join(profile, 'node_modules', pkgName)
  const pj = JSON.parse(fs.readFileSync(path.join(dir, 'package.json'), 'utf8'))
  let target = null
  const pick = (e) => typeof e === 'string' ? e : e && (e.import ?? e.node ?? e.default ?? (e['.'] ? pick(e['.']) : null))
  if (pj.exports) {
    const ex = typeof pj.exports === 'string' || pj.exports.import || pj.exports.default ? (sub === '.' ? pj.exports : null) : pj.exports[sub]
    target = pick(ex)
  }
  if (!target && sub === '.') target = pj.module ?? pj.main ?? 'index.js'
  if (!target) throw new Error(`cannot resolve ${name}`)
  return path.join(dir, target)
}

async function loadRow(entry, rowInject) {
  const row = { id: entry.id ?? entry.name, name: entry.name, kind: 'plugin', state: 'PENDING', missing: [], requested: [] }
  status.rows.push(row)
  let config
  try { config = materialize(entry.config ?? {}) } catch (e) {
    row.state = 'FAILED'; row.error = 'config: ' + String(e?.stack || e); return
  }

  if (entry.name === '@deepseek-ai/dsh-skill-filesystem') {
    row.kind = 'skills'
    const dir = config.bundledSkillDir ? path.resolve(String(config.bundledSkillDir)) : null
    const entries = dir ? scanSkills(dir) : []
    status.skills.push({ provider: config.providerName ?? row.id, dir, entries })
    row.state = entries.length ? 'ACTIVE' : 'FAILED'
    if (!entries.length) row.error = `no SKILL.md under ${dir}`
    return
  }

  const own = entry.name === spec.pkg || String(entry.name).startsWith(spec.pkg + '/')
  if (!own && String(entry.name).startsWith('@deepseek-ai/')) {
    // A DSH-internal plugin (dsh-tools, dsh-llm…) mounted by this patch: fine when the
    // host already provides its service (kit 3: `tools`), else not in the kit.
    const svc = String(entry.name).replace('@deepseek-ai/', '').replace(/^dsh-/, '')
    if (provided.has(svc)) { row.kind = 'kit'; row.state = 'ACTIVE'; return }
    row.kind = 'dsh-internal'
    row.missing = [svc]; status.missing.push(svc)
    return
  }

  let mod
  try {
    const file = resolveEntry(entry.name)
    mod = await import(pathToFileURL(file).href)
  } catch (e) {
    const m = /MIBOT_MISSING_PACKAGE (\S+)/.exec(String(e?.message))
    if (m) {
      row.state = 'PENDING'; row.missing = [m[1]]; status.missingPackages.push(m[1])
      row.error = `needs ${m[1]} (not in the Mibot host kit)`
    } else {
      row.state = 'FAILED'; row.error = String(e?.stack || e)
    }
    log(row.state === 'FAILED' ? 'error' : 'warn', 'host', `import ${entry.name}: ${row.error}`)
    return
  }
  const def = mod.default && (typeof mod.default === 'function' || typeof mod.default.apply === 'function') ? mod.default : mod
  const apply = typeof def === 'function' && !def.apply ? def : def.apply
  if (typeof apply !== 'function') {
    row.state = 'FAILED'; row.error = 'module exports no apply()'
    return
  }
  const { required } = normalizeInject(def.inject)
  for (const n of normalizeInject(rowInject ?? entry.inject).required) required.add(n)
  row.requested = [...new Set([...required].map(topName))]
  row.missing = [...required].map(topName).filter((n) => !provided.has(n) && !BUILTIN.has(n))
  status.missing.push(...row.missing)

  const plugin = { name: def.name ?? entry.id ?? entry.name, inject: def.inject, Config: def.Config ?? def.schema, apply: apply.bind(def) }
  if (rowInject ?? entry.inject) plugin.inject = [...required]
  let fiber
  try {
    fiber = root.plugin(plugin, config)
  } catch (e) {
    row.state = 'FAILED'; row.error = String(e?.stack || e); return
  }
  row.fiber = fiber
  if (row.missing.length === 0) {
    const awaitP = fiber.await()
    awaitP.catch(() => {})   // [fork/mibot-destiny] late settle after the 20s race must not be unhandled
    try { await Promise.race([awaitP, new Promise((r) => setTimeout(r, 20_000))]) } catch (e) { row.error = String(e?.stack || e) }
  } else {
    await new Promise((r) => setTimeout(r, 50))
  }
  row.state = STATE[fiber.state] ?? String(fiber.state)
}

async function main() {
  const pj = JSON.parse(fs.readFileSync(path.join(pkgDir, 'package.json'), 'utf8'))
  const patchRel = pj?.dsh?.bundle?.patch
  let rows = []
  if (patchRel) {
    const text = fs.readFileSync(path.join(pkgDir, patchRel), 'utf8')
    const doc = YAML.parse(text, { customTags: yamlTags })
    rows = Array.isArray(doc) ? doc : (doc ? [doc] : [])
  } else {
    rows = [{ insert: [{ id: spec.id, name: spec.pkg }] }]
  }
  const ownIds = new Set(rows.flatMap((r) => (r?.insert ?? []).map((e) => e?.id).filter(Boolean)))
  // Policy: a patch that rewrites rows it does not own (system-prompt, other
  // plugins…) patches DSH internals. Refuse the whole plugin, load nothing.
  for (const r of rows) {
    if (r && typeof r === 'object' && !Array.isArray(r.insert) && r.id && !ownIds.has(r.id)) status.foreignOverrides.push(String(r.id))
  }
  if (status.foreignOverrides.length && !spec.allowForeign) {
    status.state = 'BLOCKED'; status.phase = 'failed'
    status.error = 'cordis.patch.yml 改写了不属于本插件的条目：' + status.foreignOverrides.join(', ')
    pushStatus(); return
  }
  for (const r of rows) {
    if (r && typeof r === 'object' && Array.isArray(r.insert)) {
      for (const e of r.insert) if (e && !e.disabled) await loadRow(e, null)
    }
  }
  status.requested = [...new Set(status.rows.flatMap((r) => r.requested ?? []))].sort()
  status.optional = [...runtimeRequested].filter((n) => !provided.has(n) && !BUILTIN.has(n) && !status.requested.includes(n)).sort()
  status.missing = [...new Set(status.missing)].sort()
  status.missingPackages = [...new Set(status.missingPackages)].sort()
  const states = status.rows.map((r) => r.state)
  status.state = states.includes('FAILED') ? 'FAILED'
    : (status.missing.length || status.missingPackages.length || states.includes('PENDING')) ? 'PENDING'
    : status.rows.some((r) => r.kind === 'skills') && status.rows.every((r) => r.kind === 'skills' || r.kind === 'kit') ? 'SKILLS'
    : status.rows.length ? 'ACTIVE' : 'FAILED'
  if (!status.rows.length) status.error = 'cordis.patch.yml 没有可加载的行'
  status.error ??= status.rows.find((r) => r.error)?.error ?? null
  status.phase = status.state === 'FAILED' ? 'failed' : 'loaded'
  for (const r of status.rows) delete r.fiber
  pushStatus()
}

// ------------------------------------------------------------------ request loop
const rl = readline.createInterface({ input: process.stdin })
let ready = null
rl.on('line', async (line) => {
  let msg; try { msg = JSON.parse(line) } catch { return }
  if (!msg || msg.method == null || msg.id == null) return
  const reply = (result) => out({ jsonrpc: '2.0', id: msg.id, result })
  const fail = (e) => out({ jsonrpc: '2.0', id: msg.id, error: { code: -32000, message: String(e?.message || e) } })
  try {
    switch (msg.method) {
      case 'ping': return reply({ pong: true, node: process.versions.node })
      case 'status': await ready; return reply(status)
      case 'commands.list': await ready; return reply(commandsImpl.list())
      case 'tools.list': await ready; return reply(toolsImpl._list())
      case 'tools.execute': await ready; return reply(await toolsImpl._execute(msg.params ?? {}))
      case 'commands.run': await ready; return reply(safeResult(await commandsImpl.run(msg.params?.name, msg.params?.args)))
      case 'config.get': return reply(configImpl.get())
      case 'locale.set': localeImpl._set(String(msg.params?.locale || 'zh-CN')); return reply(true)
      case 'shutdown':
        reply(true)
        try { await Promise.race([root.registry?.dispose?.() ?? Promise.resolve(), new Promise((r) => setTimeout(r, 2000))]) } catch {}
        setTimeout(() => process.exit(0), 50)
        return
      default: return fail(`unknown method ${msg.method}`)
    }
  } catch (e) { fail(e) }
})
rl.on('close', () => setTimeout(() => process.exit(0), 100))
function safeResult(v) { try { return JSON.parse(JSON.stringify(v ?? null)) } catch { return String(v) } }

notify('hello', { kit: KIT_VERSION, node: process.versions.node, cordis: status.cordis })
ready = main().catch((e) => {
  status.state = 'FAILED'; status.phase = 'failed'; status.error = String(e?.stack || e)
  for (const r of status.rows) delete r.fiber
  pushStatus()
})
