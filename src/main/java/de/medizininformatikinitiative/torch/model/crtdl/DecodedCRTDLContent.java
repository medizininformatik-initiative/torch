package de.medizininformatikinitiative.torch.model.crtdl;

import java.util.List;

public record DecodedCRTDLContent(Crtdl crtdl, List<String> patientIds) {
}

