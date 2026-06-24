import smtplib
import logging
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from flask import current_app

logger = logging.getLogger(__name__)


def send_price_alert(route, price_record):
    """Send an email alert when a tracked route drops below the target price."""
    mail_username = current_app.config.get('MAIL_USERNAME')
    mail_password = current_app.config.get('MAIL_PASSWORD')
    mail_from = current_app.config.get('MAIL_FROM')

    if not all([mail_username, mail_password, route.alert_email]):
        logger.info("Email alert skipped: missing credentials or recipient.")
        return False

    subject = (
        f"Price Alert: {route.origin} → {route.destination} "
        f"now ${price_record.price:.2f} {price_record.currency}"
    )

    dep_date = route.departure_date.strftime('%B %d, %Y')
    ret_info = ''
    if route.return_date:
        ret_info = f"<p><strong>Return:</strong> {route.return_date.strftime('%B %d, %Y')}</p>"

    html_body = f"""
    <html><body style="font-family: Arial, sans-serif; max-width: 600px; margin: auto;">
      <div style="background: linear-gradient(135deg,#1a1a2e,#0077cc); padding: 30px; border-radius:8px 8px 0 0;">
        <h1 style="color:#fff; margin:0;">✈ Flight Price Alert</h1>
        <p style="color:#cce5ff; margin:8px 0 0;">Your tracked route just dropped below your target!</p>
      </div>
      <div style="background:#f8f9fa; padding:30px; border-radius:0 0 8px 8px;">
        <h2 style="color:#0077cc;">{route.origin} → {route.destination}</h2>
        <p style="font-size:2.5em; color:#28a745; font-weight:bold; margin:0;">
          ${price_record.price:.2f} <span style="font-size:0.4em; color:#666;">{price_record.currency}</span>
        </p>
        <p style="color:#666;">Target price: ${route.target_price:.2f}</p>
        <hr style="border:none; border-top:1px solid #dee2e6; margin:20px 0;">
        <p><strong>Departure:</strong> {dep_date}</p>
        {ret_info}
        <p><strong>Airline:</strong> {price_record.airline_name or price_record.airline}</p>
        <p><strong>Stops:</strong> {'Nonstop' if price_record.stops == 0 else f'{price_record.stops} stop(s)'}</p>
        <hr style="border:none; border-top:1px solid #dee2e6; margin:20px 0;">
        <p style="color:#888; font-size:0.85em;">
          You are receiving this alert because you set up price tracking for this route.
        </p>
      </div>
    </html></body>
    """

    try:
        msg = MIMEMultipart('alternative')
        msg['Subject'] = subject
        msg['From'] = mail_from
        msg['To'] = route.alert_email
        msg.attach(MIMEText(html_body, 'html'))

        with smtplib.SMTP(
            current_app.config['MAIL_SERVER'],
            current_app.config['MAIL_PORT']
        ) as server:
            server.ehlo()
            server.starttls()
            server.login(mail_username, mail_password)
            server.sendmail(mail_from, route.alert_email, msg.as_string())

        logger.info(f"Price alert sent to {route.alert_email} for route {route.id}")
        return True

    except Exception as e:
        logger.error(f"Failed to send price alert: {e}")
        return False
