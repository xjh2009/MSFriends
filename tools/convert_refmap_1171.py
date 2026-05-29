"""
Convert Fabric intermediary refmap to Forge SRG for MC 1.17.1.
- Classes: intermediary -> Mojang (same as Yarn named for 1.17.1)
- Methods: intermediary -> SRG (via Yarn official -> TSRG2)
- Fields:  intermediary -> SRG (via Yarn official -> TSRG2)
- Relocate authlib yggdrasil classes to shaded package
"""
import json, os, sys, zipfile, shutil
from pathlib import Path

HOME = os.path.expanduser("~")
YARN_TINY = Path(HOME) / ".gradle/caches/fabric-loom/1.17.1/net.fabricmc.yarn.1_17_1.1.17.1+build.38/mappings.tiny"
TSRG2 = Path("tools/mcp-1171/extracted/config/joined.tsrg")
FAT_JAR = Path("build/versions-1.17.1-forge/libs/versions-1.17.1-forge-0.1.0+26.1.2-all.jar")
COMMON_JAR = Path("build/versions-1.17.1-common/libs/versions-1.17.1-common-0.1.0+26.1.2.jar")
AUTHLIB_CORE = {"YggdrasilAuthenticationService", "YggdrasilEnvironment", "YggdrasilMinecraftSessionService"}

def parse_all():
    """Parse all mapping files and build flat replacement maps."""
    # 1. Yarn tiny v1: official -> intermediary -> named
    #    key for method: (off_class, desc, off_method) -> (int_method, named_method)
    #    key for field:  (off_class, off_field) -> (int_field, named_field)
    yarn_off_m_to_int = {}   # (off_cls, desc, off_m) -> int_m
    yarn_off_m_to_named = {} # (off_cls, desc, off_m) -> named_m
    yarn_off_f_to_int = {}
    yarn_off_f_to_named = {}
    yarn_off_to_int_cls = {}
    yarn_off_to_named_cls = {}

    with open(YARN_TINY, "r", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if line.startswith("CLASS\t"):
                p = line.split("\t")
                if len(p) >= 4:
                    yarn_off_to_int_cls[p[1]] = p[2]
                    yarn_off_to_named_cls[p[1]] = p[3]
            elif line.startswith("METHOD\t"):
                p = line.split("\t")
                if len(p) >= 6:
                    k = (p[1], p[2], p[3])
                    yarn_off_m_to_int[k] = p[4]
                    yarn_off_m_to_named[k] = p[5]
            elif line.startswith("FIELD\t"):
                p = line.split("\t")
                if len(p) >= 6:
                    k = (p[1], p[3])
                    yarn_off_f_to_int[k] = p[4]
                    yarn_off_f_to_named[k] = p[5]

    print(f"Yarn: {len(yarn_off_to_int_cls)} cls, {len(yarn_off_m_to_int)} mth, {len(yarn_off_f_to_int)} fld")

    # 2. TSRG2: obf -> SRG
    tsrg_cls = {}  # obf_class -> srg_class
    tsrg_m = {}    # (obf_class, desc, obf_method) -> srg_method
    tsrg_f = {}    # (obf_class, obf_field) -> srg_field
    cur = None
    with open(TSRG2, "r", encoding="utf-8") as f:
        for line in f:
            if line.startswith("tsrg2"):
                continue
            s = line.rstrip("\n")
            if not s.startswith("\t") and s.strip():
                parts = s.split()
                if len(parts) >= 2:
                    cur = parts[0]
                    tsrg_cls[cur] = parts[1]
            elif s.startswith("\t") and cur:
                parts = s.lstrip("\t").split()
                if len(parts) >= 3 and parts[0] not in ("<init>", "<clinit>"):
                    if parts[1].startswith("("):
                        tsrg_m[(cur, parts[1], parts[0])] = parts[2]
                    else:
                        tsrg_f[(cur, parts[0])] = parts[1]

    print(f"TSRG2: {len(tsrg_cls)} cls, {len(tsrg_m)} mth, {len(tsrg_f)} fld")

    # 3. Build flat replacement maps
    # Method: intermediary -> SRG (via official name)
    m_repl = {}  # method_XXXXX -> m_XXXXX_
    m_multi = {}
    for (off_c, desc, off_m), int_m in yarn_off_m_to_int.items():
        srg = tsrg_m.get((off_c, desc, off_m))
        if srg and int_m != srg:
            m_multi.setdefault(int_m, set()).add(srg)
    for k, v in m_multi.items():
        if len(v) == 1:
            m_repl[k] = v.pop()

    # Field: intermediary -> SRG (via official name)
    f_repl = {}
    f_multi = {}
    for (off_c, off_f), int_f in yarn_off_f_to_int.items():
        srg = tsrg_f.get((off_c, off_f))
        if srg and int_f != srg:
            f_multi.setdefault(int_f, set()).add(srg)
    for k, v in f_multi.items():
        if len(v) == 1:
            f_repl[k] = v.pop()

    # Class: intermediary -> Mojang (named in Yarn tiny)
    # Forge 1.17.1 uses Mojang class names, not SRG class names
    c_repl = {}  # Lint_cls; -> Lnamed_cls;
    for off, int_cls in yarn_off_to_int_cls.items():
        named = yarn_off_to_named_cls.get(off)
        if named and int_cls != named:
            c_repl[f"L{int_cls};"] = f"L{named};"

    print(f"Replacements: {len(m_repl)} methods, {len(f_repl)} fields, {len(c_repl)} classes")
    return m_repl, f_repl, c_repl


def convert_refmap(refmap_path, m_repl, f_repl, c_repl):
    """Convert refmap JSON: intermediary -> SRG for methods/fields, intermediary -> Mojang for classes."""
    text = refmap_path.read_text(encoding="utf-8")

    # Sort by length desc for longest-match-first
    c_sorted = sorted(c_repl.items(), key=lambda x: len(x[0]), reverse=True)
    m_sorted = sorted(m_repl.items(), key=lambda x: len(x[0]), reverse=True)
    f_sorted = sorted(f_repl.items(), key=lambda x: len(x[0]), reverse=True)

    # Class replacements first (L...; format)
    for old, new in c_sorted:
        text = text.replace(old, new)

    # Method replacements: method_XXXXX( -> m_XXXXX_(
    for old, new in m_sorted:
        text = text.replace(old + "(", new + "(")

    # Field replacements: field_XXXXX: -> f_XXXXX_:
    for old, new in f_sorted:
        text = text.replace(old + ":", new + ":")

    refmap_path.write_text(text, encoding="utf-8")
    print(f"Converted: {refmap_path}")


def relocate_authlib(unpacked_dir):
    """Relocate com/mojang/authlib/yggdrasil to shaded package."""
    src = "com/mojang/authlib/yggdrasil"
    dst = "dev/msf/friends/shaded/com/mojang/authlib/yggdrasil"

    # Copy authlib classes from common jar
    if COMMON_JAR.exists():
        with zipfile.ZipFile(COMMON_JAR, "r") as z:
            for name in z.namelist():
                if name.startswith(src) and name.endswith(".class"):
                    data = z.read(name)
                    dest = unpacked_dir / dst / name[len(src) + 1:]
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    dest.write_bytes(data)

    # Remove non-core authlib from original
    orig = unpacked_dir / src
    if orig.exists():
        for f in list(orig.rglob("*.class")):
            simple = f.stem.split("$")[0]
            if simple not in AUTHLIB_CORE:
                f.unlink()
        for d in sorted(orig.rglob("*"), reverse=True):
            if d.is_dir() and not any(d.iterdir()):
                d.rmdir()


def patch_classfiles(unpacked_dir, c_repl):
    """Patch class constant pools: intermediary -> Mojang class refs.
    
    Only class names need patching in bytecode. Method/field names in 
    annotations (e.g. @Inject(method="shutdown")) are looked up via refmap
    at runtime, so we must NOT change them.
    """
    c_sorted = sorted(c_repl.items(), key=lambda x: len(x[0]), reverse=True)
    count = 0
    for cf in unpacked_dir.rglob("*.class"):
        data = cf.read_bytes()
        patched = bytearray(data)
        for old, new in c_sorted:
            old_b = old[1:-1].encode("utf-8")  # remove L and ;
            new_b = new[1:-1].encode("utf-8")
            if old_b in patched:
                patched = patched.replace(old_b, new_b)
        if patched != bytearray(data):
            cf.write_bytes(patched)
            count += 1
    print(f"Patched {count} class files")


def main():
    print("=== 1.17.1 Forge Refmap Converter ===")
    m_repl, f_repl, c_repl = parse_all()

    # Verify key mappings
    print(f"\nKey method mappings:")
    for key in sorted(m_repl, key=lambda k: int(k.split("_")[1]) if k.startswith("method_") else 0)[:15]:
        print(f"  {key} -> {m_repl[key]}")

    # Find the jar
    jar = FAT_JAR
    if not jar.exists():
        fixed = jar.parent / (jar.stem + "-fixed.jar")
        if fixed.exists():
            jar = fixed
        else:
            print(f"ERROR: {jar} not found"); sys.exit(1)

    # Extract
    tmp = jar.parent / "relocate_tmp"
    if tmp.exists():
        shutil.rmtree(tmp)
    tmp.mkdir()
    unpacked = tmp / "unpacked"
    with zipfile.ZipFile(jar) as z:
        z.extractall(unpacked)

    # Convert refmap
    for rf in unpacked.rglob("*refmap*"):
        convert_refmap(rf, m_repl, f_repl, c_repl)

    # Relocate authlib
    relocate_authlib(unpacked)

    # Patch class files
    patch_classfiles(unpacked, c_repl)

    # Repack
    out = jar.parent / (jar.stem + "-srg.jar")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as zout:
        for root, dirs, files in os.walk(unpacked):
            for fname in sorted(files):
                fp = Path(root) / fname
                zout.write(fp, fp.relative_to(unpacked))
    shutil.rmtree(tmp)

    # Replace original
    try:
        jar.unlink()
        out.rename(jar)
        print(f"\nDone: {jar} ({jar.stat().st_size} bytes)")
    except PermissionError:
        print(f"\nJar locked, output: {out} ({out.stat().st_size} bytes)")

    # Final verification
    final_jar = jar if jar.exists() else out
    with zipfile.ZipFile(final_jar) as z:
        for name in z.namelist():
            if "refmap" in name.lower():
                data = json.loads(z.read(name).decode())
                print("\n=== Converted refmap ===")
                print(json.dumps(data, indent=2)[:3000])


if __name__ == "__main__":
    main()
