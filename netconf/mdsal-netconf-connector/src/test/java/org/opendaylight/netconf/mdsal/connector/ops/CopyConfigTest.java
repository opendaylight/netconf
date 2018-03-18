/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYangResources;

import com.google.common.io.ByteSource;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
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
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.broker.impl.SerializedDOMDataBroker;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStoreFactory;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.get.GetConfig;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class CopyConfigTest {
    private static final Logger LOG = LoggerFactory.getLogger(CopyConfigTest.class);
    private static final String SESSION_ID_FOR_REPORTING = "netconf-test-session1";
    private static final String RPC_REPLY_ELEMENT = "rpc-reply";
    private static final String DATA_ELEMENT = "data";
    private static final Document RPC_REPLY_OK = getReplyOk();

    private CurrentSchemaContext currentSchemaContext;
    private TransactionProvider transactionProvider;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);

        final SchemaContext schemaContext = parseYangResources(CopyConfigTest.class,
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
            16, 16, "CommitFutures", CopyConfigTest.class);

        final SerializedDOMDataBroker sdb = new SerializedDOMDataBroker(datastores,
            MoreExecutors.listeningDecorator(listenableFutureExecutor));
        this.transactionProvider = new TransactionProvider(sdb, SESSION_ID_FOR_REPORTING);
    }

    @Test
    public void testTargetMissing() throws Exception {
        try {
            copyConfig("messages/mapping/copyConfigs/copyConfig_no_target.xml");
            fail("Should have failed - <target> element is missing");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.MISSING_ATTRIBUTE);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
    }

    @Test
    public void testSourceMissing() throws Exception {
        try {
            copyConfig("messages/mapping/copyConfigs/copyConfig_no_source.xml");
            fail("Should have fanode1iled - <source> element is missing");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.MISSING_ELEMENT);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
    }

    @Test
    public void testConfigMissing() throws Exception {
        try {
            copyConfig("messages/mapping/copyConfigs/copyConfig_no_config.xml");
            fail("Should have failed - <config> element is missing");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.MISSING_ELEMENT);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
    }

    @Test
    public void testRunning() throws Exception {
        try {
            copyConfig("messages/mapping/copyConfigs/copyConfig_running.xml");
            fail("Should have failed - copy config on running datastore is not supported");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_NOT_SUPPORTED);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
    }

    @Test
    public void testCandidateTransaction() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_top_modules.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_top_modules_control.xml"));
        assertEmptyDatastore(getConfigRunning());

        verifyResponse(discardChanges(), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());
    }

    @Test
    public void testWithCommit() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_top_modules.xml"), RPC_REPLY_OK);
        final Document expectedConfig = XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_top_modules_control.xml");
        verifyResponse(getConfigCandidate(), expectedConfig);

        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), expectedConfig);
    }

    @Test
    public void testDeleteSubtree() throws Exception {
        // Initialize datastore
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_delete_setup.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_delete_setup_control.xml"));

        // Issue second copy-config, this time without top container
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_delete.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_delete_control.xml"));
    }

    @Test
    public void testList() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_list_setup.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_list_setup_control.xml"));

        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_list_update.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_list_update_control.xml"));
    }

    @Test
    public void testOrderedList() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_ordered_list_setup.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_ordered_list_setup_control.xml"));

        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_ordered_list_update.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_ordered_list_update_control.xml"));
    }

    @Test
    public void testToplevelList() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_toplevel_list_setup.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_toplevel_list_setup_control.xml"));

        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_toplevel_list_update.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_toplevel_list_update_control.xml"));
    }

    @Test
    public void testEmptyContainer() throws Exception {
        // Check that empty non-presence container is removed.
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_empty_container.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_empty_container_control.xml"));
    }

    @Test
    public void testEmptyPresenceContainer() throws Exception {
        // Check that empty presence container is not removed.
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_empty_presence_container.xml"),
            RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_empty_presence_container_control.xml"));
    }

    @Test
    public void testAugmentations() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_top_augmentation.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_top_augmentation_control.xml"));
    }

    @Test
    public void testChoices() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_choices1.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_choices2.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_choices3.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_choices4.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_choices_control.xml"));
    }

    @SuppressWarnings("illegalCatch")
    private static Document getReplyOk() {
        Document doc;
        try {
            doc = XmlFileLoader.xmlFileToDocument("messages/mapping/rpc-reply_ok.xml");
        } catch (final Exception e) {
            LOG.debug("unable to load rpc reply ok.", e);
            doc = XmlUtil.newDocument();
        }
        return doc;
    }

    private Document commit() throws Exception {
        final Commit commit = new Commit(SESSION_ID_FOR_REPORTING, transactionProvider);
        return executeOperation(commit, "messages/mapping/commit.xml");
    }

    private Document discardChanges() throws Exception {
        final DiscardChanges discardOp = new DiscardChanges(SESSION_ID_FOR_REPORTING, transactionProvider);
        return executeOperation(discardOp, "messages/mapping/discardChanges.xml");
    }

    private Document getConfigRunning() throws Exception {
        final GetConfig getConfig = new GetConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, "messages/mapping/getConfig.xml");
    }

    private Document getConfigCandidate() throws Exception {
        final GetConfig getConfig = new GetConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, "messages/mapping/getConfig_candidate.xml");
    }

    private Document copyConfig(final String resource) throws Exception {
        final CopyConfig copyConfig = new CopyConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext,
            transactionProvider);
        return executeOperation(copyConfig, resource);
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

    private static void assertEmptyDatastore(final Document response) {
        final NodeList nodes = response.getChildNodes();
        assertTrue(nodes.getLength() == 1);

        assertEquals(nodes.item(0).getLocalName(), RPC_REPLY_ELEMENT);

        final NodeList replyNodes = nodes.item(0).getChildNodes();
        assertTrue(replyNodes.getLength() == 1);

        final Node dataNode = replyNodes.item(0);
        assertEquals(dataNode.getLocalName(), DATA_ELEMENT);
        assertFalse(dataNode.hasChildNodes());
    }

}