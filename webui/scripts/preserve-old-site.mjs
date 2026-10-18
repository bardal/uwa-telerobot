#!/usr/bin/env node
// Preserve selected pages of the original 1990s UWA Telerobot site as static,
// sanitised pages under public/old/.  Re-runnable: clears and rebuilds the target.
//
//   node scripts/preserve-old-site.mjs <path-to-htdocs> [targetDir]
//
// Plain Node, no dependencies.  The old HTML is simple enough for regex/string work.

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const SRC = path.resolve(process.argv[2] ?? '')
const OUT = path.resolve(process.argv[3] ?? path.join(here, '..', 'public', 'old'))
const JSON_OUT = path.join(here, '..', 'src', 'content', 'old-site.json')
if (!process.argv[2] || !fs.existsSync(SRC)) {
  console.error('usage: node preserve-old-site.mjs <htdocs-path> [targetDir]')
  process.exit(1)
}
if (path.basename(OUT) !== 'old') {
  console.error('refusing to clear a target folder that is not named "old": ' + OUT)
  process.exit(1)
}

const SITE_HOST = 'telerobot.mech.uwa.edu.au'
const IMG_EXT = /\.(gif|jpe?g|png|bmp)$/i
const CSS_EXT = /\.css$/i
const PAGE_EXT = /\.html?$/i
const DEFAULT_DOCS = ['index.html', 'index.htm', 'default.htm', 'default.html']

// ---------------------------------------------------------------- stats
const stats = {
  pages: 0, assets: 0, bytes: 0,
  emailsRemoved: 0, mailtoLinks: 0, ipsRemoved: 0, phonesRemoved: 0,
  scriptBlocks: 0, eventAttrs: 0, embeddedObjects: 0, formControlsDisabled: 0, formButtonsRemoved: 0,
  linksUnlinked: 0, linksRewritten: 0, externalLinks: 0, imagesRemovedMissing: 0, imagesRemovedExternal: 0,
  trackerLinks: 0, visitorNamesRemoved: 0, referrerUrlsReduced: 0, personalHomepagesRemoved: 0, commentsRemoved: 0, privacyLines: 0,
}
const notes = []

// ---------------------------------------------------------------- case-insensitive fs lookup
const dirCache = new Map()
function listDir(rel) {
  if (!dirCache.has(rel)) {
    let m = null
    try {
      m = new Map(fs.readdirSync(path.join(SRC, rel)).map((n) => [n.toLowerCase(), n]))
    } catch { /* not a directory */ }
    dirCache.set(rel, m)
  }
  return dirCache.get(rel)
}
/** real-cased relative path (posix) of an existing file/dir under SRC, or null */
function realPath(rel) {
  const norm = path.posix.normalize(rel.replace(/\\/g, '/')).replace(/^\/+/, '')
  if (norm.startsWith('..')) return null
  if (norm === '.' || norm === '') return ''
  let cur = ''
  for (const seg of norm.split('/')) {
    if (!seg) continue
    const m = listDir(cur)
    if (!m) return null
    const real = m.get(seg.toLowerCase())
    if (!real) return null
    cur = cur ? cur + '/' + real : real
  }
  return cur
}
const isDir = (rel) => { try { return fs.statSync(path.join(SRC, rel)).isDirectory() } catch { return false } }
const isFile = (rel) => { try { return fs.statSync(path.join(SRC, rel)).isFile() } catch { return false } }

// ---------------------------------------------------------------- naming
const safeSeg = (s) => s.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9._-]+/g, '-').replace(/-+/g, '-')
const htmlName = (p) => p.replace(PAGE_EXT, '.html')
const safePath = (p) => htmlName(p.split('/').map(safeSeg).join('/'))

/** Output path (relative to /old/) for a source page key (lowercase rel path), or null if not preserved. */
function pageOut(key) {
  const fixed = {
    'index980119.html': 'home-1998.html',
    'index990713.html': 'home-1999.html',
    'index.html': 'home.html',
    'about.html': 'about.html',
    'indexmore.htm': 'home-more.html',
    'indexlinks.htm': 'home-links.html',
    'compete.htm': 'competition/index.html',
    'results.htm': 'competition/results.html',
    'joedesig.htm': 'competition/joedesig.html',
    'newrobot/htdocs/infaces.htm': 'competition/how-to-enter.html',
    'newrobot/htdocs/statsidx.htm': 'competition/interface-statistics.html',
    'robot/well.htm': 'competition/message-form.html',
    'question/index.htm': 'questionnaire/index.html',
    'question/results/feb-july/index.htm': 'questionnaire/results/index.html',
    'news.html': 'news.html',
    'award.htm': 'award.html',
    'carnegie.htm': 'going-to-america.html',
    'ktaylor/robtime5.html': 'robot-task-time/index.html',
  }
  if (fixed[key]) return fixed[key]
  let m
  if ((m = key.match(/^00teams\/(\d+)team\/(.+\.html?)$/))) return `competition/team-reports/team${m[1]}/${safePath(m[2])}`
  if ((m = key.match(/^newrobot\/template\/pub\/(operator|observer|newoprtr)\/([^/]+\.html?)$/))) return `competition/designs/${m[1]}/${safePath(m[2])}`
  if ((m = key.match(/^question\/results\/feb-july\/(t?sld\d+\.htm)$/))) return `questionnaire/results/${safePath(m[1])}`
  if ((m = key.match(/^statistics\/(telerobotday|telerobotweek|telerobotmonth|telerobottotals|startoftelerobt|carnegie|logs1998q2)\.html$/)))
    return `statistics/${m[1]}.html`
  return null
}

// Seeds: pages that are always preserved; links from them (one level) to anything pageOut() allows are preserved too.
const SEEDS = [
  'index980119.html', 'index990713.html', 'index.html', 'about.html', 'IndexMore.HTM', 'IndexLinks.htm',
  'COMPETE.HTM', 'results.htm',
  'QUESTION/Index.htm', 'QUESTION/Results/Feb-July/index.htm',
  'Statistics/TelerobotDay.html', 'Statistics/TelerobotWeek.html', 'Statistics/TelerobotMonth.html',
  'Statistics/TelerobotTotals.html', 'Statistics/StartOfTelerobt.html', 'Statistics/Carnegie.html',
  'Statistics/logs1998q2.html',
  'news.html', 'AWARD.HTM', 'CARNEGIE.HTM',
  'KTAYLOR/ROBTIME5.html',
]

// ---------------------------------------------------------------- text helpers
function decode(buf) {
  try { return new TextDecoder('utf-8', { fatal: true }).decode(buf) } catch { /* not utf-8 */ }
  return new TextDecoder('windows-1252').decode(buf)
}

const ATTR_RE = /([^\s=/"'<>]+)(?:\s*=\s*("[^"]*"|'[^']*'|[^\s"'>]+))?/g
function parseAttrs(s) {
  const out = []
  for (const m of s.matchAll(ATTR_RE)) {
    let v = m[2] ?? null
    if (v !== null && (v[0] === '"' || v[0] === "'")) v = v.slice(1, -1)
    out.push({ name: m[1], value: v })
  }
  return out
}
const buildAttrs = (attrs) =>
  attrs.map((a) => (a.value === null ? ` ${a.name}` : ` ${a.name}="${a.value.replace(/"/g, '&quot;')}"`)).join('')
const getAttr = (attrs, n) => attrs.find((a) => a.name.toLowerCase() === n)?.value ?? null
function setAttr(attrs, n, v) {
  const a = attrs.find((x) => x.name.toLowerCase() === n)
  if (a) a.value = v
  else attrs.push({ name: n, value: v })
}
const delAttr = (attrs, n) => attrs.filter((a) => a.name.toLowerCase() !== n)

const EMAIL_RE = /[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)+/g
const IPV4_RE = /\b(?:\d{1,3}\.){3}\d{1,3}\b/g

function scrubText(s) {
  // "Some Name (someone@example.org)": a visitor quoted with an address; drop the name as well
  s = s.replace(new RegExp('\\b[A-Z][a-z]+(?:\\s+[A-Z][a-z]+){1,2}\\s*\\(\\s*' + EMAIL_RE.source + '\\s*\\)', 'g'), () => { stats.visitorNamesRemoved++; stats.emailsRemoved++; return 'A visitor' })
  s = s.replace(EMAIL_RE, () => { stats.emailsRemoved++; return '' })
  s = s.replace(IPV4_RE, () => { stats.ipsRemoved++; return '' })
  return s
}

/** Directory (relative to site root) named by a page's <base href>, or null if it has none on this site. */
function baseDirOf(text) {
  const m = text.match(/<base\b((?:"[^"]*"|'[^']*'|[^>"'])*)>/i)
  const href = m && getAttr(parseAttrs(m[1]), 'href')
  if (!href) return null
  try {
    const u = new URL(href.trim())
    if (u.hostname.toLowerCase() !== SITE_HOST) return null
    return decodeURIComponentSafe(u.pathname).replace(/^\/+/, '').replace(/\/[^/]*$/, (s) => (href.trim().endsWith('/') ? s : '')).replace(/\/+$/, '')
  } catch { return null }
}

// ---------------------------------------------------------------- link resolution
function resolveLink(rawHref, pageRel, hasBase) {
  let href = rawHref.trim()
  if (!href) return { kind: 'none' }
  if (href.startsWith('#')) return { kind: 'frag', frag: href }
  if (/^mailto:/i.test(href)) return { kind: 'mailto' }
  if (/^(javascript|ftp|news|telnet|gopher|file|data|about):/i.test(href)) return { kind: 'none' }
  if (href.startsWith('//')) href = 'https:' + href
  let p
  if (/^https?:/i.test(href)) {
    let u
    try { u = new URL(href) } catch { return { kind: 'none' } }
    if (u.hostname.toLowerCase() !== SITE_HOST) return { kind: 'external', href }
    if (u.port) return { kind: 'none' }
    p = decodeURIComponentSafe(u.pathname)
    return finishLocal(p, u.hash, true)
  }
  let frag = ''
  const hi = href.indexOf('#')
  if (hi >= 0) { frag = href.slice(hi); href = href.slice(0, hi) }
  const qi = href.indexOf('?')
  if (qi >= 0) href = href.slice(0, qi)
  p = decodeURIComponentSafe(href)
  if (!p) return { kind: 'frag', frag }
  if (p.startsWith('/')) return finishLocal(p, frag, true)
  // hasBase: null when the page has no usable <base href>, else the base directory under the site root ('' = root)
  const base = typeof hasBase === 'string' ? hasBase : path.posix.dirname(pageRel)
  return finishLocal(path.posix.join(base === '.' ? '' : base, p), frag, false)
}
function decodeURIComponentSafe(s) { try { return decodeURIComponent(s) } catch { return s } }
function finishLocal(p, frag) {
  let rel = realPath(p)
  if (rel === null) return { kind: 'missing' }
  if (rel === '' || isDir(rel)) {
    const m = listDir(rel)
    let found = null
    if (m) for (const d of DEFAULT_DOCS) if (m.has(d)) { found = rel ? rel + '/' + m.get(d) : m.get(d); break }
    if (!found) return { kind: 'missing' }
    rel = found
  }
  return { kind: 'local', rel, frag: frag && frag !== '#' ? frag : '' }
}

// ---------------------------------------------------------------- phase A: registry of pages
/** key -> { rel (real), out } */
const registry = new Map()
function register(rel) {
  const key = rel.toLowerCase()
  if (registry.has(key)) return true
  const out = pageOut(key)
  if (!out) return false
  registry.set(key, { rel, out })
  return true
}
for (const s of SEEDS) {
  const rel = realPath(s)
  if (!rel || !isFile(rel)) { notes.push('seed missing: ' + s); continue }
  if (!register(rel)) notes.push('seed has no output rule: ' + s)
}
// Only these seeds have their links followed (one level). The rest (statistics, logs, news ...) are
// copied as-is and their other links are unlinked.
const FOLLOW = ['index980119.html', 'index990713.html', 'index.html', 'compete.htm', 'results.htm', 'question/results/feb-july/index.htm']
for (const seedKey of FOLLOW) {
  if (!registry.has(seedKey)) continue
  const { rel } = registry.get(seedKey)
  const text = decode(fs.readFileSync(path.join(SRC, rel)))
  const hasBase = baseDirOf(text)
  for (const m of text.matchAll(/<(?:a|area)\b((?:"[^"]*"|'[^']*'|[^>"'])*)>/gi)) {
    const href = getAttr(parseAttrs(m[1]), 'href')
    if (!href) continue
    const r = resolveLink(href, rel, hasBase)
    if (r.kind === 'local' && PAGE_EXT.test(r.rel)) register(r.rel)
  }
}

// ---------------------------------------------------------------- assets
/** key -> out path (relative to /old/) */
const assets = new Map()
const usedAssetOut = new Set()
function assetDirFor(pageOut_) {
  const seg = pageOut_.includes('/') ? pageOut_.split('/')[0] : ''
  return seg
}
function registerAsset(rel, pageOutPath) {
  const key = rel.toLowerCase()
  if (assets.has(key)) return assets.get(key)
  const isCss = CSS_EXT.test(rel)
  const dir = (assetDirFor(pageOutPath) ? assetDirFor(pageOutPath) + '/' : '') + (isCss ? 'css' : 'images')
  let name = safeSeg(path.posix.basename(rel))
  let out = `${dir}/${name}`
  if (usedAssetOut.has(out)) {
    const parent = safeSeg(path.posix.basename(path.posix.dirname(rel)) || 'root')
    out = `${dir}/${parent}-${name}`
    let n = 2
    while (usedAssetOut.has(out)) out = `${dir}/${parent}-${n++}-${name}`
  }
  usedAssetOut.add(out)
  assets.set(key, out)
  return out
}

// ---------------------------------------------------------------- page processing
const MONTHS = 'Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec'
function findDate(text, mtimeYear) {
  const plain = text.replace(/<[^>]*>/g, ' ').replace(/&nbsp;/g, ' ').replace(/\s+/g, ' ')
  const m = plain.match(/last\s+(?:modified|updated|revised)\s*:?\s*(.{0,40})/i)
  if (m) {
    const d = m[1].match(new RegExp(`(\\d{1,2}\\s+(?:${MONTHS})[a-z]*\\.?\\s+\\d{4})`, 'i'))
      || m[1].match(new RegExp(`((?:${MONTHS})[a-z]*\\.?\\s+\\d{1,2},?\\s+\\d{4})`, 'i'))
      || m[1].match(/(\d{1,2}\/\d{1,2}\/\d{2,4})/)
    if (d) return d[1].trim()
  }
  return mtimeYear ? String(mtimeYear) : 'date unknown'
}

function processPage(entry) {
  const abs = path.join(SRC, entry.rel)
  const st = fs.statSync(abs)
  const mtimeYear = st.mtime.getFullYear()
  let html = decode(fs.readFileSync(abs)).replace(/\0/g, '')
  const hasBase = baseDirOf(html)
  const pageOutPath = entry.out
  const date = findDate(html.replace(/<!--[\s\S]*?-->/g, ''), mtimeYear >= 1993 && mtimeYear <= 2005 ? mtimeYear : null)
  entry.date = date

  // comments (may carry emails, commented-out scripts, etc.)
  html = html.replace(/<!--[\s\S]*?-->/g, () => { stats.commentsRemoved++; return '' })
  html = html.replace(/<!--[\s\S]*$/, '')
  // risky blocks
  for (const t of ['script', 'noscript', 'applet', 'object', 'iframe', 'noembed', 'noframes']) {
    html = html.replace(new RegExp(`<${t}\\b[\\s\\S]*?<\\/${t}\\s*>`, 'gi'), () => { stats.scriptBlocks++; return '' })
  }
  // Traffic-origin tables list the pages that referred visitors, with full URLs and search-query strings.
  // Keep the bars and percentages but show only the referring site's host name (no path, query or link).
  if (/^statistics\/telerobot(day|week|month|totals)\.html$/.test(entry.rel.toLowerCase())) {
    html = html.replace(/<a\b[^>]*href\s*=\s*["']?(https?:\/\/[^"'\s>/]+)[^>]*>[\s\S]*?<\/a\s*>/gi, (_m, origin) => {
      stats.referrerUrlsReduced++
      return origin.replace(/^https?:\/\//i, '')
    })
  }
  // The competition results page links to each entrant's personal home page: drop those links.
  if (entry.rel.toLowerCase() === 'results.htm') {
    html = html.replace(/<a\b[^>]*href\s*=\s*["']?\s*https?:\/\/(?!telerobot\.mech\.uwa\.edu\.au|www\.mech\.uwa\.edu\.au|www\.uwa\.edu\.au)[^>]*>[\s\S]*?<\/a\s*>/gi, () => {
      stats.personalHomepagesRemoved++
      return ''
    })
  }
  // tracker anchors (hit counters)
  html = html.replace(/<a\b[^>]*hitbox[^>]*>[\s\S]*?<\/a\s*>/gi, () => { stats.trackerLinks++; return '' })
  // mailto anchors: keep visible text unless it is itself an address
  html = html.replace(/<a\b[^>]*href\s*=\s*["']?\s*mailto:[^>]*>([\s\S]*?)<\/a\s*>/gi, (_m, inner, off, whole) => {
    stats.mailtoLinks++
    if (process.env.SHOW_MAILTO) console.error('MAILTO', entry.out, '::', whole.slice(Math.max(0, off - 70), off).replace(/s+/g, ' '), '[[', inner.replace(/<[^>]*>/g, '').slice(0, 30), ']]')
    const text = inner.replace(/<[^>]*>/g, '').trim()
    if (/@/.test(text) || /^(e-?mail|mail|email me)$/i.test(text)) return ''
    return inner
  })

  let dropA = false
  const TAG_RE = /<(\/?)([a-zA-Z][a-zA-Z0-9]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>/g
  html = html.replace(TAG_RE, (whole, slash, name, rest) => {
    const tag = name.toLowerCase()
    const closing = slash === '/'
    if (closing) {
      if (tag === 'a' && dropA) { dropA = false; return '' }
      if (['script', 'noscript', 'applet', 'object', 'iframe', 'embed', 'noembed', 'noframes', 'form'].includes(tag)) {
        return tag === 'form' ? whole : ''
      }
      return whole
    }
    if (['script', 'noscript', 'applet', 'object', 'iframe', 'embed', 'param', 'base', 'noembed', 'noframes'].includes(tag)) {
      stats.embeddedObjects++
      return ''
    }
    const selfClose = /\/\s*$/.test(rest)
    let attrs = parseAttrs(rest.replace(/\/\s*$/, ''))
    let changed = false
    const before = attrs.length
    attrs = attrs.filter((a) => !/^on[a-z]+$/i.test(a.name))
    if (attrs.length !== before) { stats.eventAttrs += before - attrs.length; changed = true }

    const rebuild = () => `<${name}${buildAttrs(attrs)}${selfClose ? ' /' : ''}>`

    switch (tag) {
      case 'meta': {
        if (getAttr(attrs, 'http-equiv') !== null) return ''
        if (getAttr(attrs, 'charset') !== null) return ''
        break
      }
      case 'link': {
        const rel = (getAttr(attrs, 'rel') ?? '').toLowerCase()
        const href = getAttr(attrs, 'href')
        if (rel.includes('stylesheet') && href) {
          const r = resolveLink(href, entry.rel, hasBase)
          if (r.kind === 'local' && CSS_EXT.test(r.rel) && isFile(r.rel)) {
            setAttr(attrs, 'href', '/old/' + registerAsset(r.rel, pageOutPath))
            return rebuild()
          }
        }
        return ''
      }
      case 'a':
      case 'area': {
        const href = getAttr(attrs, 'href')
        if (href === null) return changed ? rebuild() : whole
        const r = resolveLink(href, entry.rel, hasBase)
        attrs = delAttr(attrs, 'target')
        attrs = delAttr(attrs, 'rel')
        if (r.kind === 'frag') { setAttr(attrs, 'href', r.frag); return rebuild() }
        if (r.kind === 'external' && /\/~[^/]+/.test(href) && tag === 'a') {
          // a person's personal home page: keep the text, drop the link
          stats.personalHomepagesRemoved++
          dropA = true
          return ''
        }
        if (r.kind === 'external') {
          stats.externalLinks++
          setAttr(attrs, 'href', r.href)
          attrs.push({ name: 'rel', value: 'noopener' }, { name: 'target', value: '_blank' })
          return rebuild()
        }
        if (r.kind === 'local') {
          const key = r.rel.toLowerCase()
          let target = null
          if (registry.has(key)) target = '/old/' + registry.get(key).out
          else if (IMG_EXT.test(r.rel) && isFile(r.rel)) target = '/old/' + registerAsset(r.rel, pageOutPath)
          if (target) {
            stats.linksRewritten++
            setAttr(attrs, 'href', target + r.frag)
            return rebuild()
          }
        }
        // not preserved: unlink
        stats.linksUnlinked++
        attrs = delAttr(attrs, 'href')
        if (tag === 'a') {
          if (attrs.some((a) => ['name', 'id'].includes(a.name.toLowerCase()))) return rebuild()
          dropA = true
          return ''
        }
        return rebuild() // area without href
      }
      case 'img': {
        const src = getAttr(attrs, 'src')
        const alt = (getAttr(attrs, 'alt') ?? '').trim()
        const dropImg = (external) => {
          if (process.env.SHOW_MISSING) console.error("MISSING", entry.out, src)
          if (external) stats.imagesRemovedExternal++
          else stats.imagesRemovedMissing++
          return alt ? scrubText(alt) : ''
        }
        if (!src) return dropImg(false)
        const r = resolveLink(src, entry.rel, hasBase)
        if (r.kind === 'external') return dropImg(true)
        if (r.kind === 'local' && IMG_EXT.test(r.rel) && isFile(r.rel)) {
          setAttr(attrs, 'src', '/old/' + registerAsset(r.rel, pageOutPath))
          attrs = delAttr(attrs, 'lowsrc')
          attrs = delAttr(attrs, 'dynsrc')
          return rebuild()
        }
        return dropImg(false)
      }
      case 'body':
      case 'table':
      case 'tr':
      case 'td':
      case 'th': {
        const bg = getAttr(attrs, 'background')
        if (bg !== null) {
          const r = resolveLink(bg, entry.rel, hasBase)
          if (r.kind === 'local' && IMG_EXT.test(r.rel) && isFile(r.rel)) setAttr(attrs, 'background', '/old/' + registerAsset(r.rel, pageOutPath))
          else attrs = delAttr(attrs, 'background')
          return rebuild()
        }
        return changed ? rebuild() : whole
      }
      case 'form': {
        for (const n of ['action', 'method', 'enctype', 'target']) attrs = delAttr(attrs, n)
        return rebuild()
      }
      case 'input': {
        const type = (getAttr(attrs, 'type') ?? 'text').toLowerCase()
        if (['submit', 'reset', 'button', 'image', 'hidden', 'file'].includes(type)) { stats.formButtonsRemoved++; return '' }
        attrs = delAttr(attrs, 'src')
        if (!attrs.some((a) => a.name.toLowerCase() === 'disabled')) attrs.push({ name: 'disabled', value: null })
        stats.formControlsDisabled++
        return rebuild()
      }
      case 'select':
      case 'textarea':
      case 'button': {
        if (tag === 'button') { /* never useful without scripts */ }
        if (!attrs.some((a) => a.name.toLowerCase() === 'disabled')) attrs.push({ name: 'disabled', value: null })
        stats.formControlsDisabled++
        return rebuild()
      }
      default:
        break
    }
    return changed ? rebuild() : whole
  })

  // privacy scrubbing of visible text + attributes
  html = scrubText(html)
  html = privacyScrub(html, entry)

  // --- head / body scaffolding
  const banner = `<div class="old-banner">Preserved from the original UWA Telerobot site (<em>${date}</em>). Links that no longer lead anywhere have been removed. <a href="/#/about">Back to the UWA Telerobot</a></div>`
  const headMeta = '<meta charset="utf-8">\n<meta name="viewport" content="width=device-width, initial-scale=1">'
  const cssLink = '<link rel="stylesheet" href="/old/old.css">'
  if (/<head\b[^>]*>/i.test(html)) {
    html = html.replace(/<head\b[^>]*>/i, (m) => `${m}\n${headMeta}`)
    if (/<\/head\s*>/i.test(html)) html = html.replace(/<\/head\s*>/i, `${cssLink}\n</head>`)
    else html = html.replace(/(<meta name="viewport"[^>]*>)/i, `$1\n${cssLink}`)
  } else {
    const head = `<head>\n${headMeta}\n${cssLink}\n</head>\n`
    if (/<html\b[^>]*>/i.test(html)) html = html.replace(/<html\b[^>]*>/i, (m) => `${m}\n${head}`)
    else html = head + html
  }
  if (/<body\b[^>]*>/i.test(html)) {
    html = html.replace(/<body\b[^>]*>/i, (m) => `${m}\n${banner}`)
  } else {
    // no body tag: open one after the head
    if (/<\/head\s*>/i.test(html)) html = html.replace(/<\/head\s*>/i, `</head>\n<body>\n${banner}`)
    else html = html.replace(/(<link rel="stylesheet" href="\/old\/old\.css">)/, `$1\n<body>\n${banner}`)
    if (/<\/html\s*>/i.test(html)) html = html.replace(/<\/html\s*>/i, '</body>\n</html>')
    else html += '\n</body>'
  }
  if (!/^\s*<!doctype/i.test(html)) html = '<!DOCTYPE html>\n' + html.replace(/^\s+/, '')

  const dest = path.join(OUT, ...pageOutPath.split('/'))
  fs.mkdirSync(path.dirname(dest), { recursive: true })
  fs.writeFileSync(dest, html, 'utf8')
  stats.pages++
  stats.bytes += Buffer.byteLength(html)
}

/** Page-specific privacy rules (individual contact details etc.), applied after generic scrubbing. */
function privacyScrub(html, entry) {
  // a name left in front of an emptied address, e.g. "Some Name (<removed>)": a visitor quote; drop the name
  if (entry.rel.toLowerCase() === 'carnegie.htm') html = html.replace(/\b[A-Z][a-z]+(?:\s+[A-Z][a-z]+){1,2}\s*\(\s*\)/g, () => { stats.visitorNamesRemoved++; return 'A visitor' })
  // any other empty brackets left behind by removed addresses
  html = html.replace(/\(\s*\)/g, '')
  // phone / fax numbers
  html = html.replace(/\b(?:phone|tel|telephone|fax|mobile|ph)\b\s*[:.]?\s*(?:\(?\+?[\d][\d ()\-.]{6,}\d)/gi, () => { stats.phonesRemoved++; return '' })
  html = html.replace(/\(\+?\d{2,3}\)\s*\d[\d ]{6,}\d/g, () => { stats.phonesRemoved++; return '' })
  html = html.replace(/\+\d{2}[\d ()-]{8,}\d/g, () => { stats.phonesRemoved++; return '' })
  return html
}

// ---------------------------------------------------------------- run
fs.rmSync(OUT, { recursive: true, force: true })
fs.mkdirSync(OUT, { recursive: true })

for (const entry of registry.values()) processPage(entry)

// copy assets (bytes unchanged); stylesheets get url() rewriting
for (const [key, out] of assets) {
  const rel = realPath(key)
  const dest = path.join(OUT, ...out.split('/'))
  fs.mkdirSync(path.dirname(dest), { recursive: true })
  if (CSS_EXT.test(out)) {
    let css = decode(fs.readFileSync(path.join(SRC, rel)))
    css = css.replace(/@import[^;]*;/gi, '').replace(/url\(\s*(["']?)([^)"']+)\1\s*\)/gi, (m, _q, u) => {
      const r = resolveLink(u, rel, false)
      if (r.kind === 'local' && IMG_EXT.test(r.rel) && isFile(r.rel)) return `url("/old/${registerAssetLate(r.rel, out)}")`
      return 'none'
    })
    fs.writeFileSync(dest, css, 'utf8')
    stats.bytes += Buffer.byteLength(css)
  } else {
    fs.copyFileSync(path.join(SRC, rel), dest)
    stats.bytes += fs.statSync(dest).size
  }
  stats.assets++
}
function registerAssetLate(rel, cssOut) {
  const out = registerAsset(rel, cssOut)
  const dest = path.join(OUT, ...out.split('/'))
  if (!fs.existsSync(dest)) {
    fs.mkdirSync(path.dirname(dest), { recursive: true })
    fs.copyFileSync(path.join(SRC, rel), dest)
    stats.assets++
    stats.bytes += fs.statSync(dest).size
  }
  return out
}

const oldCss = `.old-banner {
  display: block;
  padding: 6px 10px;
  background: #ffffcc;
  border-bottom: 1px solid #c0c0a0;
  font: 14px Arial, Helvetica, sans-serif;
  color: #333333;
  text-align: left;
}
.old-banner a { color: #0645ad; }
.old-banner a:visited { color: #551a8b; }
`
fs.writeFileSync(path.join(OUT, 'old.css'), oldCss, 'utf8')
stats.bytes += Buffer.byteLength(oldCss)

// ---------------------------------------------------------------- old-site.json
const dateOf = (src) => registry.get(src.toLowerCase())?.date ?? 'date unknown'
const monthYear = (src) => {
  const rel = realPath(src)
  const d = fs.statSync(path.join(SRC, rel)).mtime
  return d.toLocaleString('en-GB', { month: 'long', year: 'numeric', timeZone: 'UTC' })
}
const G = {
  home: 'The original home page', comp: 'The 1997 interface competition', q: 'The visitor questionnaire',
  stats: 'Usage statistics', news: 'In the news', research: 'Research',
}
// the landing page lists these; the day and week referrer pages and the empty stub are kept but not listed
const index = [
  { group: G.home, title: 'Home page, January 1998', href: '/old/home-1998.html', date: monthYear('index980119.html'), what: 'The telerobot front page as it stood in January 1998, with live lab video and links to the robot.' },
  { group: G.home, title: 'Home page, July 1999', href: '/old/home-1999.html', date: monthYear('index990713.html'), what: 'The July 1999 front page, redesigned with a menu of sections: help, development, statistics, publications and links.' },
  { group: G.home, title: 'Home page, final version', href: '/old/home.html', date: monthYear('index.html'), what: 'The last version of the original front page, as it stood in early 2000.' },
  { group: G.home, title: 'About the robot', href: '/old/about.html', date: dateOf('about.html'), what: 'Barney Dalton\'s 1999 description of the robot, its cameras, the controller page and a visitor-built castle.' },
  { group: G.comp, title: 'The Great Telerobot Interface Design Competition', href: '/old/competition/index.html', date: dateOf('COMPETE.HTM'), what: 'A US$100 prize for the best idea to improve the operator interface, closing 1 October 1997.' },
  { group: G.q, title: 'The visitor questionnaire', href: '/old/questionnaire/index.html', date: dateOf('QUESTION/Index.htm'), what: 'The survey form visitors filled in about themselves, their computers and how they used the robot (fields shown disabled).' },
  { group: G.q, title: 'Questionnaire results', href: '/old/questionnaire/results/index.html', date: '1997', what: 'Charts summarising the February to July questionnaire results: demographics, computers, web use and willingness to pay.' },
  { group: G.stats, title: 'Where visitors came from, by month', href: '/old/statistics/telerobotmonth.html', date: dateOf('Statistics/TelerobotMonth.html'), what: 'Which sites sent visitors to the telerobot over a month, as percentages (host names only).' },
  { group: G.stats, title: 'Where visitors came from, in total', href: '/old/statistics/telerobottotals.html', date: dateOf('Statistics/TelerobotTotals.html'), what: 'Which sites sent the most visitors to the telerobot overall, as percentages (host names only).' },
  { group: G.stats, title: 'Traffic from the Carnegie Science Center', href: '/old/statistics/carnegie.html', date: dateOf('Statistics/Carnegie.html'), what: 'Where visitors came from around the time the robot was linked from the Carnegie Science Center.' },
  { group: G.stats, title: 'Web server statistics, Q2 1998', href: '/old/statistics/logs1998q2.html', date: dateOf('Statistics/logs1998q2.html'), what: 'An Analog report of web server traffic for the second quarter of 1998.' },
  { group: G.news, title: 'In The News', href: '/old/news.html', date: dateOf('news.html'), what: 'Television, radio, newspaper and magazine coverage of the telerobot between 1994 and 1998.' },
  { group: G.news, title: 'The 1996 JARA award', href: '/old/award.html', date: dateOf('AWARD.HTM'), what: 'The Japan Robot Association award won by the telerobot paper, presented in Milan in October 1996.' },
  { group: G.news, title: 'Going to America', href: '/old/going-to-america.html', date: dateOf('CARNEGIE.HTM'), what: 'How a second telerobot was planned for the Carnegie Science Center in Pittsburgh.' },
  { group: G.research, title: 'Robot Task Time', href: '/old/robot-task-time/index.html', date: dateOf('KTAYLOR/ROBTIME5.html'), what: 'A study by Ken Taylor and James Trevelyan of task performance times for a web-based telerobot.' },
]
fs.mkdirSync(path.dirname(JSON_OUT), { recursive: true })
fs.writeFileSync(JSON_OUT, JSON.stringify(index, null, 2) + '\n', 'utf8')

// ---------------------------------------------------------------- report
console.log('pages written:')
for (const e of registry.values()) console.log(`  /old/${e.out}  <-  ${e.rel}  [${e.date}]`)
console.log('stats:', JSON.stringify(stats, null, 2))
if (notes.length) console.log('notes:', notes)
