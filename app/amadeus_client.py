"""
Amadeus API client with built-in mock mode.

If AMADEUS_CLIENT_ID / AMADEUS_CLIENT_SECRET are not configured, the client
runs in demo mode and returns realistic synthetic prices so the app is
usable out-of-the-box without any API account.
"""
import random
import datetime
import requests
from dataclasses import dataclass
from typing import Optional


AIRLINE_NAMES = {
    'AA': 'American Airlines', 'UA': 'United Airlines', 'DL': 'Delta Air Lines',
    'WN': 'Southwest Airlines', 'BA': 'British Airways', 'LH': 'Lufthansa',
    'AF': 'Air France', 'KL': 'KLM', 'EK': 'Emirates', 'QR': 'Qatar Airways',
    'SQ': 'Singapore Airlines', 'CX': 'Cathay Pacific', 'JL': 'Japan Airlines',
    'NH': 'All Nippon Airways', 'AC': 'Air Canada', 'QF': 'Qantas',
    'TK': 'Turkish Airlines', 'IB': 'Iberia', 'AZ': 'ITA Airways',
    'SK': 'Scandinavian Airlines',
}

AIRPORTS = {
    'JFK': 'New York', 'LAX': 'Los Angeles', 'ORD': 'Chicago', 'ATL': 'Atlanta',
    'DFW': 'Dallas', 'SFO': 'San Francisco', 'SEA': 'Seattle', 'MIA': 'Miami',
    'BOS': 'Boston', 'LAS': 'Las Vegas', 'DEN': 'Denver', 'MSP': 'Minneapolis',
    'PHX': 'Phoenix', 'EWR': 'Newark', 'DTW': 'Detroit', 'PHL': 'Philadelphia',
    'LHR': 'London', 'CDG': 'Paris', 'FRA': 'Frankfurt', 'AMS': 'Amsterdam',
    'MAD': 'Madrid', 'BCN': 'Barcelona', 'FCO': 'Rome', 'ZRH': 'Zurich',
    'MUC': 'Munich', 'VIE': 'Vienna', 'BRU': 'Brussels', 'LIS': 'Lisbon',
    'CPH': 'Copenhagen', 'ARN': 'Stockholm', 'OSL': 'Oslo', 'HEL': 'Helsinki',
    'DXB': 'Dubai', 'AUH': 'Abu Dhabi', 'DOH': 'Doha', 'IST': 'Istanbul',
    'SIN': 'Singapore', 'NRT': 'Tokyo', 'HND': 'Tokyo Haneda', 'PEK': 'Beijing',
    'PVG': 'Shanghai', 'HKG': 'Hong Kong', 'ICN': 'Seoul', 'BKK': 'Bangkok',
    'KUL': 'Kuala Lumpur', 'CGK': 'Jakarta', 'MNL': 'Manila',
    'SYD': 'Sydney', 'MEL': 'Melbourne', 'BNE': 'Brisbane', 'AKL': 'Auckland',
    'GRU': 'São Paulo', 'GIG': 'Rio de Janeiro', 'EZE': 'Buenos Aires',
    'BOG': 'Bogotá', 'LIM': 'Lima', 'SCL': 'Santiago',
    'YYZ': 'Toronto', 'YVR': 'Vancouver', 'YUL': 'Montreal',
    'MEX': 'Mexico City', 'CUN': 'Cancún',
    'DEL': 'Delhi', 'BOM': 'Mumbai', 'BLR': 'Bangalore',
    'CAI': 'Cairo', 'JNB': 'Johannesburg', 'NBO': 'Nairobi', 'CPT': 'Cape Town',
    'CMN': 'Casablanca', 'ADD': 'Addis Ababa',
}

AMADEUS_TEST_BASE = 'https://test.api.amadeus.com'
AMADEUS_PROD_BASE = 'https://api.amadeus.com'


@dataclass
class FlightOffer:
    price: float
    currency: str
    airline: str
    airline_name: str
    duration: str
    stops: int
    departure_time: str
    arrival_time: str


def _format_duration(hours: int, minutes: int) -> str:
    return f"PT{hours}H{minutes}M"


class AmadeusClient:
    def __init__(self, client_id: str, client_secret: str, hostname: str = 'test'):
        self.client_id = client_id.strip()
        self.client_secret = client_secret.strip()
        self.base_url = AMADEUS_PROD_BASE if hostname == 'production' else AMADEUS_TEST_BASE
        self.mock_mode = not (self.client_id and self.client_secret)
        self._token: Optional[str] = None
        self._token_expires_at: float = 0.0

    def _get_access_token(self) -> str:
        import time
        if self._token and time.time() < self._token_expires_at - 30:
            return self._token

        resp = requests.post(
            f'{self.base_url}/v1/security/oauth2/token',
            data={
                'grant_type': 'client_credentials',
                'client_id': self.client_id,
                'client_secret': self.client_secret,
            },
            timeout=10,
        )
        resp.raise_for_status()
        data = resp.json()
        import time as _time
        self._token = data['access_token']
        self._token_expires_at = _time.time() + int(data.get('expires_in', 1799))
        return self._token

    def get_cheapest_flight(
        self,
        origin: str,
        destination: str,
        departure_date: str,
        return_date: Optional[str] = None,
        adults: int = 1,
        currency: str = 'USD',
    ) -> Optional[FlightOffer]:
        if self.mock_mode:
            return self._mock_flight_offer(origin, destination, departure_date, currency)

        try:
            token = self._get_access_token()
            params = {
                'originLocationCode': origin,
                'destinationLocationCode': destination,
                'departureDate': departure_date,
                'adults': adults,
                'currencyCode': currency,
                'max': 5,
            }
            if return_date:
                params['returnDate'] = return_date

            resp = requests.get(
                f'{self.base_url}/v2/shopping/flight-offers',
                headers={'Authorization': f'Bearer {token}'},
                params=params,
                timeout=15,
            )
            resp.raise_for_status()
            data = resp.json().get('data', [])
            if data:
                return self._parse_offer(data[0], currency)
        except Exception as e:
            print(f"[AmadeusClient] API error: {e}. Falling back to mock data.")
            return self._mock_flight_offer(origin, destination, departure_date, currency)

        return None

    @staticmethod
    def _parse_offer(offer: dict, currency: str) -> FlightOffer:
        price = float(offer['price']['total'])
        airline = (offer.get('validatingAirlineCodes') or [''])[0]
        itinerary = offer['itineraries'][0]
        segments = itinerary['segments']
        duration = itinerary.get('duration', 'PT0H0M')
        stops = len(segments) - 1
        departure_time = segments[0]['departure']['at']
        arrival_time = segments[-1]['arrival']['at']

        return FlightOffer(
            price=round(price, 2),
            currency=currency,
            airline=airline,
            airline_name=AIRLINE_NAMES.get(airline, airline),
            duration=duration,
            stops=stops,
            departure_time=departure_time,
            arrival_time=arrival_time,
        )

    @staticmethod
    def _mock_flight_offer(
        origin: str, destination: str, departure_date: str, currency: str = 'USD'
    ) -> FlightOffer:
        # Deterministic base price for the route, variable daily price
        rng = random.Random(f"{origin}{destination}")
        base_price = rng.uniform(120, 900)

        day_seed = datetime.date.today().toordinal()
        variation_rng = random.Random(f"{origin}{destination}{day_seed}")
        variation = variation_rng.uniform(-0.12, 0.12)
        price = round(base_price * (1 + variation), 2)

        airline_codes = list(AIRLINE_NAMES.keys())
        airline = airline_codes[rng.randint(0, len(airline_codes) - 1)]

        try:
            dep_dt = datetime.datetime.strptime(departure_date, '%Y-%m-%d')
        except ValueError:
            dep_dt = datetime.datetime.now()

        dep_dt = dep_dt.replace(
            hour=rng.randint(6, 20),
            minute=rng.choice([0, 15, 30, 45]),
        )
        duration_h = rng.randint(2, 14)
        duration_m = rng.choice([0, 15, 30, 45])
        arr_dt = dep_dt + datetime.timedelta(hours=duration_h, minutes=duration_m)
        stops = rng.choices([0, 1, 2], weights=[60, 30, 10])[0]

        return FlightOffer(
            price=price,
            currency=currency,
            airline=airline,
            airline_name=AIRLINE_NAMES.get(airline, airline),
            duration=_format_duration(duration_h, duration_m),
            stops=stops,
            departure_time=dep_dt.strftime('%Y-%m-%dT%H:%M:%S'),
            arrival_time=arr_dt.strftime('%Y-%m-%dT%H:%M:%S'),
        )

    @staticmethod
    def get_airport_city(iata_code: str) -> str:
        return AIRPORTS.get(iata_code.upper(), iata_code.upper())
