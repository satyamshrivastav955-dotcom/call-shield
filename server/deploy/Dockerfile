# antAI server — AWS backup deployment image.
# Run via docker-compose.yml in this folder (deploy.sh does everything).
#
# Deliberately CPU-only: torch/llama-cpp install from the CPU wheel index.
# GPU-in-container needs the nvidia container runtime + CUDA wheels — extra
# failure surface for a backup rig. Every engine has a CPU fallback path
# (see hub warmup), so the demo works; it is just slower per segment.
# Swap to a CUDA base + /cu121 wheel index only if measured demo latency
# demands it.

FROM python:3.11-slim

# ffmpeg: aiortc/av media decoding. git: pip sdists (none expected, but cheap).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Deps first (cached layer): the prebuilt llama-cpp-python wheel index is
# required on any platform without a working C build chain — same trick the
# Windows dev setup documents in requirements.txt.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    --extra-index-url https://abetlen.github.io/llama-cpp-python/whl/cpu

# App code + config. ANTAI_CONFIG lets the compose file point at the AWS
# variant (config.aws.yaml) without rebuilding.
COPY src/ ./src/
COPY config.yaml config.aws.yaml ./
COPY setup/ ./setup/
COPY run_dev.py .
ENV PYTHONUNBUFFERED=1 ANTAI_CONFIG=/app/config.yaml
EXPOSE 8765

# models/ and .env are NOT in the image: compose volume-mounts them, so
# weight updates and key rotation never need a rebuild.
CMD ["python", "run_dev.py"]
