package de.medizininformatikinitiative.torch.diagnostics;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.PatientExclusionStage;
import de.medizininformatikinitiative.torch.diagnostics.exclusions.ResourceExclusionEvent;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Summarized diagnostics of all batches of a single job.
 *
 * @param numCohortPatients the amount of patients in the original cohort of the job before extraction
 * @param numFinalPatients  the amount of patients of this job after extraction
 * @param durationSummaries timing measurements for each {@link PipelineStage} summarized across all batches
 * @param patientSummaries  sum amount of patient exclusion events across all batches
 * @param resourceSummaries resource exclusion events grouped by AttributeGroup-ID
 */
public record JobDiagnosticSummary(@JsonProperty("Num-Cohort-Patients") int numCohortPatients,
                                   @JsonProperty("Num-Final-Patients") int numFinalPatients,
                                   @JsonProperty("Duration-Measurements") Map<PipelineStage, DurationSummary> durationSummaries,
                                   @JsonProperty("Patient-Exclusions") Map<PatientExclusionStage, Integer> patientSummaries,
                                   @JsonProperty("Resource-Exclusions")Map<String, GroupSummary> resourceSummaries
) {

    public static JobDiagnosticSummary empty() {
        return new JobDiagnosticSummary(0, 0, new HashMap<>(),
                new HashMap<>(), new HashMap<>()
        );
    }

    /**
     * Accumulates all batch diagnostics to create a single summary for the job.
     *
     * @param batchDiagnostics  the batch diagnostics of each batch of the job
     * @return                  the new summary
     */
    public static JobDiagnosticSummary initFromBatches(List<BatchDiagnostics> batchDiagnostics) {
        var durations = computeTimingSummary(batchDiagnostics);
        var resourcesExclusions = computeResourceSummaries(batchDiagnostics);
        var patientExclusions =  computePatientSummaries(batchDiagnostics);
        var cohortPatients = sumCohortPatients(batchDiagnostics);
        var finalPatients = sumFinalPatients(batchDiagnostics);

        return new JobDiagnosticSummary(cohortPatients, finalPatients, durations, patientExclusions, resourcesExclusions);
    }

    /**
     * Computes the average and median elapsed time for each {@link PipelineStage} across all batch diagnostics.
     *
     * @param batchDiagnostics  the batch diagnostics of each batch of the job
     * @return                  the accumulated durations per {@link PipelineStage}
     */
    private static Map<PipelineStage, DurationSummary> computeTimingSummary(List<BatchDiagnostics> batchDiagnostics) {
        Map<PipelineStage, DurationSummary> durations = new HashMap<>();
        Map<PipelineStage, List<Long>> merged = batchDiagnostics.stream()
                .flatMap(b -> b.batchDetails().nanosElapsed().entrySet().stream())
                .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

        merged.forEach((stage, nanos) -> {
            long median = getMiddle(nanos.stream().sorted().toList());
            long average = nanos.stream().reduce(0L, Long::sum)/nanos.size();
            durations.put(stage, DurationSummary.ofNanos(median, average));
        });

        return durations;
    }

    /**
     * Compute the sum of cohort patients across all batch diagnostics.
     *
     * @param diagnostics   the batch diagnostics of each batch of the job
     * @return              the sum of cohort patients
     */
    private static int sumCohortPatients(List<BatchDiagnostics> diagnostics) {
        return diagnostics.stream().reduce(0, (current, diag) -> current + diag.batchDetails().numCohortPatients(), Integer::sum);
    }

    /**
     * Compute the sum of final patients across all batch diagnostics.
     *
     * @param diagnostics   the batch diagnostics of each batch of the job
     * @return              the sum of final patients
     */
    private static int sumFinalPatients(List<BatchDiagnostics> diagnostics) {
        return diagnostics.stream().reduce(0, (current, diag) -> current + diag.batchDetails().numFinalPatients(), Integer::sum);
    }

    private static <T> T getMiddle(List<T> l) {
        return l.get(l.size()/2);
    }

    /**
     * Computes the sum of resource exclusion events for each per AttributeGroup across all batch diagnostics
     *
     * @param diagnostics   the batch diagnostics of each batch of the job
     * @return              the accumulated resource exclusions per AttributeGroup-ID
     */
    private static Map<String, GroupSummary> computeResourceSummaries(List<BatchDiagnostics> diagnostics) {
        Map<String, GroupSummary> exclusionsPerGroup = new HashMap<>();
        diagnostics.forEach(batch -> batch.batchExclusions().getResourceExclusions().forEach(event -> {

            exclusionsPerGroup.computeIfAbsent(event.groupId(), group -> GroupSummary.empty());
            GroupSummary newGroupSummary = switch(event.reason()) {
                case MUST_HAVE -> {
                    exclusionsPerGroup.get(event.groupId()).mustHaveExclusions.merge(event.attributeRef(), 1, Integer::sum);
                    yield exclusionsPerGroup.get(event.groupId());
                }
                case CONSENT -> exclusionsPerGroup.get(event.groupId()).incrementConsent();
                case REFERENCE_NOT_FOUND -> exclusionsPerGroup.get(event.groupId()).incrementRefNotFound();
                case RESOURCE_OUTSIDE_BATCH -> exclusionsPerGroup.get(event.groupId()).incrementResOutsideBatch();
            };

            exclusionsPerGroup.put(event.groupId(), newGroupSummary);
        }));

        return exclusionsPerGroup;
    }

    /**
     * Computes the sum of patient exclusion events across all batch diagnostics
     *
     * @param diagnostics   the batch diagnostics of each batch of the job
     * @return              the accumulated patient exclusions per {@link PatientExclusionStage}
     */
    private static Map<PatientExclusionStage, Integer> computePatientSummaries(List<BatchDiagnostics> diagnostics) {
        Map<PatientExclusionStage, Integer> patientExclusions = new HashMap<>();
        Arrays.stream(PatientExclusionStage.values()).forEach(stage -> patientExclusions.put(stage, 0));
        diagnostics.forEach(d -> d.batchExclusions().getPatientExclusions().forEach(event -> {
            patientExclusions.computeIfPresent(event.stage(), (stage, current) -> current+1);
        }));

        return patientExclusions;
    }


    /**
     * Summary of {@link ResourceExclusionEvent}s of an AttributeGroup across multiple batches.
     *
     * @param mustHaveExclusions        the sum of must-have exclusions per attribute reference
     * @param consentExclusions         the sum of consent exclusions
     * @param refNotFoundExclusions     the sum of reference-not-found exclusions
     * @param resOutsideBatchExclusions the sum of resource-outside-batch exclusions
     */
    public record GroupSummary(@JsonProperty("Must-Have") Map<String, Integer> mustHaveExclusions,
                                @JsonProperty("Consent") int consentExclusions,
                                @JsonProperty("Reference-Not-Found") int refNotFoundExclusions,
                                @JsonProperty("Resource-Outside-Batch") int resOutsideBatchExclusions) {

        public static GroupSummary empty() {
            return new GroupSummary(new HashMap<>(), 0, 0, 0);
        }

        public GroupSummary incrementConsent() {
            return new GroupSummary(mustHaveExclusions, consentExclusions+1, refNotFoundExclusions, resOutsideBatchExclusions);
        }
        public GroupSummary incrementRefNotFound() {
            return new GroupSummary(mustHaveExclusions, consentExclusions, refNotFoundExclusions+1, resOutsideBatchExclusions);
        }
        public GroupSummary incrementResOutsideBatch() {
            return new GroupSummary(mustHaveExclusions, consentExclusions, refNotFoundExclusions, resOutsideBatchExclusions+1);
        }
    }

    /**
     * Holds accumulated durations across multiple batches.
     *
     * @param medianMs   an average amount of milliseconds across multiple batches
     * @param averageMs  a median amount of milliseconds across multiple batches
     */
    public record DurationSummary(@JsonProperty Long medianMs, @JsonProperty Long averageMs) {

        static DurationSummary ofNanos(Long medianNanos, Long averageNanos) {
            return new DurationSummary(TimeUnit.NANOSECONDS.toMillis(medianNanos), TimeUnit.NANOSECONDS.toMillis(averageNanos));
        }
    }

}
