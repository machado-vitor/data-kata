package com.datakata.api.service;

import com.datakata.api.model.*;
import com.datakata.api.repository.TopSalesCityRepository;
import com.datakata.api.repository.TopSalesmanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SalesService {

    private static final Logger logger = LoggerFactory.getLogger(SalesService.class);
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final TopSalesCityRepository cityRepository;
    private final TopSalesmanRepository salesmanRepository;

    public SalesService(TopSalesCityRepository cityRepository, TopSalesmanRepository salesmanRepository) {
        this.cityRepository = cityRepository;
        this.salesmanRepository = salesmanRepository;
    }

    public TopSalesCityResponse getTopSalesByCity(String window, int limit) {
        var safeLimit = Math.max(1, Math.min(100, limit));
        var results = (window == null || "latest".equals(window))
            ? cityRepository.findLatest(safeLimit)
            : cityRepository.findByWindow(window, safeLimit);

        if (results.isEmpty()) {
            logger.info("No top sales by city data found");
            return new TopSalesCityResponse(new WindowInfo("N/A", "N/A"), List.of());
        }

        var first = results.getFirst();
        return new TopSalesCityResponse(
            new WindowInfo(
                first.windowStart().format(ISO_FORMATTER) + "Z",
                first.windowEnd().format(ISO_FORMATTER) + "Z"
            ),
            results.stream()
                .map(row -> new CityRanking(row.rank(), row.city(), row.totalSales(), row.transactionCount()))
                .toList()
        );
    }

    public TopSalesmanResponse getTopSalesmen(String window, String country, int limit) {
        var safeLimit = Math.max(1, Math.min(100, limit));
        var results = (window == null || "latest".equals(window))
            ? salesmanRepository.findLatest(country, safeLimit)
            : salesmanRepository.findByWindow(window, country, safeLimit);

        if (results.isEmpty()) {
            logger.info("No top salesman data found for country={}", country);
            return new TopSalesmanResponse(new WindowInfo("N/A", "N/A"), List.of());
        }

        var first = results.getFirst();
        return new TopSalesmanResponse(
            new WindowInfo(
                first.windowStart().format(ISO_FORMATTER) + "Z",
                first.windowEnd().format(ISO_FORMATTER) + "Z"
            ),
            results.stream()
                .map(row -> new SalesmanRanking(row.rank(), row.salesmanName(), row.country(), row.totalSales(), row.transactionCount()))
                .toList()
        );
    }
}
