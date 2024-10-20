package de.medizininformatikinitiative.torch.exceptions;

public class ConsentViolatedException extends Exception{

        public ConsentViolatedException(String errorMessage) {
            super(errorMessage);
        }

}
