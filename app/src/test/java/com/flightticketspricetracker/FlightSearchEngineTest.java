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
        assertEquals(5, quotes.size());
        for (int i = 1; i < quotes.size(); i++) assertTrue(quotes.get(i - 1).priceUsd <= quotes.get(i).priceUsd);
        assertTrue(quotes.get(0).summary().contains("JFK → LAX"));
    }
    @Test public void priceAlertRoundTripsThroughStorageFormat() {
        PriceAlert alert = PriceAlert.decode(new PriceAlert("jfk", "lax", 250).encode());
        assertEquals("JFK → LAX below $250", alert.summary());
    }
}
