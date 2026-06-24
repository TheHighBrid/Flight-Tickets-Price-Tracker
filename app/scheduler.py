import logging
from datetime import datetime, date
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.interval import IntervalTrigger

logger = logging.getLogger(__name__)
_scheduler = None


def check_all_routes(app):
    """Background job: fetch current prices for all active routes."""
    with app.app_context():
        from app import db
        from app.models import TrackedRoute, PriceHistory
        from app.amadeus_client import AmadeusClient
        from app.notifier import send_price_alert

        client = AmadeusClient(
            client_id=app.config.get('AMADEUS_CLIENT_ID', ''),
            client_secret=app.config.get('AMADEUS_CLIENT_SECRET', ''),
            hostname=app.config.get('AMADEUS_HOSTNAME', 'test'),
        )

        routes = TrackedRoute.query.filter_by(active=True).all()
        today = date.today()

        for route in routes:
            if route.departure_date < today:
                logger.info(f"Skipping route {route.id}: departure date is in the past.")
                continue

            offer = client.get_cheapest_flight(
                origin=route.origin,
                destination=route.destination,
                departure_date=route.departure_date.isoformat(),
                return_date=route.return_date.isoformat() if route.return_date else None,
                adults=route.adults,
                currency=route.currency,
            )

            if offer is None:
                logger.warning(f"No offer returned for route {route.id}.")
                continue

            record = PriceHistory(
                route_id=route.id,
                price=offer.price,
                currency=offer.currency,
                airline=offer.airline,
                airline_name=offer.airline_name,
                duration=offer.duration,
                stops=offer.stops,
                departure_time=offer.departure_time,
                arrival_time=offer.arrival_time,
            )
            db.session.add(record)
            route.last_checked = datetime.utcnow()

            if (
                route.target_price
                and offer.price <= route.target_price
                and route.alert_email
            ):
                try:
                    send_price_alert(route, record)
                except Exception as e:
                    logger.error(f"Alert failed for route {route.id}: {e}")

        db.session.commit()
        logger.info(f"Price check complete. Checked {len(routes)} route(s).")


def start_scheduler(app):
    global _scheduler

    if _scheduler is not None and _scheduler.running:
        return

    interval_hours = app.config.get('PRICE_CHECK_INTERVAL_HOURS', 6)

    _scheduler = BackgroundScheduler(daemon=True)
    _scheduler.add_job(
        func=check_all_routes,
        args=[app],
        trigger=IntervalTrigger(hours=interval_hours),
        id='check_all_routes',
        name='Check flight prices',
        replace_existing=True,
    )
    _scheduler.start()
    logger.info(f"Scheduler started. Price checks every {interval_hours} hour(s).")
