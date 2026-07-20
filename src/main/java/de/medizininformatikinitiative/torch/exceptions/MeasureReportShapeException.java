package de.medizininformatikinitiative.torch.exceptions;

public class MeasureReportShapeException extends RuntimeException {

    public MeasureReportShapeException(String measureUri) {
        super("MeasureReport for measure " + measureUri + " does not contain a populated group/population/subjectResults reference");
    }
}
