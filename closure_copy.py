#!/usr/bin/env python3
# 闭包复制：从 cap 出发，迭代解析 com.mibot.* import，从 Mika 源复制依赖壳类
# 冲突类（Mika 界面/入口，与 Mibot 冲突）不复制，记入冲突清单
import os, re, sys, shutil

MIKA = "/var/minis/workspace/Mika/app/src/main/java/com/mibot"
MIB  = "/var/minis/workspace/mib/src/android/app/src/main/java/com/mibot"

# 冲突类：Mika 的界面壳/入口/主题，与 Mibot 冲突，不复制（cap 对这些引用用 stub 或移除）
CONFLICT = {
    "com.mibot.MikaApp",
    "com.mibot.ui.MainActivity",
    "com.mibot.ui.PhoneSetupCopy",
    "com.mibot.ui.theme.MikaTheme",
    "com.mibot.ui.components.ValueTone",
    "com.mibot.teach.TeachRuntime",
    "com.mibot.record",
    "com.mibot.replay",
    "com.mibot.voice",
    "com.mibot.about",
    "com.mibot.mcp",
    "com.mibot.memory",
    "com.mibot.knowledge",
    "com.mibot.skills",
    "com.mibot.selfcheck",
    "com.mibot.merge",
    "com.mibot.confirm",
    "com.mibot.wechat",
    "com.mibot.device",
    "com.mibot.browser",
    "com.mibot.quit",
}

def class_to_path(class_name):
    """com.mibot.x.y.Class -> 相对路径 x/y/Class.kt（Mika 源里找）"""
    parts = class_name.split(".")
    # 包 = 前 n-1，类 = 最后
    pkg = "/".join(parts[:-1])
    cls = parts[-1]
    return os.path.join(pkg, cls + ".kt")

def source_exists(class_name):
    rel = class_to_path(class_name)
    return os.path.exists(os.path.join(MIKA, rel))

def copy_class(class_name):
    """复制一个类的 .kt 到 MIB（同包路径）。返回复制的文件路径或 None"""
    rel = class_to_path(class_name)
    src = os.path.join(MIKA, rel)
    if not os.path.exists(src):
        return None
    dst = os.path.join(MIB, rel)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    if os.path.exists(dst):
        # 已复制，跳过（但可能源更新了，覆盖）
        pass
    shutil.copy2(src, dst)
    return dst

def scan_imports(filepath):
    """扫描一个 .kt 文件的 import com.mibot.* 列表"""
    try:
        with open(filepath, encoding="utf-8") as f:
            content = f.read()
    except Exception:
        return []
    return re.findall(r'^import\s+(com\.mibot\.[A-Za-z0-9_.]+)', content, re.M)

def collect_all_files(root):
    for dirpath, _, files in os.walk(root):
        for fn in files:
            if fn.endswith(".kt"):
                yield os.path.join(dirpath, fn)

# 已复制集 = MIB 下所有 .kt 的类名
def existing_classes():
    classes = set()
    for fp in collect_all_files(MIB):
        rel = os.path.relpath(fp, MIB).replace(os.sep, "/")
        if rel.endswith(".kt"):
            cls = rel[:-3].replace("/", ".")
            classes.add(cls)
    return classes

copied = []
conflict_hits = []
queue = []

# 初始：扫描 cap 全部文件
cap_root = os.path.join(MIB, "cap")
for fp in collect_all_files(cap_root):
    for imp in scan_imports(fp):
        queue.append(imp)

seen = set()
while queue:
    imp = queue.pop(0)
    if imp in seen:
        continue
    seen.add(imp)
    # 跳过 com.mibot.core / com.mibot.cap（已有）
    if imp.startswith("com.mibot.core") or imp.startswith("com.mibot.cap"):
        continue
    # 冲突？
    if any(imp.startswith(c) for c in CONFLICT):
        conflict_hits.append(imp)
        continue
    # 已存在？
    if imp in existing_classes():
        continue
    # 从源复制
    if source_exists(imp):
        dst = copy_class(imp)
        if dst:
            copied.append(imp)
            # 新复制文件的 import 入队
            for sub in scan_imports(dst):
                queue.append(sub)
    else:
        # 源里也没有——记录缺失
        conflict_hits.append("MISSING:" + imp)

print(f"=== 闭包复制完成 ===")
print(f"新复制: {len(copied)} 文件")
for c in sorted(copied):
    print(f"  + {c}")
print(f"")
print(f"冲突/缺失（需 stub 或已有）: {len(set(conflict_hits))}")
for c in sorted(set(conflict_hits)):
    print(f"  ! {c}")
