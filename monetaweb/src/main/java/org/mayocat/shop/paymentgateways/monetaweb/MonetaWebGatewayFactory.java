package org.mayocat.shop.paymentgateways.monetaweb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.mayocat.accounts.model.Tenant;
import org.mayocat.configuration.SiteSettings;
import org.mayocat.configuration.general.FilesSettings;
import org.mayocat.context.WebContext;
import org.mayocat.shop.payment.GatewayFactory;
import org.mayocat.shop.payment.PaymentGateway;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yammer.dropwizard.json.ObjectMapperFactory;

/**
 * @version $Id: da42277c42e6c89a97b7d2ee2c0da91a07df1ffa $
 */
@Component(MonetaWebGatewayFactory.ID)
public class MonetaWebGatewayFactory implements GatewayFactory
{
    public static final String ID = "monetaweb";

    private static final String PAYMENTS_DIRECTORY = "payments";

    private static final String SLASH = "/";

    private static final String TENANT_CONFIGURATION_FILENAME = "configuration.yml";

    private static final String TENANTS_DIRECTORY = "tenants";

    @Inject
    private FilesSettings filesSettings;

    @Inject
    private Logger logger;

    @Inject
    private WebContext context;

    @Inject
    private ObjectMapperFactory objectMapperFactory;

    @Inject
    private SiteSettings siteSettings;

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public PaymentGateway createGateway()
    {
        Path permanentDirectory = filesSettings.getPermanentDirectory();
        File tenantConfigurationFile = permanentDirectory
                .resolve(TENANTS_DIRECTORY)
                .resolve(context.getTenant().getSlug())
                .resolve(PAYMENTS_DIRECTORY)
                .resolve(ID)
                .resolve(TENANT_CONFIGURATION_FILENAME).toFile();

        ObjectMapper mapper = objectMapperFactory.build(new YAMLFactory());


        try {
            JsonNode node = mapper.readTree(tenantConfigurationFile);
            MonetaWebGatewayConfiguration configuration =
                    mapper.readValue(new TreeTraversingParser(node), MonetaWebGatewayConfiguration.class);

            return new MonetaWebPaymentGateway(configuration, getSchemeAndDomain(context.getTenant()));
        } catch (FileNotFoundException e) {
            logger.error("Failed to create MonetaWeb Adaptive payment gateway : configuration file not found");
            return null;
        } catch (JsonProcessingException e) {
            logger.error("Failed to create MonetaWeb Adaptive payment gateway : invalid configuration file");
            return null;
        } catch (IOException e) {
            logger.error("Failed to create MonetaWeb Adaptive payment gateway : IO exception");
            return null;
        }
    }

    protected String getSchemeAndDomain(Tenant tenant)
    {
        return "http://" + getDomain(tenant);
    }

    protected String getDomain(Tenant tenant)
    {
        return StringUtils
                .defaultIfBlank(tenant.getDefaultHost(), tenant.getSlug() + "." + siteSettings.getDomainName());
    }

}
