package com.datakata.api.model;

import java.util.List;

public record TopSalesCityResponse(WindowInfo window, List<CityRanking> rankings) {}
