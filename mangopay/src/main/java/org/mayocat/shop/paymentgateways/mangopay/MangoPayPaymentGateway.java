package org.mayocat.shop.paymentgateways.mangopay;

import java.math.BigDecimal;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.mayocat.accounts.model.Tenant;
import org.mayocat.shop.billing.model.Customer;
import org.mayocat.shop.payment.BasePaymentData;
import org.mayocat.shop.payment.GatewayException;
import org.mayocat.shop.payment.GatewayResponse;
import org.mayocat.shop.payment.PaymentData;
import org.mayocat.shop.payment.PaymentGateway;
import org.mayocat.shop.payment.model.GatewayCustomerData;
import org.mayocat.shop.payment.model.GatewayTenantData;
import org.mayocat.shop.payment.store.GatewayDataStore;

import com.google.common.base.Optional;
import com.google.common.collect.Maps;
import com.mangopay.MangoPayApi;
import com.mangopay.entities.User;
import com.mangopay.entities.UserNatural;

/**
 * Payment gateway for <a href="http://www.mangopay.com">MangoPay</a>.
 *
 * @version $Id$
 */
public class MangoPayPaymentGateway implements PaymentGateway
{
    private MangoPayTenantConfiguration tenantConfiguration;

    private MangoPayGatewayConfiguration globalConfiguration;

    private GatewayDataStore gatewayDataStore;

    private MangoPayApi api;

    private Tenant tenant;

    public MangoPayPaymentGateway(MangoPayGatewayConfiguration globalConfiguration,
            MangoPayTenantConfiguration tenantConfiguration,
            Tenant tenant,
            GatewayDataStore gatewayDataStore)
    {
        this.globalConfiguration = globalConfiguration;
        this.gatewayDataStore = gatewayDataStore;
        this.tenantConfiguration = tenantConfiguration;
        this.tenant = tenant;

        this.api = new MangoPayApi();

        this.api.Config.ClientId = globalConfiguration.getClientId();
        this.api.Config.ClientPassword = globalConfiguration.getClientPassword();

        if (globalConfiguration.getEnvironment().equalsIgnoreCase("production")) {
            this.api.Config.BaseUrl = "https://api.mangopay.com";
        }
    }

    @Override
    public GatewayResponse purchase(BigDecimal amount, Map<PaymentData, Object> data) throws GatewayException
    {
        Customer customer = (Customer) data.get(BasePaymentData.CUSTOMER);
        Optional<GatewayCustomerData> customerData = gatewayDataStore.getCustomerData(customer, "mangopay");
        Optional<GatewayTenantData> tenantData = gatewayDataStore.getTenantData(tenant, "mangopay");
        String customerUserId;
        String tenantUserId;

        if (!customerData.isPresent()) {
            customerUserId = createMangoUserFromCustomer(customer);
        } else {
            customerUserId = (String) customerData.get().getData().get("userId");
        }

        if (!tenantData.isPresent()) {
            tenantUserId = createMangoUserFromTenant();
        } else {
            tenantUserId = (String) tenantData.get().getData().get("userId");
        }

        return null;
    }

    private String createMangoUserFromTenant() throws GatewayException
    {
        String userId;

        UserNatural mangoUser = new UserNatural();
        mangoUser.Email = tenantConfiguration.getBeneficiary().getEmail();
        mangoUser.FirstName = tenantConfiguration.getBeneficiary().getFirstName();
        mangoUser.LastName = tenantConfiguration.getBeneficiary().getLastName();
        mangoUser.Birthday = tenantConfiguration.getBeneficiary().getBirthday().getTime() / 1000;
        mangoUser.Nationality = Locale.FRANCE.getCountry();
        mangoUser.CountryOfResidence = Locale.FRANCE.getCountry();

        try {
            User user = api.Users.create(mangoUser);
            userId = user.Id;

            Map<String, Object> gatewayData = Maps.newHashMap();
            gatewayData.put("userId", userId);
            GatewayTenantData newTenantData =
                    new GatewayTenantData(tenant.getId(), "mangopay", gatewayData);
            gatewayDataStore.storeTenantData(newTenantData);

            return userId;
        } catch (Exception e) {
            throw new GatewayException(e);
        }
    }

    private String createMangoUserFromCustomer(Customer customer) throws GatewayException
    {
        String userId;

        UserNatural mangoUser = new UserNatural();
        mangoUser.Email = customer.getEmail();
        mangoUser.FirstName = customer.getFirstName();
        mangoUser.LastName = customer.getLastName();

        // FIXME: actually collect the customer birth date
        GregorianCalendar birthDate = new GregorianCalendar(1984, 3 - 1, 2);

        ///mangoUser.Birthday = birthDate.getTime();
        mangoUser.Birthday = 444614400;

        // FIXME: get customer nationality & country of residence
        mangoUser.Nationality = Locale.FRANCE.getCountry();
        mangoUser.CountryOfResidence = Locale.FRANCE.getCountry();

        try {
            User user = api.Users.create(mangoUser);
            userId = user.Id;

            Map<String, Object> gatewayData = Maps.newHashMap();
            gatewayData.put("userId", userId);
            GatewayCustomerData newCustomerData =
                    new GatewayCustomerData(customer.getId(), "mangopay", gatewayData);
            gatewayDataStore.storeCustomerData(newCustomerData);

            return userId;
        } catch (Exception e) {
            throw new GatewayException(e);
        }
    }

    @Override
    public GatewayResponse acknowledge(Map<String, List<String>> data) throws GatewayException
    {
        return null;
    }
}
