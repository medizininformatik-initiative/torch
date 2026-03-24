package de.medizininformatikinitiative.torch.taskHandling;

import de.medizininformatikinitiative.torch.jobhandling.JobPriority;

import java.util.Optional;

public record TaskPatch(
        Optional<JobCommand> command,
        Optional<JobPriority> priority
) {
}
