from datetime import datetime, date
from flask import Blueprint, render_template, request, redirect, url_for, jsonify, flash, abort
from app import db
from app.models import TrackedRoute, PriceHistory
from app.amadeus_client import AmadeusClient, AIRPORTS
from flask import current_app

bp = Blueprint('main', __name__)


def get_amadeus_client() -> AmadeusClient:
    return AmadeusClient(
        client_id=current_app.config.get('AMADEUS_CLIENT_ID', ''),
        client_secret=current_app.config.get('AMADEUS_CLIENT_SECRET', ''),
        hostname=current_app.config.get('AMADEUS_HOSTNAME', 'test'),
    )


@bp.route('/')
def index():
    routes = TrackedRoute.query.order_by(TrackedRoute.created_at.desc()).all()
    stats = {
        'total': len(routes),
        'active': sum(1 for r in routes if r.active),
        'alerts_set': sum(1 for r in routes if r.target_price),
        'below_target': sum(1 for r in routes if r.is_below_target),
    }
    return render_template('index.html', routes=routes, stats=stats)


@bp.route('/routes/add', methods=['GET', 'POST'])
def add_route():
    if request.method == 'POST':
        origin = request.form.get('origin', '').strip().upper()
        destination = request.form.get('destination', '').strip().upper()
        departure_date_str = request.form.get('departure_date', '').strip()
        return_date_str = request.form.get('return_date', '').strip()
        adults = int(request.form.get('adults', 1))
        currency = request.form.get('currency', 'USD').strip().upper()
        target_price_str = request.form.get('target_price', '').strip()
        alert_email = request.form.get('alert_email', '').strip()

        if not origin or not destination or not departure_date_str:
            flash('Origin, destination, and departure date are required.', 'danger')
            return redirect(url_for('main.add_route'))

        if len(origin) != 3 or len(destination) != 3:
            flash('Please use 3-letter IATA airport codes (e.g., JFK, LAX).', 'danger')
            return redirect(url_for('main.add_route'))

        try:
            departure_date = datetime.strptime(departure_date_str, '%Y-%m-%d').date()
        except ValueError:
            flash('Invalid departure date format.', 'danger')
            return redirect(url_for('main.add_route'))

        if departure_date < date.today():
            flash('Departure date must be in the future.', 'danger')
            return redirect(url_for('main.add_route'))

        return_date = None
        if return_date_str:
            try:
                return_date = datetime.strptime(return_date_str, '%Y-%m-%d').date()
                if return_date <= departure_date:
                    flash('Return date must be after departure date.', 'danger')
                    return redirect(url_for('main.add_route'))
            except ValueError:
                flash('Invalid return date format.', 'danger')
                return redirect(url_for('main.add_route'))

        target_price = None
        if target_price_str:
            try:
                target_price = float(target_price_str)
                if target_price <= 0:
                    raise ValueError
            except ValueError:
                flash('Target price must be a positive number.', 'danger')
                return redirect(url_for('main.add_route'))

        client = get_amadeus_client()
        route = TrackedRoute(
            origin=origin,
            destination=destination,
            origin_city=client.get_airport_city(origin),
            destination_city=client.get_airport_city(destination),
            departure_date=departure_date,
            return_date=return_date,
            adults=adults,
            currency=currency,
            target_price=target_price,
            alert_email=alert_email if alert_email else None,
        )
        db.session.add(route)
        db.session.flush()

        # Fetch initial price immediately
        offer = client.get_cheapest_flight(
            origin=origin,
            destination=destination,
            departure_date=departure_date_str,
            return_date=return_date_str if return_date_str else None,
            adults=adults,
            currency=currency,
        )
        if offer:
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

        db.session.commit()
        flash(f'Now tracking {origin} → {destination}!', 'success')
        return redirect(url_for('main.route_detail', route_id=route.id))

    return render_template('add_route.html', airports=AIRPORTS, now=datetime.now())


@bp.route('/routes/<int:route_id>')
def route_detail(route_id):
    route = TrackedRoute.query.get_or_404(route_id)
    history_json = [r.to_dict() for r in route.price_history]
    return render_template('route_detail.html', route=route, history_json=history_json)


@bp.route('/routes/<int:route_id>/delete', methods=['POST'])
def delete_route(route_id):
    route = TrackedRoute.query.get_or_404(route_id)
    db.session.delete(route)
    db.session.commit()
    flash(f'Route {route.origin} → {route.destination} removed.', 'info')
    return redirect(url_for('main.index'))


@bp.route('/routes/<int:route_id>/toggle', methods=['POST'])
def toggle_route(route_id):
    route = TrackedRoute.query.get_or_404(route_id)
    route.active = not route.active
    db.session.commit()
    status = 'active' if route.active else 'paused'
    return jsonify({'success': True, 'active': route.active, 'status': status})


@bp.route('/routes/<int:route_id>/check', methods=['POST'])
def check_route(route_id):
    route = TrackedRoute.query.get_or_404(route_id)

    if route.departure_date < date.today():
        return jsonify({'success': False, 'error': 'Departure date is in the past.'}), 400

    client = get_amadeus_client()
    offer = client.get_cheapest_flight(
        origin=route.origin,
        destination=route.destination,
        departure_date=route.departure_date.isoformat(),
        return_date=route.return_date.isoformat() if route.return_date else None,
        adults=route.adults,
        currency=route.currency,
    )

    if offer is None:
        return jsonify({'success': False, 'error': 'Could not fetch price.'}), 500

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
    db.session.commit()

    return jsonify({
        'success': True,
        'price': offer.price,
        'currency': offer.currency,
        'airline_name': offer.airline_name,
        'checked_at': record.checked_at.isoformat(),
    })


@bp.route('/api/routes/<int:route_id>/history')
def api_price_history(route_id):
    route = TrackedRoute.query.get_or_404(route_id)
    history = [r.to_dict() for r in route.price_history]
    return jsonify({
        'route': route.to_dict(),
        'history': history,
    })


@bp.route('/api/airports')
def api_airports():
    query = request.args.get('q', '').upper().strip()
    if not query:
        return jsonify([])

    results = []
    for code, city in AIRPORTS.items():
        if query in code or query.lower() in city.lower():
            results.append({'code': code, 'city': city, 'label': f'{code} – {city}'})
    return jsonify(results[:10])
