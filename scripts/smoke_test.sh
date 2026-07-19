#!/usr/bin/env bash
set -euo pipefail

OUT=${OUT:-/tmp/ftpt-classes}
rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
  app/src/main/java/com/flightticketspricetracker/AirportCatalog.java \
  app/src/main/java/com/flightticketspricetracker/SearchCriteria.java \
  app/src/main/java/com/flightticketspricetracker/FareQuote.java \
  app/src/main/java/com/flightticketspricetracker/FlightSearchEngine.java \
  app/src/main/java/com/flightticketspricetracker/PriceAlert.java \
  app/src/main/java/com/flightticketspricetracker/AlertEvaluator.java

cat > /tmp/CoreSmokeTest.java <<'JAVA'
import com.flightticketspricetracker.*;
import java.util.*;

public class CoreSmokeTest {
  public static void main(String[] args) {
    if (!"SFO".equals(AirportCatalog.resolveCode("San Francisco"))) {
      throw new AssertionError("airport alias resolution");
    }

    SearchCriteria criteria = new SearchCriteria(
        "YOW",
        "CMN",
        "2099-08-10",
        "2099-08-25",
        "Economy",
        true,
        1,
        "CAD"
    );
    if (!criteria.isValid()) throw new AssertionError(criteria.firstValidationError());

    FlightSearchEngine engine = new FlightSearchEngine();
    List<FareQuote> quotes = engine.search(criteria);
    if (quotes.size() != 7) throw new AssertionError("quote count");
    for (int i = 1; i < quotes.size(); i++) {
      if (quotes.get(i - 1).totalPrice > quotes.get(i).totalPrice) {
        throw new AssertionError("quotes are not sorted");
      }
    }
    if (!quotes.get(0).summary().contains("YOW → CMN")) {
      throw new AssertionError("quote summary");
    }

    PriceAlert alert = new PriceAlert(criteria, quotes.get(0).totalPrice);
    PriceAlert decoded = PriceAlert.decode(alert.encode());
    if (!alert.key().equals(decoded.key())) throw new AssertionError("alert serialization");

    List<AlertEvaluator.Result> results = new AlertEvaluator(engine)
        .evaluate(Collections.singletonList(alert));
    if (results.size() != 1 || !results.get(0).targetReached) {
      throw new AssertionError("alert evaluation");
    }
  }
}
JAVA

javac -cp "$OUT" -d "$OUT" /tmp/CoreSmokeTest.java
java -cp "$OUT" CoreSmokeTest
