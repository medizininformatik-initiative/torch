package de.medizininformatikinitiative.torch.consent;

public class ConsentViolatedException extends Exception{

        public ConsentViolatedException(String errorMessage) {
            super(errorMessage);
        }

}
