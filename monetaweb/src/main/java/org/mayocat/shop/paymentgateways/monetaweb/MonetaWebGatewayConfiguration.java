package org.mayocat.shop.paymentgateways.monetaweb;

import javax.validation.constraints.NotNull;

/**
 * @version $Id: 43917241c3d463d9c0971c33f3938b47a19bca73 $
 */
public class MonetaWebGatewayConfiguration
{
    @NotNull
    private String environment = "test";

    @NotNull
    private String id;

    @NotNull
    private String password;

    private String languageId = "FRA";

    public String getEnvironment()
    {
        return environment;
    }

    public String getId()
    {
        return id;
    }

    public String getPassword()
    {
        return password;
    }

    public String getLanguageId()
    {
        return languageId;
    }
}
