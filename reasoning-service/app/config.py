"""
Centralized configuration for the reasoning service.

Uses pydantic-settings to load values from environment variables.
All EVAL-track config is prefixed with EVAL_ for clarity.
"""

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Application settings loaded from environment variables."""

    # Backend API connection
    backend_url: str = "http://localhost:8080"

    # Claude API (used in EVAL-1.2, but define the setting now)
    anthropic_api_key: str = ""

    # EVAL pipeline configuration
    eval_default_model: str = "claude-sonnet-4-20250514"
    eval_rate_limit_rpm: int = 50  # Max Claude API requests per minute
    eval_max_batch_size: int = 50  # Max entities per batch run
    eval_dry_run: bool = False  # Skip API calls, log prompts only

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "extra": "ignore",  # Ignore unrecognized env vars
    }


# Module-level singleton — avoids re-parsing env vars on every import
settings = Settings()
