// Mibot DSH host — preload (`node --import ./bootstrap.mjs host.mjs …`).
//  1. ESM resolve hook: @deepseek-ai/* → host kit (hooks.mjs).
//  2. Android FBE/FUSE rejects link(); DSH uses it as a no-clobber publish
//     primitive. Fall back to an exclusive copy.
//  3. Scrub ambient environment down to a fixed allowlist (no stray secrets).
import { register, syncBuiltinESMExports } from 'node:module'
import fs from 'node:fs'

register('./hooks.mjs', import.meta.url)

const LINK_FALLBACK = new Set(['EPERM', 'ENOSYS', 'EXDEV', 'EACCES', 'ENOTSUP', 'EOPNOTSUPP'])
const origLink = fs.promises.link
fs.promises.link = async function link(src, dst) {
  try { return await origLink(src, dst) } catch (e) {
    if (!LINK_FALLBACK.has(e?.code)) throw e
    await fs.promises.copyFile(src, dst, fs.constants.COPYFILE_EXCL)
  }
}
const origLinkSync = fs.linkSync
fs.linkSync = function linkSync(src, dst) {
  try { return origLinkSync(src, dst) } catch (e) {
    if (!LINK_FALLBACK.has(e?.code)) throw e
    fs.copyFileSync(src, dst, fs.constants.COPYFILE_EXCL)
  }
}
syncBuiltinESMExports()

const KEEP = /^(PATH|HOME|LANG|LC_ALL|TZ|TMPDIR|NODE_PATH|NODE_OPTIONS|MIBOT_[A-Z_]+|https?_proxy|HTTPS?_PROXY|no_proxy|NO_PROXY)$/
for (const k of Object.keys(process.env)) if (!KEEP.test(k)) delete process.env[k]
