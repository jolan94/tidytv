#!/usr/bin/env python3
"""Builds the per-model pages and guides for tidytv.winkle.dev.

    python3 tools/build-pages.py            # writes site/tv/, site/guides/, sitemap, main-page TV data
    python3 tools/build-pages.py --data     # also writes content/tv/data/<id>.json for the writers

Inputs:
  upstream/cleanyourtv/examples/<id>/debloat.txt   the tested lists (MIT, by Gathin): the facts
  content/tv/<id>.json                             plain-English intro, FAQ and "stays on" notes
  content/guides/<slug>.md                         articles, with a small front matter block

Every app, section and note on a model page comes from its list. The written intro only rephrases
the list's own header, so no page claims anything the list does not say.
"""
import html
import json
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LISTS = ROOT / "upstream/cleanyourtv/examples"
CONTENT = ROOT / "content"
SITE = ROOT / "site"
BASE = "https://tidytv.winkle.dev"
REPO = "https://github.com/jolan94/tidytv"
LIST_URL = "https://github.com/Gathin23/cleanyourtv/blob/main/examples/{id}/debloat.txt"
VERSION = "0.10.1"
PLAY = "https://play.google.com/store/apps/details?id=com.appfactory.tidytv"
APK = "https://github.com/jolan94/tidytv/releases/latest/download/TidyTV.apk"  # stable name, uploaded with every release
TODAY = date.today().isoformat()

PKG = re.compile(r"^([a-zA-Z][\w]*(?:\.[\w]+)+)\s*(?:#\s*(.*))?$")
OPT = re.compile(r"^#\s*([a-zA-Z][\w]*(?:\.[\w]+)+)\s*(?:#\s*(.*))?$")
e = html.escape

# Friendly names, read from the app's own table so the site and the app call apps the same thing.
_labels_src = (ROOT / "core/src/main/kotlin/dev/tidytv/core/lists/AppLabels.kt").read_text()
KNOWN = dict(re.findall(r'"([\w.]+)" to Known\("([^"]+)"', _labels_src))
GENERIC = {"com", "org", "net", "tv", "android", "app", "google", "apps", "service", "services"}


def label(pkg: str, note: str = "") -> str:
    """Same rule as AppLabels.label: known name, else the list's note, else the package's last words."""
    if pkg in KNOWN:
        return KNOWN[pkg]
    first = re.split(r"[,;(]| - | -- ", note)[0].strip()
    if 3 <= len(first) <= 40 and "re-enable" not in first.lower():
        return first[0].upper() + first[1:]
    parts = [p for p in pkg.split(".") if p not in GENERIC]
    tail = " ".join(p.replace("_", " ") for p in parts[-2:])
    return (tail[:1].upper() + tail[1:]) or pkg


# ---------- lists ----------

def parse_list(path: Path) -> dict:
    lines = path.read_text(encoding="utf-8-sig").replace("\r\n", "\n").split("\n")
    header, i = [], 0
    while i < len(lines) and lines[i].startswith("#"):
        header.append(lines[i][1:].strip() if len(lines[i]) > 1 else "")
        i += 1
    sections, cur, pending = [], None, []
    for raw in lines[i:]:
        line = raw.strip()
        if not line:
            pending = [] if cur is None or cur["apps"] or cur["optional"] else pending
            continue
        m = PKG.match(line)
        if m:
            if cur is None or (pending and (cur["apps"] or cur["optional"])):
                cur = {"title": " ".join(pending).strip() or "Other apps", "apps": [], "optional": []}
                sections.append(cur)
                pending = []
            cur["apps"].append({"pkg": m.group(1), "note": (m.group(2) or "").strip()})
            continue
        o = OPT.match(line)
        if o and cur is not None:
            cur["optional"].append({"pkg": o.group(1), "note": (o.group(2) or "").strip()})
            continue
        if line.startswith("#"):
            pending.append(line.lstrip("#").strip())
    for s in sections:
        # A heading is its first sentence; the rest of a long comment block is a note.
        t = s["title"]
        first = re.split(r"(?<=[.:])\s", t, maxsplit=1)
        s["title"], s["note"] = (first[0].rstrip(".:"), first[1] if len(first) > 1 else "") if len(t) > 60 else (t, "")
    head = "\n".join(header)
    kept = []
    m = re.search(r"Kept on purpose[^\n]*\n((?:\s*-.*\n?|\s{2,}\S.*\n?)+)", head)
    if m:
        for item in re.split(r"\n\s*-\s*", "\n" + m.group(1).strip()):
            item = " ".join(item.split()).lstrip("- ").strip()
            if item:
                kept.append(item)
    stock = re.search(r"Stock launcher:\s*([\w.]+)", head)
    return {
        "title": header[0] if header else path.parent.name,
        "header": head,
        "sections": sections,
        "kept": kept,
        "stock_launcher": stock.group(1) if stock else None,
        "count": sum(len(s["apps"]) for s in sections),
    }


def load_models() -> list:
    models = []
    for d in sorted(p for p in LISTS.iterdir() if (p / "debloat.txt").exists()):
        data = parse_list(d / "debloat.txt")
        data["id"] = d.name
        text = CONTENT / "tv" / f"{d.name}.json"
        data["copy"] = json.loads(text.read_text()) if text.exists() else None
        models.append(data)
    return models


# ---------- tiny markdown, for the guides ----------

def inline(s: str) -> str:
    s = e(s, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"(?<!\*)\*([^*]+)\*(?!\*)", r"<em>\1</em>", s)
    s = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", lambda m: f'<a href="{m.group(2)}">{m.group(1)}</a>', s)
    return s


def markdown(md: str) -> str:
    out, para, lst, quote = [], [], None, []

    def flush():
        nonlocal para, lst, quote
        if para:
            out.append(f"<p>{inline(' '.join(para))}</p>"); para = []
        if lst:
            tag, items = lst
            out.append(f"<{tag}>" + "".join(f"<li>{inline(x)}</li>" for x in items) + f"</{tag}>"); lst = None
        if quote:
            out.append(f"<blockquote><p>{inline(' '.join(quote))}</p></blockquote>"); quote = []

    for line in md.split("\n"):
        s = line.rstrip()
        if not s.strip():
            flush(); continue
        h = re.match(r"^(#{2,4})\s+(.*)", s)
        if h:
            flush(); n = len(h.group(1)); out.append(f"<h{n}>{inline(h.group(2))}</h{n}>"); continue
        b = re.match(r"^\s*[-*]\s+(.*)", s)
        o = re.match(r"^\s*\d+[.)]\s+(.*)", s)
        if b or o:
            tag = "ul" if b else "ol"
            if para: flush()
            if lst and lst[0] != tag: flush()
            lst = lst or (tag, [])
            lst[1].append((b or o).group(1)); continue
        if s.startswith(">"):
            if para or lst: flush()
            quote.append(s.lstrip("> ")); continue
        if lst and line.startswith("  "):
            lst[1][-1] += " " + s.strip(); continue
        if lst or quote: flush()
        para.append(s.strip())
    flush()
    return "\n".join(out)


def front_matter(text: str):
    meta = {}
    if text.startswith("---\n"):
        end = text.index("\n---", 4)
        for line in text[4:end].split("\n"):
            if ":" in line:
                k, v = line.split(":", 1)
                meta[k.strip()] = v.strip().strip('"')
        text = text[end + 4:]
    return meta, text.strip()


# ---------- page shell ----------

STAR = '<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.6l2.9 5.9 6.5.9-4.7 4.6 1.1 6.5L12 17.4l-5.8 3.1 1.1-6.5L2.6 9.4l6.5-.9z"/></svg>'
DOWN = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 3v12m0 0l-5-5m5 5l5-5M4 20h16"/></svg>'


def buttons() -> str:
    return (f'<div class="actions"><a class="play" href="{PLAY}"><img src="/img/google-play-badge.png" width="564" height="168" alt="Get it on Google Play"></a>'
            f'<a class="btn apk" href="{APK}">{DOWN}Download APK</a>'
            f'<a class="btn star" href="{REPO}" data-star>{STAR}Star on GitHub<span class="stars" data-count></span></a></div>')


def page(path: str, title: str, desc: str, body: str, ld: list, crumbs: list) -> str:
    url = f"{BASE}/{path}"
    crumb_html = " › ".join(f'<a href="{h}">{e(n)}</a>' if h else e(n) for n, h in crumbs)
    crumb_ld = {"@context": "https://schema.org", "@type": "BreadcrumbList", "itemListElement": [
        {"@type": "ListItem", "position": i + 1, "name": n, **({"item": BASE + h} if h else {})} for i, (n, h) in enumerate(crumbs)]}
    return f"""<!doctype html>
<html lang="en">
<meta charset="utf-8">
<title>{e(title)}</title>
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="description" content="{e(desc)}">
<link rel="canonical" href="{url}">
<meta name="theme-color" content="#2F6BFF">
<link rel="icon" href="/img/icons/a1-mascot-flat.webp" type="image/webp">
<meta property="og:type" content="article">
<meta property="og:site_name" content="TidyTV">
<meta property="og:url" content="{url}">
<meta property="og:title" content="{e(title)}">
<meta property="og:description" content="{e(desc)}">
<meta property="og:image" content="{BASE}/img/og.png">
<meta name="twitter:card" content="summary_large_image">
<script type="application/ld+json">{json.dumps(ld + [crumb_ld], ensure_ascii=False)}</script>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Nunito:wght@500;600;700;800;900&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/assets/pages.css">
<header class="top"><div class="wrap bar">
  <a class="wordmark" href="/"><img src="/img/icons/a1-mascot-flat.webp" width="40" height="40" alt="">TidyTV</a>
  <nav aria-label="Main"><a href="/tv/">TV models</a><a href="/guides/">Guides</a><a class="starpill" href="{REPO}" data-star>{STAR}Star<span class="stars" data-count></span></a></nav>
</div></header>
<main class="wrap">
<p class="crumbs">{crumb_html}</p>
{body}
<section class="card cta">
  <div>
    <h2>Tidy your TV from your phone</h2>
    <p>TidyTV is free and open source. It saves a backup first, turns apps off instead of deleting them, and puts everything back with one tap.</p>
    {buttons()}
    <p class="ask">A star on GitHub is how other people find TidyTV.</p>
  </div>
  <img src="/img/art/mascot-cheer.webp" width="800" height="683" loading="lazy" alt="The TidyTV mascot cheering.">
</section>
</main>
<footer class="wrap">
  <p class="tag">Free and open source · No AI · No laptop · One-tap undo</p>
  <p class="small">TidyTV (MIT) uses TV lists from <a href="https://github.com/Gathin23/cleanyourtv">cleanyourtv</a> and the home screen from <a href="https://github.com/Ansub/tv-detox">tv-detox</a>, both MIT. <a href="{REPO}">Source on GitHub</a> (a star helps) · <a href="/privacy/">Privacy policy</a>. Not affiliated with Google, Amazon or any TV maker.</p>
</footer>
<script src="/assets/stars.js" defer></script>
"""


# ---------- model pages ----------

def short_name(m: dict) -> str:
    c = m["copy"] or {}
    return c.get("name") or re.split(r"\s*[(,]", m["title"])[0].strip()


def os_label(m: dict) -> str:
    c = m["copy"] or {}
    if c.get("os"):
        return c["os"]
    g = re.search(r"(Google TV[^,(]*?\d+|Android TV \d+|Fire OS [\d.]+|Android \d+[\d.]*)", m["title"])
    return g.group(1).strip() if g else "Android TV"


def is_fire(m: dict) -> bool:
    return "fire" in m["id"] or "Fire OS" in m["title"]


def app_row(a: dict, optional=False) -> str:
    name = label(a["pkg"], a["note"])
    # Show the note only when it adds something beyond the name it produced.
    extra = a["note"] if a["note"] and not a["note"].lower().startswith(name.lower()) else ""
    tag = ' <span class="opt">your call</span>' if optional else ""
    note = f'<span class="note">{e(extra)}</span>' if extra else ""
    return f'<li><b>{e(name)}</b>{tag}{note}<code>{e(a["pkg"])}</code></li>'


def model_page(m: dict, same_brand: list) -> str:
    c = m["copy"] or {}
    name, osl = short_name(m), os_label(m)
    n = m["count"]
    desc = c.get("summary") or f"The {n} apps TidyTV can turn off on the {name} ({osl}), from a list tested on a real set. Free phone app, backup first, one-tap undo."
    intro = "".join(f"<p>{inline(p)}</p>" for p in c.get("intro", [])) or (
        f"<p>This list was tested on a real {e(name)}. TidyTV uses it to turn off {n} apps over Wi-Fi from your phone, "
        f"after saving a backup. Nothing is deleted, and one tap puts it all back.</p>")
    fire = ('<p class="note-box">Fire TV: TidyTV turns the apps off but keeps Amazon\'s home screen, '
            'because Fire OS won\'t let another home screen take over.</p>') if is_fire(m) else ""
    secs = []
    for s in m["sections"]:
        if not s["apps"] and not s["optional"]:
            continue
        rows = "".join(app_row(a) for a in s["apps"]) + "".join(app_row(a, True) for a in s["optional"])
        note = f'<p class="secnote">{e(s["note"])}</p>' if s.get("note") else ""
        secs.append(f'<div class="group"><h3>{e(s["title"])} <span class="n">{len(s["apps"])}</span></h3>{note}<ul class="apps">{rows}</ul></div>')
    keep = c.get("keep") or m["kept"]
    keep_html = ("<ul class=\"keep\">" + "".join(f"<li>{inline(k)}</li>" for k in keep) + "</ul>") if keep else \
        "<p>Play Store, Play Services, keyboards, TV inputs, the remote and Bluetooth pairing, Settings and setup apps are always kept, whatever a list says.</p>"
    notes = c.get("notes") or []
    notes_html = ("<h2>Good to know on this model</h2><ul class=\"keep\">" + "".join(f"<li>{inline(x)}</li>" for x in notes) + "</ul>") if notes else ""
    faq = c.get("faq") or []
    faq_html = ("<h2>Questions</h2>" + "".join(f'<details><summary>{e(q["q"])}</summary><p>{inline(q["a"])}</p></details>' for q in faq)) if faq else ""
    related = "".join(f'<li><a href="/tv/{o["id"]}/">{e(short_name(o))}</a> <span>{e(os_label(o))}</span></li>' for o in same_brand[:8])
    related_html = f'<h2>Other {e(brand(m))} models</h2><ul class="related">{related}</ul>' if related else ""
    body = f"""
<article class="card model">
  <h1>Debloat the {e(name)}</h1>
  <p class="meta">{e(osl)} · {n} apps on the list · tested on a real set</p>
  {intro}{fire}
  {buttons()}
  <h2>Apps TidyTV can turn off on the {e(name)}</h2>
  <p>Grouped as the tested list groups them. In the app each one has a switch, so you choose. Apps marked <span class="opt">your call</span> are extras the list leaves on.</p>
  {''.join(secs)}
  <h2>What always stays on</h2>
  {keep_html}
  {notes_html}
  <h2>How to do it</h2>
  <ol class="steps">
    <li><b>Turn on developer options</b> On the TV, open Settings › System › About and select the build number 7 times. Then turn on USB debugging, and Network debugging if you see it.</li>
    <li><b>Tap your TV in TidyTV</b> Your phone finds the TV on the Wi-Fi. Tick "Always allow" and press Allow when the TV asks.</li>
    <li><b>Review, then tidy</b> TidyTV saves a backup, shows these apps with switches, and turns off the ones you pick, checking each one.</li>
  </ol>
  {faq_html}
  <p class="source">Source: the <a href="{LIST_URL.format(id=m['id'])}">{e(m['id'])} list</a> in cleanyourtv by Gathin (MIT), tested on this model. TidyTV re-checks every app against its own protected list before each change.</p>
  {related_html}
</article>"""
    ld = [{"@context": "https://schema.org", "@type": "HowTo", "name": f"Debloat the {name}",
           "description": desc, "step": [
               {"@type": "HowToStep", "name": "Turn on developer options and network debugging on the TV"},
               {"@type": "HowToStep", "name": "Open TidyTV on your phone and tap your TV"},
               {"@type": "HowToStep", "name": f"Review the {n} apps and tap Turn off"}]}]
    if faq:
        ld.append({"@context": "https://schema.org", "@type": "FAQPage", "mainEntity": [
            {"@type": "Question", "name": q["q"], "acceptedAnswer": {"@type": "Answer", "text": re.sub(r"[*`]", "", q["a"])}} for q in faq]})
    title = c.get("title") or f"Debloat the {name}: {n} apps you can turn off ({osl}) | TidyTV"
    return page(f"tv/{m['id']}/", title, desc, body, ld, [("TidyTV", "/"), ("TV models", "/tv/"), (name, None)])


def brand(m: dict) -> str:
    c = m["copy"] or {}
    return c.get("brand") or short_name(m).split()[0]


def tv_index(models: list) -> str:
    by = {}
    for m in models:
        by.setdefault(brand(m), []).append(m)
    blocks = "".join(
        f'<div class="group"><h2>{e(b)} <span class="n">{len(ms)}</span></h2><ul class="related">'
        + "".join(f'<li><a href="/tv/{m["id"]}/">{e(short_name(m))}</a> <span>{e(os_label(m))} · {m["count"]} apps</span></li>' for m in ms)
        + "</ul></div>" for b, ms in sorted(by.items(), key=lambda kv: (-len(kv[1]), kv[0].lower())))
    body = f"""
<article class="card">
  <h1>Debloat lists for {len(models)} TV models</h1>
  <p class="meta">Each list was tested on a real TV, box or projector.</p>
  <p>Pick your model to see exactly which apps TidyTV turns off, what it always keeps, and anything special about that set. Not on the list? TidyTV still backs up your TV and only offers apps it knows are safe to switch off.</p>
  {buttons()}
  {blocks}
</article>"""
    return page("tv/", f"Debloat lists for {len(models)} Android TV and Google TV models | TidyTV",
                f"Tested debloat lists for {len(models)} Android TV, Google TV and Fire TV models from TCL, Sony, Xiaomi, Hisense and more. See which apps are safe to turn off.",
                body, [], [("TidyTV", "/"), ("TV models", None)])


# ---------- guides ----------

def load_guides() -> list:
    out = []
    for p in sorted((CONTENT / "guides").glob("*.md")):
        meta, md = front_matter(p.read_text(encoding="utf-8"))
        meta["slug"] = p.stem
        meta["html"] = markdown(md)
        meta["words"] = len(re.findall(r"\w+", md))
        out.append(meta)
    return sorted(out, key=lambda g: int(g.get("order", 99)))


def guide_page(g: dict, others: list) -> str:
    more = "".join(f'<li><a href="/guides/{o["slug"]}/">{e(o["title"])}</a></li>' for o in others if o is not g)
    body = f"""
<article class="card guide">
  <h1>{e(g['title'])}</h1>
  <p class="meta">{e(g.get('dek', ''))}</p>
  <div class="prose">{g['html']}</div>
  <h2>More guides</h2>
  <ul class="related">{more}</ul>
</article>"""
    ld = [{"@context": "https://schema.org", "@type": "Article", "headline": g["title"], "description": g.get("description", ""),
           "datePublished": g.get("date", TODAY), "dateModified": TODAY,
           "author": {"@type": "Organization", "name": "TidyTV"}, "publisher": {"@type": "Organization", "name": "TidyTV"},
           "image": f"{BASE}/img/og.png", "mainEntityOfPage": f"{BASE}/guides/{g['slug']}/"}]
    return page(f"guides/{g['slug']}/", g.get("seo_title") or f"{g['title']} | TidyTV", g.get("description", ""), body, ld,
                [("TidyTV", "/"), ("Guides", "/guides/"), (g["title"], None)])


def guides_index(gs: list) -> str:
    items = "".join(f'<li><a href="/guides/{g["slug"]}/"><b>{e(g["title"])}</b></a><span>{e(g.get("dek", ""))}</span></li>' for g in gs)
    body = f"""
<article class="card">
  <h1>Guides</h1>
  <p class="meta">Cleaning up an Android TV or Google TV, with an AI agent or without one.</p>
  <ul class="guides">{items}</ul>
</article>"""
    return page("guides/", "Guides: debloat Android TV and Google TV, with or without AI | TidyTV",
                "How people are cleaning up Android TV and Google TV with Claude and other AI agents, what can go wrong, and how to do it safely from your phone.",
                body, [], [("TidyTV", "/"), ("Guides", None)])


# ---------- main ----------


# ---------- privacy ----------

PRIVACY_DATE = "24 October 2026"

def privacy_page() -> str:
    body = f"""<article class="card guide">
  <h1>Privacy policy</h1>
  <p class="meta">TidyTV for Android · Effective {PRIVACY_DATE}</p>
  <div class="prose">
<p>TidyTV is a free, open-source phone app that cleans up Android TV and Google TV sets over your home network. It has no account, no ads, no analytics and no tracking. This page explains the little data it handles and where it goes. The full source code is on <a href="{REPO}">GitHub</a>, so anyone can check it.</p>

<h2>The short version</h2>
<ul>
<li>TidyTV talks to your TV over your own home network. That traffic never leaves your network.</li>
<li>Installed from Google Play, it never checks for updates itself: Play does that. A copy installed from GitHub asks GitHub once a day whether a newer version is out, and sends nothing about you or your TV.</li>
<li>Anything else leaves your phone only when you choose to send it: a message in the feedback form, or something you share through Android's share sheet.</li>
<li>We don't sell data, show ads, or use analytics or advertising SDKs.</li>
</ul>

<h2>What stays on your phone</h2>
<p>To work and to let you undo changes, TidyTV keeps these files in its own private storage on your phone:</p>
<ul>
<li><b>TVs you've connected to:</b> the TV's name, model, network address and serial number, so it can reconnect in one tap.</li>
<li><b>Backups and a change log:</b> the list of apps on each TV and what TidyTV changed, so Restore can put everything back.</li>
<li><b>A connection key:</b> the key your TV asks you to allow ("Always allow from this computer"), so it doesn't ask every time.</li>
<li><b>A short activity log</b> for troubleshooting, and app icons read from your TV.</li>
<li><b>A random install ID</b>, created on the phone, used only to tell feedback messages from the same install apart.</li>
</ul>
<p>None of this is uploaded. Uninstalling TidyTV, or clearing its storage in Android's settings, deletes all of it.</p>

<h2>What happens on your TV</h2>
<p>With the debugging permission you turn on and allow on the TV, TidyTV turns apps off and on, can set a different home screen, sends remote key presses and text you type in the Remote, and saves files you choose to send into the TV's Downloads › TidyTV folder. It reads the list of installed apps and basic device details (model, Android version) to pick the right clean-up list. It doesn't read your files, accounts, viewing history or passwords on the TV.</p>

<h2>What goes over the internet</h2>
<h3>Update check</h3>
<p>Copies installed from Google Play skip this: Play handles their updates. A copy installed another way (for example the APK from GitHub) requests the public "latest release" page of TidyTV's GitHub repository once a day to see whether a newer version exists. The request contains nothing about you or your TV. Like any web request, GitHub sees your IP address; see <a href="https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement">GitHub's privacy statement</a>.</p>
<h3>Feedback form (only when you tap Send)</h3>
<p>If you send a message from the app, it includes:</p>
<ul>
<li>your message and the kind of message you picked;</li>
<li>your email address, only if you type one so we can reply;</li>
<li>your phone model, Android version, TidyTV version and the random install ID;</li>
<li>the activity log, only if you tick the box. It includes your TV's model and Android version and what TidyTV did. Serial numbers, Wi-Fi names, MAC addresses, email addresses and most of each IP address are removed from it on your phone before sending;</li>
<li>a screenshot, only if you add one.</li>
</ul>
<p>Messages are received through a Google Apps Script form and stored in a private Google Sheet and email inbox used by the developer to read and answer feedback. They are used only to fix problems and improve TidyTV, never for marketing, and never shared or sold. We delete them on request and otherwise remove them when they are no longer needed.</p>
<h3>Sharing</h3>
<p>The share card and Share logs use Android's share sheet. Whatever you share goes only to the app you pick, under that app's own privacy policy. Logs are cleaned the same way as above.</p>
<h3>Projectivy Launcher</h3>
<p>If you choose Projectivy as your home screen, TidyTV opens its page in the Google Play Store on your TV. Installing it is between you and Google Play; TidyTV doesn't send anything to Projectivy's developer.</p>

<h2>Permissions</h2>
<ul>
<li><b>Internet and network state:</b> to reach your TV on your home network, and for the update check and feedback.</li>
<li><b>Wi-Fi state and multicast:</b> to find TVs on your network.</li>
</ul>
<p>TidyTV doesn't ask for your location, contacts, camera, microphone or storage. When you send files, you pick them in Android's own file picker, and TidyTV only reads the files you pick.</p>

<h2>Children</h2>
<p>TidyTV is a general-audience utility and isn't directed at children under 13. We don't knowingly collect personal information from children.</p>

<h2>Your choices</h2>
<p>You can use TidyTV without ever sending feedback or sharing anything. To have a feedback message deleted, send a request through the app's feedback form or open an issue on <a href="{REPO}/issues">GitHub</a> with the date you sent it.</p>

<h2>Changes and contact</h2>
<p>If this policy changes, we'll update this page and the date above. Questions: use the feedback form in the app or open an issue on <a href="{REPO}/issues">GitHub</a>.</p>
  </div>
</article>"""
    ld = [{"@context": "https://schema.org", "@type": "WebPage", "name": "TidyTV privacy policy", "url": f"{BASE}/privacy/", "dateModified": "2026-10-24"}]
    return page("privacy/", "Privacy policy | TidyTV", "What data the TidyTV app handles: almost none. No account, ads, analytics or tracking. What stays on your phone and what is sent only when you choose.",
                body, ld, [("TidyTV", "/"), ("Privacy policy", None)])


def write(rel: str, text: str):
    p = SITE / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text, encoding="utf-8")


def main():
    models = load_models()
    if "--data" in sys.argv:
        d = CONTENT / "tv" / "data"
        d.mkdir(parents=True, exist_ok=True)
        for m in models:
            facts = {k: m[k] for k in ("id", "title", "header", "sections", "kept", "stock_launcher", "count")}
            (d / f"{m['id']}.json").write_text(json.dumps(facts, indent=1, ensure_ascii=False))
    for m in models:
        same = [o for o in models if o is not m and brand(o) == brand(m)]
        write(f"tv/{m['id']}/index.html", model_page(m, same))
    write("tv/index.html", tv_index(models))
    gs = load_guides()
    for g in gs:
        write(f"guides/{g['slug']}/index.html", guide_page(g, gs))
    if gs:
        write("guides/index.html", guides_index(gs))
    write("privacy/index.html", privacy_page())
    urls = [f"{BASE}/", f"{BASE}/tv/"] + [f"{BASE}/tv/{m['id']}/" for m in models] + \
        ([f"{BASE}/guides/"] if gs else []) + [f"{BASE}/guides/{g['slug']}/" for g in gs] + [f"{BASE}/privacy/"]
    write("sitemap.xml", '<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
          + "".join(f"  <url><loc>{u}</loc><lastmod>{TODAY}</lastmod></url>\n" for u in urls) + "</urlset>\n")
    # The main page's TV search links each result to its page.
    tvs = [[short_name(m), os_label(m), m["count"], m["id"]] for m in models]
    idx = SITE / "index.html"
    t = idx.read_text(encoding="utf-8")
    t = re.sub(r"const TVS = \[.*?\];", "const TVS = " + json.dumps(tvs, ensure_ascii=False, separators=(",", ":")) + ";", t, count=1, flags=re.S)
    brands = {brand(m) for m in models}
    t = re.sub(r"\d+ models from \d+ brands", f"{len(models)} models from {len(brands)} brands", t)
    t = re.sub(r'(<a href="/tv/">)[^<]*(</a>)', r"\g<1>TV models\g<2>", t, count=1)
    idx.write_text(t, encoding="utf-8")
    missing = [m["id"] for m in models if not m["copy"]]
    print(f"{len(models)} model pages ({len(models) - len(missing)} with written intros), {len(gs)} guides, {len(urls)} URLs in the sitemap")
    if missing:
        print("no intro yet:", " ".join(missing))


if __name__ == "__main__":
    main()
