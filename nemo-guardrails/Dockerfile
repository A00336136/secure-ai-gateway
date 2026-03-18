# NVIDIA NeMo Guardrails v0.10.0 — Layer 1 Sidecar
FROM python:3.11-slim

WORKDIR /app

RUN pip install --no-cache-dir nemoguardrails==0.10.0

COPY config/ /app/config/

EXPOSE 8001

CMD ["nemoguardrails", "server", "--config", "/app/config", "--port", "8001"]
