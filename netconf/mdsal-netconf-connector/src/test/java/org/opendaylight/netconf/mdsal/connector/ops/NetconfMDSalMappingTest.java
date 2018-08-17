/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringWriter;
import java.net.URI;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.mdsal.connector.CurrentSchemaContext;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.opendaylight.netconf.mdsal.connector.ops.get.GetConfig;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class NetconfMDSalMappingTest extends AbstractNetconfOperationTest {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfMDSalMappingTest.class);

    private static final String TARGET_KEY = "target";
    private static final String FILTER_NODE = "filter";
    private static final String GET_CONFIG = "get-config";
    private static final QName TOP = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "top");
    private static final QName USERS = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "users");
    private static final QName USER = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "user");
    private static final QName MODULES = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26", "modules");
    private static final QName AUGMENTED_CONTAINER = QName.create("urn:opendaylight:mdsal:mapping:test",
            "2015-02-26", "augmented-container");
    private static final QName AUGMENTED_STRING_IN_CONT = QName.create("urn:opendaylight:mdsal:mapping:test",
            "2015-02-26", "identifier");
    private static final QName CHOICE_NODE = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26",
            "choice-node");
    private static final QName AUGMENTED_CASE = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26",
            "augmented-case");
    private static final QName CHOICE_WRAPPER = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26",
            "choice-wrapper");
    private static final QName INNER_CHOICE = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26",
            "inner-choice");
    private static final QName INNER_CHOICE_TEXT = QName.create("urn:opendaylight:mdsal:mapping:test", "2015-02-26",
            "text");

    private static final YangInstanceIdentifier AUGMENTED_CONTAINER_IN_MODULES =
            YangInstanceIdentifier.builder().node(TOP).node(MODULES).build();

    @Override
    protected SchemaContext getSchemaContext() {
        return YangParserTestUtils.parseYangResources(NetconfMDSalMappingTest.class,
            "/yang/mdsal-netconf-mapping-test.yang");
    }

    @Test
    public void testEmptyDatastore() throws Exception {
        assertEmptyDatastore(get());
        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());
    }

    @Test
    public void testIncorrectGet() throws Exception {
        try {
            executeOperation(new GetConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
                    getTransactionProvider()), "messages/mapping/bad_getConfig.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_FAILED);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
        }

        try {
            executeOperation(new GetConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
                    getTransactionProvider()), "messages/mapping/bad_namespace_getConfig.xml");
            fail("Should have failed, this is an incorrect request");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_FAILED);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
        }
    }

    @Test
    public void testConfigMissing() throws Exception {
        try {
            edit("messages/mapping/editConfigs/editConfig_no_config.xml");
            fail("Should have failed - neither <config> nor <url> element is present");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.MISSING_ELEMENT);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
    }

    @Test
    public void testEditRunning() throws Exception {
        try {
            edit("messages/mapping/editConfigs/editConfig_running.xml");
            fail("Should have failed - edit config on running datastore is not supported");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_NOT_SUPPORTED);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
    }

    @Test
    public void testCommitWithoutOpenTransaction() throws Exception {
        verifyResponse(commit(), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());
    }

    @Test
    public void testCandidateTransaction() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));
        assertEmptyDatastore(getConfigRunning());

        verifyResponse(discardChanges(), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());
    }

    @Test
    public void testEditWithCommit() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_n1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));

        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_n1_control.xml"));

        deleteDatastore();
    }

    @Test
    public void testKeyOrder() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_keys_1.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        final Document configRunning = getConfigRunning();
        final String responseAsString = XmlUtil.toString(configRunning);
        verifyResponse(configRunning, XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_keys_1_control.xml"));

        final int key3 = responseAsString.indexOf("key3");
        final int key1 = responseAsString.indexOf("key1");
        final int key2 = responseAsString.indexOf("key2");

        assertTrue(String.format("Key ordering invalid, should be key3(%d) < key1(%d) < key2(%d)", key3, key1, key2),
                key3 < key1 && key1 < key2);

        deleteDatastore();
    }


    @Test
    public void testMultipleEditsWithMerge() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_control_1.xml"));
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_single_1.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_control_2.xml"));
        assertEmptyDatastore(getConfigRunning());

        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_control_2.xml"));

        deleteDatastore();
    }

    @Test
    public void testMoreComplexEditConfigs() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_1.xml"), RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_single_1.xml"), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_2.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_after_more_complex_merge.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_3.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_after_more_complex_merge_2.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_4_replace.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_after_replace.xml"));
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_after_replace.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_replace_default.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_replace_default_control.xml"));
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_replace_default_control.xml"));

        deleteDatastore();
    }

    @Test
    public void testOrderedListEdits() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_ordered_list_create.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_ordered_list_replace.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        deleteDatastore();

    }

    @Test
    public void testAugmentedOrderedListEdits() throws Exception {

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_augmented_ordered_list_create.xml"),
                RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_augmented_ordered_list_replace.xml"),
                RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        deleteDatastore();

    }

    @Test
    public void testAugmentedContainerReplace() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_empty_modules_create.xml"),
                RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_augmented_container_replace.xml"),
                RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        deleteDatastore();
    }

    @Test
    public void testLeafFromAugmentReplace() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_empty_modules_create.xml"),
                RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_leaf_from_augment_replace.xml"),
                RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        deleteDatastore();
    }

    @Test
    public void testLock() throws Exception {
        verifyResponse(lockCandidate(), RPC_REPLY_OK);

        try {
            lock();
            fail("Should have failed - locking of running datastore is not supported");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_NOT_SUPPORTED);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
        }

        try {
            lockWithoutTarget();
            fail("Should have failed, target is missing");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.INVALID_VALUE);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
        }
    }

    @Test
    public void testUnlock() throws Exception {
        verifyResponse(unlockCandidate(), RPC_REPLY_OK);

        try {
            unlock();
            fail("Should have failed - unlocking of running datastore is not supported");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_NOT_SUPPORTED);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
        }

        try {
            unlockWithoutTarget();
            fail("Should have failed, target is missing");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.INVALID_VALUE);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
        }
    }

    @Test
    public void testEditWithCreate() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_create.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfig_create_n1_control.xml"));

        try {
            edit("messages/mapping/editConfigs/editConfig_create.xml");
            fail("Create should have failed - data already exists");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.DATA_EXISTS);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }

        verifyResponse(discardChanges(), RPC_REPLY_OK);
    }

    @Test
    public void testDeleteNonExisting() throws Exception {
        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());

        try {
            edit("messages/mapping/editConfigs/editConfig_delete-top.xml");
            fail("Delete should have failed - data is missing");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.DATA_MISSING);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
    }

    @Test
    public void testEditMissingDefaultOperation() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_missing_default-operation_1.xml"),
                RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_missing_default-operation_2.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_missing_default-operation_control.xml"));

        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_missing_default-operation_control.xml"));

        deleteDatastore();
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

    @Test
    public void testEditConfigWithMultipleOperations() throws Exception {
        deleteDatastore();

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_setup.xml"),
                RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_1.xml"),
                RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_2.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_operations_2_control.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_3_leaf_operations.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_operations_3_control.xml"));

        deleteDatastore();

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_setup.xml"),
                RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_default-replace.xml"),
                RPC_REPLY_OK);

        try {
            edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_create_existing.xml");
            fail();
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.DATA_EXISTS);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }

        verifyResponse(edit(
                "messages/mapping/editConfigs/"
                        + "editConfig_merge_multiple_operations_4_delete_children_operations.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/"
                        + "editConfig_merge_multiple_operations_4_delete_children_operations_control.xml"));
        verifyResponse(edit(
                "messages/mapping/editConfigs/"
                        + "editConfig_merge_multiple_operations_4_remove-non-existing.xml"),
                RPC_REPLY_OK);
        try {
            edit("messages/mapping/editConfigs/"
                    + "editConfig_merge_multiple_operations_4_delete-non-existing.xml");
            fail();
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.DATA_MISSING);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup-control.xml"));

        // Test files have been modified. RFC6020 requires that at most once case inside a choice is present at any time
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup2.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_setup2-control.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_5_choice_delete.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs"
                        + "/editConfig_merge_multiple_operations_4_delete_children_operations_control.xml"));

        deleteDatastore();
    }

    @Test
    public void testEditConfigGetElementByTagName() throws Exception {
        EditConfig editConfig = new EditConfig("test_edit-config", Mockito.mock(CurrentSchemaContext.class),
                Mockito.mock(TransactionProvider.class));

        String stringWithoutPrefix =
                "<rpc xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"0\">\n"
                        + "  <edit-config>\n"
                        + "    <target>\n"
                        + "      <candidate/>\n"
                        + "    </target>\n"
                        + "  </edit-config>\n"
                        + "</rpc>";
        XmlElement xe = getXmlElement(stringWithoutPrefix);
        NodeList nodeList = EditConfig.getElementsByTagName(xe, TARGET_KEY);
        Assert.assertEquals(1, nodeList.getLength());

        String stringWithPrefix =
                "<nc:rpc xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"0\">\n"
                        + "  <nc:edit-config>\n"
                        + "    <nc:target>\n"
                        + "      <nc:candidate/>\n"
                        + "    </nc:target>\n"
                        + "  </nc:edit-config>\n"
                        + "</nc:rpc>";

        xe = getXmlElement(stringWithPrefix);
        nodeList = EditConfig.getElementsByTagName(xe, TARGET_KEY);
        Assert.assertEquals(1, nodeList.getLength());

        String stringWithoutTarget =
                "<nc:rpc xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"0\">\n"
                        + "  <nc:edit-config>\n"
                        + "    <nc:target>\n"
                        + "    </nc:target>\n"
                        + "  </nc:edit-config>\n"
                        + "</nc:rpc>";
        xe = getXmlElement(stringWithoutTarget);

        try {
            nodeList = EditConfig.getElementsByTagName(xe, TARGET_KEY);
            XmlElement.fromDomElement((Element) nodeList.item(0)).getOnlyChildElement();
            Assert.fail("Not specified target, we should fail");
        } catch (DocumentedException documentedException) {
            // Ignore
        }

    }

    private static XmlElement getXmlElement(final String elementAsString) throws Exception {
        Document document = XmlUtil.readXmlToDocument(elementAsString);
        Element element = document.getDocumentElement();
        return XmlElement.fromDomElement(element);
    }

    @Test
    public void testReplaceMapEntry() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/edit-config-replace-map-entry.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-config-map-entry.xml"));
    }

    @Test
    public void testMergeMapEntry() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/edit-config-merge-map-entry.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-config-map-entry.xml"));
    }

    @Test
    public void testFiltering() throws Exception {
        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());

        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-config-empty-filter.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));
        verifyResponse(getWithFilter("messages/mapping/filters/get-empty-filter.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));

        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response"
                + ".xml"));
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));
        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-users.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/get-empty-response.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig-filtering-setup.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

        verifyFilterIdentifier("messages/mapping/filters/get-filter-alluser.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-company-info.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-modules-and-admin.xml",
                YangInstanceIdentifier.builder().node(TOP).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-only-names-types.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-specific-module-type-and-user.xml",
                YangInstanceIdentifier.builder().node(TOP).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-superuser.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).node(USER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-users.xml",
                YangInstanceIdentifier.builder().node(TOP).node(USERS).build());

        final YangInstanceIdentifier ident = YangInstanceIdentifier
                .builder(AUGMENTED_CONTAINER_IN_MODULES)
                .node(AUGMENTED_CONTAINER)
                .node(AUGMENTED_STRING_IN_CONT).build();

        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-string.xml", ident);
        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-case.xml",
                YangInstanceIdentifier.builder().node(TOP).node(CHOICE_NODE).node(AUGMENTED_CASE).build());

        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-case.xml"),
                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-case.xml"));

        /*
         *  RFC6020 requires that at most once case inside a choice is present at any time.
         *  Therefore
         *  <augmented-case>augmented case</augmented-case>
         *  from
         *  messages/mapping/editConfigs/editConfig-filtering-setup.xml
         *  cannot exists together with
         *  <text>augmented nested choice text1</text>
         *  from
         *  messages/mapping/editConfigs/editConfig-filtering-setup2.xml
         */
        //verifyResponse(edit("messages/mapping/editConfigs/editConfig-filtering-setup2.xml"), RPC_REPLY_OK);
        //verifyResponse(commit(), RPC_REPLY_OK);

        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-case-inner-choice.xml",
                YangInstanceIdentifier.builder().node(TOP).node(CHOICE_NODE).node(CHOICE_WRAPPER).build());
        verifyFilterIdentifier("messages/mapping/filters/get-filter-augmented-case-inner-case.xml",
                YangInstanceIdentifier.builder().node(TOP).node(CHOICE_NODE).node(CHOICE_WRAPPER).node(INNER_CHOICE)
                        .node(INNER_CHOICE_TEXT).build());

//        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-string.xml"),
//                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-string.xml"));
//        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-case-inner-choice.xml"),
//                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-case-inner-choice.xml"));
//        verifyResponse(getConfigWithFilter("messages/mapping/filters/get-filter-augmented-case-inner-case.xml"),
//                XmlFileLoader.xmlFileToDocument("messages/mapping/filters/response-augmented-case-inner-choice.xml"));

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_delete-top.xml"), RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);

    }

    private void verifyFilterIdentifier(final String resource, final YangInstanceIdentifier identifier)
            throws Exception {
        final TestingGetConfig getConfig = new TestingGetConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
                getTransactionProvider());
        final Document request = XmlFileLoader.xmlFileToDocument(resource);
        final YangInstanceIdentifier iid = getConfig.getInstanceIdentifierFromDocument(request);
        assertEquals(identifier, iid);
    }

    private class TestingGetConfig extends GetConfig {
        TestingGetConfig(final String sessionId, final CurrentSchemaContext schemaContext,
                         final TransactionProvider transactionProvider) {
            super(sessionId, schemaContext, transactionProvider);
        }

        YangInstanceIdentifier getInstanceIdentifierFromDocument(final Document request) throws DocumentedException {
            final XmlElement filterElement = XmlElement.fromDomDocument(request).getOnlyChildElement(GET_CONFIG)
                    .getOnlyChildElement(FILTER_NODE);
            return getInstanceIdentifierFromFilter(filterElement);
        }
    }

    private void deleteDatastore() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_delete-root.xml"), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigCandidate());

        verifyResponse(commit(), RPC_REPLY_OK);
        assertEmptyDatastore(getConfigRunning());
    }

    @Test
    public void testEditUsingConfigFromFile() throws Exception {
        // Ask class loader for URI of config file and use it as <url> in <edit-config> RPC:
        final String template = XmlFileLoader.fileToString("messages/mapping/editConfigs/editConfig_from_file.xml");
        final URI uri = getClass().getClassLoader().getResource("messages/mapping/editConfigs/config_file.xml").toURI();
        final String copyConfig = template.replaceFirst("URL", uri.toString());
        final Document request = XmlUtil.readXmlToDocument(copyConfig);

        verifyResponse(edit(request), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/editConfigs/editConfig_from_file_control.xml"));
    }
}
