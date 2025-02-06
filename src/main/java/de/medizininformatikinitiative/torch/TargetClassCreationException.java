package de.medizininformatikinitiative.torch;

public class TargetClassCreationException extends Exception {

    public TargetClassCreationException(Class<?> targetClass) {

        super("Could not create HAPI DomainResource class " + targetClass.getName());
    }
}
