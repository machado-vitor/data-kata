package com.datakata.api.model;

import java.util.List;

public record TopSalesmanResponse(WindowInfo window, List<SalesmanRanking> rankings) {}
