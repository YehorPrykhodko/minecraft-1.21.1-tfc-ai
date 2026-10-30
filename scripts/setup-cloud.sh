#!/usr/bin/env bash
set -euo pipefail
wildlife_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
wildlife_tools="${WILDLIFE_TOOL_ROOT:-/workspace/tools}"
mkdir -p "$wildlife_tools"
if [[ ! -x "$wildlife_tools/jdk-21.0.8+9/bin/javac" ]]; then
    curl --fail --silent --show-error --location --retry 2 \
        'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz' \
        -o "$wildlife_tools/jdk21.tar.gz"
    curl --fail --silent --show-error --location --retry 2 \
        'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz.sha256.txt' \
        -o "$wildlife_tools/jdk21.sha256"
    python3 - "$wildlife_tools" <<'PY'
import hashlib, sys
from pathlib import Path
p=Path(sys.argv[1])
actual=hashlib.sha256((p/'jdk21.tar.gz').read_bytes()).hexdigest()
expected=(p/'jdk21.sha256').read_text().split()[0]
if actual != expected:
    raise SystemExit('JDK checksum mismatch')
PY
    tar -xzf "$wildlife_tools/jdk21.tar.gz" -C "$wildlife_tools"
fi
if [[ -f "${CODEX_PROXY_CERT:-}" ]]; then
    cp "$wildlife_tools/jdk-21.0.8+9/lib/security/cacerts" "$wildlife_tools/cloud-cacerts"
    "$wildlife_tools/jdk-21.0.8+9/bin/keytool" -importcert -noprompt -alias codex-proxy \
        -file "$CODEX_PROXY_CERT" -keystore "$wildlife_tools/cloud-cacerts" -storepass changeit
fi
source "$wildlife_root/scripts/cloud-env.sh"
cd "$wildlife_root"
./gradlew -PcoreOnly build --console=plain
# La compilation complète doit réussir avant de livrer le mod.
./gradlew build :mod:runGameTestServer --console=plain
