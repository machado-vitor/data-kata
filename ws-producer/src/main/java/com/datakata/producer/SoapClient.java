package com.datakata.producer;

import jakarta.annotation.PostConstruct;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.ws.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;

@Component
public class SoapClient {

    private static final Logger log = LoggerFactory.getLogger(SoapClient.class);

    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000L, 2000L, 4000L};

    @Value("${soap.wsdl-url}")
    private String wsdlUrl;

    private SalesServicePort servicePort;

    // --- JAX-WS SEI matching the SOAP service's SalesService interface ---
    @WebService(
            name = "SalesService",
            targetNamespace = "http://soap.datakata.com/sales"
    )
    public interface SalesServicePort {

        @WebMethod(operationName = "GetRecentSales")
        @WebResult(name = "sales")
        List<Sale> getRecentSales(
                @WebParam(name = "sinceTimestamp") long sinceTimestamp
        );
    }

    // --- JAXB model matching the SOAP service's Sale type ---
    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(name = "Sale", propOrder = {
            "saleId",
            "salesmanName",
            "city",
            "country",
            "amount",
            "product",
            "saleDate"
    })
    public static class Sale {

        @XmlElement(required = true)
        private String saleId;

        @XmlElement(required = true)
        private String salesmanName;

        @XmlElement(required = true)
        private String city;

        @XmlElement(required = true)
        private String country;

        @XmlElement(required = true)
        private double amount;

        @XmlElement(required = true)
        private String product;

        @XmlElement(required = true)
        private long saleDate;

        public Sale() {
        }

        public String getSaleId() {
            return saleId;
        }

        public void setSaleId(String saleId) {
            this.saleId = saleId;
        }

        public String getSalesmanName() {
            return salesmanName;
        }

        public void setSalesmanName(String salesmanName) {
            this.salesmanName = salesmanName;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        public String getProduct() {
            return product;
        }

        public void setProduct(String product) {
            this.product = product;
        }

        public long getSaleDate() {
            return saleDate;
        }

        public void setSaleDate(long saleDate) {
            this.saleDate = saleDate;
        }

        @Override
        public String toString() {
            return "Sale{saleId='" + saleId + "', salesmanName='" + salesmanName + "', city='" + city
                    + "', country='" + country + "', amount=" + amount + ", product='" + product
                    + "', saleDate=" + saleDate + "}";
        }
    }

    @PostConstruct
    public void init() {
        try {
            URL url = URI.create(wsdlUrl).toURL();
            QName serviceName = new QName("http://soap.datakata.com/sales", "SalesService");
            Service service = Service.create(url, serviceName);
            servicePort = service.getPort(SalesServicePort.class);
            log.info("SOAP client initialized with WSDL URL: {}", wsdlUrl);
        } catch (Exception e) {
            log.warn("Could not initialize SOAP client at startup (WSDL: {}). Will retry on first call.", wsdlUrl, e);
        }
    }

    /**
     * Calls the SOAP getRecentSales operation with retry logic and exponential backoff.
     */
    public List<Sale> getRecentSales(long sinceTimestamp) {
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                if (servicePort == null) {
                    log.info("SOAP client not yet initialized, attempting to connect to WSDL: {}", wsdlUrl);
                    URL url = URI.create(wsdlUrl).toURL();
                    QName serviceName = new QName("http://soap.datakata.com/sales", "SalesService");
                    Service service = Service.create(url, serviceName);
                    servicePort = service.getPort(SalesServicePort.class);
                    log.info("SOAP client initialized successfully.");
                }

                log.debug("Calling getRecentSales(sinceTimestamp={}), attempt {}/{}", sinceTimestamp, attempt + 1, MAX_RETRIES + 1);
                List<Sale> sales = servicePort.getRecentSales(sinceTimestamp);

                if (sales == null) {
                    return Collections.emptyList();
                }

                log.info("SOAP service returned {} sale(s) since timestamp {}", sales.size(), sinceTimestamp);
                return sales;

            } catch (Exception e) {
                if (attempt < MAX_RETRIES) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    log.warn("SOAP call failed (attempt {}/{}). Retrying in {} ms...", attempt + 1, MAX_RETRIES + 1, delay, e);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Retry sleep interrupted", ie);
                        return Collections.emptyList();
                    }
                    // Reset the port to force reconnection on next attempt
                    servicePort = null;
                } else {
                    log.error("SOAP call failed after {} attempts. Giving up.", MAX_RETRIES + 1, e);
                }
            }
        }
        return Collections.emptyList();
    }
}
