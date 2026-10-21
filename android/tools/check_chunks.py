#!/usr/bin/env python3
"""打包前校验:EngineInjector 实际会 eval 的每一块,必须能单独通过语法检查。

血泪教训:曾经按字符数在换行处切块,每块都切在函数中间 -> 单块 eval 直接语法错误
被整块丢弃 -> 引擎一行都没加载。这里用 node --check 把这类问题挡在打包之前。
"""
import os, re, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "app/src/main/java/com/m8re/apk/EngineInjector.java")
ASSETS = os.path.join(ROOT, "app/src/main/assets")

src = open(JAVA, encoding="utf-8").read()
m = re.search(r"ENGINE_FILES\s*=\s*\{(.*?)\};", src, re.S)
engine = re.findall(r'"(m8re/[^"]+)"', m.group(1))
files = ["m8re/host_shim.js", "m8re/_mouse.js"] + engine

units = []
for f in files:
    path = os.path.join(ASSETS, f)
    if not os.path.exists(path):
        print("缺少文件:", f); sys.exit(1)
    units.append(open(path, encoding="utf-8").read())

# 与 Java 端一致:不切断文件,小文件合并到 ~60KB
chunks, cur = [], ""
for u in units:
    if cur and len(cur) + len(u) > 60000:
        chunks.append(cur); cur = ""
    cur += u
if cur:
    chunks.append(cur)

bad = 0
for i, c in enumerate(chunks):
    with tempfile.NamedTemporaryFile("w", suffix=".js", delete=False, encoding="utf-8") as fh:
        fh.write(c); path = fh.name
    r = subprocess.run(["node", "--check", path], capture_output=True, text=True)
    size = len(c)
    if r.returncode != 0:
        bad += 1
        print("❌ 第 %d 块 (%d 字符) 语法错误:\n%s" % (i, size, r.stderr.strip()[:400]))
    else:
        print("✓ 第 %d 块 %6d 字符" % (i, size))
    os.unlink(path)
sys.exit(1 if bad else 0)
