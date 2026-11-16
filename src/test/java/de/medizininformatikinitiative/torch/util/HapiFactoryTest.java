package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.Age;
import org.hl7.fhir.r4.model.Count;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Distance;
import org.hl7.fhir.r4.model.Duration;
import org.hl7.fhir.r4.model.Money;
import org.hl7.fhir.r4.model.MoneyQuantity;
import org.hl7.fhir.r4.model.SimpleQuantity;
import org.hl7.fhir.r4.model.Type;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HapiFactoryTest {

    static Stream<Arguments> typesMissingFromHapiR4Factory() {
        return Stream.of(
                Arguments.of("Age", Age.class, "Age"),
                Arguments.of("Duration", Duration.class, "Duration"),
                Arguments.of("Count", Count.class, "Count"),
                Arguments.of("Distance", Distance.class, "Distance"),
                Arguments.of("Money", Money.class, "Money"),
                Arguments.of("SimpleQuantity", SimpleQuantity.class, "Quantity"),
                Arguments.of("MoneyQuantity", MoneyQuantity.class, "Quantity")
        );
    }

    /**
     * SimpleQuantity and MoneyQuantity are profiles on Quantity, not distinct data types, so their FHIR type is Quantity.
     */
    @ParameterizedTest
    @MethodSource("typesMissingFromHapiR4Factory")
    void createsQuantitySpecialisationsAndMoney(String name, Class<? extends Type> expectedClass, String expectedFhirType) {
        Type type = HapiFactory.create(name);

        assertThat(type).isExactlyInstanceOf(expectedClass);
        assertThat(type.fhirType()).isEqualTo(expectedFhirType);
    }

    @Test
    void returnsCanonicalNameForPrimitiveRegardlessOfCase() {
        Type type = HapiFactory.create("DateTime");

        assertThat(type).isInstanceOf(DateTimeType.class);
        assertThat(type.fhirType()).isEqualTo("dateTime");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Extension", "Narrative"})
    void resolvesExtensionAndNarrativeViaRegistry(String name) {
        assertThat(HapiFactory.create(name).fhirType()).isEqualTo(name);
    }

    @ParameterizedTest
    @CsvSource({"SampledData, origin", "UsageContext, value[x]", "Expression, language"})
    void detectsChildrenRequiredByBaseSpecification(String type, String child) {
        assertThat(HapiFactory.isRequiredChild(HapiFactory.create(type), child)).isTrue();
    }

    /**
     * HAPI R4's generated {@code listChildren()} reports min 0 for every {@link org.hl7.fhir.r4.model.Property},
     * even for children the base specification requires, so {@link HapiFactory#isRequiredChild} has to read the
     * cardinality from the runtime definitions instead.
     */
    @ParameterizedTest
    @CsvSource({"SampledData, origin", "UsageContext, value[x]", "Expression, language"})
    void propertyMinCardinalityIsUnreliableForBaseRequiredChildren(String type, String child) {
        assertThat(HapiFactory.create(type).children())
                .filteredOn(property -> property.getName().equals(child))
                .singleElement()
                .satisfies(property -> assertThat(property.getMinCardinality()).isZero());
    }

    @ParameterizedTest
    @CsvSource({"SampledData, data", "Quantity, value", "Expression, description", "Expression, noSuchChild", "string, value"})
    void childrenNotRequiredByBaseSpecification(String type, String child) {
        assertThat(HapiFactory.isRequiredChild(HapiFactory.create(type), child)).isFalse();
    }

    @Test
    void rejectsUnknownTypeName() {
        assertThatThrownBy(() -> HapiFactory.create("NoSuchType"))
                .isInstanceOf(FHIRException.class)
                .hasMessageContaining("NoSuchType");
    }

    @Test
    void rejectsNonDataTypeRegistryEntries() {
        assertThatThrownBy(() -> HapiFactory.create("xhtml")).isInstanceOf(FHIRException.class);
    }
}
