package com.datakata.soap.service;

import com.datakata.soap.model.Sale;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebResult;
import jakarta.jws.WebService;

import java.util.List;

@WebService(
        name = "SalesService",
        targetNamespace = "http://soap.datakata.com/sales"
)
public interface SalesService {

    @WebMethod(operationName = "GetRecentSales")
    @WebResult(name = "sales")
    List<Sale> getRecentSales(
            @WebParam(name = "sinceTimestamp") long sinceTimestamp
    );
}
