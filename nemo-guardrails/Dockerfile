# NVIDIA NeMo Guardrails — Layer 1 Sidecar
FROM python:3.11-slim

WORKDIR /app

# Install build tools needed by annoy (C++ extension)
RUN apt-get update && \
    apt-get install -y --no-install-recommends g++ curl && \
    rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir nemoguardrails==0.10.0

COPY config/ /app/config/

EXPOSE 8001

HEALTHCHECK --interval=30s --timeout=10s --retries=3 --start-period=45s \
    CMD curl -f http://localhost:8001/v1/health || exit 1

CMD ["nemoguardrails", "server", "--config", "/app/config", "--port", "8001"]
