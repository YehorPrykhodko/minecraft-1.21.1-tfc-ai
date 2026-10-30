#!/usr/bin/env bash
# À sourcer avant Gradle dans un environnement cloud avec proxy.
wildlife_project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
wildlife_tool_root="${WILDLIFE_TOOL_ROOT:-/workspace/tools}"
if [[ -x "$wildlife_tool_root/jdk-21.0.8+9/bin/javac" ]]; then
    export JAVA_HOME="$wildlife_tool_root/jdk-21.0.8+9"
    export PATH="$JAVA_HOME/bin:$PATH"
fi
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.gradle-cache}"
if [[ -f "$wildlife_tool_root/cloud-cacerts" ]]; then
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Djavax.net.ssl.trustStore=$wildlife_tool_root/cloud-cacerts"
fi
if [[ -n "${HTTPS_PROXY:-}" ]]; then
    wildlife_proxy_options="$(python3 - <<'PY'
import os
from urllib.parse import urlsplit
p = urlsplit(os.environ['HTTPS_PROXY'])
if p.username or p.password:
    raise SystemExit('Authenticated proxy requires platform Java configuration; credentials are never saved.')
if p.hostname and p.port:
    print(f'-Dhttps.proxyHost={p.hostname} -Dhttps.proxyPort={p.port} -Dhttp.proxyHost={p.hostname} -Dhttp.proxyPort={p.port}')
PY
)" || return
    export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} $wildlife_proxy_options"
fi
unset wildlife_proxy_options wildlife_tool_root wildlife_project_root
