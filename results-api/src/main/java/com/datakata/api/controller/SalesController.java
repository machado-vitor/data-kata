package com.datakata.api.controller;

import com.datakata.api.model.TopSalesCityResponse;
import com.datakata.api.model.TopSalesmanResponse;
import com.datakata.api.service.SalesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/v1/sales")
public class SalesController {

    private final SalesService salesService;

    public SalesController(SalesService salesService) {
        this.salesService = salesService;
    }

    @GetMapping("/top-by-city")
    public Mono<TopSalesCityResponse> topByCity(
            @RequestParam(defaultValue = "latest") String window,
            @RequestParam(defaultValue = "10") int limit) {
        return Mono.fromCallable(() -> salesService.getTopSalesByCity(window, limit))
            .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/top-salesman")
    public Mono<TopSalesmanResponse> topSalesman(
            @RequestParam(defaultValue = "latest") String window,
            @RequestParam(defaultValue = "BR") String country,
            @RequestParam(defaultValue = "10") int limit) {
        return Mono.fromCallable(() -> salesService.getTopSalesmen(window, country, limit))
            .subscribeOn(Schedulers.boundedElastic());
    }
}
