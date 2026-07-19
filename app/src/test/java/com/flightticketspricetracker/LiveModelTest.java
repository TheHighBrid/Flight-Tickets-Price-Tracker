package com.flightticketspricetracker;

import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.*;

public class LiveModelTest {
    @Test
    public void resolvesAirportsWithoutInventingUnknownCodes() {
        assertEquals("SFO", AirportCatalog.resolveCode("San Francisco"));
        assertEquals("YUL", AirportCatalog.resolveCode("Montréal"));
        assertNull(AirportCatalog.resolveCode("Unknownopolis"));
    }

    @Test
    public void validatesRealSearchInputs() {
        SearchCriteria criteria = criteria("YOW", "CMN");
        assertTrue(criteria.isValid());
        assertEquals("ECONOMY", criteria.travelClassCode());

        SearchCriteria invalid = criteria("YOW", "YOW");
        assertFalse(invalid.isValid());
        assertTrue(invalid.firstValidationError().contains("different"));
    }

    @Test
    public void priceAlertRoundTripsWithoutLegacyDemoFormat() {
        PriceAlert alert = new PriceAlert(criteria("YOW", "CMN"), new BigDecimal("799.50"));
        PriceAlert decoded = PriceAlert.decode(alert.encode());
        assertEquals(alert.key(), decoded.key());
        assertEquals(0, decoded.targetPrice.compareTo(new BigDecimal("799.5")));
        assertNull(PriceAlert.tryDecode("JFK,LAX,250"));
    }

    @Test
    public void fareQuoteKeepsProviderTruth() {
        FareQuote quote = new FareQuote(
                "1", "Air Canada", "AC123", "YOW → YUL", "YOW 08:00 → YUL 09:00", "",
                0, 60, new BigDecimal("212.40"), "CAD", "1 checked bag",
                "Amadeus", "production", 1000L
        );
        assertEquals("CAD $212.40", quote.priceLabel());
        assertTrue(quote.summary().contains("Air Canada"));
        assertTrue(quote.summary().contains("Amadeus production"));
    }

    @Test
    public void providerConfigRoundTripsAndRequiresHttpsBackend() {
        ProviderConfig config = new ProviderConfig(
                ProviderConfig.Mode.BACKEND,
                ProviderConfig.Environment.PRODUCTION,
                "", "", "https://flights.example.com/", "secret"
        );
        ProviderConfig decoded = ProviderConfig.decode(config.encode(), "");
        assertEquals("https://flights.example.com", decoded.backendUrl);
        assertTrue(decoded.isConfigured());
        assertNotNull(new ProviderConfig(
                ProviderConfig.Mode.BACKEND,
                ProviderConfig.Environment.PRODUCTION,
                "", "", "http://insecure.example.com", ""
        ).validationError());
    }

    private static SearchCriteria criteria(String origin, String destination) {
        return new SearchCriteria(
                origin, destination, "2099-08-10", "2099-08-25",
                "Economy", true, false, 1, "CAD"
        );
    }
}
