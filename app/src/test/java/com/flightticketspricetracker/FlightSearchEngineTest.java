package com.flightticketspricetracker;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class FlightSearchEngineTest {
    @Test public void normalizesAirportInputs() {
        assertEquals("JFK", FlightSearchEngine.normalizeAirport("jfk"));
        assertEquals("SFO", FlightSearchEngine.normalizeAirport("San Francisco"));
        assertEquals("ANY", FlightSearchEngine.normalizeAirport(""));
    }

    @Test public void returnsSortedFareQuotes() {
        List<FareQuote> quotes = new FlightSearchEngine().search("JFK", "LAX", "Economy", true, 1);
        assertEquals(6, quotes.size());
        for (int i = 1; i < quotes.size(); i++) assertTrue(quotes.get(i - 1).priceUsd <= quotes.get(i).priceUsd);
        assertTrue(quotes.get(0).summary().contains("JFK → LAX"));
    }

    @Test public void momPriorityDefaultsAreDirectVisaSafeAndPreferAirCanadaWhenClose() {
        TripRequest request = TripRequest.momPriorityDefaults();
        assertEquals("CMN → YUL • 2026-08-20 to 2027-02-10 • 174 days", request.routeSummary());
        assertTrue(request.isVisaSafeReturnWindow());
        List<FareQuote> prioritized = new TripAdvisor().prioritize(request, new FlightSearchEngine().search(request));
        assertFalse(prioritized.isEmpty());
        assertEquals(0, prioritized.get(0).stops);
        assertEquals("Air Canada", prioritized.get(0).airline);
        assertEquals(2, prioritized.get(0).checkedBags);
    }

    @Test public void detectsReturnAtSixMonthsAsUnsafe() {
        TripRequest unsafe = new TripRequest("CMN", "YUL", "2026-08-20", "2027-02-20", "Economy", true, true, 1, 850);
        assertFalse(unsafe.isVisaSafeReturnWindow());
    }

    @Test public void priceAlertRoundTripsThroughStorageFormat() {
        PriceAlert alert = PriceAlert.decode(new PriceAlert("jfk", "lax", 250).encode());
        assertEquals("JFK → LAX below $250", alert.summary());
    }
}
