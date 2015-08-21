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
public class StripePaymentGateway implements PaymentGateway
{
    private Logger logger = LoggerFactory.getLogger(StripePaymentGateway.class);

    private StripeTenantConfiguration tenantConfiguration;

    public StripePaymentGateway(StripeTenantConfiguration tenantConfiguration) {
        this.tenantConfiguration = tenantConfiguration;
    }

    @Override
    public GatewayResponse purchase(BigDecimal amount, Map<PaymentData, Object> data) throws GatewayException {

        RequestOptions requestOptions = (new RequestOptions.RequestOptionsBuilder())
                .setApiKey(this.tenantConfiguration.getApiKey()).build();

        ;

        Map<String, Object> chargeMap = new HashMap<>();
        chargeMap.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue());
        chargeMap.put("currency", ((Currency) data.get(BasePaymentData.CURRENCY)).getCurrencyCode());
        Map<String, Object> cardMap = new HashMap<>();
        cardMap.put("number", "4242424242424242");
        cardMap.put("exp_month", 12);
        cardMap.put("exp_year", 2020);
        chargeMap.put("card", cardMap);

        PaymentOperation op = new PaymentOperation();

        try {
            Charge.create(chargeMap, requestOptions);
            op.setResult(PaymentOperation.Result.CAPTURED);
            return new GatewayResponse(true, op);

        } catch (final CardException e) {
            this.logger.warn("Card has been declined", e);
            op.setMemo(new HashMap<String, Object>() {{
                put("status", e.getCode());
                put("message", e.getMessage());
            }});
            op.setResult(PaymentOperation.Result.REFUSED);
            return new GatewayResponse(false, op);
        } catch (InvalidRequestException | AuthenticationException | APIConnectionException e) {
            this.logger.error("Failed to perform charge", e);
            op.setResult(PaymentOperation.Result.FAILED);
            final StringWriter stackTraceWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTraceWriter));
            op.setMemo(new HashMap<String, Object>() {{
                put("exceptionClass", e.getClass().getSimpleName());
                put("exceptionMessage", e.getMessage());
                put("stackTrace", stackTraceWriter.toString());
            }});

            return new GatewayResponse(false, op);
        } catch (Exception e) {
            throw new GatewayException(e);
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
