package org.mayocat.shop.paymentgateways.paybox;

import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.Maps;
import com.google.common.io.Resources;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Collection;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import org.apache.commons.codec.binary.Base64;
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
    public static final String UTF_8 = "UTF-8";

    private PayboxTenantConfiguration configuration;

    private Logger logger = LoggerFactory.getLogger(PayboxPaymentGateway.class);

    private static final Map<String, String> RETURN_PARAMS = Maps.newLinkedHashMap();

    private static final String RETURN_PARAM_STATUS = "status";

    private static final String RETURN_PARAM_AUTHORIZATION = "authorization";

    private static final String RETURN_PARAM_TRANSACTION_ID = "transactionId";

    private static final String RETURN_PARAM_REQUEST_ID = "requestId";

    private static final String STATUS_SUCCESSFUL = "00000";

    static {
        RETURN_PARAMS.put(RETURN_PARAM_STATUS, "E");
        RETURN_PARAMS.put(RETURN_PARAM_AUTHORIZATION, "A");
        RETURN_PARAMS.put(RETURN_PARAM_TRANSACTION_ID, "S");
        RETURN_PARAMS.put(RETURN_PARAM_REQUEST_ID, "T");
    }

    public PayboxPaymentGateway(PayboxTenantConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public GatewayResponse purchase(BigDecimal amount, Map<PaymentData, Object> options) throws GatewayException {
        final Map<String, Object> paymentData = Maps.newLinkedHashMap();

        Currency currency = (Currency) options.get(BasePaymentData.CURRENCY);
        Order order = (Order) options.get(BasePaymentData.ORDER);
        Customer customer = (Customer) options.get(BasePaymentData.CUSTOMER);

        DateTimeFormatter formatter = ISODateTimeFormat.dateTimeNoMillis();

        paymentData.put("PBX_SITE", configuration.getSite());
        paymentData.put("PBX_RANG", configuration.getRang());
        paymentData.put("PBX_IDENTIFIANT", configuration.getId());
        paymentData.put("PBX_TOTAL", "1000");
        paymentData.put("PBX_TOTAL", amount.multiply(BigDecimal.valueOf(100)).longValue());
        paymentData.put("PBX_DEVISE", currency.getNumericCode());
        paymentData.put("PBX_CMD", order.getId().toString());
        paymentData.put("PBX_PORTEUR", customer.getEmail());
        paymentData.put("PBX_HASH", "SHA512");
        paymentData.put("PBX_TIME", formatter.print(new DateTime()));

        // Params
        String returnParams = Joiner.on(";").join(FluentIterable.from(RETURN_PARAMS.keySet())
                .transform(new Function<String, String>()
                {
                    public String apply(String key) {
                        return key + ":" + RETURN_PARAMS.get(key);
                    }
                }).toList());
        paymentData.put("PBX_RETOUR", returnParams + ";signature:K");

        // Success (return), cancel & IPN URL
        paymentData.put("PBX_EFFECTUE", options.get(BasePaymentData.RETURN_URL));
        paymentData.put("PBX_ANNULE", options.get(BasePaymentData.CANCEL_URL));
        paymentData.put("PBX_REPONDRE_A", options.get(BasePaymentData.IPN_URL));

        // Failed (refused) URL
        // paymentData.put("PBX_REFUSE", "");
        // "Waiting" URL
        // paymentData.put("PBX_ATTENTE", "");

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

    @Override
    public GatewayResponse acknowledge(UUID orderId, final Map<String, List<String>> data) throws GatewayException {
        this.logger.info("Acknowledge paybox payment!");

        // First, verify the message signature from paybox, to ensure its authenticity

        String message = Joiner.on("&").join(FluentIterable.from(RETURN_PARAMS.keySet())
                .transform(new Function<String, String>()
                {
                    public String apply(String key) {
                        return key + "=" + data.get(key).get(0);
                    }
                }).toList());
        boolean isVerified = this.verifySignature(message, data.get("signature").get(0));

        GatewayResponse gatewayResponse;
        PaymentOperation operation = new PaymentOperation();
        Map<String, Object> memo = Maps.newHashMap();

        if (isVerified) {
            // Request is legit.

            this.logger.info("Paybox signature verified");

            String transactionId = data.get(RETURN_PARAM_TRANSACTION_ID).get(0);
            String responseStatus = data.get(RETURN_PARAM_STATUS).get(0);

            memo.put("requestId", data.get(RETURN_PARAM_REQUEST_ID).get(0));
            memo.put("authorization", data.get(RETURN_PARAM_AUTHORIZATION).get(0));
            memo.put("status", responseStatus);

            operation.setGatewayId(PayboxGatewayFactory.ID);
            operation.setExternalId(transactionId);
            operation.setMemo(memo);

            if (responseStatus.equals(STATUS_SUCCESSFUL)) {
                operation.setResult(PaymentOperation.Result.CAPTURED);
                gatewayResponse = new GatewayResponse(true, operation);
            } else {
                operation.setResult(PaymentOperation.Result.FAILED);
                gatewayResponse = new GatewayResponse(false, operation);
            }
            return gatewayResponse;
        } else {
            // Request is not legit, it is not verified against the Paybox public key.

            this.logger.error("Paybox signature not verified");
            throw new GatewayException("Illegal Paybox IPN attempt : signature check failed");
        }
    }

    @Override
    public GatewayResponse acknowledge(Map<String, List<String>> data) throws GatewayException {
        throw new RuntimeException("Not implemented : use the version with the order ID instead");
    }

    @Override
    public GatewayResponse callback(Map<String, List<String>> data) throws GatewayException {
        throw new RuntimeException("Not supported");
    }

    // ---------------------------------------------------------------------------------------------

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

    private boolean verifySignature(String message, String signatureText) {
        final Signature signature;
        try {
            signature = Signature.getInstance("SHA1withRSA");
            signature.initVerify(getKey());
            signature.update(message.getBytes(UTF_8));

            final Base64 b64 = new Base64();
            final byte[] bytes = b64.decode(signatureText.getBytes(UTF_8));

            return signature.verify(bytes);
        } catch (Exception e) {
            this.logger.warn("Exception while verifying paybox signature", e);
            return false;
        }
    }

    private PublicKey getKey() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        String keyAsString = Resources.toString(
                Resources.getResource("org.mayocat.shop.paymentgateways.paybox/paybox.pem"), Charsets.UTF_8);
        keyAsString = keyAsString.replace("-----BEGIN PUBLIC KEY-----\n", "");
        keyAsString = keyAsString.replace("-----END PUBLIC KEY-----", "");
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.decodeBase64(keyAsString));
        return keyFactory.generatePublic(publicKeySpec);
    }
}
