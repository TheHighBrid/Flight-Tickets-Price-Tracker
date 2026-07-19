#!/usr/bin/env bash
set -euo pipefail

OUT=${OUT:-/tmp/ftpt-live-classes}
rm -rf "$OUT"
mkdir -p "$OUT"

javac -d "$OUT" \
  app/src/main/java/com/flightticketspricetracker/AirportCatalog.java \
  app/src/main/java/com/flightticketspricetracker/SearchCriteria.java \
  app/src/main/java/com/flightticketspricetracker/FareQuote.java \
  app/src/main/java/com/flightticketspricetracker/PriceAlert.java \
  app/src/main/java/com/flightticketspricetracker/ProviderConfig.java

cat > /tmp/LiveCoreSmokeTest.java <<'JAVA'
import com.flightticketspricetracker.*;
import java.math.BigDecimal;

public class LiveCoreSmokeTest {
  public static void main(String[] args) {
    if (!"SFO".equals(AirportCatalog.resolveCode("San Francisco"))) throw new AssertionError("airport alias");
    if (AirportCatalog.resolveCode("Unknownopolis") != null) throw new AssertionError("invented airport code");

    SearchCriteria criteria = new SearchCriteria(
        "YOW", "CMN", "2099-08-10", "2099-08-25",
        "Economy", true, false, 1, "CAD"
    );
    if (!criteria.isValid()) throw new AssertionError(criteria.firstValidationError());

    PriceAlert alert = new PriceAlert(criteria, new BigDecimal("750.50"));
    PriceAlert decoded = PriceAlert.decode(alert.encode());
    if (!alert.key().equals(decoded.key())) throw new AssertionError("alert serialization");

    ProviderConfig config = new ProviderConfig(
        ProviderConfig.Mode.BACKEND,
        ProviderConfig.Environment.PRODUCTION,
        "", "", "https://flights.example.com", "token"
    );
    if (!ProviderConfig.decode(config.encode(), "").isConfigured()) throw new AssertionError("provider config");
  }
}
JAVA

javac -cp "$OUT" -d "$OUT" /tmp/LiveCoreSmokeTest.java
java -cp "$OUT" LiveCoreSmokeTest
