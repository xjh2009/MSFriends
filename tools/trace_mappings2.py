"""
Find the SRG name for MinecraftServer.shutdown() in 1.17.1

Yarn tiny v2 format:
  c\tintermediary\tyarn\tobf
   m\tdesc\tintermediary\tyarn

TSRG2 format:
  obf_class srg_class id
   obf_method obf_desc srg_name id
"""
import sys

yarn_tiny = "C:/Users/xjh37/.gradle/caches/fabric-loom/1.17.1/net.fabricmc.yarn.1_17_1.1.17.1+build.38/mappings.tiny"
tsrg_path = "C:/Users/xjh37/Desktop/MSF/msf-friends-multi/tools/mcp-1171/extracted/config/joined.tsrg"

# Step 1: Get Yarn intermediary -> obfuscated for MinecraftServer methods
yarn_int_to_obf = {}  # intermediary_name -> obfuscated_name
yarn_named_to_int = {}  # named -> intermediary
with open(yarn_tiny, "r") as f:
    in_mcs = False
    for line in f:
        if line.startswith("c\t"):
            parts = line.strip().split("\t")
            cls_name = parts[1]
            if cls_name == "net/minecraft/server/MinecraftServer":
                in_mcs = True
            else:
                in_mcs = False
        elif in_mcs and line.startswith("\tm\t"):
            parts = line.strip().split("\t")
            if len(parts) >= 4:
                desc = parts[1]
                int_name = parts[2]
                named_name = parts[3]
                yarn_named_to_int[named_name] = int_name
                yarn_int_to_obf[int_name] = None  # we'll get obf from TSRG

# Step 2: Get SRG mapping - obf -> SRG
srg_obf_to_name = {}  # obf_method -> srg_name
with open(tsrg_path, "r") as f:
    in_mcs = False
    for line in f:
        if line.startswith("tsrg2"):
            continue
        stripped = line.rstrip()
        if not stripped.startswith("\t") and not stripped.startswith(" "):
            parts = stripped.split()
            if len(parts) >= 2 and parts[1] == "net/minecraft/server/MinecraftServer":
                in_mcs = True
            elif len(parts) >= 2 and parts[0] != "net/minecraft/server/MinecraftServer":
                in_mcs = False
        elif in_mcs:
            content = stripped.lstrip("\t ")
            parts = content.split()
            if len(parts) >= 3 and not parts[0].startswith("0") and not parts[0].startswith("1"):
                obf = parts[0]
                desc = parts[1]
                srg = parts[2]
                srg_obf_to_name[(obf, desc)] = srg

# Step 3: Trace the chain for each Yarn method in MinecraftServer
# Yarn tiny: intermediary and obf might be the same (Mojang mapped or intermediary)
# Actually for Fabric intermediary, obf != intermediary
# Let me check: in the tiny file, columns are: intermediary, named, obf
# For MC 1.17.1, the obf column might be empty (same as intermediary) or Mojang-mapped

# Let me re-read the Yarn tiny more carefully for MinecraftServer
print("=== All MinecraftServer()V methods from Yarn ===")
mcs_methods = {}
with open(yarn_tiny, "r") as f:
    in_mcs = False
    for line in f:
        if line.startswith("c\t"):
            parts = line.strip().split("\t")
            if len(parts) >= 2 and parts[1] == "net/minecraft/server/MinecraftServer":
                in_mcs = True
                print(f"Found class: {'  '.join(parts)}")
            else:
                in_mcs = False
        elif in_mcs and line.startswith("\tm\t"):
            parts = line.strip().split("\t")
            if len(parts) >= 4:
                desc = parts[1]
                int_name = parts[2]
                named = parts[3]
                if "()V" in desc:
                    print(f"  {desc}  int={int_name}  named={named}")

# Now let me find what method_3782 maps to in SRG
print("\n=== Searching for method_3782 in TSRG ===")
# method_3782 (Fabric intermediary) -> need to find obf name -> then SRG
# Actually, in TSRG2 for Forge 1.17.1:
# The SRG names use format m_XXXXX_ which corresponds to the Mojang obfuscation
# But Fabric intermediary uses method_XXXXX
# The numbers might not match between intermediary and SRG!

# Let me just dump ALL MinecraftServer()V methods from TSRG
print("\n=== All MinecraftServer()V methods from TSRG ===")
with open(tsrg_path, "r") as f:
    in_mcs = False
    for line in f:
        if line.startswith("tsrg2"):
            continue
        stripped = line.rstrip()
        if not stripped.startswith("\t") and not stripped.startswith(" "):
            parts = stripped.split()
            if len(parts) >= 2 and parts[1] == "net/minecraft/server/MinecraftServer":
                in_mcs = True
            elif len(parts) >= 2 and in_mcs:
                in_mcs = False
        elif in_mcs:
            content = stripped.lstrip("\t ")
            parts = content.split()
            if len(parts) >= 3:
                obf = parts[0]
                desc = parts[1]
                srg = parts[2]
                if desc == "()V" and not srg.startswith("f_") and "<" not in srg:
                    print(f"  obf={obf}  desc={desc}  srg={srg}")
