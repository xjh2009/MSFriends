import json, os, subprocess, zipfile

mc_dir = os.path.expanduser(r"~\AppData\Roaming\.minecraft")
ver_json_path = os.path.join(mc_dir, "versions", "1.11.2-forge-13.20.1.2588", "1.11.2-forge-13.20.1.2588.json")

with open(ver_json_path, "r") as f:
    data = json.load(f)

print(f"MainClass: {data['mainClass']}")
print(f"ID: {data['id']}")
print(f"InheritsFrom: {data.get('inheritsFrom', 'N/A')}")
print(f"\nLibraries:")
for lib in data.get("libraries", []):
    name = lib["name"]
    parts = name.split(":")
    grp = parts[0].replace(".", "/")
    art = parts[1]
    ver = parts[2]
    jar_path = os.path.join(mc_dir, "libraries", grp, art, ver, f"{art}-{ver}.jar")
    exists = os.path.exists(jar_path)
    size = os.path.getsize(jar_path) if exists else 0
    print(f"  {name} -> exists={exists} size={size}")

# Check the mod jar
mod_jar = os.path.join(mc_dir, "mods", "msfriends-forge-1.11.2-0.1.0.jar")
print(f"\nMod jar: {mod_jar}")
if os.path.exists(mod_jar):
    print(f"Size: {os.path.getsize(mod_jar)}")
    z = zipfile.ZipFile(mod_jar)
    print(f"Entries:")
    for n in z.namelist():
        print(f"  {n}")
    z.close()

# Check the forge version jar
ver_jar = os.path.join(mc_dir, "versions", "1.11.2-forge-13.20.1.2588", "1.11.2-forge-13.20.1.2588.jar")
print(f"\nVersion jar: {ver_jar}")
print(f"Exists: {os.path.exists(ver_jar)}")
if os.path.exists(ver_jar):
    print(f"Size: {os.path.getsize(ver_jar)}")
