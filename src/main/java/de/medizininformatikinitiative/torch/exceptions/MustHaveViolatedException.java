package de.medizininformatikinitiative.torch.exceptions;

import java.util.Set;

 public abstract class MustHaveViolatedException
        extends Exception {

        public MustHaveViolatedException(String errorMessage) {
            super(errorMessage);
        }

        // TODO docs
        public static final class AttributeViolated extends MustHaveViolatedException {
            private final String attributeRef;

            public AttributeViolated(String errorMessage, String attributeRef) {
                super(errorMessage);
                this.attributeRef = attributeRef;
            }

            public String getAttributeRef() {
                return attributeRef;
            }
        }

         public static final class GroupViolated extends MustHaveViolatedException {
             private final Set<String> missingGroupIds;

             public GroupViolated(String errorMessage, Set<String> missingGroupIds) {
                 super(errorMessage);
                 this.missingGroupIds = missingGroupIds;
             }

             public Set<String> getMissingGroupIds() {
                 return missingGroupIds;
             }
         }
}
