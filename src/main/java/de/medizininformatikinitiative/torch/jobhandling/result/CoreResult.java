package de.medizininformatikinitiative.torch.jobhandling.result;

import de.medizininformatikinitiative.torch.jobhandling.Issue;

import java.util.List;

public record CoreResult(java.util.UUID jobId, List<Issue> issues) {
}
