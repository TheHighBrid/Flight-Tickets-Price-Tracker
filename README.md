# ✈ Flight Tickets Price Tracker

Automatically track flight prices and get alerted when they drop below your target.

![Python](https://img.shields.io/badge/Python-3.9+-blue)
![Flask](https://img.shields.io/badge/Flask-3.0-green)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

## Features

- **Track flight routes** — one-way or round-trip, any IATA airport pair
- **Automatic price checks** — background scheduler checks prices every 6 hours (configurable)
- **Price history charts** — visualize price trends over time with Chart.js
- **Email alerts** — get notified when prices drop below your target
- **Demo mode** — works out of the box with realistic mock data (no API keys needed)
- **Live mode** — connect to the Amadeus API for real flight prices

## Quick Start

```bash
# 1. Clone the repo
git clone https://github.com/thehighbrid/flight-tickets-price-tracker.git
cd flight-tickets-price-tracker

# 2. Create a virtual environment
python -m venv venv
source venv/bin/activate      # Windows: venv\Scripts\activate

# 3. Install dependencies
pip install -r requirements.txt

# 4. Configure environment (optional for demo mode)
cp .env.example .env
# Edit .env with your Amadeus API keys and email settings

# 5. Run the app
python run.py
```

Open http://localhost:5000 in your browser.

## Configuration

Copy `.env.example` to `.env` and configure:

| Variable | Description | Default |
|---|---|---|
| `SECRET_KEY` | Flask secret key | dev key |
| `AMADEUS_CLIENT_ID` | Amadeus API client ID | — (demo mode) |
| `AMADEUS_CLIENT_SECRET` | Amadeus API client secret | — (demo mode) |
| `AMADEUS_HOSTNAME` | `test` or `production` | `test` |
| `MAIL_USERNAME` | Gmail / SMTP username | — |
| `MAIL_PASSWORD` | Gmail app password | — |
| `PRICE_CHECK_INTERVAL_HOURS` | Hours between automatic checks | `6` |
| `SCHEDULER_ENABLED` | Enable background price checks | `true` |

## Getting Amadeus API Keys

1. Sign up at [developers.amadeus.com](https://developers.amadeus.com) (free)
2. Create a new app to get `Client ID` and `Client Secret`
3. The sandbox environment is free and has realistic test data

## Project Structure

```
Flight-Tickets-Price-Tracker/
├── app/
│   ├── __init__.py          # Flask app factory
│   ├── models.py            # SQLAlchemy models
│   ├── routes.py            # Flask routes & API endpoints
│   ├── amadeus_client.py    # Amadeus API client + mock data
│   ├── scheduler.py         # APScheduler background jobs
│   ├── notifier.py          # Email price alerts
│   ├── templates/           # Jinja2 HTML templates
│   └── static/              # CSS & JavaScript
├── config.py                # App configuration
├── run.py                   # Entry point
├── requirements.txt
└── .env.example
```

## Tech Stack

- **Backend**: Python, Flask, Flask-SQLAlchemy, APScheduler
- **Database**: SQLite (dev) / PostgreSQL (prod via `DATABASE_URL`)
- **Flight Data**: Amadeus for Developers API
- **Frontend**: Bootstrap 5, Chart.js, Bootstrap Icons
- **Email**: SMTP / Gmail

## License

MIT
