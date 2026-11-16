package de.medizininformatikinitiative.torch.util;

import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceUtilsTest {


    @Nested
    class GetMethodWithOneParam {

        static class MultiOverload {
            public void doSomething() {}
            public void doSomething(String s) { s.hashCode(); }
        }

        @Test
        void findsMethodByName() throws NoSuchMethodException {
            Patient patient = new Patient();
            Method method = ResourceUtils.getMethodWithOneParam(patient, "setId");
            assertThat(method.getName()).isEqualTo("setId");
            assertThat(method.getParameterCount()).isEqualTo(1);
        }

        @Test
        void skipsOverloadsWithWrongParamCount() throws NoSuchMethodException {
            Method method = ResourceUtils.getMethodWithOneParam(new MultiOverload(), "doSomething");
            assertThat(method.getParameterCount()).isEqualTo(1);
        }

        @Test
        void throwsWhenMethodNotFound() {
            assertThatThrownBy(() -> ResourceUtils.getMethodWithOneParam(new Patient(), "nonExistentMethod"))
                    .isInstanceOf(NoSuchMethodException.class);
        }
    }
}
