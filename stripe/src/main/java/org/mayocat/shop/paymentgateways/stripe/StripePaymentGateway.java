package org.mayocat.shop.paymentgateways.stripe;

import com.stripe.exception.APIConnectionException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Charge;
import com.stripe.net.RequestOptions;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mayocat.shop.billing.model.Order;
import org.mayocat.shop.payment.BasePaymentData;
import org.mayocat.shop.payment.CreditCardError;
import org.mayocat.shop.payment.CreditCardPaymentData;
import org.mayocat.shop.payment.CreditCardPaymentGateway;
import org.mayocat.shop.payment.GatewayException;
import org.mayocat.shop.payment.GatewayResponse;
import org.mayocat.shop.payment.PaymentData;
import org.mayocat.shop.payment.model.PaymentOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @version $Id$
 */
public class StripePaymentGateway implements CreditCardPaymentGateway
{
    private Logger logger = LoggerFactory.getLogger(StripePaymentGateway.class);

    private StripeTenantConfiguration tenantConfiguration;

    public StripePaymentGateway(StripeTenantConfiguration tenantConfiguration) {
        this.tenantConfiguration = tenantConfiguration;
    }

    @Override
    public GatewayResponse purchase(BigDecimal amount, Map<PaymentData, Object> data) throws GatewayException {
        Order order = (Order) data.get(BasePaymentData.ORDER);

        if (!data.containsKey(CreditCardPaymentData.CARD_NUMBER)) {
            throw new GatewayException("Card information is required");
        }

        RequestOptions requestOptions = (new RequestOptions.RequestOptionsBuilder())
                .setApiKey(this.tenantConfiguration.getApiKey()).build();

        Map<String, Object> chargeMap = new HashMap<>();
        chargeMap.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue());
        chargeMap.put("currency", ((Currency) data.get(BasePaymentData.CURRENCY)).getCurrencyCode());
        Map<String, Object> cardMap = new HashMap<>();
        cardMap.put("number", data.get(CreditCardPaymentData.CARD_NUMBER));
        cardMap.put("exp_month", data.get(CreditCardPaymentData.EXPIRATION_MONTH));
        cardMap.put("exp_year", data.get(CreditCardPaymentData.EXPIRATION_YEAR));
        if (data.containsKey(CreditCardPaymentData.HOLDER_NAME)) {
            cardMap.put("name", data.get(CreditCardPaymentData.HOLDER_NAME));
        }
        if (data.containsKey(CreditCardPaymentData.VERIFICATION_CODE)) {
            cardMap.put("cvc", data.get(CreditCardPaymentData.VERIFICATION_CODE));
        }
        chargeMap.put("card", cardMap);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("order_id", order.getId().toString());
        if (order.getCustomer() != null) {
            metadata.put("customer_id", order.getCustomer().getId().toString());
            metadata.put("customer_email", order.getCustomer().getEmail());
        }
        chargeMap.put("metadata", metadata);

        PaymentOperation op = new PaymentOperation();

        try {
            final Charge charge = Charge.create(chargeMap, requestOptions);
            op.setGatewayId("stripe");
            op.setResult(PaymentOperation.Result.CAPTURED);
            op.setMemo(new HashMap<String, Object>()
            {
                {
                    put("charge", charge.toString());
                }
            });
            return new GatewayResponse(true, op);

        } catch (final CardException e) {
            op.setMemo(new HashMap<String, Object>()
            {
                {
                    put("status", e.getCode());
                    put("message", e.getMessage());
                }
            });
            op.setResult(PaymentOperation.Result.REFUSED);
            GatewayResponse response = new GatewayResponse(true, op);
            response.setData(new HashMap<String, Object>()
            {
                {
                    put("error", getCode(e.getCode()));
                }
            });
            return response;
        } catch (InvalidRequestException | AuthenticationException | APIConnectionException e) {
            this.logger.error("Failed to perform charge", e);
            op.setResult(PaymentOperation.Result.FAILED);
            final StringWriter stackTraceWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTraceWriter));
            op.setMemo(new HashMap<String, Object>() {
                {
                    put("exceptionClass", e.getClass().getSimpleName());
                    put("exceptionMessage", e.getMessage());
                    put("stackTrace", stackTraceWriter.toString());
                }
            });

            return new GatewayResponse(false, op);
        } catch (Exception e) {
            throw new GatewayException(e);
        }
    }

    private CreditCardError getCode(String code) {
        switch (code) {
            case "invalid_number":
            case "incorrect_number":
                return CreditCardError.INVALID_NUMBER;
            case "invalid_expiry_month":
            case "invalid_expiry_year":
                return CreditCardError.INVALID_EXPIRATION_DATE;
            case "invalid_cvc":
            case "incorrect_cvc":
                return CreditCardError.INVALID_VERIFICATION_CODE;
            case "expired_card":
                return CreditCardError.CARD_EXPIRED;
            case "card_declined":
                return CreditCardError.CARD_DECLINED;
            case "incorrect_zip":
            case "missing":
            case "processing_error":
            case "rate_limit":
            default:
                return CreditCardError.OTHER;
        }
    }

    @Override
    public GatewayResponse acknowledge(UUID orderId, Map<String, List<String>> data) throws GatewayException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public GatewayResponse acknowledge(Map<String, List<String>> data) throws GatewayException {
        throw new RuntimeException("Not supported");
    }

    @Override
    public GatewayResponse callback(Map<String, List<String>> data) throws GatewayException {
        throw new RuntimeException("Not supported");
    }
}
