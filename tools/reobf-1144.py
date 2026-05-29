#!/usr/bin/env python3
"""Reobfuscate mod jar from MCP to SRG names using SpecialSource."""
import subprocess, sys, os

JAVA = r'C:\Program Files\Zulu\zulu-17\bin\java.exe'
SS_JAR = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\maven\mcp-tools\net\md-5\SpecialSource\1.8.3\SpecialSource-1.8.3-shaded.jar'
TSRG = r'C:\Users\xjh37\.gradle\caches\minecraftforge\forgegradle\mavenizer\caches\mcp\de\oceanlabs\mcp\mcp_config\1.14.4-20190829.143755\joined\data\mappings\joined.tsrg'

def reobf(input_jar, output_jar):
    cmd = [
        JAVA, '-cp', SS_JAR,
        'net.md_5.specialsource.SpecialSource', 'reobf',
        '-i', input_jar,
        '-o', output_jar,
        '-m', TSRG,
        '-r'  # reverse: MCP→SRG
    ]
    print('Reobfuscating %s -> %s' % (os.path.basename(input_jar), os.path.basename(output_jar)))
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.stdout:
        print(result.stdout)
    if result.stderr:
        print(result.stderr, file=sys.stderr)
    if result.returncode != 0:
        print('SpecialSource failed with exit code %d' % result.returncode)
        sys.exit(1)
    
    in_size = os.path.getsize(input_jar) / 1024
    out_size = os.path.getsize(output_jar) / 1024
    print('Input: %.1f KB, Output: %.1f KB' % (in_size, out_size))

if __name__ == '__main__':
    if len(sys.argv) < 3:
        print('Usage: %s <input.jar> <output.jar>' % sys.argv[0])
        sys.exit(1)
    reobf(sys.argv[1], sys.argv[2])
