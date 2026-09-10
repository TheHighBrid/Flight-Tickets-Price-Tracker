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
    if (!"YUL".equals(AirportCatalog.resolveCode("Montreal Trudeau"))) throw new AssertionError("Montreal Trudeau alias");
    if (AirportCatalog.resolveCode("Unknownopolis") != null) throw new AssertionError("invented airport code");

    SearchCriteria criteria = new SearchCriteria(
        "YUL", "CMN", "2099-08-10", "2099-08-25",
        "Economy", true, false, 1, "CAD"
    );
    if (!criteria.isValid()) throw new AssertionError(criteria.firstValidationError());

    PriceAlert alert = new PriceAlert(criteria, new BigDecimal("750.50"));
    PriceAlert decoded = PriceAlert.decode(alert.encode());
    if (!alert.key().equals(decoded.key())) throw new AssertionError("alert serialization");

    ProviderConfig backend = new ProviderConfig(
        ProviderConfig.Mode.BACKEND,
        ProviderConfig.Environment.PRODUCTION,
        "", "", "https://flights.example.com", "token"
    );
    if (!ProviderConfig.decode(backend.encode(), "").isConfigured()) throw new AssertionError("provider config");

    ProviderConfig pool = new ProviderConfig(
        ProviderConfig.Mode.SERPAPI_DIRECT,
        ProviderConfig.Environment.PRODUCTION,
        new String[]{"key-1", "key-2", "key-3", "key-4", "key-5", "key-6"},
        "", "", ""
    );
    ProviderConfig poolDecoded = ProviderConfig.decode(pool.encode(), "");
    if (poolDecoded.apiKeyCount() != 5) throw new AssertionError("SerpApi pool size");
    if (!"key-1".equals(poolDecoded.apiKeyAt(0))) throw new AssertionError("SerpApi primary key");
    if (!"key-5".equals(poolDecoded.apiKeyAt(4))) throw new AssertionError("SerpApi fifth key");
  }
}
JAVA

javac -cp "$OUT" -d "$OUT" /tmp/LiveCoreSmokeTest.java
java -cp "$OUT" LiveCoreSmokeTest
