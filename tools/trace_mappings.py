"""
Trace the mapping chain for MinecraftServer methods in 1.17.1:
  Yarn (named) -> intermediary -> obf -> SRG
"""
import os, sys

# Paths
yarn_tiny = "C:/Users/xjh37/.gradle/caches/fabric-loom/1.17.1/net.fabricmc.yarn.1_17_1.1.17.1+build.38/mappings.tiny"
tsrg_path = "C:/Users/xjh37/Desktop/MSF/msf-friends-multi/tools/mcp-1171/extracted/config/joined.tsrg"

# 1. Parse Yarn tiny: get intermediary -> named for MinecraftServer methods
# Format: c\tint_name\tnamed_name\tdesc
#          m\tint_desc\tint_name\tnamed_name
yarn_methods = {}  # intermediary_name -> named_name
with open(yarn_tiny, "r") as f:
    in_mcs = False
    for line in f:
        if line.startswith("c\t"):
            parts = line.strip().split("\t")
            if len(parts) >= 3 and "MinecraftServer" in parts[1] and "$" not in parts[1]:
                in_mcs = True
            else:
                in_mcs = False
        elif in_mcs and line.startswith("\tm\t"):
            parts = line.strip().split("\t")
            # m\tint_desc\tint_name\tnamed_name
            if len(parts) >= 4:
                int_desc = parts[1]
                int_name = parts[2]
                named_name = parts[3]
                if int_desc == "()V":  # We're looking for no-arg void methods
                    yarn_methods[int_name] = named_name

print("Yarn intermediary -> named for MinecraftServer()V methods:")
for int_name, named_name in sorted(yarn_methods.items()):
    print(f"  {int_name} -> {named_name}")

# 2. Parse TSRG2: get obf -> SRG for MinecraftServer
# Format: obf_class srg_class id
#         obf_method obf_desc srg_name srg_id
tsrg_methods = {}  # obf_name -> (srg_name, srg_desc)
with open(tsrg_path, "r") as f:
    in_mcs = False
    for line in f:
        if line.startswith("tsrg2"):
            continue
        if not line.startswith("\t") and not line.startswith(" "):
            parts = line.strip().split()
            if len(parts) >= 2 and parts[1] == "net/minecraft/server/MinecraftServer":
                in_mcs = True
            else:
                in_mcs = False
        elif in_mcs and line.startswith("\t"):
            parts = line.strip().split("\t")
            # obf_name obf_desc srg_name srg_id
            if len(parts) >= 3:
                sub = parts[0].strip().split()
                if len(sub) >= 2:
                    obf_name = sub[0]
                    obf_desc = sub[1]
                    srg_name = sub[2] if len(sub) > 2 else parts[0].strip()
                    if obf_desc == "()V":
                        tsrg_methods[obf_name] = srg_name

print("\nTSRG obf -> SRG for MinecraftServer()V methods:")
for obf_name, srg_name in sorted(tsrg_methods.items()):
    print(f"  {obf_name} -> {srg_name}")

# 3. Find the target: Yarn 'shutdown' -> intermediary -> obf -> SRG
print("\n=== LOOKING FOR 'shutdown' ===")
# First find intermediary name for 'shutdown'
target_int = None
for int_name, named_name in yarn_methods.items():
    if named_name == "shutdown":
        target_int = int_name
        print(f"Yarn 'shutdown' -> intermediary '{int_name}'")
        break

if target_int:
    # intermediary names look like method_XXXXX
    # Find in TSRG2: the obf name for this intermediary
    # In TSRG2, SRG names ARE the intermediary names (f_XXXXX_ for fields, m_XXXXX_ for methods)
    # So method_3782 corresponds to m_3782_ in SRG
    srg_target = f"m_{target_int.replace('method_', '')}_"
    print(f"Expected SRG name: {srg_target}")
    
    if srg_target in tsrg_methods.values():
        print(f"FOUND in TSRG: {srg_target}")
    else:
        print(f"NOT FOUND directly in TSRG, searching...")
        for obf, srg in tsrg_methods.items():
            if srg_target in srg:
                print(f"  Partial match: {obf} -> {srg}")

# 4. Also check 'close' method
print("\n=== CHECKING 'close' ===")
# close is a standard Java method, it might keep its name in SRG
for obf, srg in tsrg_methods.items():
    if srg == "close":
        print(f"  SRG 'close': obf '{obf}' -> SRG '{srg}'")
