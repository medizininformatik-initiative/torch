package de.medizininformatikinitiative.torch.config;

import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * A context to retrieve {@link DseMappingTreeBase} bean from a non-Spring-managed instance.
 */
@Component
public class SpringContext implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext( ApplicationContext applicationContext) {

        Objects.requireNonNull(applicationContext, "ApplicationContext cannot be null");
        context = applicationContext;
    }

    public static DseMappingTreeBase getDseMappingTreeBase() {
        return context.getBean(DseMappingTreeBase.class);
    }
}
