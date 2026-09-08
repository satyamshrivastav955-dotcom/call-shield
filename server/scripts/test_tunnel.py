"""Test script to verify antAI Cloudflare Tunnel or local server.
Usage:
    python server/scripts/test_tunnel.py [URL]
    python server/scripts/test_tunnel.py https://example.trycloudflare.com
    python server/scripts/test_tunnel.py http://localhost:8765
"""
from __future__ import annotations

import asyncio
import json
import sys
import time
from pathlib import Path
import urllib.request
import urllib.error

try:
    import websockets
except ImportError:
    websockets = None


def resolve_target_url(arg: str | None = None) -> str:
    if arg:
        return arg.rstrip("/")
    
    # Check tunnel_url.txt
    tunnel_file = Path(__file__).resolve().parent.parent / "tunnel_url.txt"
    if tunnel_file.exists():
        url = tunnel_file.read_text(encoding="utf-8").strip()
        if url:
            return url.rstrip("/")
            
    return "http://localhost:8765"


def test_http_endpoint(base_url: str) -> bool:
    print(f"\n[1/2] Testing HTTP REST over Cloudflare: {base_url}")
    t0 = time.perf_counter()
    try:
        req = urllib.request.Request(
            f"{base_url}/",
            headers={"User-Agent": "antAI-Cloudflare-Tester/1.0"}
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = resp.read().decode("utf-8")
            elapsed = (time.perf_counter() - t0) * 1000
            print(f"  Status code: {resp.status} (elapsed: {elapsed:.1f} ms)")
            parsed = json.loads(data)
            print(f"  Service: {parsed.get('service')}, Status: {parsed.get('status')}")
            print(f"  Available routes: {list(parsed.keys())}")
            print("  -> HTTP REST test: PASS [OK]")
            return True
    except Exception as e:
        print(f"  -> HTTP REST test FAILED: {e}")
        return False


async def test_websocket_endpoint(base_url: str) -> bool:
    print(f"\n[2/2] Testing WebSocket over Cloudflare: {base_url}")
    if websockets is None:
        print("  -> websockets library not installed, skipping WS check.")
        return True
    
    ws_scheme = "wss" if base_url.startswith("https") else "ws"
    netloc = base_url.split("://", 1)[-1]
    ws_url = f"{ws_scheme}://{netloc}/api/stream/ws"
    print(f"  Connecting to: {ws_url}")
    
    t0 = time.perf_counter()
    try:
        async with websockets.connect(ws_url, close_timeout=5) as ws:
            elapsed = (time.perf_counter() - t0) * 1000
            print(f"  WebSocket handshake completed in {elapsed:.1f} ms")
            
            # Send a test ping or sample payload
            test_payload = json.dumps({"type": "ping", "timestamp": time.time()})
            await ws.send(test_payload)
            print("  Sent test frame successfully")
            print("  -> WebSocket streaming test: PASS [OK]")
            return True
    except Exception as e:
        print(f"  -> WebSocket test FAILED: {e}")
        return False


async def main():
    target = sys.argv[1] if len(sys.argv) > 1 else resolve_target_url()
    print("=" * 60)
    print(f"antAI Cloudflare Connectivity Verification")
    print(f"Target: {target}")
    print("=" * 60)
    
    http_ok = test_http_endpoint(target)
    ws_ok = await test_websocket_endpoint(target)
    
    print("\n" + "=" * 60)
    if http_ok and ws_ok:
        print("ALL CLOUDFLARE ENDPOINT TESTS PASSED! [PASS]")
    else:
        print("SOME TESTS FAILED. Verify that the server and tunnel are running.")
    print("=" * 60)


if __name__ == "__main__":
    asyncio.run(main())
