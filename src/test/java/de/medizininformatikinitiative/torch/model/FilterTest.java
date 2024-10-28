package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.config.SpringContext;
import de.medizininformatikinitiative.torch.model.mapping.DseMappingTreeBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FilterTest {
    static final String FILTER_TYPE_TOKEN = "token";
    static final String NAME = "name-164612";
    static final String SYSTEM_A = "system-a";
    static final String SYSTEM_B = "system-b";
    static final String CODE_A_NO_CHILDREN = "code-no-children-a";
    static final String CODE_B_NO_CHILDREN = "code-no-children-b";
    static final String CODE_A_TWO_CHILDREN = "code-a-two-children";
    static final String CODE_A_CHILD_1 = "code-a-child-1";
    static final String CODE_A_CHILD_2 = "code-a-child-2";

    @Mock
    DseMappingTreeBase mappingTreeBase;
    MockedStatic<SpringContext> mockedSpringContext;

    @BeforeEach
    public void setup() {
        mockedSpringContext = Mockito.mockStatic(SpringContext.class);
        mockedSpringContext.when(SpringContext::getDseMappingTreeBase).thenReturn(mappingTreeBase);
    }

    @AfterEach
    public void close() {
        mockedSpringContext.close();
    }

    @Test
    void testOneCodeNoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(Stream.of(CODE_A_NO_CHILDREN));

        Code code = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(code),null,null);

        var result = filter.getCodeFilter();

        assertThat(result).isEqualTo("name-164612=system-a%7Ccode-no-children-a");
    }

    @Test
    void testTwoCodesNoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(Stream.of(CODE_A_NO_CHILDREN));
        when(mappingTreeBase.expand(SYSTEM_B, CODE_B_NO_CHILDREN)).thenReturn(Stream.of(CODE_B_NO_CHILDREN));

        Code codeA = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Code codeB = new Code(SYSTEM_B, CODE_B_NO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(codeA, codeB),null,null);

        var result = filter.getCodeFilter();

        assertThat(result).isEqualTo("name-164612=system-a%7Ccode-no-children-a,system-b%7Ccode-no-children-b");
    }
    @Test
    void testOneCodeTwoChildren() {
        when(mappingTreeBase.expand(SYSTEM_A, CODE_A_TWO_CHILDREN)).thenReturn(Stream.of(CODE_A_TWO_CHILDREN, CODE_A_CHILD_1, CODE_A_CHILD_2));

        Code code = new Code(SYSTEM_A, CODE_A_TWO_CHILDREN);
        Filter filter = new Filter(FILTER_TYPE_TOKEN, NAME, List.of(code),null,null);

        var result = filter.getCodeFilter();

        assertThat(result).isEqualTo("name-164612=system-a%7Ccode-a-two-children,system-a%7Ccode-a-child-1,system-a%7Ccode-a-child-2");
    }
}
