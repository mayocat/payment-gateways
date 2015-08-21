package org.mayocat.shop.paymentgateways.stripe;

import com.stripe.exception.APIConnectionException;
import com.stripe.exception.APIException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.model.Charge;
import com.stripe.model.Token;
import com.stripe.net.RequestOptions;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

        Map<String, Object> tokenParams = new HashMap<>();
        Map<String, Object> cardParams = new HashMap<>();
        cardParams.put("number", "4242424242424242");
        cardParams.put("exp_month", 8);
        cardParams.put("exp_year", 2016);
        cardParams.put("cvc", "314");
        tokenParams.put("card", cardParams);

        boolean isSuccessful = false;
        PaymentOperation op = new PaymentOperation();

        Token token = null;
        try {
            token = Token.create(tokenParams, requestOptions);
        } catch (CardException | APIException | InvalidRequestException | AuthenticationException | APIConnectionException e) {
            this.logger.error("Failed to create cart token", e);
            op.setResult(PaymentOperation.Result.FAILED);
            final StringWriter stackTraceWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stackTraceWriter));
            op.setMemo(new HashMap<String, Object>() {{
                put("exceptionClass", e.getClass().getSimpleName());
                put("exceptionMessage", e.getMessage());
                put("stackTrace", stackTraceWriter.toString());
            }});

            return new GatewayResponse(false, op);
        }  catch (Exception e) {
            throw new GatewayException(e);
        }

        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", 400);
        chargeParams.put("currency", "usd");
        chargeParams.put("source", token.getId());
        chargeParams.put("description", "Charge for test@example.com");

        try {
            Charge.create(chargeParams, requestOptions);
            isSuccessful = true;
            op.setResult(PaymentOperation.Result.CAPTURED);

        } catch (CardException e) {
            // Since it's a decline, CardException will be caught
            System.out.println("Status is: " + e.getCode());
            System.out.println("Message is: " + e.getMessage());
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

        return new GatewayResponse(isSuccessful, op);
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
