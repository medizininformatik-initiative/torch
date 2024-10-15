package de.medizininformatikinitiative.torch.config;

import de.medizininformatikinitiative.torch.model.mapping.SystemCodeToContextMapping;
import de.numcodex.sq2cql.model.MappingTreeBase;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * A Context to retrieve {@link MappingTreeBase} and {@link SystemCodeToContextMapping} beans from a non-Spring-managed
 * instance.
 */
@Component
public class SpringContext implements ApplicationContextAware {

    private static ApplicationContext context;

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        context = applicationContext;
    }

    public static MappingTreeBase getMappingTreeBase() {
        return context.getBean(MappingTreeBase.class);
    }

    public static SystemCodeToContextMapping getCodeSystemToContextMapping() {
        return context.getBean(SystemCodeToContextMapping.class);
    }
}
