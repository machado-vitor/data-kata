#!/usr/bin/env bash
set -euo pipefail

# Trigger SOAP service data refresh by calling the SOAP endpoint
# The SOAP service generates random data on each call

SOAP_URL="${SOAP_URL:-http://localhost:8090/ws/sales}"

echo "Triggering SOAP service to generate fresh sales data..."

# SOAP request to GetRecentSales with sinceTimestamp = 0 (get all)
SOAP_REQUEST='<?xml version="1.0" encoding="UTF-8"?>
<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                  xmlns:soap="http://soap.datakata.com/">
    <soapenv:Header/>
    <soapenv:Body>
        <soap:getRecentSales>
            <sinceTimestamp>0</sinceTimestamp>
        </soap:getRecentSales>
    </soapenv:Body>
</soapenv:Envelope>'

RESPONSE=$(curl -s -w "\n%{http_code}" \
    -X POST \
    -H "Content-Type: text/xml;charset=UTF-8" \
    -H "SOAPAction: \"\"" \
    -d "$SOAP_REQUEST" \
    "$SOAP_URL" 2>/dev/null || echo "000")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" = "200" ]; then
    # Count sales in response
    SALE_COUNT=$(echo "$BODY" | grep -o '<sale>' | wc -l | tr -d ' ')
    echo "SOAP service returned $SALE_COUNT sales records."
    echo "SOAP data refresh triggered successfully."
else
    echo "Warning: SOAP service returned HTTP $HTTP_CODE (service may still be starting)"
fi
