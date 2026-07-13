package com.flightticketspricetracker;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class AlertEvaluatorTest {
    @Test
    public void marksTargetsAtOrAboveTheBestFareAsReached() {
        SearchCriteria criteria = new SearchCriteria(
                "YOW", "CMN", "2099-08-10", "2099-08-25",
                "Economy", true, 1, "CAD"
        );
        FlightSearchEngine engine = new FlightSearchEngine();
        int bestPrice = engine.bestPrice(criteria);
        PriceAlert alert = new PriceAlert(criteria, bestPrice);

        List<AlertEvaluator.Result> results = new AlertEvaluator(engine)
                .evaluate(Collections.singletonList(alert));

        assertEquals(1, results.size());
        assertEquals(bestPrice, results.get(0).bestPrice);
        assertTrue(results.get(0).targetReached);
    }
}
