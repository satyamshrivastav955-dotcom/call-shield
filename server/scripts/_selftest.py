import sys, asyncio, json, numpy as np, av
from pathlib import Path
ROOT = Path("C:/Users/satya/OneDrive/Desktop/antAI/server")
sys.path.insert(0, str(ROOT / "src"))
import websockets
from aiortc import RTCPeerConnection, RTCSessionDescription, MediaStreamTrack, AudioStreamTrack
from aiortc.mediastreams import AudioFrame

WS = "ws://127.0.0.1:8765/ws/tap"

def load_audio(path, sr=48000, target_s=35.0):
    c = av.open(path)
    rs = av.AudioResampler(format="s16", layout="mono", rate=sr)
    chunks=[]
    for f in c.decode(audio=0):
        for o in rs.resample(f):
            a = o.to_ndarray()
            if a.ndim==2: a=a[0]
            chunks.append(np.asarray(a,dtype=np.float32))
    data = np.concatenate(chunks)
    n = int(target_s*sr)
    if len(data) < n:
        data = np.tile(data, (n//len(data))+1)[:n]
    return (data/32768.0).astype(np.float32)

class RealAudioTrack(AudioStreamTrack):
    kind="audio"
    def __init__(self, pcm, sr):
        super().__init__(); self.pcm=pcm; self.sr=sr; self.i=0
    async def recv(self):
        n = self.sr//50
        seg = self.pcm[self.i:self.i+n]
        if len(seg) < n:
            seg = np.zeros(n, dtype=np.float32)
        self.i += n
        f = AudioFrame(format="s16", layout="mono", samples=n)
        f.sample_rate = self.sr
        f.planes[0].update((np.clip(seg,-1,1)*32767).astype(np.int16).tobytes())
        return f

async def main():
    pcm = load_audio("audio_samples/Satyam_real_voice_1.wav")
    async with websockets.connect(WS + "?user=selftest2") as ws:
        pc = RTCPeerConnection()
        pc.addTrack(RealAudioTrack(pcm, 48000))
        offer = await pc.createOffer()
        await pc.setLocalDescription(offer)
        for _ in range(60):
            if pc.iceGatheringState=="complete": break
            await asyncio.sleep(0.05)
        await ws.send(json.dumps({"type":"tap.start","peer":"peerX","kind":"voice","offer":{"sdp":pc.localDescription.sdp,"type":"offer"}}))
        counts={}; texts=[]; vds=[]; gs=[]
        async def pump():
            while True:
                m = json.loads(await ws.recv())
                t = m["type"]; counts[t]=counts.get(t,0)+1
                if t=="tap.answer":
                    await pc.setRemoteDescription(RTCSessionDescription(sdp=m["sdp"], type="answer"))
                elif t=="transcript.update": texts.append(m.get("text",""))
                elif t=="signals.update":
                    v=m.get("voice_deepfake")
                    if v is not None: vds.append(round(v,3))
                elif t=="guidance.update": gs.append(m.get("guidance",""))
        task = asyncio.create_task(pump())
        await asyncio.sleep(35)
        task.cancel()
        print("COUNTS:", counts)
        print("transcripts:", texts[:6])
        print("voice_deepfake:", vds[:6])
        print("guidances:", gs[-2:])
        await pc.close()
asyncio.run(main())

