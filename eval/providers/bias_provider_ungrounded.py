"""
Promptfoo provider for UNGROUNDED bias detection (EVAL-3.5 A/B comparison).

Same endpoint as bias_provider.py but with grounded=False — the LLM prompt
does NOT include ontology definitions. This produces the "naive" baseline
for comparing against the ontology-grounded detector.
"""

import json
import os
import urllib.request
import urllib.error

_base = os.environ.get("REASONING_SERVICE_URL", "http://localhost:8000").rstrip("/")
BIAS_URL = f"{_base}/eval/bias/detect"


def call_api(prompt, options, context):
    """Promptfoo provider entry point — ungrounded mode."""
    body = {
        "text": prompt,
        "confidence_threshold": 0.0,
        "include_ontology_metadata": False,
        "grounded": False,
    }

    payload = json.dumps(body).encode("utf-8")

    req = urllib.request.Request(
        BIAS_URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return {"output": data}
    except urllib.error.URLError as e:
        return {"error": f"Bias detection endpoint unavailable: {e}"}
    except Exception as e:
        return {"error": f"Ungrounded bias provider error: {e}"}
