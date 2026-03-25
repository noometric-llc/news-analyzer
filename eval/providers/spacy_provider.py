"""
Promptfoo provider for spaCy entity extraction.

Calls the existing reasoning-service /entities/extract endpoint.
"""

import json
import urllib.request
import urllib.error

SPACY_URL = "http://localhost:8000/entities/extract"


def call_api(prompt, options, context):
    """Promptfoo provider entry point.

    Args:
        prompt: The article text to extract entities from.
        options: Provider config from promptfooconfig.yaml.
        context: Promptfoo context with vars.

    Returns:
        dict with "output" key containing the extraction response.
    """
    payload = json.dumps({
        "text": prompt,
        "confidence_threshold": 0.0,
    }).encode("utf-8")

    req = urllib.request.Request(
        SPACY_URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return {"output": data}
    except urllib.error.URLError as e:
        return {"error": f"spaCy endpoint unavailable: {e}"}
    except Exception as e:
        return {"error": f"spaCy provider error: {e}"}
