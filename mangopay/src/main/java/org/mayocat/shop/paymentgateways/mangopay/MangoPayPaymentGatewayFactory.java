package org.mayocat.shop.paymentgateways.mangopay;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.inject.Inject;

import org.mayocat.configuration.general.FilesSettings;
import org.mayocat.context.WebContext;
import org.mayocat.shop.payment.GatewayFactory;
import org.mayocat.shop.payment.PaymentGateway;
import org.mayocat.shop.payment.store.GatewayDataStore;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yammer.dropwizard.json.ObjectMapperFactory;

/**
 * Factory for {@link MangoPayPaymentGateway}
 *
 * @version $Id$
 */
@Component(MangoPayPaymentGatewayFactory.ID)
public class MangoPayPaymentGatewayFactory implements GatewayFactory
{
    public static final String ID = "mangopay";

    private static final String PAYMENTS_DIRECTORY = "payments";

    private static final String TENANTS_DIRECTORY = "tenants";

    @Inject
    private GatewayDataStore gatewayDataStore;

    @Inject
    private FilesSettings filesSettings;

    @Inject
    private WebContext webContext;

    @Inject
    private ObjectMapperFactory objectMapperFactory;

    @Inject
    private Logger logger;

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public PaymentGateway createGateway()
    {
        File globalConfigurationFile = filesSettings.getPermanentDirectory().resolve(PAYMENTS_DIRECTORY).resolve(ID)
                .resolve("configuration.yml").toFile();

        File tenantConfigurationFile = filesSettings.getPermanentDirectory().resolve(TENANTS_DIRECTORY)
                .resolve(webContext.getTenant().getSlug())
                .resolve(PAYMENTS_DIRECTORY).resolve(ID)
                .resolve("configuration.yml").toFile();

        ObjectMapper mapper = objectMapperFactory.build(new YAMLFactory());

        try {
            JsonNode nodeGlobal = mapper.readTree(globalConfigurationFile);
            JsonNode nodeTenant = mapper.readTree(tenantConfigurationFile);
            MangoPayGatewayConfiguration configuration =
                    mapper.readValue(new TreeTraversingParser(nodeGlobal), MangoPayGatewayConfiguration.class);

            MangoPayTenantConfiguration tenantConfiguration =
                    mapper.readValue(new TreeTraversingParser(nodeTenant), MangoPayTenantConfiguration.class);

            return new MangoPayPaymentGateway(configuration, tenantConfiguration, webContext.getTenant(),
                    gatewayDataStore);
        } catch (FileNotFoundException e) {
            logger.error("Failed to create mangopay payment gateway : configuration file not found");
            return null;
        } catch (IOException e) {
            logger.error("Failed to create mangopay payment gateway : invalid configuration file");
            return null;
        }
    }
}
