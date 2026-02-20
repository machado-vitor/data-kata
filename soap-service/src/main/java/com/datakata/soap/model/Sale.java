package com.datakata.soap.model;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

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
public class Sale {

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

    public Sale(String saleId, String salesmanName, String city, String country,
                double amount, String product, long saleDate) {
        this.saleId = saleId;
        this.salesmanName = salesmanName;
        this.city = city;
        this.country = country;
        this.amount = amount;
        this.product = product;
        this.saleDate = saleDate;
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
        return "Sale{" +
                "saleId='" + saleId + '\'' +
                ", salesmanName='" + salesmanName + '\'' +
                ", city='" + city + '\'' +
                ", country='" + country + '\'' +
                ", amount=" + amount +
                ", product='" + product + '\'' +
                ", saleDate=" + saleDate +
                '}';
    }
}
