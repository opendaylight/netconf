package org.opendaylight.restconf.utils.parser;

import java.net.URI;
import java.text.SimpleDateFormat;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.util.DataSchemaContextNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;

public class ParserFieldsParameterTest {

    private SchemaContext schemaContext;

    @Mock
    private InstanceIdentifierContext<DataSchemaNode> identifierContext;
    @Mock
    private DataSchemaNode jukeboxDataSchemaNode;

    // container jukebox
    private QName jukeboxQName;
    private ContainerNode jukeboxNode;
    // container library
    private QName librayQName;
    private DataSchemaContextNode<?> libraryNode;
    // list album
    private QName albumQName;
    private DataSchemaContextNode<?> albumNode;
    // leaf name
    private QName nameQName;
    private DataSchemaContextNode<?> nameNode;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        schemaContext = TestRestconfUtils.loadSchemaContext("/jukebox");
        final QNameModule qNameModule = QNameModule.create(
                URI.create("http://example.com/ns/example-jukebox"),
                new SimpleDateFormat("yyyy-MM-dd").parse("2015-04-04"));

        Mockito.when(identifierContext.getSchemaContext()).thenReturn(schemaContext);
        Mockito.when(jukeboxDataSchemaNode.getQName()).thenReturn(QName.create(qNameModule, "jukebox"));
        Mockito.when(identifierContext.getSchemaNode()).thenReturn(jukeboxDataSchemaNode);

        jukeboxQName = QName.create(qNameModule, "jukebox");
        librayQName = QName.create(qNameModule, "library");
        librayQName = QName.create(qNameModule, "album");
        librayQName = QName.create(qNameModule, "name");
    }

    /**
     * Test fields parameter containing only one child selected
     */
    @Test
    public void parseFieldsParameterSimplePathTest() {
        final String input = "library";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }

    /**
     * Test fields parameter containing two child nodes
     */
    @Test
    public void parseFieldsParameterDoublePathTest() {
        final String input = "library;player";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }

    /**
     * Test fields parameter containing two child nodes
     */
    @Test
    public void parseFieldsMissingStartingNodeNegativeTest() {
        final String input = "??";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }

    /**
     * Test fields parameter containing sub-children selected
     */
    @Test
    public void parseFieldsParameterSubPathTest() {
        final String input = "library/album/name";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }

    /**
     * Test fields parameter containing two child nodes
     */
    @Test
    public void parseFieldsParameterChildrenPathTest() {
        final String input = "library(album(name))";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }

    /**
     * Test fields parameter containing not expected character
     */
    @Test
    public void parseFieldsParameterNoeExpectedCharacterNegativeTest() {
        final String input = "*";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }

    /**
     * Test fields parameter with missing closing parenthesis
     */
    @Test
    public void parseFieldsParameterMissingParenthesisNegativeTest() {
        final String input = "library(";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }

    /**
     * Test fields parameter not existing child nodes
     */
    @Test
    public void parseFieldsParameterMissingChildNodeNegativeTest() {
        final String input = "library(not-existing)";
        ParserFieldsParameter.parseFieldsParameter(identifierContext, input);
    }
}