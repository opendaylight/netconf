/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.confignetconfconnector;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.controller.config.util.xml.XmlUtil.readXmlToElement;
import static org.opendaylight.netconf.util.test.XmlUnitUtil.assertContainsElement;
import static org.opendaylight.netconf.util.test.XmlUnitUtil.assertContainsElementWithText;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.netty.channel.Channel;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.ObjectName;
import javax.xml.parsers.ParserConfigurationException;
import org.custommonkey.xmlunit.AbstractNodeTester;
import org.custommonkey.xmlunit.NodeTest;
import org.custommonkey.xmlunit.NodeTestException;
import org.custommonkey.xmlunit.NodeTester;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.config.api.ConflictingVersionException;
import org.opendaylight.controller.config.api.ValidationException;
import org.opendaylight.controller.config.api.annotations.AbstractServiceInterface;
import org.opendaylight.controller.config.api.annotations.ServiceInterfaceAnnotation;
import org.opendaylight.controller.config.facade.xml.ConfigSubsystemFacade;
import org.opendaylight.controller.config.facade.xml.osgi.EnumResolver;
import org.opendaylight.controller.config.facade.xml.osgi.YangStoreService;
import org.opendaylight.controller.config.facade.xml.transactions.TransactionProvider;
import org.opendaylight.controller.config.manager.impl.AbstractConfigTest;
import org.opendaylight.controller.config.manager.impl.factoriesresolver.HardcodedModuleFactoriesResolver;
import org.opendaylight.controller.config.util.ConfigTransactionJMXClient;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.XmlMappingConstants;
import org.opendaylight.controller.config.util.xml.XmlUtil;
import org.opendaylight.controller.config.yang.test.impl.ComplexDtoBInner;
import org.opendaylight.controller.config.yang.test.impl.ComplexList;
import org.opendaylight.controller.config.yang.test.impl.Deep;
import org.opendaylight.controller.config.yang.test.impl.DepTestImplModuleFactory;
import org.opendaylight.controller.config.yang.test.impl.DtoAInner;
import org.opendaylight.controller.config.yang.test.impl.DtoAInnerInner;
import org.opendaylight.controller.config.yang.test.impl.DtoC;
import org.opendaylight.controller.config.yang.test.impl.DtoD;
import org.opendaylight.controller.config.yang.test.impl.IdentityTestModuleFactory;
import org.opendaylight.controller.config.yang.test.impl.NetconfTestImplModuleFactory;
import org.opendaylight.controller.config.yang.test.impl.NetconfTestImplModuleMXBean;
import org.opendaylight.controller.config.yang.test.impl.Peers;
import org.opendaylight.controller.config.yang.test.impl.TestImplModuleFactory;
import org.opendaylight.controller.config.yangjmxgenerator.ModuleMXBeanEntry;
import org.opendaylight.mdsal.binding.generator.util.BindingRuntimeContext;
import org.opendaylight.netconf.api.messages.NetconfHelloMessageAdditionalHeader;
import org.opendaylight.netconf.confignetconfconnector.operations.Commit;
import org.opendaylight.netconf.confignetconfconnector.operations.DiscardChanges;
import org.opendaylight.netconf.confignetconfconnector.operations.Lock;
import org.opendaylight.netconf.confignetconfconnector.operations.UnLock;
import org.opendaylight.netconf.confignetconfconnector.operations.editconfig.EditConfig;
import org.opendaylight.netconf.confignetconfconnector.operations.get.Get;
import org.opendaylight.netconf.confignetconfconnector.operations.getconfig.GetConfig;
import org.opendaylight.netconf.confignetconfconnector.operations.runtimerpc.RuntimeRpc;
import org.opendaylight.netconf.impl.NetconfServerSession;
import org.opendaylight.netconf.impl.NetconfServerSessionListener;
import org.opendaylight.netconf.impl.mapping.operations.DefaultCloseSession;
import org.opendaylight.netconf.impl.osgi.AggregatedNetconfOperationServiceFactory;
import org.opendaylight.netconf.impl.osgi.NetconfOperationRouter;
import org.opendaylight.netconf.mapping.api.HandlingPriority;
import org.opendaylight.netconf.mapping.api.NetconfOperation;
import org.opendaylight.netconf.mapping.api.NetconfOperationChainedExecution;
import org.opendaylight.netconf.util.messages.NetconfMessageUtil;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.test.types.rev131127.TestIdentity1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.config.test.types.rev131127.TestIdentity2;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaContextProvider;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceProvider;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.osgi.framework.Filter;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.w3c.dom.traversal.DocumentTraversal;
import org.xml.sax.SAXException;


public class NetconfMappingTest extends AbstractConfigTest {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfMappingTest.class);

    private static final String INSTANCE_NAME = "instance-from-code";
    private static final String NETCONF_SESSION_ID = "foo";
    private static final String TEST_NAMESPACE = "urn:opendaylight:params:xml:ns:yang:controller:test:impl";
    private NetconfTestImplModuleFactory factory;
    private DepTestImplModuleFactory factory2;
    private IdentityTestModuleFactory factory3;
    private TestImplModuleFactory factory4;

    @Mock
    YangStoreService yangStoreSnapshot;
    @Mock
    NetconfOperationRouter netconfOperationRouter;
    @Mock
    AggregatedNetconfOperationServiceFactory netconfOperationServiceSnapshot;
    @Mock
    private AutoCloseable sessionCloseable;

    private TransactionProvider transactionProvider;

    private ConfigSubsystemFacade configSubsystemFacade;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);


        final Filter filter = mock(Filter.class);
        doReturn(filter).when(mockedContext).createFilter(anyString());
        doNothing().when(mockedContext).addServiceListener(any(ServiceListener.class), anyString());
        doReturn(new ServiceReference<?>[]{}).when(mockedContext).getServiceReferences(anyString(), anyString());

        doReturn(yangStoreSnapshot).when(yangStoreSnapshot).getCurrentSnapshot();
        doReturn(getMbes()).when(this.yangStoreSnapshot).getModuleMXBeanEntryMap();
        doReturn(getModules()).when(this.yangStoreSnapshot).getModules();
        doReturn(new EnumResolver() {
            @Override
            public String fromYang(final String enumType, final String enumYangValue) {
                return Preconditions.checkNotNull(getEnumMapping().get(enumYangValue),
                        "Unable to resolve enum value %s, for enum %s with mappings %s",
                        enumYangValue, enumType, getEnumMapping());
            }

            @Override
            public String toYang(final String enumType, final String enumYangValue) {
                return Preconditions.checkNotNull(getEnumMapping().inverse().get(enumYangValue),
                        "Unable to resolve enum value %s, for enum %s with mappings %s",
                        enumYangValue, enumType, getEnumMapping().inverse());
            }
        }).when(this.yangStoreSnapshot).getEnumResolver();

        this.factory = new NetconfTestImplModuleFactory();
        this.factory2 = new DepTestImplModuleFactory();
        this.factory3 = new IdentityTestModuleFactory();
        factory4 = new TestImplModuleFactory();
        doNothing().when(sessionCloseable).close();

        super.initConfigTransactionManagerImpl(new HardcodedModuleFactoriesResolver(mockedContext, this.factory,
                this.factory2, this.factory3, factory4));

        transactionProvider = new TransactionProvider(this.configRegistryClient, NETCONF_SESSION_ID);

        configSubsystemFacade = new ConfigSubsystemFacade(configRegistryClient, configRegistryClient, yangStoreSnapshot,
                "mapping-test");
    }

    private ObjectName createModule(final String instanceName) throws InstanceAlreadyExistsException,
            InstanceNotFoundException, URISyntaxException, ValidationException, ConflictingVersionException {
        final ConfigTransactionJMXClient transaction = this.configRegistryClient.createTransaction();

        final ObjectName on = transaction.createModule(this.factory.getImplementationName(), instanceName);
        final NetconfTestImplModuleMXBean mxBean = transaction.newMXBeanProxy(on, NetconfTestImplModuleMXBean.class);
        setModule(mxBean, transaction, instanceName + "_dep");

        int index = 1;
        for (final Class<? extends AbstractServiceInterface> serviceInterface :
                factory.getImplementedServiceIntefaces()) {
            final ServiceInterfaceAnnotation annotation =
                    serviceInterface.getAnnotation(ServiceInterfaceAnnotation.class);
            transaction.saveServiceReference(
                    transaction.getServiceInterfaceName(annotation.namespace(), annotation.localName()),
                    "ref_from_code_to_" + instanceName + "_" + index++, on);

        }
        transaction.commit();
        return on;
    }

    @Test
    public void testIdentityRefs() throws Exception {
        edit("netconfMessages/editConfig_identities.xml");

        commit();
        Document configRunning = getConfigRunning();
        String asString = XmlUtil.toString(configRunning);
        assertThat(asString, containsString("test-identity2"));
        assertThat(asString, containsString("test-identity1"));
        assertEquals(2, countSubstringOccurence(asString, "</identities>"));

        edit("netconfMessages/editConfig_identities_inner_replace.xml");
        commit();
        configRunning = getConfigRunning();
        asString = XmlUtil.toString(configRunning);
        // test-identity1 was removed by replace
        assertThat(asString, not(containsString("test-identity2")));
        // now only 1 identities entry is present
        assertEquals(1, countSubstringOccurence(asString, "</identities>"));
    }

    private static int countSubstringOccurence(final String string, final String substring) {
        final Matcher matches = Pattern.compile(substring).matcher(string);
        int count = 0;
        while (matches.find()) {
            count++;
        }
        return count;
    }

    @Override
    protected BindingRuntimeContext getBindingRuntimeContext() {
        final BindingRuntimeContext ret = super.getBindingRuntimeContext();
        doReturn(TestIdentity1.class).when(ret).getIdentityClass(TestIdentity1.QNAME);
        doReturn(TestIdentity2.class).when(ret).getIdentityClass(TestIdentity2.QNAME);
        final List<InputStream> streams = getYangs();
        try {
            doReturn(YangParserTestUtils.parseYangStreams(streams)).when(ret).getSchemaContext();
        } catch (final ReactorException e) {
            throw new RuntimeException("Unable to build schema context from " + streams, e);
        }
        return ret;
    }

    @Test
    public void testServicePersistance() throws Exception {
        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        Document config = getConfigCandidate();
        assertCorrectServiceNames(config, Sets.newHashSet("user_to_instance_from_code", "ref_dep_user",
                "ref_dep_user_two", "ref_from_code_to_instance-from-code_dep_1",
                "ref_from_code_to_instance-from-code_1"));


        edit("netconfMessages/editConfig_addServiceName.xml");
        config = getConfigCandidate();
        assertCorrectServiceNames(config, Sets.newHashSet("user_to_instance_from_code", "ref_dep_user",
                "ref_dep_user_two", "ref_from_code_to_instance-from-code_dep_1",
                "ref_from_code_to_instance-from-code_1", "ref_dep_user_another"));

        edit("netconfMessages/editConfig_addServiceNameOnTest.xml");
        config = getConfigCandidate();
        assertCorrectServiceNames(config, Sets.newHashSet("user_to_instance_from_code", "ref_dep_user",
                "ref_dep_user_two", "ref_from_code_to_instance-from-code_dep_1",
                "ref_from_code_to_instance-from-code_1", "ref_dep_user_another"));

        commit();
        config = getConfigRunning();
        assertCorrectRefNamesForDependencies(config);
        assertCorrectServiceNames(config, Sets.newHashSet("user_to_instance_from_code", "ref_dep_user",
                "ref_dep_user_two", "ref_from_code_to_instance-from-code_dep_1",
                "ref_from_code_to_instance-from-code_1", "ref_dep_user_another"));

        edit("netconfMessages/editConfig_removeServiceNameOnTest.xml");
        config = getConfigCandidate();
        assertCorrectServiceNames(config, Sets.newHashSet("user_to_instance_from_code", "ref_dep_user",
                "ref_dep_user_two", "ref_from_code_to_instance-from-code_dep_1",
                "ref_from_code_to_instance-from-code_1"));

        try {
            edit("netconfMessages/editConfig_removeServiceNameOnTest.xml");
            fail("Should've failed, non-existing service instance");
        } catch (final DocumentedException e) {
            assertEquals(e.getErrorSeverity(), DocumentedException.ErrorSeverity.ERROR);
            assertEquals(e.getErrorTag(), DocumentedException.ErrorTag.OPERATION_FAILED);
            assertEquals(e.getErrorType(), DocumentedException.ErrorType.APPLICATION);
        }

        edit("netconfMessages/editConfig_replace_default.xml");
        config = getConfigCandidate();
        assertCorrectServiceNames(config, Collections.<String>emptySet());

        edit("netconfMessages/editConfig_remove.xml");
        config = getConfigCandidate();
        assertCorrectServiceNames(config, Collections.<String>emptySet());

        commit();
        config = getConfigCandidate();
        assertCorrectServiceNames(config, Collections.<String>emptySet());

    }

    @Test
    public void testUnLock() throws Exception {
        assertTrue(NetconfMessageUtil.isOKMessage(lockCandidate()));
        assertTrue(NetconfMessageUtil.isOKMessage(unlockCandidate()));
    }

    private static void assertCorrectRefNamesForDependencies(final Document config) throws NodeTestException {
        final NodeList modulesList = config.getElementsByTagName("modules");
        assertEquals(1, modulesList.getLength());

        final NodeTest nt = new NodeTest((DocumentTraversal) config, modulesList.item(0));
        final NodeTester tester = new AbstractNodeTester() {
            private int defaultRefNameCount = 0;
            private int userRefNameCount = 0;

            @Override
            public void testText(final Text text) throws NodeTestException {
                if (text.getData().equals("ref_dep2")) {
                    defaultRefNameCount++;
                } else if (text.getData().equals("ref_dep_user_two")) {
                    userRefNameCount++;
                }
            }

            @Override
            public void noMoreNodes(final NodeTest forTest) throws NodeTestException {
                assertEquals(0, defaultRefNameCount);
                assertEquals(2, userRefNameCount);
            }
        };
        nt.performTest(tester, Node.TEXT_NODE);
    }

    private static void assertCorrectServiceNames(final Document configCandidate,
                                                  final Set<String> refNames) throws NodeTestException {
        final Set<String> refNames2 = new HashSet<>(refNames);
        final NodeList servicesNodes = configCandidate.getElementsByTagName("services");
        assertEquals(1, servicesNodes.getLength());

        final NodeTest nt = new NodeTest((DocumentTraversal) configCandidate, servicesNodes.item(0));
        final NodeTester tester = new AbstractNodeTester() {

            @Override
            public void testElement(final Element element) throws NodeTestException {
                if (element.getNodeName() != null) {
                    if (element.getNodeName().equals("name")) {
                        final String elmText = element.getTextContent();
                        if (refNames2.contains(elmText)) {
                            refNames2.remove(elmText);
                        } else {
                            throw new NodeTestException("Unexpected services defined: " + elmText);
                        }
                    }
                }
            }

            @Override
            public void noMoreNodes(final NodeTest forTest) throws NodeTestException {
                assertEquals(Collections.<String>emptySet(), refNames2);
                assertTrue(refNames2.toString(), refNames2.isEmpty());
            }
        };
        nt.performTest(tester, Node.ELEMENT_NODE);
    }

    @Test
    public void testConfigNetconfUnionTypes() throws Exception {

        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        commit();
        Document response = getConfigRunning();
        final Element ipElement = readXmlToElement(
                "<ip xmlns=\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">0:0:0:0:0:0:0:1</ip>");
        assertContainsElement(response, readXmlToElement(
                "<ip xmlns=\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">0:0:0:0:0:0:0:1</ip>"));

        assertContainsElement(response, readXmlToElement("<union-test-attr xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">456</union-test-attr>"));


        edit("netconfMessages/editConfig_setUnions.xml");
        commit();
        response = getConfigRunning();
        assertContainsElement(response, readXmlToElement(
                "<ip xmlns=\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">127.1.2.3</ip>"));
        assertContainsElement(response, readXmlToElement("<union-test-attr xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">"
                + "randomStringForUnion</union-test-attr>"));

    }

    @Test
    public void testConfigNetconf() throws Exception {

        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        final Document configCandidate = getConfigCandidate();
        checkBinaryLeafEdited(configCandidate);


        // default-operation:none, should not affect binary leaf
        edit("netconfMessages/editConfig_none.xml");
        checkBinaryLeafEdited(getConfigCandidate());

        // check after edit
        commit();
        final Document response = getConfigRunning();

        checkBinaryLeafEdited(response);
        checkTypeConfigAttribute(response);
        checkTypedefs(response);
        checkTestingDeps(response);
        checkEnum(response);
        checkBigDecimal(response);

        edit("netconfMessages/editConfig_remove.xml");

        commit();
        assertXMLEqual(getConfigCandidate(), getConfigRunning());

        final Document expectedResult =
                XmlFileLoader.xmlFileToDocument("netconfMessages/editConfig_expectedResult.xml");
        XMLUnit.setIgnoreWhitespace(true);
        assertXMLEqual(expectedResult, getConfigRunning());
        assertXMLEqual(expectedResult, getConfigCandidate());

        edit("netconfMessages/editConfig_none.xml");
        closeSession();
        verify(sessionCloseable).close();
        verifyNoMoreInteractions(netconfOperationRouter);
        verifyNoMoreInteractions(netconfOperationServiceSnapshot);
    }

    private static void checkBigDecimal(final Document response) throws NodeTestException, SAXException, IOException {
        assertContainsElement(response, readXmlToElement("<sleep-factor xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">2.58</sleep-factor>"));
        // Default
        assertContainsElement(response, readXmlToElement("<sleep-factor xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">2.00</sleep-factor>"));
    }

    private void closeSession() throws ParserConfigurationException, SAXException,
            IOException, DocumentedException {
        final Channel channel = mock(Channel.class);
        doReturn("channel").when(channel).toString();
        final NetconfServerSessionListener listener = mock(NetconfServerSessionListener.class);
        final NetconfServerSession session =
                new NetconfServerSession(listener, channel, 1L,
                        NetconfHelloMessageAdditionalHeader.fromString("[netconf;10.12.0.102:48528;ssh;;;;;;]"));
        final DefaultCloseSession closeOp = new DefaultCloseSession(NETCONF_SESSION_ID, sessionCloseable);
        closeOp.setNetconfSession(session);
        executeOp(closeOp, "netconfMessages/closeSession.xml");
    }

    private void edit(final String resource) throws ParserConfigurationException, SAXException, IOException,
            DocumentedException {
        final EditConfig editOp = new EditConfig(configSubsystemFacade, NETCONF_SESSION_ID);
        executeOp(editOp, resource);
    }

    private void commit() throws ParserConfigurationException, SAXException, IOException, DocumentedException {
        final Commit commitOp = new Commit(configSubsystemFacade, NETCONF_SESSION_ID);
        executeOp(commitOp, "netconfMessages/commit.xml");
    }

    private static Document lockCandidate() throws ParserConfigurationException, SAXException, IOException,
            DocumentedException {
        final Lock commitOp = new Lock(NETCONF_SESSION_ID);
        return executeOp(commitOp, "netconfMessages/lock.xml");
    }

    private static Document unlockCandidate() throws ParserConfigurationException, SAXException, IOException,
            DocumentedException {
        final UnLock commitOp = new UnLock(NETCONF_SESSION_ID);
        return executeOp(commitOp, "netconfMessages/unlock.xml");
    }

    private Document getConfigCandidate() throws ParserConfigurationException, SAXException, IOException,
            DocumentedException {
        final GetConfig getConfigOp = new GetConfig(configSubsystemFacade, Optional.<String>absent(),
                NETCONF_SESSION_ID);
        return executeOp(getConfigOp, "netconfMessages/getConfig_candidate.xml");
    }

    private Document getConfigRunning() throws ParserConfigurationException, SAXException, IOException,
            DocumentedException {
        final GetConfig getConfigOp = new GetConfig(configSubsystemFacade, Optional.<String>absent(),
                NETCONF_SESSION_ID);
        return executeOp(getConfigOp, "netconfMessages/getConfig.xml");
    }

    @Ignore("second edit message corrupted")
    @Test(expected = DocumentedException.class)
    public void testConfigNetconfReplaceDefaultEx() throws Exception {

        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        edit("netconfMessages/editConfig_replace_default_ex.xml");
    }

    @Test
    public void testConfigNetconfReplaceDefault() throws Exception {

        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        commit();
        Document response = getConfigRunning();
        final int allInstances = response.getElementsByTagName("module").getLength();

        edit("netconfMessages/editConfig_replace_default.xml");

        commit();
        response = getConfigRunning();

        final int afterReplace = response.getElementsByTagName("module").getLength();
        assertEquals(4, allInstances);
        assertEquals(2, afterReplace);
    }

    @Test
    public void testSameAttrDifferentNamespaces() throws Exception {
        try {
            edit("netconfMessages/namespaces/editConfig_sameAttrDifferentNamespaces.xml");
            fail();
        } catch (final DocumentedException e) {
            final String message = e.getMessage();
            assertContainsString(message, "Element simpleInt present multiple times with different namespaces");
            assertContainsString(message, TEST_NAMESPACE);
            assertContainsString(message, XmlMappingConstants.URN_OPENDAYLIGHT_PARAMS_XML_NS_YANG_CONTROLLER_CONFIG);
        }
    }

    @Test
    public void testDifferentNamespaceInTO() throws Exception {
        try {
            edit("netconfMessages/namespaces/editConfig_differentNamespaceTO.xml");
            fail();
        } catch (final DocumentedException e) {
            final String message = e.getMessage();
            assertContainsString(message, "Unrecognised elements");
            assertContainsString(message, "simple-int2");
            assertContainsString(message, "dto_d");
        }
    }

    @Test
    public void testSameAttrDifferentNamespacesList() throws Exception {
        try {
            edit("netconfMessages/namespaces/editConfig_sameAttrDifferentNamespacesList.xml");
            fail();
        } catch (final DocumentedException e) {
            final String message = e.getMessage();
            assertContainsString(message, "Element allow-user present multiple times with different namespaces");
            assertContainsString(message, TEST_NAMESPACE);
            assertContainsString(message, XmlMappingConstants.URN_OPENDAYLIGHT_PARAMS_XML_NS_YANG_CONTROLLER_CONFIG);
        }
    }

    @Test
    public void testTypeNameConfigAttributeMatching() throws Exception {
        edit("netconfMessages/editConfig.xml");
        commit();
        edit("netconfMessages/namespaces/editConfig_typeNameConfigAttributeMatching.xml");
        commit();

        final Document response = getConfigRunning();
        checkTypeConfigAttribute(response);
    }

    // TODO add <modules operation="replace"> functionality
    @Test(expected = DocumentedException.class)
    public void testConfigNetconfReplaceModuleEx() throws Exception {

        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        edit("netconfMessages/editConfig_replace_module_ex.xml");
    }

    @Test
    public void testUnrecognisedConfigElements() throws Exception {

        final String format = "netconfMessages/unrecognised/editConfig_unrecognised%d.xml";
        final int testsCount = 8;

        for (int i = 0; i < testsCount; i++) {
            final String file = String.format(format, i + 1);
            LOG.info("Reading {}", file);
            try {
                edit(file);
            } catch (final DocumentedException e) {
                assertContainsString(e.getMessage(), "Unrecognised elements");
                assertContainsString(e.getMessage(), "unknownAttribute");
                continue;
            }
            fail("Unrecognised test should throw exception " + file);
        }
    }

    @Test
    @Ignore
    // FIXME
    public void testConfigNetconfReplaceModule() throws Exception {

        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        commit();
        Document response = getConfigRunning();
        final int allInstances = response.getElementsByTagName("instance").getLength();

        edit("netconfMessages/editConfig_replace_module.xml");

        commit();
        response = getConfigRunning();
        final int afterReplace = response.getElementsByTagName("instance").getLength();

        assertEquals(4 + 4 /* Instances from services */, allInstances);
        assertEquals(3 + 3, afterReplace);
    }

    @Test
    public void testEx2() throws Exception {
        //check abort before tx creation
        assertContainsElement(discard(), readXmlToElement("<ok xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));

        //check abort after tx creation
        edit("netconfMessages/editConfig.xml");
        assertContainsElement(discard(), readXmlToElement("<ok xmlns=\"urn:ietf:params:xml:ns:netconf:base:1.0\"/>"));
    }

    @Test
    public void testFailedDiscardChangesAbort() throws Exception {
        final ConfigSubsystemFacade facade = mock(ConfigSubsystemFacade.class);
        doThrow(new RuntimeException("Mocked runtime exception, Abort has to fail")).when(facade).abortConfiguration();

        final DiscardChanges discardOp = new DiscardChanges(facade, NETCONF_SESSION_ID);

        try {
            executeOp(discardOp, "netconfMessages/discardChanges.xml");
            fail("Should've failed, abort on mocked is supposed to throw RuntimeException");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorTag() == DocumentedException.ErrorTag.OPERATION_FAILED);
            assertTrue(e.getErrorSeverity() == DocumentedException.ErrorSeverity.ERROR);
            assertTrue(e.getErrorType() == DocumentedException.ErrorType.APPLICATION);
        }
    }

    private Document discard() throws ParserConfigurationException, SAXException, IOException, DocumentedException {
        final DiscardChanges discardOp = new DiscardChanges(configSubsystemFacade, NETCONF_SESSION_ID);
        return executeOp(discardOp, "netconfMessages/discardChanges.xml");
    }

    private static void checkBinaryLeafEdited(final Document response)
            throws NodeTestException, SAXException, IOException {
        assertContainsElement(response, readXmlToElement("<binaryLeaf xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">YmluYXJ5</binaryLeaf>"));
        assertContainsElement(response, readXmlToElement("<binaryLeaf xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">ZGVmYXVsdEJpbg==</binaryLeaf>"));
    }

    private static void checkTypedefs(final Document response) throws NodeTestException, SAXException, IOException {

        assertContainsElement(response, readXmlToElement(
                "<extended xmlns=\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">10</extended>"));
        // Default
        assertContainsElement(response, readXmlToElement(
                "<extended xmlns=\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">1</extended>"));

        assertContainsElement(response, readXmlToElement("<extended-twice xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">20</extended-twice>"));
        // Default
        assertContainsElement(response, readXmlToElement("<extended-twice xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">2</extended-twice>"));

        assertContainsElement(response, readXmlToElement("<extended-enum xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">two</extended-enum>"));
        // Default
        assertContainsElement(response, readXmlToElement("<extended-enum xmlns="
                + "\"urn:opendaylight:params:xml:ns:yang:controller:test:impl\">one</extended-enum>"));
    }

    private static void assertContainsString(final String string, final String substring) {
        assertThat(string, containsString(substring));
    }

    private static void checkEnum(final Document response) throws Exception {

        final String expectedEnumContent = "two";

        XMLAssert.assertXpathEvaluatesTo(expectedEnumContent,
                getXpathForNetconfImplSubnode(INSTANCE_NAME, "extended-enum"),
                response);
    }

    private static void checkTestingDeps(final Document response) {
        final int testingDepsSize = response.getElementsByTagName("testing-deps").getLength();
        assertEquals(2, testingDepsSize);
    }

    private static String getXpathForNetconfImplSubnode(final String instanceName, final String subnode) {
        return "/urn:ietf:params:xml:ns:netconf:base:1.0:rpc-reply"
                + "/urn:ietf:params:xml:ns:netconf:base:1.0:data"
                + "/urn:opendaylight:params:xml:ns:yang:controller:config:modules"
                + "/urn:opendaylight:params:xml:ns:yang:controller:config:module"
                + "[urn:opendaylight:params:xml:ns:yang:controller:config:name='" + instanceName + "']"
                + "/urn:opendaylight:params:xml:ns:yang:controller:test:impl:impl-netconf"
                + "/urn:opendaylight:params:xml:ns:yang:controller:test:impl:" + subnode;
    }

    private static void checkTypeConfigAttribute(final Document response) throws Exception {

        final Map<String, String> namesToTypeValues = ImmutableMap.of("instance-from-code", "configAttributeType",
                "test2", "default-string");
        for (final Entry<String, String> nameToExpectedValue : namesToTypeValues.entrySet()) {
            XMLAssert.assertXpathEvaluatesTo(nameToExpectedValue.getValue(),
                    getXpathForNetconfImplSubnode(nameToExpectedValue.getKey(), "type"),
                    response);
        }
    }

    private Map<String, Map<String, ModuleMXBeanEntry>> getMbes() throws Exception {
        final List<InputStream> yangDependencies = getYangs();

        final Map<String, Map<String, ModuleMXBeanEntry>> mBeanEntries = Maps.newHashMap();

        final SchemaContext schemaContext = YangParserTestUtils.parseYangStreams(yangDependencies);
        final YangStoreService yangStoreService = new YangStoreService(new SchemaContextProvider() {
            @Override
            public SchemaContext getSchemaContext() {
                return schemaContext;
            }
        }, mock(SchemaSourceProvider.class));
        final BindingRuntimeContext bindingRuntimeContext = mock(BindingRuntimeContext.class);
        doReturn(schemaContext).when(bindingRuntimeContext).getSchemaContext();
        doReturn(getEnumMapping()).when(bindingRuntimeContext).getEnumMapping(any(Class.class));
        yangStoreService.refresh(bindingRuntimeContext);
        mBeanEntries.putAll(yangStoreService.getModuleMXBeanEntryMap());

        return mBeanEntries;
    }

    private static BiMap<String, String> getEnumMapping() {
        final HashBiMap<String, String> enumBiMap = HashBiMap.create();
        // Enum constants mapping from yang -> Java and back
        enumBiMap.put("one", "One");
        enumBiMap.put("two", "Two");
        enumBiMap.put("version1", "Version1");
        enumBiMap.put("version2", "Version2");
        return enumBiMap;
    }

    private Set<org.opendaylight.yangtools.yang.model.api.Module> getModules() throws Exception {
        final SchemaContext resolveSchemaContext = YangParserTestUtils.parseYangStreams(getYangs());
        return resolveSchemaContext.getModules();
    }

    @Test
    public void testConfigNetconfRuntime() throws Exception {

        createModule(INSTANCE_NAME);

        edit("netconfMessages/editConfig.xml");
        checkBinaryLeafEdited(getConfigCandidate());

        // check after edit
        commit();
        Document response = get();

        assertEquals(2/*With runtime beans*/ + 2 /*Without runtime beans*/, getElementsSize(response, "module"));
        // data from state
        assertEquals(2, getElementsSize(response, "asdf"));
        // data from running config
        assertEquals(2, getElementsSize(response, "simple-short"));

        assertEquals(8, getElementsSize(response, "inner-running-data"));
        assertEquals(8, getElementsSize(response, "deep2"));
        assertEquals(8 * 4, getElementsSize(response, "inner-inner-running-data"));
        assertEquals(8 * 4, getElementsSize(response, "deep3"));
        assertEquals(8 * 4 * 2, getElementsSize(response, "list-of-strings"));
        assertEquals(8, getElementsSize(response, "inner-running-data-additional",
                "urn:opendaylight:params:xml:ns:yang:controller:test:impl"));
        assertEquals(8, getElementsSize(response, "deep4"));
        // TODO assert keys

        final RuntimeRpc netconf = new RuntimeRpc(configSubsystemFacade, NETCONF_SESSION_ID);

        response = executeOp(netconf, "netconfMessages/rpc.xml");
        assertContainsElementWithText(response, "testarg1");

        response = executeOp(netconf, "netconfMessages/rpcInner.xml");
        final Document expectedReplyOk = XmlFileLoader.xmlFileToDocument("netconfMessages/rpc-reply_ok.xml");
        XMLUnit.setIgnoreWhitespace(true);
        XMLAssert.assertXMLEqual(expectedReplyOk, response);

        response = executeOp(netconf, "netconfMessages/rpcInnerInner.xml");
        assertContainsElementWithText(response, "true");

        response = executeOp(netconf, "netconfMessages/rpcInnerInner_complex_output.xml");
        assertContainsElementWithText(response, "1");
        assertContainsElementWithText(response, "2");
    }

    private Document get() throws ParserConfigurationException, SAXException, IOException, DocumentedException {
        final Get getOp = new Get(configSubsystemFacade, NETCONF_SESSION_ID);
        return executeOp(getOp, "netconfMessages/get.xml");
    }

    private static int getElementsSize(final Document response, final String elementName) {
        return response.getElementsByTagName(elementName).getLength();
    }

    private static int getElementsSize(final Document response, final String elementName, final String namespace) {
        return response.getElementsByTagNameNS(namespace, elementName).getLength();
    }

    private static Document executeOp(final NetconfOperation op,
                                      final String filename) throws ParserConfigurationException,
            SAXException, IOException, DocumentedException {

        final Document request = XmlFileLoader.xmlFileToDocument(filename);

        LOG.debug("Executing netconf operation\n{}", XmlUtil.toString(request));
        final HandlingPriority priority = op.canHandle(request);

        Preconditions.checkState(priority != HandlingPriority.CANNOT_HANDLE);

        final Document response = op.handle(request, NetconfOperationChainedExecution.EXECUTION_TERMINATION_POINT);
        LOG.debug("Got response\n{}", XmlUtil.toString(response));
        return response;
    }

    private List<InputStream> getYangs() {
        final List<String> paths = Arrays.asList("/META-INF/yang/config@2013-04-05.yang",
                "/META-INF/yang/rpc-context@2013-06-17.yang",
                "/META-INF/yang/config-test.yang", "/META-INF/yang/config-test-impl.yang",
                "/META-INF/yang/test-types.yang", "/META-INF/yang/test-groups.yang",
                "/META-INF/yang/ietf-inet-types@2013-07-15.yang");
        final Collection<InputStream> yangDependencies = new ArrayList<>();
        for (final String path : paths) {
            final InputStream is = Preconditions
                    .checkNotNull(getClass().getResourceAsStream(path), path + " not found");
            yangDependencies.add(is);
        }
        return Lists.newArrayList(yangDependencies);
    }

    private void setModule(final NetconfTestImplModuleMXBean mxBean, final ConfigTransactionJMXClient transaction,
                           final String depName)
            throws InstanceAlreadyExistsException, InstanceNotFoundException {
        mxBean.setSimpleInt((long) 44);
        mxBean.setBinaryLeaf(new byte[]{8, 7, 9});
        final DtoD dtob = getDtoD();
        mxBean.setDtoD(dtob);
        //
        final DtoC dtoa = getDtoC();
        mxBean.setDtoC(dtoa);
        mxBean.setSimpleBoolean(false);
        //
        final Peers p1 = new Peers();
        p1.setCoreSize(44L);
        p1.setPort("port1");
        p1.setSimpleInt3(456);
        final Peers p2 = new Peers();
        p2.setCoreSize(44L);
        p2.setPort("port23");
        p2.setSimpleInt3(456);
        mxBean.setPeers(Lists.<Peers>newArrayList(p1, p2));
        // //
        mxBean.setSimpleLong(454545L);
        mxBean.setSimpleLong2(44L);
        mxBean.setSimpleBigInteger(BigInteger.valueOf(999L));
        mxBean.setSimpleByte(new Byte((byte) 4));
        mxBean.setSimpleShort(new Short((short) 4));
        mxBean.setSimpleTest(545);

        mxBean.setComplexList(Lists.<ComplexList>newArrayList());
        mxBean.setSimpleList(Lists.<Integer>newArrayList());

        final ObjectName testingDepOn = transaction.createModule(this.factory2.getImplementationName(), depName);
        int index = 1;
        for (final Class<? extends AbstractServiceInterface> serviceInterface :
                factory2.getImplementedServiceIntefaces()) {
            final ServiceInterfaceAnnotation annotation =
                    serviceInterface.getAnnotation(ServiceInterfaceAnnotation.class);
            transaction.saveServiceReference(
                    transaction.getServiceInterfaceName(annotation.namespace(), annotation.localName()),
                    "ref_from_code_to_" + depName + "_" + index++, testingDepOn);

        }
        mxBean.setTestingDep(testingDepOn);
    }

    private static DtoD getDtoD() {
        final DtoD dtob = new DtoD();
        dtob.setSimpleInt1((long) 444);
        dtob.setSimpleInt2((long) 4444);
        dtob.setSimpleInt3(454);
        final ComplexDtoBInner dtobInner = new ComplexDtoBInner();
        final Deep deep = new Deep();
        deep.setSimpleInt3(4);
        dtobInner.setDeep(deep);
        dtobInner.setSimpleInt3(44);
        dtobInner.setSimpleList(Lists.newArrayList(4));
        dtob.setComplexDtoBInner(Lists.newArrayList(dtobInner));
        dtob.setSimpleList(Lists.newArrayList(4));
        return dtob;
    }

    private static DtoC getDtoC() {
        final DtoC dtoa = new DtoC();
        // dtoa.setSimpleArg((long) 55);
        final DtoAInner dtoAInner = new DtoAInner();
        final DtoAInnerInner dtoAInnerInner = new DtoAInnerInner();
        dtoAInnerInner.setSimpleArg(456L);
        dtoAInner.setDtoAInnerInner(dtoAInnerInner);
        dtoAInner.setSimpleArg(44L);
        dtoa.setDtoAInner(dtoAInner);
        return dtoa;
    }

}
