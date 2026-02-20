package com.datakata.api.controller

import com.datakata.api.model.TopSalesCityResponse
import com.datakata.api.model.TopSalesmanResponse
import com.datakata.api.service.SalesService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

@RestController
@RequestMapping("/api/v1/sales")
class SalesController(private val salesService: SalesService) {

    @GetMapping("/top-by-city")
    fun topByCity(
        @RequestParam(defaultValue = "latest") window: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): Mono<TopSalesCityResponse> {
        return Mono.fromCallable { salesService.getTopSalesByCity(window, limit) }
            .subscribeOn(Schedulers.boundedElastic())
    }

    @GetMapping("/top-salesman")
    fun topSalesman(
        @RequestParam(defaultValue = "latest") window: String,
        @RequestParam(defaultValue = "BR") country: String,
        @RequestParam(defaultValue = "10") limit: Int
    ): Mono<TopSalesmanResponse> {
        return Mono.fromCallable { salesService.getTopSalesmen(window, country, limit) }
            .subscribeOn(Schedulers.boundedElastic())
    }
}
