// antAI PCM worklet — capture-rate stereo → 16 kHz mono f32le chunks.
// ponytail: naive linear resampler; good enough for the acoustic/prosody
// features — swap in a proper polyphase filter if quality ever matters.

const TARGET_RATE = 16000;
const CHUNK_SAMPLES = 4000; // 250 ms @ 16 kHz

class AntaiPcmProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this._buf = new Float32Array(CHUNK_SAMPLES);
    this._n = 0;
    this._phase = 0;
    this._last = 0;
  }

  process(inputs) {
    const input = inputs[0];
    if (!input || input.length === 0 || !input[0]) return true;

    const nCh = input.length;
    const srcLen = input[0].length;
    const ratio = TARGET_RATE / sampleRate;

    for (let i = 0; i < srcLen; i++) {
      // mono mix
      let s = 0;
      for (let c = 0; c < nCh; c++) s += input[c][i];
      s /= nCh;

      this._phase += ratio;
      while (this._phase >= 1) {
        this._phase -= 1;
        this._buf[this._n++] = this._last + (s - this._last) * this._phase;
        if (this._n === CHUNK_SAMPLES) {
          this.port.postMessage(this._buf.buffer, [this._buf.buffer]);
          this._buf = new Float32Array(CHUNK_SAMPLES);
          this._n = 0;
        }
      }
      this._last = s;
    }
    return true;
  }
}

registerProcessor("antai-pcm", AntaiPcmProcessor);
