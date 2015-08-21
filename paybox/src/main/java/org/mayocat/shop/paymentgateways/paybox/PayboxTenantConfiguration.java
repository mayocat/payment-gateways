package org.mayocat.shop.paymentgateways.paybox;

import com.google.common.base.Optional;

/**
 * @version $Id$
 */
public class PayboxTenantConfiguration
{
    private String site;

    private String rang;

    private String id;

    private String secret;

    private Optional<String> returnUrl;

    private Optional<String> cancelUrl;

    public String getSite() {
        return site;
    }

    public String getRang() {
        return rang;
    }

    public String getId() {
        return id;
    }

    public String getSecret() {
        return secret;
    }

    public Optional<String> getReturnUrl() {
        return returnUrl;
    }

    public Optional<String> getCancelUrl() {
        return cancelUrl;
    }
}
