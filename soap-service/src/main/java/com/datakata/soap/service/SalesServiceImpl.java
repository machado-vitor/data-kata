package com.datakata.soap.service;

import com.datakata.soap.model.Sale;
import jakarta.jws.WebService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@WebService(
        serviceName = "SalesService",
        portName = "SalesServicePort",
        targetNamespace = "http://soap.datakata.com/sales",
        endpointInterface = "com.datakata.soap.service.SalesService"
)
public class SalesServiceImpl implements SalesService {

    private static final Logger log = LoggerFactory.getLogger(SalesServiceImpl.class);

    private static final String[] CITIES = {
            "S\u00e3o Paulo", "Rio de Janeiro", "Belo Horizonte", "Curitiba",
            "Porto Alegre", "Salvador", "Bras\u00edlia", "Florian\u00f3polis",
            "Recife", "Fortaleza"
    };

    private static final String[] PRODUCTS = {
            "Laptop", "Monitor", "Keyboard", "Mouse", "Headset",
            "Webcam", "SSD", "RAM", "GPU", "Motherboard"
    };

    private static final String[] SALESMEN = {
            "Carlos Silva", "Ana Oliveira", "Pedro Santos", "Maria Souza",
            "Jo\u00e3o Costa", "Fernanda Lima", "Roberto Almeida", "Juliana Ferreira",
            "Lucas Rodrigues", "Camila Martins"
    };

    private static final String COUNTRY = "BR";

    @Override
    public List<Sale> getRecentSales(long sinceTimestamp) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int count = random.nextInt(5, 16);

        log.info("GetRecentSales called with sinceTimestamp={}, generating {} sales", sinceTimestamp, count);

        long now = System.currentTimeMillis();
        List<Sale> sales = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            String saleId = UUID.randomUUID().toString();
            String salesman = SALESMEN[random.nextInt(SALESMEN.length)];
            String city = CITIES[random.nextInt(CITIES.length)];
            String product = PRODUCTS[random.nextInt(PRODUCTS.length)];
            double amount = Math.round(random.nextDouble(50.0, 5000.0) * 100.0) / 100.0;
            long saleDate = random.nextLong(
                    Math.max(sinceTimestamp, now - 86_400_000L),
                    now + 1
            );

            Sale sale = new Sale(saleId, salesman, city, COUNTRY, amount, product, saleDate);
            sales.add(sale);

            log.debug("Generated sale: {}", sale);
        }

        log.info("Returning {} sales", sales.size());
        return sales;
    }
}
