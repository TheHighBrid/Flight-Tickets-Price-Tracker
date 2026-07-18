package com.flightticketspricetracker;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FlightSearchEngineTest {
    @Test
    public void resolvesAirportCodesWithoutInventingCityCodes() {
        assertEquals("JFK", AirportCatalog.resolveCode("jfk"));
        assertEquals("SFO", AirportCatalog.resolveCode("San Francisco"));
        assertEquals("YUL", AirportCatalog.resolveCode("Montréal"));
        assertEquals("YOW", AirportCatalog.resolveCode("Ottawa (YOW)"));
        assertNull(AirportCatalog.resolveCode("Unknownopolis"));
    }

    @Test
    public void rejectsInvalidSearches() {
        SearchCriteria sameAirport = criteria("YOW", "YOW", true, 1);
        assertFalse(sameAirport.isValid());
        assertTrue(sameAirport.firstValidationError().contains("different"));

        SearchCriteria tooManyPassengers = criteria("YOW", "CMN", true, 10);
        assertFalse(tooManyPassengers.isValid());
        assertTrue(tooManyPassengers.firstValidationError().contains("1 and 9"));
    }

    @Test
    public void returnsSortedDeterministicFareQuotes() {
        SearchCriteria criteria = criteria("YOW", "CMN", true, 2);
        FlightSearchEngine engine = new FlightSearchEngine();
        List<FareQuote> first = engine.search(criteria);
        List<FareQuote> second = engine.search(criteria);

        assertEquals(7, first.size());
        assertEquals(first.get(0).totalPrice, second.get(0).totalPrice);
        for (int i = 1; i < first.size(); i++) {
            assertTrue(first.get(i - 1).totalPrice <= first.get(i).totalPrice);
        }
        assertTrue(first.get(0).summary().contains("YOW → CMN"));
    }

    @Test
    public void formatsStopsCorrectly() {
        FareQuote nonstop = new FareQuote("Air", "A → B", "08:00", "09:00", 0, 100, "CAD", "Economy", 60);
        FareQuote oneStop = new FareQuote("Air", "A → B", "08:00", "10:00", 1, 120, "CAD", "Economy", 120);
        FareQuote twoStops = new FareQuote("Air", "A → B", "08:00", "12:00", 2, 140, "CAD", "Economy", 240);
        assertEquals("Nonstop", nonstop.stopsLabel());
        assertEquals("1 stop", oneStop.stopsLabel());
        assertEquals("2 stops", twoStops.stopsLabel());
    }

    @Test
    public void priceAlertsRoundTripAndLegacyDataMigrates() {
        PriceAlert current = new PriceAlert(criteria("YOW", "CMN", true, 1), 750);
        PriceAlert decoded = PriceAlert.decode(current.encode());
        assertEquals(current.key(), decoded.key());
        assertEquals(750, decoded.targetPrice);

        PriceAlert legacy = PriceAlert.decode("JFK,LAX,250");
        assertEquals("JFK", legacy.criteria.origin);
        assertEquals("LAX", legacy.criteria.destination);
        assertEquals("USD", legacy.criteria.currency);
        assertEquals(250, legacy.targetPrice);
        assertNull(PriceAlert.tryDecode("corrupted-data"));
    }

    private static SearchCriteria criteria(String from, String to, boolean roundTrip, int passengers) {
        return new SearchCriteria(
                from,
                to,
                "2099-08-10",
                "2099-08-25",
                "Economy",
                roundTrip,
                passengers,
                "CAD"
        );
    }
}
