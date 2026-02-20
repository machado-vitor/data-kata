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

    public CxfConfig(Bus bus) {
        this.bus = bus;
    }

    @Bean
    public Endpoint salesServiceEndpoint() {
        EndpointImpl endpoint = new EndpointImpl(bus, new SalesServiceImpl());
        endpoint.publish("/sales");
        return endpoint;
    }
}
