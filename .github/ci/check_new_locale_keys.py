#!/usr/bin/env python3
"""Fork CI locale gate for fork-owned string files (strings_glass_ui.xml ...).

Upstream's scripts/check_android_locale.py validates values-<loc>/strings.xml
against the base and is run per locale by hand; upstream itself currently has
a few known gaps there, so CI does not gate on it. This gate covers the string
files the fork adds: every key in the English base file must exist in zh and
zh-rTW (required) and in every other locale that ships the file; format
specifiers must match; no bare apostrophes (aapt2 hard error).
"""
import os, re, sys
ROOT = os.path.normpath(os.path.join(os.path.dirname(__file__), "..", ".."))
RES = os.path.join(ROOT, "src/android/app/src/main/res")
FILES = ["strings_glass_ui.xml"]
REQUIRED = ["zh", "zh-rTW"]
SPEC = re.compile(r"%(?:\d+\$)?[-+#0 ]*[\d.]*[sdfx]")
STR = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.S)

def load(p):
    with open(p, encoding="utf-8") as f:
        return dict(STR.findall(f.read()))

errors = []
for fn in FILES:
    base_p = os.path.join(RES, "values", fn)
    if not os.path.exists(base_p):
        print(f"skip {fn}: not present"); continue
    base = load(base_p)
    locs = sorted(d[len("values-"):] for d in os.listdir(RES)
                  if d.startswith("values-") and os.path.exists(os.path.join(RES, d, fn)))
    for req in REQUIRED:
        if req not in locs:
            errors.append(f"{fn}: missing required locale values-{req}")
    for loc in ["", *locs]:
        tr = base if loc == "" else load(os.path.join(RES, f"values-{loc}", fn))
        for k, v in tr.items():
            if re.search(r"(?<!\\)'", v):
                errors.append(f"values-{loc or 'base'}/{fn}: bare apostrophe in {k}")
        if loc == "":
            continue
        miss = sorted(set(base) - set(tr)); extra = sorted(set(tr) - set(base))
        if miss: errors.append(f"values-{loc}/{fn}: missing {miss}")
        if extra: errors.append(f"values-{loc}/{fn}: extra {extra}")
        for k in set(base) & set(tr):
            if sorted(SPEC.findall(base[k])) != sorted(SPEC.findall(tr[k])):
                errors.append(f"values-{loc}/{fn}: format specifiers differ in {k}")
    print(f"{fn}: {len(base)} keys, locales {locs}")
if errors:
    print("FAIL"); [print(" -", e) for e in errors]; sys.exit(1)
print("OK")
