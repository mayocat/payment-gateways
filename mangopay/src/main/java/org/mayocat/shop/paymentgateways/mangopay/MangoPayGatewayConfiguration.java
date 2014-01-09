package org.mayocat.shop.paymentgateways.mangopay;

import org.hibernate.validator.constraints.NotEmpty;

/**
 * Global configuration for a mangopay gateway
 *
 * @version $Id$
 */
public class MangoPayGatewayConfiguration
{
    @NotEmpty
    private String clientId;

    @NotEmpty
    private String clientPassword;

    private String environment = "sandbox";

    public String getClientId()
    {
        return clientId;
    }

    public String getClientPassword()
    {
        return clientPassword;
    }

    public String getEnvironment()
    {
        return environment;
    }
}
