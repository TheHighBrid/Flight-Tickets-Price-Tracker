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
        SearchCriteria criteria = criteria("YUL", "CMN");
        assertTrue(criteria.isValid());
        assertEquals("ECONOMY", criteria.travelClassCode());

        SearchCriteria invalid = criteria("YUL", "YUL");
        assertFalse(invalid.isValid());
        assertTrue(invalid.firstValidationError().contains("different"));
    }

    @Test
    public void priceAlertRoundTripsWithoutLegacyDemoFormat() {
        PriceAlert alert = new PriceAlert(criteria("YUL", "CMN"), new BigDecimal("799.50"));
        PriceAlert decoded = PriceAlert.decode(alert.encode());
        assertEquals(alert.key(), decoded.key());
        assertEquals(0, decoded.targetPrice.compareTo(new BigDecimal("799.5")));
        assertNull(PriceAlert.tryDecode("JFK,LAX,250"));
    }

    @Test
    public void fareQuoteKeepsProviderTruth() {
        FareQuote quote = new FareQuote(
                "1", "Air Canada", "AC123", "YUL → CMN", "YUL 08:00 → CMN 18:00", "",
                0, 600, new BigDecimal("612.40"), "CAD", "1 checked bag",
                "Google Flights via SerpApi", "cache-enabled", 1000L
        );
        assertEquals("CAD $612.40", quote.priceLabel());
        assertTrue(quote.summary().contains("Air Canada"));
        assertTrue(quote.summary().contains("Google Flights via SerpApi cache-enabled"));
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

    @Test
    public void providerConfigRoundTripsFiveKeyPoolAndDeduplicates() {
        ProviderConfig config = new ProviderConfig(
                ProviderConfig.Mode.SERPAPI_DIRECT,
                ProviderConfig.Environment.PRODUCTION,
                new String[]{" key-a ", "", "key-b", "key-a", "key-c", "key-d", "key-e", "key-f"},
                "", "", ""
        );
        assertEquals(5, config.apiKeyCount());
        assertEquals("key-a", config.apiKeyAt(0));
        assertEquals("key-e", config.apiKeyAt(4));
        assertEquals("", config.apiKeyAt(5));

        ProviderConfig decoded = ProviderConfig.decode(config.encode(), "");
        assertEquals(config.apiKeys, decoded.apiKeys);
        assertEquals("key-a", decoded.apiKey);
        assertTrue(decoded.isConfigured());
    }

    @Test
    public void providerConfigMigratesLegacySingleSerpApiKey() {
        ProviderConfig decoded = ProviderConfig.decode(
                "v2|SERPAPI_DIRECT|PRODUCTION|legacy-key|||",
                ""
        );
        assertEquals(1, decoded.apiKeyCount());
        assertEquals("legacy-key", decoded.apiKeyAt(0));
    }

    private static SearchCriteria criteria(String origin, String destination) {
        return new SearchCriteria(
                origin, destination, "2099-08-10", "2099-08-25",
                "Economy", true, false, 1, "CAD"
        );
    }
}
