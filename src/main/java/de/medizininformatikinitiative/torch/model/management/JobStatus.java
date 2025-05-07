package de.medizininformatikinitiative.torch.model.management;

import org.springframework.http.HttpStatusCode;

public record JobStatus(HttpStatusCode status, String message) {


}
