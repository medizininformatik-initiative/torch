package de.medizininformatikinitiative.torch.util;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import de.medizininformatikinitiative.torch.model.management.CopyTreeNode;
import org.hl7.fhir.r4.model.Base;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Copying class using FHIR Path and reflective Element Set
 */
@Component
public class ElementCopier {

    private final IFhirPath fhirPathEngine;


    /**
     * Copying class using FHIR Path and reflective Element Set
     *
     * @param ctx the FHIRContext to use for creating the FhirPathEngine
     */
    public ElementCopier(FhirContext ctx) {
        this.fhirPathEngine = ctx.newFhirPath();
    }


    /**
     * Copies elements from the source resource to the target resource according to the copy tree.
     *
     * @param src      Source Resource to copy from
     * @param tgt      Target Resource to copy to
     * @param copyTree Attribute tree describing which elements to copy
     */
    public void copy(Base src, Base tgt, CopyTreeNode copyTree) throws ReflectiveOperationException {
        Map<String, List<CopyTreeNode>> childMap = copyTree.children().stream()
                .collect(Collectors.groupingBy(CopyTreeNode::fieldName));

        for (var entry : new ArrayList<>(childMap.entrySet())) {
            String fieldName = entry.getKey();
            Map<Base, Base> processed = new LinkedHashMap<>();
            for (var child : entry.getValue()) {

                // Evaluate the elements in the source resource for this subpath
                List<Base> elements = fhirPathEngine.evaluate(src, child.fhirPath(), Base.class);
                if (elements.isEmpty()) continue;


                for (Base element : elements) {
                    Base targetElement = processed.get(element);
                    // Create a new instance or extract a child target
                    if (targetElement == null) {
                        targetElement = createEmptyElement(element.getClass());
                    }
                    if (targetElement.isPrimitive() || child.children().isEmpty()) {
                        // Leaf node or primitives are copied directly
                        targetElement = element.copy();
                    } else {
                        copy(element, targetElement, child);
                    }
                    processed.put(element, targetElement);
                }
            }

            // Set the field on the target reflectively
            CopyUtils.setFieldReflectively(tgt, fieldName, processed.values().stream().toList());
        }
    }


    public <T extends Base> T createEmptyElement(Class<T> clazz) throws ReflectiveOperationException {
        try {
            Constructor<T> ctor = clazz.getDeclaredConstructor();
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new ReflectiveOperationException("Cannot create empty instance of " + clazz.getSimpleName(), e);
        }
    }

}

