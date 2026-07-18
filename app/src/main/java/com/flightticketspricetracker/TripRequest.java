package com.flightticketspricetracker;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class TripRequest {
    public final String origin;
    public final String destination;
    public final String departDate;
    public final String returnDate;
    public final String cabin;
    public final boolean roundTrip;
    public final boolean directOnly;
    public final int passengers;
    public final int targetUsd;

    public TripRequest(String origin, String destination, String departDate, String returnDate, String cabin, boolean roundTrip, boolean directOnly, int passengers, int targetUsd) {
        this.origin = FlightSearchEngine.normalizeAirport(origin);
        this.destination = FlightSearchEngine.normalizeAirport(destination);
        this.departDate = validDateOrFallback(departDate, "2026-08-20");
        this.returnDate = validDateOrFallback(returnDate, "2027-02-10");
        this.cabin = cabin == null || cabin.trim().isEmpty() ? "Economy" : cabin.trim();
        this.roundTrip = roundTrip;
        this.directOnly = directOnly;
        this.passengers = Math.max(1, passengers);
        this.targetUsd = Math.max(1, targetUsd);
    }

    public static TripRequest momPriorityDefaults() {
        return new TripRequest("CMN", "YUL", "2026-08-20", "2027-02-10", "Economy", true, true, 1, 850);
    }

    public boolean isVisaSafeReturnWindow() {
        Calendar latest = parse(departDate);
        latest.add(Calendar.MONTH, 6);
        Calendar returns = parse(returnDate);
        return returns.before(latest);
    }

    public int tripLengthDays() {
        long diff = parse(returnDate).getTimeInMillis() - parse(departDate).getTimeInMillis();
        return (int) (diff / (24L * 60L * 60L * 1000L));
    }

    public String routeSummary() {
        return origin + " → " + destination + " • " + departDate + " to " + returnDate + " • " + tripLengthDays() + " days";
    }

    private static String validDateOrFallback(String value, String fallback) {
        try { parse(value); return value; } catch (Exception ignored) { return fallback; }
    }

    private static Calendar parse(String value) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
            calendar.setTime(format.parse(value));
            return calendar;
        } catch (ParseException ex) {
            throw new IllegalArgumentException("Expected yyyy-MM-dd date: " + value, ex);
        }
    }
}
