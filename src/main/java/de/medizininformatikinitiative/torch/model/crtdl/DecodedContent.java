package de.medizininformatikinitiative.torch.model.crtdl;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record DecodedContent(byte[] crtdl, List<String> patientIds) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecodedContent(byte[] otherCrtdl, List<String> otherPatientIds))) return false;
        return Arrays.equals(this.crtdl, otherCrtdl) &&
                Objects.equals(this.patientIds, otherPatientIds);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(crtdl);
        result = 31 * result + Objects.hashCode(patientIds);
        return result;
    }

    @Override
    public String toString() {
        return "DecodedContent[" +
                "crtdl=" + Arrays.toString(crtdl) +
                ", patientIds=" + patientIds +
                ']';
    }
}
