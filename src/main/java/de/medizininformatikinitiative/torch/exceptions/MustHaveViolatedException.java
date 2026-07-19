package de.medizininformatikinitiative.torch.exceptions;

 public abstract class MustHaveViolatedException
        extends Exception {

        public MustHaveViolatedException(String errorMessage) {
            super(errorMessage);
        }

     /**
      * Exception for when it is known which attribute reference caused the must-have violation.
      * <p>
      * Holds the attribute reference that was violated.
      */
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

         /**
          * Exception for when there is no single attribute known that causes the violation.
          * <p>
          * Can happen for example during direct loading, when all resources of a group fail the must-have evaluation.
          * After the loading step, there is no valid resource available for the AttributeGroup and this broad
          * {@link GroupViolated} exception is thrown. In this case there is no single attribute resonsible for this
          * kind of must-have violation, because each of the failed resources might have violated a different
          * attribute, which is not known afterward.
          */
         public static final class GroupViolated extends MustHaveViolatedException {
             public GroupViolated(String errorMessage) {
                 super(errorMessage);
             }
         }
}
