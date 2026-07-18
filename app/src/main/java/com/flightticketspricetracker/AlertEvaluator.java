package com.flightticketspricetracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AlertEvaluator {
    public static final class Result {
        public final PriceAlert alert;
        public final int bestPrice;
        public final boolean targetReached;

        Result(PriceAlert alert, int bestPrice) {
            this.alert = alert;
            this.bestPrice = bestPrice;
            this.targetReached = bestPrice <= alert.targetPrice;
        }

        public String summary() {
            return alert.criteria.route() + ": " + alert.criteria.currency + " $" + bestPrice
                    + (targetReached ? " • target reached" : " • target " + alert.criteria.currency + " $" + alert.targetPrice);
        }
    }

    private final FlightSearchEngine engine;

    public AlertEvaluator(FlightSearchEngine engine) {
        this.engine = engine;
    }

    public List<Result> evaluate(List<PriceAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) return Collections.emptyList();
        List<Result> results = new ArrayList<>();
        for (PriceAlert alert : alerts) results.add(new Result(alert, engine.bestPrice(alert.criteria)));
        return results;
    }
}
