#!/usr/bin/env bash
# Short audit before the full run (paper Sec. IV-E, after SandboxEval): env vars, writable paths, outbound access.
docker run --rm --network none --user 10001:10001 --tmpfs /tmp --cap-drop ALL \
  --security-opt no-new-privileges --entrypoint bash exp-sandbox:1 -c '
echo "== env (look for keys/tokens) =="; env | sort
echo "== writable paths (outside /tmp, /work) =="; find / -xdev -writable -not -path "/proc/*" -not -path "/tmp/*" -not -path "/work/*" -not -path "/dev/*" 2>/dev/null | head -30
echo "== outbound connection attempt (must fail) =="; (exec 3<>/dev/tcp/1.1.1.1/53) 2>&1 && echo "REACHABLE (BAD)" || echo "blocked (good)"
echo "== uid =="; id'
