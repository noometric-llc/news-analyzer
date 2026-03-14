@echo off
REM ============================================================
REM  NewsAnalyzer Backend - Local Development Launcher
REM  Requires: Docker infrastructure running (docker-compose.dev.yml)
REM  Usage:    run-local.bat          (without observability)
REM            run-local.bat --otel   (with observability)
REM ============================================================

cd /d "%~dp0"

if "%1"=="--otel" (
    echo Starting backend WITH OpenTelemetry instrumentation...

    REM Check for OTel Java agent
    if not exist opentelemetry-javaagent.jar (
        echo.
        echo ERROR: opentelemetry-javaagent.jar not found.
        echo Download it first:
        echo   curl -L -o opentelemetry-javaagent.jar https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.12.0/opentelemetry-javaagent.jar
        echo.
        exit /b 1
    )

    set OTEL_SERVICE_NAME=newsanalyzer-backend
    set OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317
    set OTEL_EXPORTER_OTLP_PROTOCOL=grpc
    set OTEL_TRACES_SAMPLER=always_on
    set OTEL_METRICS_EXPORTER=otlp
    set OTEL_LOGS_EXPORTER=otlp
    set OTEL_RESOURCE_ATTRIBUTES=deployment.environment=dev

    call mvnw spring-boot:run -Dspring.profiles.active=dev -Dspring-boot.run.jvmArguments="-javaagent:./opentelemetry-javaagent.jar"
) else (
    echo Starting backend without observability...
    call mvnw spring-boot:run -Dspring.profiles.active=dev
)
