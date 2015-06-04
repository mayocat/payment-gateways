package org.mayocat.shop.paymentgateways.paybox;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.codec.binary.Hex;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.mayocat.shop.billing.model.Order;
import org.mayocat.shop.customer.model.Customer;
import org.mayocat.shop.payment.BasePaymentData;
import org.mayocat.shop.payment.GatewayException;
import org.mayocat.shop.payment.GatewayResponse;
import org.mayocat.shop.payment.PaymentData;
import org.mayocat.shop.payment.PaymentGateway;
import org.mayocat.shop.payment.model.PaymentOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
public class PayboxPaymentGateway implements PaymentGateway
{
    private PayboxTenantConfiguration configuration;

    private Logger logger = LoggerFactory.getLogger(PayboxPaymentGateway.class);

    public PayboxPaymentGateway(PayboxTenantConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public GatewayResponse purchase(BigDecimal amount, Map<PaymentData, Object> data) throws GatewayException {
        final Map<String, Object> paymentData = Maps.newLinkedHashMap();

        Currency currency = (Currency) data.get(BasePaymentData.CURRENCY);
        Order order = (Order) data.get(BasePaymentData.ORDER);
        Customer customer = (Customer) data.get(BasePaymentData.CUSTOMER);

        DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();

        paymentData.put("PBX_SITE", configuration.getSite());
        paymentData.put("PBX_RANG", configuration.getRang());
        paymentData.put("PBX_IDENTIFIANT", configuration.getId());
        paymentData.put("PBX_TOTAL", "1000");
        paymentData.put("PBX_TOTAL", amount.multiply(BigDecimal.valueOf(100)).longValue());
        paymentData.put("PBX_DEVISE", currency.getNumericCode());
        paymentData.put("PBX_CMD", order.getId().toString());
        paymentData.put("PBX_PORTEUR", customer.getEmail());
        paymentData.put("PBX_RETOUR", "Mt:M;Ref:R;Auto:A;Erreur:E");
        paymentData.put("PBX_HASH", "SHA512");
        paymentData.put("PBX_TIME", formatter.print(new DateTime()));

        Collection<String> params = FluentIterable.from(paymentData.keySet())
                .transform(new Function<String, String>()
                {
                    public String apply(String key) {
                        return key + "=" + paymentData.get(key);
                    }
                }).toList();

        String message = Joiner.on("&").join(params);

        this.logger.debug("PAYBOX PARAMS: {}", message);

        paymentData.put("PBX_HMAC", this.computeHMAC(message));

        PaymentOperation paymentOperation = new PaymentOperation();
        paymentOperation.setResult(PaymentOperation.Result.INITIALIZED);

        GatewayResponse response = new GatewayResponse(true, paymentOperation);
        response.setData(paymentData);
        response.setFormURL("https://preprod-tpeweb.paybox.com/cgi/MYchoix_pagepaiement.cgi");

        return response;
    }

    private String computeHMAC(String input) {
        Mac mac = null;
        SecretKeySpec secretKey = new SecretKeySpec(DatatypeConverter.parseHexBinary(this.configuration.getSecret()), "HmacSHA512");
        try {
            mac = Mac.getInstance("HmacSHA512");
            mac.init(secretKey);
            final byte[] macData = mac.doFinal(input.getBytes());
            byte[] hex = new Hex().encode(macData);
            return new String(hex, "UTF-8").toUpperCase();
        } catch (NoSuchAlgorithmException | InvalidKeyException | UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to compute HMAC", e);
        }
    }

    @Override
    public GatewayResponse acknowledge(UUID orderId, Map<String, List<String>> data) throws GatewayException {
        return null;
    }

    @Override
    public GatewayResponse acknowledge(Map<String, List<String>> data) throws GatewayException {
        return null;
    }

    @Override
    public GatewayResponse callback(Map<String, List<String>> data) throws GatewayException {
        return null;
    }
}
