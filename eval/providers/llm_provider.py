"""
Promptfoo provider for LLM (Claude) entity extraction.

Calls the EVAL-2.2 reasoning-service /eval/extract/llm endpoint.
"""

import json
import os
import urllib.request
import urllib.error

_base = os.environ.get("REASONING_SERVICE_URL", "http://localhost:8000").rstrip("/")
LLM_URL = f"{_base}/eval/extract/llm"


def call_api(prompt, options, context):
    """Promptfoo provider entry point.

    Args:
        prompt: The article text to extract entities from.
        options: Provider config from promptfooconfig.yaml.
        context: Promptfoo context with vars.

    Returns:
        dict with "output" key containing the extraction response.
    """
    config = options.get("config", {}) if options else {}
    model = config.get("model", None)

    body = {
        "text": prompt,
        "confidence_threshold": 0.0,
    }
    if model:
        body["model"] = model

    payload = json.dumps(body).encode("utf-8")

    req = urllib.request.Request(
        LLM_URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return {"output": data}
    except urllib.error.URLError as e:
        return {"error": f"LLM endpoint unavailable: {e}"}
    except Exception as e:
        return {"error": f"LLM provider error: {e}"}
