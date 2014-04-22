package org.mayocat.shop.paymentgateways.monetaweb;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.joda.money.CurrencyUnit;
import org.mayocat.shop.payment.BasePaymentData;
import org.mayocat.shop.payment.GatewayException;
import org.mayocat.shop.payment.GatewayResponse;
import org.mayocat.shop.payment.PaymentData;
import org.mayocat.shop.payment.PaymentGateway;
import org.mayocat.shop.payment.api.resources.PaymentResource;
import org.mayocat.shop.payment.model.PaymentOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

/**
 * @version $Id: 5df83ccae164b9327fca9ee3bfd848117be288ee $
 */
public class MonetaWebPaymentGateway implements PaymentGateway
{
    private static final String PROD_ENVIRONMENT = "prod";

    private static final String TEST_PAYMENT_ENDPOINT = "https://test.monetaonline.it/monetaweb/hosted/init/http";

    private static final String PROD_PAYMENT_ENDPOINT = "https://www.monetaonline.it/monetaweb/hosted/init/http";

    private static final String ACTION_AUTHORIZATION = "4";

    private Logger logger = LoggerFactory.getLogger(MonetaWebPaymentGateway.class);

    private String paymentEndpoint;

    private String id;

    private String password;

    private String languageId;

    private String baseURL;

    public MonetaWebPaymentGateway(MonetaWebGatewayConfiguration configuration, String baseURL)
    {
        this.paymentEndpoint =
                configuration.getEnvironment().equalsIgnoreCase(PROD_ENVIRONMENT) ? PROD_PAYMENT_ENDPOINT :
                        TEST_PAYMENT_ENDPOINT;
        this.id = configuration.getId();
        this.password = configuration.getPassword();
        this.languageId = configuration.getLanguageId();
        this.baseURL = baseURL;

        Preconditions.checkNotNull(id);
        Preconditions.checkNotNull(password);
        Preconditions.checkNotNull(languageId);
        Preconditions.checkNotNull(baseURL);
    }

    @Override
    public GatewayResponse purchase(BigDecimal amount, Map<PaymentData, Object> options) throws GatewayException
    {
        String baseURI = (String) options.get(BasePaymentData.BASE_URL);
        String orderId = options.get(BasePaymentData.ORDER_ID).toString();

        String responseUrl =
                baseURI + PaymentResource.PATH + "/" + orderId + "/" + PaymentResource.ACKNOWLEDGEMENT_PATH + "/" +
                        MonetaWebGatewayFactory.ID;
        String errorUrl = (String) options.get(BasePaymentData.CANCEL_URL);

        Currency currency = ((Currency) options.get(BasePaymentData.CURRENCY));

        PaymentOperation operation = new PaymentOperation();
        operation.setGatewayId(MonetaWebGatewayFactory.ID);

        if (logger.isDebugEnabled()) {
            logger.debug("paymentEndpoint : " + paymentEndpoint);
            logger.debug("id : " + id);
            logger.debug("password : " + password);
            logger.debug("action : " + ACTION_AUTHORIZATION);
            logger.debug("amt : " + amount.setScale(2).toString());
            logger.debug("currencyCode : " + currency.getNumericCode());
            logger.debug("langId : " + languageId);
            logger.debug("responseUrl : " + responseUrl);
            logger.debug("errorUrl : " + errorUrl);
            logger.debug("trackId : " + orderId);
        }

        HttpPost httpPost = new HttpPost(paymentEndpoint);
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("id", id));
        nvps.add(new BasicNameValuePair("password", password));
        nvps.add(new BasicNameValuePair("action", ACTION_AUTHORIZATION));
        nvps.add(new BasicNameValuePair("amt", amount.setScale(2).toString()));
        nvps.add(new BasicNameValuePair("currencycode", String.valueOf(currency.getNumericCode())));
        nvps.add(new BasicNameValuePair("langid", languageId)); // TODO get language from option locale
        nvps.add(new BasicNameValuePair("responseurl", responseUrl));
        nvps.add(new BasicNameValuePair("errorurl", errorUrl));   // TODO
        nvps.add(new BasicNameValuePair("trackid", orderId));
        nvps.add(new BasicNameValuePair("udf1", "")); // No description for now. We will have to get it from options.
        nvps.add(new BasicNameValuePair("baseurl", baseURI));

        try {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            map.put("baseURI", baseURI);
            operation.setMemo(map);

            DefaultHttpClient httpClient = new DefaultHttpClient();
            httpPost.setEntity(new UrlEncodedFormEntity(nvps));
            HttpResponse response = httpClient.execute(httpPost);

            if (response.getStatusLine().getStatusCode() == 200) {
                operation.setResult(PaymentOperation.Result.INITIALIZED);
                GatewayResponse gatewayResponse;
                HttpEntity entity = response.getEntity();

                if (entity != null) {
                    gatewayResponse = new GatewayResponse(true, operation);
                    String stringResponse = EntityUtils.toString(entity);
                    logger.debug("response : " + stringResponse);
                    if (stringResponse.contains("!ERROR!")) {
                        throw new GatewayException("Failed to get a response entity : " + stringResponse);
                    } else {
                        String redirectURL = stringResponse.split(":")[1] + ":" + stringResponse.split(":")[2];
                        String paymentId = stringResponse.split(":")[0];

                        gatewayResponse.setRedirectURL(redirectURL + "?PaymentID=" + paymentId);
                    }

                    EntityUtils.consume(entity);
                } else {
                    throw new GatewayException("Failed to get a response entity");
                }

                return gatewayResponse;
            } else {
                return new GatewayResponse(false, operation);
            }
        } catch (UnsupportedEncodingException e) {
            throw new GatewayException(e);
        } catch (IOException e) {
            throw new GatewayException(e);
        } finally {
            httpPost.releaseConnection();
        }
    }

    public GatewayResponse acknowledge(UUID orderId, Map<String, List<String>> data) throws GatewayException
    {
        Preconditions.checkElementIndex(0, data.get("result").size());
        Preconditions.checkElementIndex(0, data.get("paymentid").size());

        String result = data.get("result").get(0).toString();
        String externalId = data.get("paymentid").get(0).toString();

        PaymentOperation operation = new PaymentOperation();
        operation.setGatewayId(MonetaWebGatewayFactory.ID);
        GatewayResponse response;

        if (result.equalsIgnoreCase("Approved")) {
            operation.setResult(PaymentOperation.Result.CAPTURED);
            operation.setExternalId(externalId);
            response = new GatewayResponse(true, operation);
            response.setResponseText(baseURL + "/checkout/return/" + orderId.toString());
        } else {
            operation.setResult(PaymentOperation.Result.FAILED);
            response = new GatewayResponse(false, operation);
            response.setResponseText(baseURL + "/checkout/error");
        }

        return response;
    }
}

