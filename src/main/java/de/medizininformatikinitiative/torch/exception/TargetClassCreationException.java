package de.medizininformatikinitiative.torch.exception;

public class TargetClassCreationException extends Exception {

    TargetClassCreationException(Class<?> targetClass) {

        super("Could not create HAPI DomainResource class " + targetClass.getName());
    }
}