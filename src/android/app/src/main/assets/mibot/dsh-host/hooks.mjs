// Mibot DSH host — ESM resolve hook (registered by bootstrap.mjs).
// `@deepseek-ai/*` peer packages resolve to the host kit first (one Cordis
// copy for the whole process, or DI breaks), then to the plugin's own deps.
// A package neither has is reported as MIBOT_MISSING_PACKAGE so host.mjs can
// list it as 待支持 instead of crashing with a generic resolution error.
const KIT_PARENT = new URL('./host.mjs', import.meta.url).href

export async function resolve(specifier, context, next) {
  if (specifier.startsWith('@deepseek-ai/')) {
    try { return await next(specifier, { ...context, parentURL: KIT_PARENT }) } catch {}
    try { return await next(specifier, context) } catch {}
    const err = new Error(`MIBOT_MISSING_PACKAGE ${specifier}`)
    err.code = 'ERR_MODULE_NOT_FOUND'
    throw err
  }
  return next(specifier, context)
}
