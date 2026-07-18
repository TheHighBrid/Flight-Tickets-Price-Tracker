#!/usr/bin/env bash
set -euo pipefail
OUT=${OUT:-/tmp/ftpt-classes}
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  app/src/main/java/com/flightticketspricetracker/FareQuote.java \
  app/src/main/java/com/flightticketspricetracker/FlightSearchEngine.java \
  app/src/main/java/com/flightticketspricetracker/PriceAlert.java \
  app/src/main/java/com/flightticketspricetracker/TripRequest.java \
  app/src/main/java/com/flightticketspricetracker/TripAdvisor.java
cat > /tmp/CoreSmokeTest.java <<'JAVA'
import com.flightticketspricetracker.*;
import java.util.*;
public class CoreSmokeTest {
  public static void main(String[] args) {
    if (!FlightSearchEngine.normalizeAirport("jfk").equals("JFK")) throw new AssertionError("normalize");
    TripRequest request = TripRequest.momPriorityDefaults();
    if (!request.origin.equals("CMN") || !request.destination.equals("YUL")) throw new AssertionError("mom route");
    if (!request.isVisaSafeReturnWindow()) throw new AssertionError("visa-safe return");
    List<FareQuote> quotes = new TripAdvisor().prioritize(request, new FlightSearchEngine().search(request));
    if (quotes.isEmpty()) throw new AssertionError("quote count");
    if (!quotes.get(0).airline.equals("Air Canada")) throw new AssertionError("Air Canada preferred when close");
    if (quotes.get(0).stops != 0) throw new AssertionError("direct only");
    if (quotes.get(0).checkedBags != 2) throw new AssertionError("baggage preference");
    if (new TripRequest("CMN","YUL","2026-08-20","2027-02-20","Economy",true,true,1,850).isVisaSafeReturnWindow()) throw new AssertionError("six months unsafe");
    if (!PriceAlert.decode(new PriceAlert("jfk","lax",250).encode()).summary().equals("JFK → LAX below $250")) throw new AssertionError("alert");
  }
}
JAVA
javac -cp "$OUT" -d "$OUT" /tmp/CoreSmokeTest.java
java -cp "$OUT" CoreSmokeTest
