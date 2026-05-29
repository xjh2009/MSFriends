"""Remove 1.14.4:common and 1.14.4:fabric from settings.gradle.kts"""
path = r"C:\Users\xjh37\Desktop\MSF\msf-friends-multi\settings.gradle.kts"
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    stripped = line.strip()
    if stripped == 'include(":versions:1.14.4:common")':
        continue
    if stripped == 'include(":versions:1.14.4:fabric")':
        continue
    new_lines.append(line)

with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.writelines(new_lines)
print(f"Written {len(new_lines)} lines (removed {len(lines) - len(new_lines)})")
