package org.mayocat.shop.paymentgateways.stripe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.google.common.base.Optional;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.inject.Inject;
import org.mayocat.shop.payment.AbstractGatewayFactory;
import org.mayocat.shop.payment.GatewayFactory;
import org.mayocat.shop.payment.PaymentGateway;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

/**
 * @version $Id$
 */
@Component(StripeGatewayFactory.ID)
public class StripeGatewayFactory  extends AbstractGatewayFactory implements GatewayFactory
{
    public static final String ID = "stripe";

    @Inject
    private ObjectMapper mapper;

    @Inject
    private Logger logger;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public PaymentGateway createGateway() {
        Optional<File> tenantConfigurationFile = getTenantConfigurationFile("configuration.yml");
        if (!tenantConfigurationFile.isPresent()) {
            logger.error("Failed to create Paybox payment gateway : no tenant configuration found");
            return null;
        }

        try {
            JsonNode node = mapper.readTree(tenantConfigurationFile.get());
            StripeTenantConfiguration configuration =
                    mapper.readValue(new TreeTraversingParser(node), StripeTenantConfiguration.class);

            return new StripePaymentGateway(configuration);

        } catch (FileNotFoundException e) {
            logger.error("Failed to create stripe payment gateway : configuration file not found");
            return null;
        } catch (JsonProcessingException e) {
            logger.error("Failed to create stripe payment gateway : invalid configuration file");
            return null;
        } catch (IOException e) {
            logger.error("Failed to create stripe payment gateway : IO exception");
            return null;
        }
    }
}
