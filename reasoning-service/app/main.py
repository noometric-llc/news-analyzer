"""
NewsAnalyzer Reasoning Service

FastAPI service for entity extraction, logical reasoning, and Prolog inference.
Replaces V1's slow Java subprocess integration (500ms) with fast HTTP API (50ms).
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.api import entities, reasoning, fallacies, government_orgs
from app.api.eval import facts as eval_facts
from app.api.eval import articles as eval_articles
from app.api.eval import batches as eval_batches
from app.api.eval import extraction as eval_extraction
from app.telemetry import init_telemetry, instrument_app

# Initialize OTel BEFORE app creation — providers must be set
# before FastAPI and HTTPX are instrumented (OBS-1.3)
init_telemetry()

app = FastAPI(
    title="NewsAnalyzer Reasoning Service",
    description="Entity extraction, logical reasoning, and Prolog inference for news analysis",
    version="2.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # Configure in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Instrument FastAPI AFTER app creation and middleware setup (OBS-1.3)
instrument_app(app)


@app.get("/")
async def root():
    """Health check endpoint"""
    return {
        "service": "NewsAnalyzer Reasoning Service",
        "version": "2.0.0",
        "status": "healthy",
    }


@app.get("/health")
async def health():
    """Detailed health check"""
    return JSONResponse(
        content={
            "status": "healthy",
            "services": {
                "spacy": "loaded",
                "prolog": "available",
            }
        }
    )


# Include routers
app.include_router(entities.router, prefix="/entities", tags=["entities"])
app.include_router(reasoning.router, prefix="/reasoning", tags=["reasoning"])
app.include_router(fallacies.router, prefix="/fallacies", tags=["fallacies"])
app.include_router(government_orgs.router, prefix="/government-orgs", tags=["government-organizations"])
app.include_router(eval_facts.router, prefix="/eval/facts", tags=["eval-facts"])
app.include_router(eval_articles.router, prefix="/eval/articles", tags=["eval-articles"])
app.include_router(eval_batches.router, prefix="/eval/batches", tags=["eval-batches"])
app.include_router(eval_extraction.router, prefix="/eval/extract", tags=["eval-extraction"])


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="0.0.0.0", port=8000, reload=True)
