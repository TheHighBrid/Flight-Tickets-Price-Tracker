# Live flight backend

This FastAPI service is the production-safe data path for the Android app. It keeps Amadeus credentials on the server and proxies the official Flight Offers Search endpoint. It never manufactures fares or airline names.

## Run locally

```bash
cd server
python -m venv .venv
. .venv/bin/activate
pip install -r requirements-dev.txt
cp .env.example .env
# Export the values from .env in your shell.
uvicorn app.main:app --reload --port 8080
```

Required secrets:

- `AMADEUS_CLIENT_ID`
- `AMADEUS_CLIENT_SECRET`
- `AMADEUS_ENVIRONMENT`, either `test` or `production`
- `FLIGHT_API_ACCESS_TOKEN`, recommended to protect the public endpoint

The Android app sends `FLIGHT_API_ACCESS_TOKEN` as `X-App-Token` when configured.

## Production truthfulness

The Amadeus test environment contains limited provider test data. Only an Amadeus production key with `AMADEUS_ENVIRONMENT=production` should be presented to users as live inventory.
