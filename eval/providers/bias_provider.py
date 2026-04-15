"""
Promptfoo provider for ontology-grounded bias detection (EVAL-3.4).

Calls the EVAL-3.3 reasoning-service /eval/bias/detect endpoint.
"""

import json
import os
import urllib.request
import urllib.error

_base = os.environ.get("REASONING_SERVICE_URL", "http://localhost:8000").rstrip("/")
BIAS_URL = f"{_base}/eval/bias/detect"


def call_api(prompt, options, context):
    """Promptfoo provider entry point.

    Args:
        prompt: The article text to analyze for biases.
        options: Provider config from promptfoo-bias.yaml.
        context: Promptfoo context with vars.

    Returns:
        dict with "output" key containing the detection response.
    """
    body = {
        "text": prompt,
        "confidence_threshold": 0.0,
        "include_ontology_metadata": False,
        "grounded": True,
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
        return {"error": f"Bias provider error: {e}"}
