package org.opendaylight.netconf.mdsal.connector.ops;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.netconf.mdsal.connector.ops.AbstractNetconfOperationTest.executeOperation;
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYangResources;

import com.google.common.base.Optional;
import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import java.util.Collections;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.DOMDataTransactionValidator;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.w3c.dom.Document;

public class EditConfigTestOptionTest {
    protected static final String SESSION_ID_FOR_REPORTING = "netconf-test-session";

    @Mock
    private DOMDataTransactionValidator noopValidator;
    @Mock
    private DOMDataReadWriteTransaction readWriteTx;
    @Mock
    private DOMDataBroker dataBroker;

    private CurrentSchemaContext currentSchemaContext;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        doReturn(Futures.immediateCheckedFuture(null)).when(noopValidator).validate(any());
        doReturn(Collections.singletonMap(DOMDataTransactionValidator.class, noopValidator))
            .when(dataBroker).getSupportedExtensions();
        doReturn(readWriteTx).when(dataBroker).newReadWriteTransaction();

        final SchemaContext schemaContext = parseYangResources(CopyConfigTest.class,
            "/yang/mdsal-netconf-mapping-test.yang");
        final SchemaService schemaService = new SchemaServiceStub(schemaContext);
        currentSchemaContext = new CurrentSchemaContext(schemaService, sourceIdentifier -> {
            final YangTextSchemaSource yangTextSchemaSource =
                YangTextSchemaSource.delegateForByteSource(sourceIdentifier, ByteSource.wrap("module test".getBytes()));
            return Futures.immediateCheckedFuture(yangTextSchemaSource);
        });

        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void testThenSetOptionIsNotRejected() throws Exception {

        doReturn(Futures.immediateCheckedFuture(Optional.absent())).when(readWriteTx).read(any(), any());
        doAnswer(invocation -> null).when(readWriteTx).put(any(), any(), any());
        edit("messages/mapping/validate/edit-config-test-then-set.xml");
    }

    // TODO: reject edit config with test-option specified if validate capability is not announced?
    // advantage:
    // Required accoridng to the RFC
    // disadvatage:
    // - big effort (many tests provide test-option already)
    // - ODL Netconf does not really provide a support for set,
    //   syntax validation is done when XML is parsed and then by Yangtools

    protected Document edit(final String resource) throws Exception {
        final EditConfig editConfig = new EditConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext,
            new TransactionProvider(dataBroker, SESSION_ID_FOR_REPORTING));
        return executeOperation(editConfig, resource);
    }

}
