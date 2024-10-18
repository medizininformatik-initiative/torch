package de.medizininformatikinitiative.torch.config;

import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * A context to retrieve {@link DseMappingTreeBase} bean from a non-Spring-managed instance.
 */
@Component
public class SpringContext implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static DseMappingTreeBase getDseMappingTreeBase() {
        return context.getBean(DseMappingTreeBase.class);
    }
}
