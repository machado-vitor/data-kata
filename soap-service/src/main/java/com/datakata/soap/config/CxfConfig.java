package com.datakata.soap.config;

import com.datakata.soap.service.SalesServiceImpl;
import jakarta.xml.ws.Endpoint;
import org.apache.cxf.Bus;
import org.apache.cxf.jaxws.EndpointImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CxfConfig {

    private final Bus bus;
    private final SalesServiceImpl salesService;

    public CxfConfig(Bus bus, SalesServiceImpl salesService) {
        this.bus = bus;
        this.salesService = salesService;
    }

    @Bean
    public Endpoint salesServiceEndpoint() {
        EndpointImpl endpoint = new EndpointImpl(bus, salesService);
        endpoint.publish("/sales");
        return endpoint;
    }
}
