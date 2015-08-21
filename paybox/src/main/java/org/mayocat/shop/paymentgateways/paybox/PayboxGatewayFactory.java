package org.mayocat.shop.paymentgateways.paybox;

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
@Component(PayboxGatewayFactory.ID)
public class PayboxGatewayFactory extends AbstractGatewayFactory implements GatewayFactory
{
    public static final String ID = "paybox";

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
            PayboxTenantConfiguration configuration =
                    mapper.readValue(new TreeTraversingParser(node), PayboxTenantConfiguration.class);

            return new PayboxPaymentGateway(configuration);

        } catch (FileNotFoundException e) {
            logger.error("Failed to create Paybox payment gateway : configuration file not found");
            return null;
        } catch (JsonProcessingException e) {
            logger.error("Failed to create Paybox payment gateway : invalid configuration file");
            return null;
        } catch (IOException e) {
            logger.error("Failed to create Paybox payment gateway : IO exception");
            return null;
        }
    }
}
