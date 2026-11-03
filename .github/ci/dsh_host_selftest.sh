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
    [ -n "${3:-}" ] && echo "$3"; sleep 2; echo '{"jsonrpc":"2.0","id":9,"method":"shutdown"}'; } |
    NODE_PATH="$W/kit/node_modules" timeout 60 node --import "$W/kit/bootstrap.mjs" "$W/kit/host.mjs" "$P/host.json" > "$P/out.ndjson"
  grep -m1 '"id":1,' "$P/out.ndjson" > "$P/status.json"
  echo "== $1"; cut -c1-600 "$P/status.json"
}
fetch alchaincyf/nuwa-skill huashu-nuwa nuwa
fetch QingYunA/answer-me-with-html answer-me-with-html amwh
fetch liustack/modlens @liustack/modlens modlens
# synthetic fixture: logger + locale + config + commands
F="$W/p/fixture/node_modules/mibot-fixture"; mkdir -p "$F"
cat > "$F/package.json" <<'J'
{"name":"mibot-fixture","version":"1.0.0","type":"module","main":"index.js","dsh":{"bundle":{"patch":"./cordis.patch.yml"}}}
J
cat > "$F/cordis.patch.yml" <<'J'
- insert:
    - id: mibot-fixture
      name: mibot-fixture
      config:
        greeting: !!js "'hi from ' + process.getBuiltinModule('node:path').basename(new URL(baseUrl).pathname)"
J
cat > "$F/index.js" <<'J'
export const name = 'mibot-fixture'
export const inject = ['locale', 'commands', 'config']
export function apply(ctx, config) {
  ctx.locale.register('zh-CN', { fixture: { hello: '你好 {who}' } })
  const t = ctx.locale.bind('fixture')
  ctx.logger.info('fixture up: %s', config.greeting)
  ctx.commands.register({ name: 'hello', description: 'say hello', execute: (args) => t('hello', { who: args.who ?? '?' }) + ' · ' + config.greeting })
  ctx.config.set('lastStart', 1)
}
J
run nuwa huashu-nuwa
run amwh answer-me-with-html
run modlens @liustack/modlens
run fixture mibot-fixture '{"jsonrpc":"2.0","id":3,"method":"commands.run","params":{"name":"hello","args":{"who":"Mibot"}}}'
python3 - "$W" <<'PY'
import json, sys, os
W = sys.argv[1]
def st(i): return json.load(open(f"{W}/p/{i}/status.json"))["result"]
n = st("nuwa"); assert n["state"] == "SKILLS" and any(e.endswith("/huashu-nuwa") for s in n["skills"] for e in s["entries"]), n
a = st("amwh"); assert a["state"] == "SKILLS" and any(e.endswith("skills/answer-me-with-html") for s in a["skills"] for e in s["entries"]), a
m = st("modlens"); assert m["state"] == "PENDING" and set(m["missing"]) >= {"tools", "llm", "attachments", "agents"}, m
f = st("fixture"); assert f["state"] == "ACTIVE" and f["commands"] == ["hello"], f
out = [json.loads(l) for l in open(f"{W}/p/fixture/out.ndjson") if l.strip().startswith("{")]
r3 = next(x for x in out if x.get("id") == 3)
assert r3["result"] == "你好 Mibot · hi from fixture", r3
assert any(x.get("method") == "log" and "fixture up" in x["params"]["text"] for x in out)
print("DSH host self-test OK: nuwa+amwh skills, modlens PENDING", sorted(m["missing"]), ", fixture ACTIVE + commands.run")
PY
