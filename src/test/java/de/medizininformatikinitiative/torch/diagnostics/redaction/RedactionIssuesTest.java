package de.medizininformatikinitiative.torch.diagnostics.redaction;

import de.medizininformatikinitiative.torch.jobhandling.failure.Issue;
import de.medizininformatikinitiative.torch.jobhandling.failure.Severity;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason.NO_SETTER;
import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason.UNMASKABLE_REQUIRED_CHILD;
import static de.medizininformatikinitiative.torch.diagnostics.redaction.RedactionViolationReason.UNRESOLVABLE_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class RedactionIssuesTest {

    private static final String ELEMENT_ID = "Observation.component.value[x]";

    private static Observation observation(int i) {
        Observation observation = new Observation();
        observation.setId("obs-" + i);
        return observation;
    }

    @Test
    void sameFailureOnManyResourcesIsOneKeyWithCountAndCappedExamples() {
        RedactionIssues issues = new RedactionIssues();

        IntStream.range(0, 10).forEach(i -> issues.record(observation(i), ELEMENT_ID, "SampledData", UNMASKABLE_REQUIRED_CHILD));

        assertThat(issues.violations()).singleElement().isEqualTo(new RedactionViolation("Observation", ELEMENT_ID,
                "SampledData", UNMASKABLE_REQUIRED_CHILD, 10, List.of("Observation/obs-0", "Observation/obs-1", "Observation/obs-2")));
    }

    @Test
    void differentReasonsAreSeparateKeys() {
        RedactionIssues issues = new RedactionIssues();

        issues.record(observation(1), ELEMENT_ID, "SampledData", UNMASKABLE_REQUIRED_CHILD);
        issues.record(observation(1), ELEMENT_ID, "SampledData", UNRESOLVABLE_TYPE);

        assertThat(issues.violations()).extracting(RedactionViolation::reason)
                .containsExactly(UNMASKABLE_REQUIRED_CHILD, UNRESOLVABLE_TYPE);
    }

    @Test
    void patientResourcesAreCountedWithoutExamples() {
        RedactionIssues issues = new RedactionIssues();
        Patient patient = new Patient();
        patient.setId("pat-1");

        issues.record(patient, "Patient.contact", "BackboneElement", NO_SETTER);

        assertThat(issues.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.count()).isEqualTo(1);
            assertThat(violation.examples()).isEmpty();
        });
    }

    @Test
    void resourceWithoutIdIsCountedWithoutExample() {
        RedactionIssues issues = new RedactionIssues();

        issues.record(new Observation(), ELEMENT_ID, "SampledData", UNMASKABLE_REQUIRED_CHILD);

        assertThat(issues.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.count()).isEqualTo(1);
            assertThat(violation.examples()).isEmpty();
        });
    }

    @Test
    void addAllSumsCountsAndKeepsCap() {
        RedactionIssues target = new RedactionIssues();
        target.record(observation(1), ELEMENT_ID, "SampledData", UNMASKABLE_REQUIRED_CHILD);
        RedactionIssues other = new RedactionIssues();
        IntStream.range(2, 6).forEach(i -> other.record(observation(i), ELEMENT_ID, "SampledData", UNMASKABLE_REQUIRED_CHILD));

        target.addAll(other);

        assertThat(target.violations()).singleElement().satisfies(violation -> {
            assertThat(violation.count()).isEqualTo(5);
            assertThat(violation.examples()).containsExactly("Observation/obs-1", "Observation/obs-2", "Observation/obs-3");
        });
    }

    @Test
    void parallelRecordingKeepsTotals() {
        RedactionIssues issues = new RedactionIssues();
        RedactionViolationReason[] reasons = RedactionViolationReason.values();

        IntStream.range(0, 30_000).parallel()
                .forEach(i -> issues.record(observation(i), ELEMENT_ID, "SampledData", reasons[i % reasons.length]));

        assertThat(issues.violations()).hasSize(reasons.length).allSatisfy(violation -> {
            assertThat(violation.count()).isEqualTo(30_000 / reasons.length);
            assertThat(violation.examples()).hasSize(RedactionViolation.MAX_EXAMPLES);
        });
    }

    @Test
    void toIssuesGivesOneWarningPerKey() {
        RedactionIssues issues = new RedactionIssues();
        issues.record(observation(1), ELEMENT_ID, "SampledData", UNMASKABLE_REQUIRED_CHILD);
        issues.record(observation(2), ELEMENT_ID, "SampledData", UNMASKABLE_REQUIRED_CHILD);

        assertThat(issues.toIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.severity()).isEqualTo(Severity.WARNING);
            assertThat(issue.msg()).isEqualTo("Redaction produced resources violating required cardinality: "
                    + "Observation.component.value[x] (SampledData) – base-required child could not be masked");
            assertThat(issue.diagnostics()).isEqualTo("count=2; reason=UNMASKABLE_REQUIRED_CHILD; examples=Observation/obs-1, Observation/obs-2");
            assertThat(issue.redactionViolation()).isNotNull();
        });
    }

    @Test
    void differentCausesAreSeparateKeys() {
        RedactionIssues issues = new RedactionIssues();
        RedactionCause code = new RedactionCause("Observation.component.code", "NoSuchType", UNRESOLVABLE_TYPE);
        RedactionCause value = new RedactionCause("Observation.component.value[x]", "NoSuchType", UNRESOLVABLE_TYPE);

        issues.record(observation(1), "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD, code);
        issues.record(observation(2), "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD, code);
        issues.record(observation(3), "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD, value);

        assertThat(issues.violations()).extracting(RedactionViolation::cause, RedactionViolation::count)
                .containsExactly(tuple(code, 2L), tuple(value, 1L));
    }

    @Test
    void issueNamesTheFailedChild() {
        RedactionIssues issues = new RedactionIssues();
        issues.record(observation(1), "Observation.component", "BackboneElement", UNMASKABLE_REQUIRED_CHILD,
                new RedactionCause("Observation.component.code", "NoSuchType", UNRESOLVABLE_TYPE));

        assertThat(issues.toIssues()).singleElement().satisfies(issue -> {
            assertThat(issue.msg()).isEqualTo("Redaction produced resources violating required cardinality: "
                    + "Observation.component (BackboneElement) – base-required child could not be masked: "
                    + "Observation.component.code (NoSuchType) – type cannot be instantiated for a masked placeholder");
            assertThat(issue.diagnostics()).isEqualTo("count=1; reason=UNMASKABLE_REQUIRED_CHILD; "
                    + "cause=UNRESOLVABLE_TYPE at Observation.component.code (NoSuchType); examples=Observation/obs-1");
        });
    }

    @Test
    void nothingRecordedGivesNoIssues() {
        assertThat(new RedactionIssues().toIssues()).isEmpty();
    }

    @Test
    void issueWithoutExamplesOmitsExamplesFromDiagnostics() {
        Issue issue = Issue.of(new RedactionViolation("Patient", "Patient.contact", "BackboneElement", NO_SETTER, 4, List.of()));

        assertThat(issue.diagnostics()).isEqualTo("count=4; reason=NO_SETTER");
    }
}
