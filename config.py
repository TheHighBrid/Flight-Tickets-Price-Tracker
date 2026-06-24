import os
from dotenv import load_dotenv

basedir = os.path.abspath(os.path.dirname(__file__))
load_dotenv(os.path.join(basedir, '.env'))


class Config:
    SECRET_KEY = os.environ.get('SECRET_KEY') or 'dev-secret-change-in-production'
    SQLALCHEMY_DATABASE_URI = os.environ.get('DATABASE_URL') or \
        f'sqlite:///{os.path.join(basedir, "instance", "flight_tracker.db")}'
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    AMADEUS_CLIENT_ID = os.environ.get('AMADEUS_CLIENT_ID', '')
    AMADEUS_CLIENT_SECRET = os.environ.get('AMADEUS_CLIENT_SECRET', '')
    AMADEUS_HOSTNAME = os.environ.get('AMADEUS_HOSTNAME', 'test')

    MAIL_SERVER = os.environ.get('MAIL_SERVER', 'smtp.gmail.com')
    MAIL_PORT = int(os.environ.get('MAIL_PORT', 587))
    MAIL_USERNAME = os.environ.get('MAIL_USERNAME', '')
    MAIL_PASSWORD = os.environ.get('MAIL_PASSWORD', '')
    MAIL_FROM = os.environ.get('MAIL_FROM', 'flighttracker@example.com')

    PRICE_CHECK_INTERVAL_HOURS = int(os.environ.get('PRICE_CHECK_INTERVAL_HOURS', 6))
    SCHEDULER_ENABLED = os.environ.get('SCHEDULER_ENABLED', 'true').lower() == 'true'
