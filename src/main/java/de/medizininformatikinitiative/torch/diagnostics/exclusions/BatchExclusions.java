package de.medizininformatikinitiative.torch.diagnostics.exclusions;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.Objects.requireNonNull;

/**
 * Tracks exclusion events that occurred during processing of a single batch. The batch can be a core batch or a patient
 * batch.
 * <p>
 * Not a record to avoid mutations of the queue without the dedicated methods.
 */
public class BatchExclusions {

    private final Queue<PatientExclusionEvent> patientExclusions;
    private final Queue<ResourceExclusionEvent> resourceExclusions;

    public BatchExclusions(Queue<PatientExclusionEvent> patientExclusions, Queue<ResourceExclusionEvent> resourceExclusions) {
        this.patientExclusions = requireNonNull(patientExclusions);
        this.resourceExclusions = requireNonNull(resourceExclusions);
    }

    public static BatchExclusions empty() {
        return new BatchExclusions(new ConcurrentLinkedQueue<>(), new ConcurrentLinkedQueue<>());
    }

    public boolean isEmpty() {
        return patientExclusions.isEmpty() && resourceExclusions.isEmpty();
    }

    /**
     * Creates a copy of the current patient exclusions.
     *
     * @return  a list of the patient exclusion events.
     */
    public List<PatientExclusionEvent> getPatientExclusions() {
        return patientExclusions.stream().toList();
    }

    /**
     * Creates a copy of the current resource exclusions.
     *
     * @return  a list of the patient exclusion events.
     */
    public List<ResourceExclusionEvent> getResourceExclusions() {
        return resourceExclusions.stream().toList();
    }

    public void addResourceExclusion(ResourceExclusionEvent event) {
        resourceExclusions.add(event);
    }

    public void addConsentExclusion(String groupId, String resourceId, String patientId) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.CONSENT, groupId, resourceId, patientId, ""));
    }

    public void addMustHaveExclusion(String groupId, String resourceId, String attributeRef, String patientId) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE, groupId, resourceId, patientId, attributeRef));
    }

    public void addMustHaveExclusionCore(String groupId, String resourceId, String attributeRef) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.MUST_HAVE, groupId, resourceId, "", attributeRef));
    }

    public void addReferenceNotFoundExclusion(String groupId, String resourceId, String patientId) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND, groupId, resourceId, patientId, ""));
    }
    public void addReferenceNotFoundExclusionCore(String groupId, String resourceId) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_NOT_FOUND, groupId, resourceId, "", ""));
    }

    public void addResourceOutsideBatch(String groupId, String resourceId) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.RESOURCE_OUTSIDE_BATCH, groupId, resourceId, "", ""));
    }

    public void addReferenceInvalidExclusion(String groupId, String resourceId, String attributeRef, String patientId) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_INVALID, groupId, resourceId, patientId, attributeRef));
    }

    public void addReferenceInvalidExclusionCore(String groupId, String resourceId, String attributeRef) {
        resourceExclusions.add(new ResourceExclusionEvent(ResourceExclusionReason.REFERENCE_INVALID, groupId, resourceId, "", attributeRef));
    }

    public void addPatientExclusion(PatientExclusionStage stage, String patientId) {
        patientExclusions.add(new PatientExclusionEvent(stage, patientId));
    }

    public void addPatientExclusion(PatientExclusionEvent e) {
        patientExclusions.add(e);
    }

}
