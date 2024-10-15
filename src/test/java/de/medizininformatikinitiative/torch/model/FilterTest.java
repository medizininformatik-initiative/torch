package de.medizininformatikinitiative.torch.model;

import de.medizininformatikinitiative.torch.config.SpringContext;
import de.medizininformatikinitiative.torch.model.mapping.SystemCodeToContextMapping;
import de.numcodex.sq2cql.model.MappingTreeBase;
import de.numcodex.sq2cql.model.common.TermCode;
import de.numcodex.sq2cql.model.structured_query.ContextualTermCode;
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
    static final String TOKEN_TYPE = "token";
    static final String NAME = "name-164612";
    static final TermCode CONTEXT_A = TermCode.of("context-a-sys", "context-a-code", "");
    static final TermCode CONTEXT_B = TermCode.of("context-b-sys", "context-b-code", "");
    static final String SYSTEM_A = "system";
    static final String SYSTEM_B = "system";
    static final String CODE_A_NO_CHILDREN = "code-no-children-a";
    static final String CODE_B_NO_CHILDREN = "code-no-children-b";
    static final String CODE_A_TWO_CHILDREN = "code-a-two-children";
    static final String CODE_A_CHILD_1 = "code-a-child-1";
    static final String CODE_A_CHILD_2 = "code-a-child-2";

    @Mock
    MappingTreeBase mappingTreeBase;
    @Mock
    SystemCodeToContextMapping systemCodeToContextMapping;
    MockedStatic<SpringContext> mockedSpringContext;

    @BeforeEach
    public void setup() {
        mockedSpringContext = Mockito.mockStatic(SpringContext.class);
        mockedSpringContext.when(SpringContext::getMappingTreeBase).thenReturn(mappingTreeBase);
        mockedSpringContext.when(SpringContext::getCodeSystemToContextMapping).thenReturn(systemCodeToContextMapping);
    }

    @AfterEach
    public void close() {
        mockedSpringContext.close();
    }

    @Test
    void testOneCodeNoChildren() {
        var contextual = ContextualTermCode.of(CONTEXT_A, TermCode.of(SYSTEM_A, CODE_A_NO_CHILDREN, ""));

        when(mappingTreeBase.expand(contextual)).thenReturn(Stream.of(contextual));
        when(systemCodeToContextMapping.getContext(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(CONTEXT_A);

        Code code = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Filter filter = new Filter(TOKEN_TYPE, NAME, List.of(code));

        var result = filter.getCodeFilter();

        assertThat(result).isEqualTo("name-164612=system%7Ccode-no-children-a");
    }

    @Test
    void testTwoCodesNoChildren() {
        var contextualA = ContextualTermCode.of(CONTEXT_A, TermCode.of(SYSTEM_A, CODE_A_NO_CHILDREN, ""));
        var contextualB = ContextualTermCode.of(CONTEXT_B, TermCode.of(SYSTEM_B, CODE_B_NO_CHILDREN, ""));

        when(mappingTreeBase.expand(contextualA)).thenReturn(Stream.of(contextualA));
        when(mappingTreeBase.expand(contextualB)).thenReturn(Stream.of(contextualB));
        when(systemCodeToContextMapping.getContext(SYSTEM_A, CODE_A_NO_CHILDREN)).thenReturn(CONTEXT_A);
        when(systemCodeToContextMapping.getContext(SYSTEM_B, CODE_B_NO_CHILDREN)).thenReturn(CONTEXT_B);

        Code codeA = new Code(SYSTEM_A, CODE_A_NO_CHILDREN);
        Code codeB = new Code(SYSTEM_B, CODE_B_NO_CHILDREN);
        Filter filter = new Filter(TOKEN_TYPE, NAME, List.of(codeA, codeB));

        var result = filter.getCodeFilter();

        assertThat(result).isEqualTo("name-164612=system%7Ccode-no-children-a,system%7Ccode-no-children-b");
    }
    @Test
    void testOneCodeTwoChildren() {
        var contextualA = ContextualTermCode.of(CONTEXT_A, TermCode.of(SYSTEM_A, CODE_A_TWO_CHILDREN, ""));
        var contextualChild1 = ContextualTermCode.of(CONTEXT_A, TermCode.of(SYSTEM_A, CODE_A_CHILD_1, ""));
        var contextualChild2 = ContextualTermCode.of(CONTEXT_A, TermCode.of(SYSTEM_A, CODE_A_CHILD_2, ""));

        when(mappingTreeBase.expand(contextualA)).thenReturn(Stream.of(contextualA, contextualChild1, contextualChild2));
        when(systemCodeToContextMapping.getContext(SYSTEM_A, CODE_A_TWO_CHILDREN)).thenReturn(CONTEXT_A);

        Code code = new Code(SYSTEM_A, CODE_A_TWO_CHILDREN);
        Filter filter = new Filter(TOKEN_TYPE, NAME, List.of(code));

        var result = filter.getCodeFilter();

        assertThat(result).isEqualTo("name-164612=system%7Ccode-a-two-children,system%7Ccode-a-child-1,system%7Ccode-a-child-2");
    }
}
