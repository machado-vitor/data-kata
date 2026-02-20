package com.datakata.api.service

import com.datakata.api.model.*
import com.datakata.api.repository.TopSalesCityRepository
import com.datakata.api.repository.TopSalesmanRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.format.DateTimeFormatter

@Service
class SalesService(
    private val cityRepository: TopSalesCityRepository,
    private val salesmanRepository: TopSalesmanRepository
) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun getTopSalesByCity(window: String?, limit: Int): TopSalesCityResponse {
        val results = if (window == null || window == "latest") {
            cityRepository.findLatest(limit)
        } else {
            cityRepository.findByWindow(window, limit)
        }

        if (results.isEmpty()) {
            logger.info("No top sales by city data found")
            return TopSalesCityResponse(
                window = WindowInfo(start = "N/A", end = "N/A"),
                rankings = emptyList()
            )
        }

        val first = results.first()
        return TopSalesCityResponse(
            window = WindowInfo(
                start = first.windowStart.format(isoFormatter) + "Z",
                end = first.windowEnd.format(isoFormatter) + "Z"
            ),
            rankings = results.map { row ->
                CityRanking(
                    rank = row.rank,
                    city = row.city,
                    totalSales = row.totalSales,
                    transactionCount = row.transactionCount
                )
            }
        )
    }

    fun getTopSalesmen(window: String?, country: String, limit: Int): TopSalesmanResponse {
        val results = if (window == null || window == "latest") {
            salesmanRepository.findLatest(country, limit)
        } else {
            salesmanRepository.findByWindow(window, country, limit)
        }

        if (results.isEmpty()) {
            logger.info("No top salesman data found for country={}", country)
            return TopSalesmanResponse(
                window = WindowInfo(start = "N/A", end = "N/A"),
                rankings = emptyList()
            )
        }

        val first = results.first()
        return TopSalesmanResponse(
            window = WindowInfo(
                start = first.windowStart.format(isoFormatter) + "Z",
                end = first.windowEnd.format(isoFormatter) + "Z"
            ),
            rankings = results.map { row ->
                SalesmanRanking(
                    rank = row.rank,
                    salesmanName = row.salesmanName,
                    country = row.country,
                    totalSales = row.totalSales,
                    transactionCount = row.transactionCount
                )
            }
        )
    }
}
