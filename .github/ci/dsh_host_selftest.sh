#!/usr/bin/env bash
# [fork/mibot-v5b4] DSH host kit self-test on the runner's Node 22: loads real
# plugins through assets/mibot/dsh-host exactly as the app does inside the
# sandbox (one process, NDJSON JSON-RPC over stdio) and asserts the reported
# status. No plugin postinstall scripts run (nothing is npm-installed here).
set -euo pipefail
KIT_SRC="src/android/app/src/main/assets/mibot/dsh-host"
W="${RUNNER_TEMP:-/tmp}/dshhost"; rm -rf "$W"; mkdir -p "$W"
cp -r "$KIT_SRC" "$W/kit"
node --version
fetch() { # repo pkg id
  local P="$W/p/$3"; mkdir -p "$P/node_modules/$2"
  curl -fsSL "https://codeload.github.com/$1/tar.gz/HEAD" | tar xz -C "$P/node_modules/$2" --strip-components=1
}
run() { # id pkg [extra requests]
  local P="$W/p/$1"; mkdir -p "$P"
  printf '{"id":"%s","pkg":"%s","profile":"%s","locale":"zh-CN"}' "$1" "$2" "$P" > "$P/host.json"
  { echo '{"jsonrpc":"2.0","id":1,"method":"status"}'; echo '{"jsonrpc":"2.0","id":2,"method":"commands.list"}'
    [ -n "${3:-}" ] && echo "$3"; sleep 4; echo '{"jsonrpc":"2.0","id":9,"method":"shutdown"}'; } |
    NODE_PATH="$W/kit/node_modules" timeout 60 node --import "$W/kit/bootstrap.mjs" "$W/kit/host.mjs" "$P/host.json" > "$P/out.ndjson"
  grep -m1 '"id":1,' "$P/out.ndjson" > "$P/status.json"
  echo "== $1"; cut -c1-600 "$P/status.json"
}
fetch alchaincyf/nuwa-skill huashu-nuwa nuwa
fetch QingYunA/answer-me-with-html answer-me-with-html amwh
fetch liustack/modlens @liustack/modlens modlens
# synthetic fixture: logger + locale + config + commands + tools (2 rows: the
# second row's guard must not reach the first row's tools)
F="$W/p/fixture/node_modules/mibot-fixture"; mkdir -p "$F"
cat > "$F/package.json" <<'J'
{"name":"mibot-fixture","version":"1.0.0","type":"module","main":"index.js","exports":{".":"./index.js","./other":"./other.js"},"dsh":{"bundle":{"patch":"./cordis.patch.yml"}}}
J
cat > "$F/cordis.patch.yml" <<'J'
- insert:
    - id: mibot-fixture
      name: mibot-fixture
      config:
        greeting: !!js "'hi from ' + process.getBuiltinModule('node:path').basename(new URL(baseUrl).pathname)"
    - id: mibot-fixture-other
      name: mibot-fixture/other
    - id: mibot-fixture-dsh-tools
      name: '@deepseek-ai/dsh-tools'
J
cat > "$F/index.js" <<'J'
import { defineTool, MIBOT_STAND_IN } from '@deepseek-ai/dsh-tools'
export const name = 'mibot-fixture'
export const inject = ['locale', 'commands', 'config', 'tools']
export function apply(ctx, config) {
  ctx.locale.register('zh-CN', { fixture: { hello: '你好 {who}' } })
  const t = ctx.locale.bind('fixture')
  ctx.logger.info('fixture up: %s', config.greeting)
  ctx.commands.register({ name: 'hello', description: 'say hello', execute: (args) => t('hello', { who: args.who ?? '?' }) + ' · ' + config.greeting })
  ctx.config.set('lastStart', 1)
  ctx.logger.info('dsh-tools stand-in: %s', MIBOT_STAND_IN)
  ctx.tools.register(defineTool({
    name: 'fixture_echo', description: 'Echo text back',
    annotations: { readOnlyHint: true },
    parameters: { type: 'object', properties: { text: { type: 'string', description: 'text' }, big: { type: 'boolean' }, slow: { type: 'boolean' } }, required: ['text'] },
    async execute(args, exec) {
      if (args.slow) await new Promise((r) => setTimeout(r, 5000))
      if (args.big) return 'x'.repeat(200 * 1024)
      return { echoed: args.text, callId: exec.callId }
    },
  }))
  ctx.tools.guard((exec) => exec.args.text === 'forbidden' ? 'no forbidden words' : undefined)
  ctx.on('tools/pre-execute', (exec, next) => { ctx.logger.info('pre-execute %s', exec.name); return next() })
}
J
cat > "$F/other.js" <<'J'
export const name = 'mibot-fixture-other'
export const inject = ['tools']
export function apply(ctx) {
  ctx.tools.guard(() => 'other row denies everything')   // must only cover its own tools
  ctx.tools.register({ name: 'other_tool', description: 'x', risk: 'external', parameters: { type: 'object', properties: {} }, execute: () => 'never' })
  let bad = null
  try { ctx.tools.register({ name: 'fixture_echo', description: 'dup', execute: () => 1 }) } catch (e) { bad = e.message }
  ctx.logger.info('dup register: %s', bad)
}
J
run nuwa huashu-nuwa
run amwh answer-me-with-html
run modlens @liustack/modlens
run fixture mibot-fixture "$(printf '%s\n' \
  '{"jsonrpc":"2.0","id":3,"method":"commands.run","params":{"name":"hello","args":{"who":"Mibot"}}}' \
  '{"jsonrpc":"2.0","id":4,"method":"tools.list"}' \
  '{"jsonrpc":"2.0","id":5,"method":"tools.execute","params":{"name":"fixture_echo","args":{"text":"hi"},"callId":"c1"}}' \
  '{"jsonrpc":"2.0","id":6,"method":"tools.execute","params":{"name":"fixture_echo","args":{"text":"forbidden"}}}' \
  '{"jsonrpc":"2.0","id":7,"method":"tools.execute","params":{"name":"fixture_echo","args":{"text":"x","big":true}}}' \
  '{"jsonrpc":"2.0","id":8,"method":"tools.execute","params":{"name":"fixture_echo","args":{"text":"x","slow":true},"timeoutMs":1000}}' \
  '{"jsonrpc":"2.0","id":10,"method":"tools.execute","params":{"name":"other_tool","args":{}}}' \
  '{"jsonrpc":"2.0","id":11,"method":"tools.execute","params":{"name":"nope","args":{}}}')"
python3 - "$W" <<'PY'
import json, sys, os
W = sys.argv[1]
def st(i): return json.load(open(f"{W}/p/{i}/status.json"))["result"]
n = st("nuwa"); assert n["state"] == "SKILLS" and any(e.endswith("/huashu-nuwa") for s in n["skills"] for e in s["entries"]), n
a = st("amwh"); assert a["state"] == "SKILLS" and any(e.endswith("skills/answer-me-with-html") for s in a["skills"] for e in s["entries"]), a
m = st("modlens"); assert m["state"] == "PENDING" and set(m["missing"]) >= {"llm", "attachments", "agents"} and "tools" not in m["missing"], m
f = st("fixture"); assert f["state"] == "ACTIVE" and f["commands"] == ["hello"], f
assert f["kit"] == 3 and "tools" not in f["missing"] and not f["missingPackages"], f
assert any(r["name"] == "@deepseek-ai/dsh-tools" and r["kind"] == "kit" and r["state"] == "ACTIVE" for r in f["rows"]), f["rows"]
out = [json.loads(l) for l in open(f"{W}/p/fixture/out.ndjson") if l.strip().startswith("{")]
r = {x["id"]: x for x in out if "id" in x and "method" not in x}
assert r[3]["result"] == "你好 Mibot · hi from fixture", r[3]
assert any(x.get("method") == "log" and "fixture up" in x["params"]["text"] for x in out)
tl = {t["name"]: t for t in r[4]["result"]}
assert set(tl) == {"fixture_echo", "other_tool"}, tl
assert tl["fixture_echo"]["risk"] == "read" and tl["fixture_echo"]["parameters"]["required"] == ["text"], tl
assert tl["other_tool"]["risk"] == "external", tl
e5 = r[5]["result"]; assert e5["ok"] and json.loads(e5["output"]) == {"echoed": "hi", "callId": "c1"}, e5
e6 = r[6]["result"]; assert not e6["ok"] and e6.get("denied") and "no forbidden words" in e6["output"], e6
e7 = r[7]["result"]; assert e7["ok"] and e7.get("truncated") and len(e7["output"].encode()) <= 64 * 1024, (e7["ok"], len(e7["output"]))
e8 = r[8]["result"]; assert not e8["ok"] and e8.get("timeout"), e8
e10 = r[10]["result"]; assert not e10["ok"] and "other row denies" in e10["output"], e10
e11 = r[11]["result"]; assert not e11["ok"] and "unknown tool" in e11["output"], e11
ch = [x for x in out if x.get("method") == "tools/change"]
assert ch and {t["name"] for t in ch[-1]["params"]["tools"]} == {"fixture_echo", "other_tool"}, ch
assert any(x.get("method") == "log" and "dup register: tool fixture_echo is already registered" in x["params"]["text"] for x in out)
assert any(x.get("method") == "log" and "pre-execute fixture_echo" in x["params"]["text"] for x in out)
assert any(x.get("method") == "log" and "dsh-tools stand-in: true" in x["params"]["text"] for x in out)
assert sorted(f["tools"]) == ["fixture_echo", "other_tool"], f
print("DSH host self-test OK: nuwa+amwh skills, modlens PENDING", sorted(m["missing"]), ", fixture ACTIVE (kit 3, dsh-tools stand-in) + commands.run + tools list/execute/guard/cap/timeout")
PY
