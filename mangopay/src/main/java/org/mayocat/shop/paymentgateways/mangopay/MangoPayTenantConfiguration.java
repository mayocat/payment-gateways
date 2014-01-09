package org.mayocat.shop.paymentgateways.mangopay;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tenant configuration for mango pay
 *
 * @version $Id$
 */
public class MangoPayTenantConfiguration
{
    @JsonProperty
    private Beneficiary beneficiary = new Beneficiary();

    @JsonProperty
    private BankAccount bankAccount = new BankAccount();

    public Beneficiary getBeneficiary()
    {
        return beneficiary;
    }

    public BankAccount getBankAccount()
    {
        return bankAccount;
    }

    static class Beneficiary
    {
        @JsonProperty
        private String email;

        @JsonProperty
        private String firstName;

        @JsonProperty
        private String lastName;

        @JsonProperty
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd", timezone = "CET")
        private Date birthday;

        @JsonProperty
        private String nationality;

        @JsonProperty
        private String countryOfResidence;

        String getEmail()
        {
            return email;
        }

        String getFirstName()
        {
            return firstName;
        }

        String getLastName()
        {
            return lastName;
        }

        Date getBirthday()
        {
            return birthday;
        }

        String getNationality()
        {
            return nationality;
        }

        String getCountryOfResidence()
        {
            return countryOfResidence;
        }
    }

    static class BankAccount
    {
        private String ownerName;

        private String ownerAddress;

        private String IBAN;

        private String BIC;

        String getOwnerName()
        {
            return ownerName;
        }

        String getOwnerAddress()
        {
            return ownerAddress;
        }

        String getIBAN()
        {
            return IBAN;
        }

        String getBIC()
        {
            return BIC;
        }
    }
}
