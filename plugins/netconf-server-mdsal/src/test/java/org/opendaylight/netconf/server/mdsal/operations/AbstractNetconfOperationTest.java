/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYangResources;

import com.google.common.io.CharSource;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.io.StringWriter;
import java.util.EnumMap;
import java.util.concurrent.ExecutorService;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.SerializedDOMDataBroker;
import org.opendaylight.mdsal.dom.spi.store.DOMStore;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStoreFactory;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.api.operations.NetconfOperation;
import org.opendaylight.netconf.server.api.operations.NetconfOperationChainedExecution;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.netconf.test.util.NetconfXmlUnitRecursiveQualifier;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.util.concurrent.SpecialExecutors;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public abstract class AbstractNetconfOperationTest {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractNetconfOperationTest.class);
    protected static final SessionIdType SESSION_ID_FOR_REPORTING = new SessionIdType(Uint32.valueOf(123));
    private static final String RPC_REPLY_ELEMENT = "rpc-reply";
    private static final String DATA_ELEMENT = "data";
    protected static final Document RPC_REPLY_OK = getReplyOk();

    private static EffectiveModelContext SCHEMA_CONTEXT;

    private CurrentSchemaContext currentSchemaContext;
    private TransactionProvider transactionProvider;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = parseYangResources(AbstractNetconfOperationTest.class,
            "/yang/mdsal-netconf-mapping-test.yang");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        XMLUnit.setIgnoreAttributeOrder(true);

        final DOMSchemaService schemaService = new SchemaServiceStub(SCHEMA_CONTEXT);
        final DOMStore operStore = InMemoryDOMDataStoreFactory.create("DOM-OPER", schemaService);
        final DOMStore configStore = InMemoryDOMDataStoreFactory.create("DOM-CFG", schemaService);

        currentSchemaContext = CurrentSchemaContext.create(schemaService,
            sourceIdentifier -> Futures.immediateFuture(YangTextSchemaSource.delegateForCharSource(sourceIdentifier,
                CharSource.wrap("module test"))));

        final var datastores = new EnumMap<LogicalDatastoreType, DOMStore>(LogicalDatastoreType.class);
        datastores.put(LogicalDatastoreType.CONFIGURATION, configStore);
        datastores.put(LogicalDatastoreType.OPERATIONAL, operStore);

        final ExecutorService listenableFutureExecutor = SpecialExecutors.newBlockingBoundedCachedThreadPool(
            16, 16, "CommitFutures", CopyConfigTest.class);

        final SerializedDOMDataBroker sdb = new SerializedDOMDataBroker(datastores,
            MoreExecutors.listeningDecorator(listenableFutureExecutor));
        transactionProvider = new TransactionProvider(sdb, SESSION_ID_FOR_REPORTING);
    }

    protected CurrentSchemaContext getCurrentSchemaContext() {
        return currentSchemaContext;
    }

    protected TransactionProvider getTransactionProvider() {
        return transactionProvider;
    }

    private static Document getReplyOk() {
        Document doc;
        try {
            doc = XmlFileLoader.xmlFileToDocument("messages/mapping/rpc-reply_ok.xml");
        } catch (final IOException | SAXException | ParserConfigurationException e) {
            LOG.debug("unable to load rpc reply ok.", e);
            doc = XmlUtil.newDocument();
        }
        return doc;
    }

    protected Document commit() throws Exception {
        final Commit commit = new Commit(SESSION_ID_FOR_REPORTING, transactionProvider);
        return executeOperation(commit, "messages/mapping/commit.xml");
    }

    protected Document discardChanges() throws Exception {
        final DiscardChanges discardOp = new DiscardChanges(SESSION_ID_FOR_REPORTING, transactionProvider);
        return executeOperation(discardOp, "messages/mapping/discardChanges.xml");
    }

    protected Document edit(final String resource) throws Exception {
        final EditConfig editConfig = new EditConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext,
            transactionProvider);
        return executeOperation(editConfig, resource);
    }

    protected Document edit(final Document request) throws Exception {
        final EditConfig editConfig = new EditConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext,
            transactionProvider);
        return executeOperation(editConfig, request);
    }

    protected Document get() throws Exception {
        final Get get = new Get(SESSION_ID_FOR_REPORTING, currentSchemaContext, transactionProvider);
        return executeOperation(get, "messages/mapping/get.xml");
    }

    protected Document getWithFilter(final String resource) throws Exception {
        final Get get = new Get(SESSION_ID_FOR_REPORTING, currentSchemaContext, transactionProvider);
        return executeOperation(get, resource);
    }

    protected Document getConfigRunning() throws Exception {
        final GetConfig getConfig = new GetConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, "messages/mapping/getConfig.xml");
    }

    protected Document getConfigCandidate() throws Exception {
        final GetConfig getConfig = new GetConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, "messages/mapping/getConfig_candidate.xml");
    }

    protected Document getConfigWithFilter(final String resource) throws Exception {
        final GetConfig getConfig = new GetConfig(SESSION_ID_FOR_REPORTING, currentSchemaContext, transactionProvider);
        return executeOperation(getConfig, resource);
    }

    protected static Document lock() throws Exception {
        final Lock lock = new Lock(SESSION_ID_FOR_REPORTING);
        return executeOperation(lock, "messages/mapping/lock.xml");
    }

    protected static Document unlock() throws Exception {
        final Unlock unlock = new Unlock(SESSION_ID_FOR_REPORTING);
        return executeOperation(unlock, "messages/mapping/unlock.xml");
    }

    protected static Document lockWithoutTarget() throws Exception {
        final Lock lock = new Lock(SESSION_ID_FOR_REPORTING);
        return executeOperation(lock, "messages/mapping/lock_notarget.xml");
    }

    protected static Document unlockWithoutTarget() throws Exception {
        final Unlock unlock = new Unlock(SESSION_ID_FOR_REPORTING);
        return executeOperation(unlock, "messages/mapping/unlock_notarget.xml");
    }

    protected static Document lockCandidate() throws Exception {
        final Lock lock = new Lock(SESSION_ID_FOR_REPORTING);
        return executeOperation(lock, "messages/mapping/lock_candidate.xml");
    }

    protected static Document unlockCandidate() throws Exception {
        final Unlock unlock = new Unlock(SESSION_ID_FOR_REPORTING);
        return executeOperation(unlock, "messages/mapping/unlock_candidate.xml");
    }

    protected static Document executeOperation(final NetconfOperation op, final String filename) throws Exception {
        final Document request = XmlFileLoader.xmlFileToDocument(filename);
        return executeOperation(op, request);
    }

    protected static Document executeOperation(final NetconfOperation op, final Document request) throws Exception {
        final Document response = op.handle(request, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);
        LOG.debug("Got response {}", response);
        return response;
    }

    protected static void assertEmptyDatastore(final Document response) {
        final NodeList nodes = response.getChildNodes();
        assertTrue(nodes.getLength() == 1);

        assertEquals(nodes.item(0).getLocalName(), RPC_REPLY_ELEMENT);

        final NodeList replyNodes = nodes.item(0).getChildNodes();
        assertTrue(replyNodes.getLength() == 1);

        final Node dataNode = replyNodes.item(0);
        assertEquals(dataNode.getLocalName(), DATA_ELEMENT);
        assertFalse(dataNode.hasChildNodes());
    }

    protected static void verifyResponse(final Document response, final Document template) throws Exception {
        final DetailedDiff dd = new DetailedDiff(new Diff(response, template));
        dd.overrideElementQualifier(new NetconfXmlUnitRecursiveQualifier());
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
