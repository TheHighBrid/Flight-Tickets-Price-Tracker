package com.flightticketspricetracker;

import java.util.List;

public interface FlightService {
    List<FareQuote> search(SearchCriteria criteria) throws FlightServiceException;
}
