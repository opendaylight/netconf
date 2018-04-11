/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYangResources;

import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import java.io.StringWriter;
import java.util.EnumMap;
import java.util.concurrent.ExecutorService;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.custommonkey.xmlunit.examples.RecursiveElementNameAndTextQualifier;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.cluster.databroker.ConcurrentDOMDataBroker;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStoreFactory;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.NetconfDataBroker;
import org.opendaylight.netconf.mdsal.connector.NetconfReadWriteTransaction;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.common.OperationFailedException;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class ValidateTest {
    private static final Logger LOG = LoggerFactory.getLogger(ValidateTest.class);
    private static final String SESSION_ID_FOR_REPORTING = "netconf-test-session1";
    private static final Document RPC_REPLY_OK = getReplyOk();

    @Mock
    private NetconfDataBroker dataBroker;
    @Mock
    private NetconfReadWriteTransaction validTx;
    @Mock
    private NetconfReadWriteTransaction invalidTx;

    private CurrentSchemaContext currentSchemaContext;
    private TransactionProvider transactionProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);

        final SchemaContext schemaContext = parseYangResources(ValidateTest.class,
            "/yang/mdsal-netconf-mapping-test.yang");
        final SchemaService schemaService = new SchemaServiceStub(schemaContext);
        final DOMStore operStore = InMemoryDOMDataStoreFactory.create("DOM-OPER", schemaService);
        final DOMStore configStore = InMemoryDOMDataStoreFactory.create("DOM-CFG", schemaService);

        currentSchemaContext = new CurrentSchemaContext(schemaService, sourceIdentifier -> {
            final YangTextSchemaSource yangTextSchemaSource =
                YangTextSchemaSource.delegateForByteSource(sourceIdentifier, ByteSource.wrap("module test".getBytes()));
            return Futures.immediateCheckedFuture(yangTextSchemaSource);
        });

        final EnumMap<LogicalDatastoreType, DOMStore> datastores = new EnumMap<>(LogicalDatastoreType.class);
        datastores.put(LogicalDatastoreType.CONFIGURATION, configStore);
        datastores.put(LogicalDatastoreType.OPERATIONAL, operStore);

        final ExecutorService listenableFutureExecutor = SpecialExecutors.newBlockingBoundedCachedThreadPool(
            16, 16, "CommitFutures", ValidateTest.class);

        final ConcurrentDOMDataBroker cdb = new ConcurrentDOMDataBroker(datastores, listenableFutureExecutor);
        transactionProvider = new TransactionProvider(dataBroker, SESSION_ID_FOR_REPORTING);
        doReturn(Futures.immediateCheckedFuture(null)).when(validTx).validate();
        doReturn(Futures.immediateFailedCheckedFuture(new OperationFailedException("failed", RpcResultBuilder
            .newError(RpcError.ErrorType.APPLICATION, null, "message"))))
            .when(invalidTx)
            .validate();
    }

    @Test
    public void testValidateCandidate() throws Exception {
        doReturn(validTx).when(dataBroker).newReadWriteTransaction();
        verifyResponse(validate("messages/mapping/validate/validate_candidate.xml"), RPC_REPLY_OK);
    }


    @Test
    public void testValidateCandidateFailed() throws Exception {
        doReturn(invalidTx).when(dataBroker).newReadWriteTransaction();
        try {
            verifyResponse(validate("messages/mapping/validate/validate_candidate.xml"), RPC_REPLY_OK);
            fail("Should have failed with rpc invocation not implemented yet");
        } catch (final IllegalStateException e) {
            // TODO change ex type
            assertTrue(e.getCause() instanceof OperationFailedException);
        }
    }

    @SuppressWarnings("illegalCatch")
    private static Document getReplyOk() {
        Document doc;
        try {
            doc = XmlFileLoader.xmlFileToDocument("messages/mapping/validate/validate_reply_ok.xml");
        } catch (final Exception e) {
            LOG.debug("unable to load rpc reply ok.", e);
            doc = XmlUtil.newDocument();
        }
        return doc;
    }

    private Document validate(final String resource) throws Exception {
        final Validate validate = new Validate(SESSION_ID_FOR_REPORTING, transactionProvider);
        return executeOperation(validate, resource);
    }

    private static Document executeOperation(final NetconfOperation op, final String filename) throws Exception {
        final Document request = XmlFileLoader.xmlFileToDocument(filename);
        final Document response = op.handle(request, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);

        LOG.debug("Got response {}", response);
        return response;
    }

    private static void verifyResponse(final Document response, final Document template) throws Exception {
        final DetailedDiff dd = new DetailedDiff(new Diff(response, template));
        dd.overrideElementQualifier(new RecursiveElementNameAndTextQualifier());
        if (!dd.similar()) {
            LOG.warn("Actual response:");
            printDocument(response);
            LOG.warn("Expected response:");
            printDocument(template);
            fail("Differences found: " + dd.toString());
        }
    }

    private static void printDocument(final Document doc) throws Exception {
        final TransformerFactory tf = TransformerFactory.newInstance();
        final Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        final StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc),
            new StreamResult(writer));
        LOG.warn(writer.getBuffer().toString());
    }
}