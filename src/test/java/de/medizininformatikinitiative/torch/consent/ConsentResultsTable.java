package de.medizininformatikinitiative.torch.consent;

import de.medizininformatikinitiative.torch.model.consent.NonContinuousPeriod;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Collects the consent outcome each test actually observed and writes it as a Markdown table to
 * {@code target/consent-results/<title>.md} after each test class, so CI can post the current results on the PR.
 * <p>
 * Test display names must have the form {@code "<scenario> [<status>]: <description>"}; rows are sorted by scenario.
 * Record before asserting, so a failing test still shows what TORCH returned.
 */
final class ConsentResultsTable implements BeforeEachCallback, AfterAllCallback {

    private static final Path OUTPUT_DIR = Path.of("target", "consent-results");
    private static final Pattern DISPLAY_NAME = Pattern.compile("(\\S+) \\[(\\w+)]: (.*)");
    private static final String OBSERVATION_ID_INFIX = "-obs-";

    private final String title;
    private final Map<String, String> rows = new TreeMap<>();
    private Matcher currentTest;

    ConsentResultsTable(String title) {
        this.title = title;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        currentTest = DISPLAY_NAME.matcher(context.getDisplayName());
        if (!currentTest.matches()) {
            throw new IllegalStateException("Display name does not match '<scenario> [<status>]: <description>': "
                    + context.getDisplayName());
        }
    }

    /**
     * @param eligibleObservationIds ids of the form {@code <patient>-obs-<date>}; only the date is shown
     */
    void recordIncluded(NonContinuousPeriod collectionPeriod, List<String> eligibleObservationIds) {
        String periods = collectionPeriod.periods().stream()
                .map(period -> period.start() + "–" + period.end())
                .collect(Collectors.joining(", "));
        String eligible = eligibleObservationIds.stream()
                .map(id -> id.substring(id.indexOf(OBSERVATION_ID_INFIX) + OBSERVATION_ID_INFIX.length()))
                .sorted()
                .collect(Collectors.joining(", "));
        record("yes", periods, eligible.isEmpty() ? "–" : eligible);
    }

    void recordExcluded(String reason) {
        record("no, " + reason, "–", "–");
    }

    private void record(String included, String collectionPeriod, String eligible) {
        rows.put(currentTest.group(1), "| %s | %s | %s | %s | %s | %s |".formatted(currentTest.group(1),
                currentTest.group(3), included, collectionPeriod, eligible, currentTest.group(2)));
    }

    /**
     * Rewrites the file with all rows so far, since {@code @Nested} classes report after each nested class.
     */
    @Override
    public void afterAll(ExtensionContext context) throws IOException {
        Files.createDirectories(OUTPUT_DIR);
        Files.writeString(OUTPUT_DIR.resolve(title + ".md"), """
                ### %s

                | Scenario | Description | Included | Collection period | Eligible Observations | Status |
                |---|---|---|---|---|---|
                %s
                """.formatted(title, String.join("\n", rows.values())));
    }
}
