import sys

tsrg_path = sys.argv[1] if len(sys.argv) > 1 else "C:/Users/xjh37/Desktop/MSF/msf-friends-multi/tools/mcp-1171/extracted/config/joined.tsrg"
target_class = sys.argv[2] if len(sys.argv) > 2 else "MinecraftServer"
target_methods = sys.argv[3:] if len(sys.argv) > 3 else ["shutdown", "stopServer", "stop", "close", "tick", "enforceSecureProfile", "shouldEnforceSecureProfile"]

found_class = False
with open(tsrg_path, "r") as f:
    for line in f:
        if line.startswith("tsrg2"):
            continue
        if not line.startswith("\t"):
            parts = line.strip().split()
            if len(parts) >= 2 and target_class in parts[1]:
                found_class = True
                print(f"Class: {parts[0]} -> {parts[1]}")
            else:
                found_class = False
        elif found_class:
            parts = line.strip().split()
            if len(parts) >= 3:
                obf_name = parts[0]
                obf_desc = parts[1]
                srg_name = parts[2]
                srg_desc = parts[3] if len(parts) > 3 else ""
                for t in target_methods:
                    if t.lower() in srg_name.lower():
                        print(f"  {obf_name} {obf_desc} -> {srg_name} {srg_desc}")
                        break
