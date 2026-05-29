#!/usr/bin/env python3
import urllib.request, json, sys, os

version_id = sys.argv[1] if len(sys.argv) > 1 else "1.18.2"
output_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
output_file = os.path.join(output_dir, "build", f"mojang-client-{version_id}.txt")

os.makedirs(os.path.dirname(output_file), exist_ok=True)

manifest_url = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
req = urllib.request.Request(manifest_url, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req) as resp:
    manifest = json.loads(resp.read())

version_url = None
for v in manifest["versions"]:
    if v["id"] == version_id:
        version_url = v["url"]
        break

if not version_url:
    print(f"Version {version_id} not found!")
    sys.exit(1)

print(f"Found {version_id} at {version_url}")

req2 = urllib.request.Request(version_url, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req2) as resp2:
    version_data = json.loads(resp2.read())

client_url = version_data["downloads"]["client_mappings"]["url"]
client_size = version_data["downloads"]["client_mappings"]["size"]
print(f"Downloading client mappings from {client_url} ({client_size} bytes)")

req3 = urllib.request.Request(client_url, headers={"User-Agent": "Mozilla/5.0"})
with urllib.request.urlopen(req3) as resp3:
    data = resp3.read()

with open(output_file, "wb") as f:
    f.write(data)

print(f"Saved to {output_file} ({len(data)} bytes)")
