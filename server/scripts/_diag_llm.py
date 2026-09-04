import sys, time
sys.path.insert(0, "src")
from antai.inference.hub import get_hub
llm = get_hub().get("llm")
print("llm ready:", llm.ready(), "device:", getattr(llm, "device", "?"))
msgs = [{"role":"system","content":"You are a scam-detection safety assistant. Reply in one short plain sentence."},
        {"role":"user","content":"Risk: 60/100. The caller asks for an OTP. What should the user do?"}]
t=time.time()
out = llm.complete(msgs, max_tokens=30)
print(f"LLM output ({time.time()-t:.1f}s): {out!r}")
