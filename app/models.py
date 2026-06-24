from datetime import datetime
from app import db


class TrackedRoute(db.Model):
    __tablename__ = 'tracked_routes'

    id = db.Column(db.Integer, primary_key=True)
    origin = db.Column(db.String(3), nullable=False)
    destination = db.Column(db.String(3), nullable=False)
    origin_city = db.Column(db.String(100), nullable=True)
    destination_city = db.Column(db.String(100), nullable=True)
    departure_date = db.Column(db.Date, nullable=False)
    return_date = db.Column(db.Date, nullable=True)
    adults = db.Column(db.Integer, default=1)
    currency = db.Column(db.String(3), default='USD')
    target_price = db.Column(db.Float, nullable=True)
    alert_email = db.Column(db.String(120), nullable=True)
    active = db.Column(db.Boolean, default=True)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    last_checked = db.Column(db.DateTime, nullable=True)

    price_history = db.relationship(
        'PriceHistory', backref='route', lazy=True,
        cascade='all, delete-orphan', order_by='PriceHistory.checked_at'
    )

    @property
    def latest_price(self):
        if self.price_history:
            return self.price_history[-1].price
        return None

    @property
    def previous_price(self):
        if len(self.price_history) >= 2:
            return self.price_history[-2].price
        return None

    @property
    def price_change(self):
        latest = self.latest_price
        previous = self.previous_price
        if latest is not None and previous is not None:
            return latest - previous
        return None

    @property
    def price_change_pct(self):
        change = self.price_change
        previous = self.previous_price
        if change is not None and previous and previous > 0:
            return (change / previous) * 100
        return None

    @property
    def is_below_target(self):
        if self.target_price and self.latest_price:
            return self.latest_price <= self.target_price
        return False

    @property
    def trip_type(self):
        return 'Round Trip' if self.return_date else 'One Way'

    def to_dict(self):
        return {
            'id': self.id,
            'origin': self.origin,
            'destination': self.destination,
            'origin_city': self.origin_city,
            'destination_city': self.destination_city,
            'departure_date': self.departure_date.isoformat(),
            'return_date': self.return_date.isoformat() if self.return_date else None,
            'adults': self.adults,
            'currency': self.currency,
            'target_price': self.target_price,
            'alert_email': self.alert_email,
            'active': self.active,
            'latest_price': self.latest_price,
            'price_change': self.price_change,
            'price_change_pct': round(self.price_change_pct, 2) if self.price_change_pct else None,
            'last_checked': self.last_checked.isoformat() if self.last_checked else None,
        }


class PriceHistory(db.Model):
    __tablename__ = 'price_history'

    id = db.Column(db.Integer, primary_key=True)
    route_id = db.Column(db.Integer, db.ForeignKey('tracked_routes.id'), nullable=False)
    price = db.Column(db.Float, nullable=False)
    currency = db.Column(db.String(3), default='USD')
    airline = db.Column(db.String(10), nullable=True)
    airline_name = db.Column(db.String(100), nullable=True)
    duration = db.Column(db.String(30), nullable=True)
    stops = db.Column(db.Integer, default=0)
    departure_time = db.Column(db.String(30), nullable=True)
    arrival_time = db.Column(db.String(30), nullable=True)
    checked_at = db.Column(db.DateTime, default=datetime.utcnow)

    def to_dict(self):
        return {
            'id': self.id,
            'price': self.price,
            'currency': self.currency,
            'airline': self.airline,
            'airline_name': self.airline_name,
            'duration': self.duration,
            'stops': self.stops,
            'departure_time': self.departure_time,
            'arrival_time': self.arrival_time,
            'checked_at': self.checked_at.isoformat(),
        }
