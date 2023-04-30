/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import org.junit.Ignore;
import org.junit.Test;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.server.mdsal.CurrentSchemaContext;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.netconf.base._1._0.rev110601.SessionIdType;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class NetconfMDSalMappingTest extends AbstractNetconfOperationTest {
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

    @Test
    public void testEmptyDatastore() throws Exception {
        assertEmptyDatastore(get());
        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());
    }

    @Test
    public void testIncorrectGet() throws Exception {
        DocumentedException ex = assertThrows(DocumentedException.class,
            () -> executeOperation(new GetConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
                    getTransactionProvider()), "messages/mapping/bad_getConfig.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, ex.getErrorTag());
        assertEquals(ErrorType.APPLICATION, ex.getErrorType());

        ex = assertThrows(DocumentedException.class,
            () -> executeOperation(new GetConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
                    getTransactionProvider()), "messages/mapping/bad_namespace_getConfig.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, ex.getErrorTag());
        assertEquals(ErrorType.APPLICATION, ex.getErrorType());
    }

    @Test
    public void testConfigMissing() throws Exception {
        final DocumentedException ex = assertThrows(DocumentedException.class,
            () -> edit("messages/mapping/editConfigs/editConfig_no_config.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.MISSING_ELEMENT, ex.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
    }

    @Test
    public void testEditRunning() throws Exception {
        final DocumentedException ex = assertThrows(DocumentedException.class,
            () -> edit("messages/mapping/editConfigs/editConfig_running.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, ex.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
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

        DocumentedException ex = assertThrows(DocumentedException.class, NetconfMDSalMappingTest::lock);
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, ex.getErrorTag());
        assertEquals(ErrorType.APPLICATION, ex.getErrorType());

        ex = assertThrows(DocumentedException.class, NetconfMDSalMappingTest::lockWithoutTarget);
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrorTag());
        assertEquals(ErrorType.APPLICATION, ex.getErrorType());
    }

    @Test
    public void testUnlock() throws Exception {
        verifyResponse(unlockCandidate(), RPC_REPLY_OK);

        DocumentedException ex = assertThrows(DocumentedException.class, NetconfMDSalMappingTest::unlock);
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, ex.getErrorTag());
        assertEquals(ErrorType.APPLICATION, ex.getErrorType());

        ex = assertThrows(DocumentedException.class, NetconfMDSalMappingTest::unlockWithoutTarget);
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrorTag());
        assertEquals(ErrorType.APPLICATION, ex.getErrorType());
    }

    @Test
    public void testEditWithCreate() throws Exception {
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_create.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfig_create_n1_control.xml"));

        final DocumentedException ex = assertThrows(DocumentedException.class,
            () -> edit("messages/mapping/editConfigs/editConfig_create.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.DATA_EXISTS, ex.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());

        verifyResponse(discardChanges(), RPC_REPLY_OK);
    }

    @Test
    public void testDeleteNonExisting() throws Exception {
        assertEmptyDatastore(getConfigCandidate());
        assertEmptyDatastore(getConfigRunning());

        final DocumentedException ex = assertThrows(DocumentedException.class,
            () -> edit("messages/mapping/editConfigs/editConfig_delete-top.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.DATA_MISSING, ex.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());
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

    @Test
    public void testEditConfigWithMultipleOperations() throws Exception {
        deleteDatastore();

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_setup.xml"),
                RPC_REPLY_OK);
        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_1.xml"), RPC_REPLY_OK);

        verifyResponse(edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_2.xml"), RPC_REPLY_OK);
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

        DocumentedException ex = assertThrows(DocumentedException.class,
            () -> edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_create_existing.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.DATA_EXISTS, ex.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());

        verifyResponse(edit(
                "messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_delete_children_operations.xml"),
                RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
                "messages/mapping/editConfigs/"
                        + "editConfig_merge_multiple_operations_4_delete_children_operations_control.xml"));
        verifyResponse(edit(
                "messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_remove-non-existing.xml"),
                RPC_REPLY_OK);
        ex = assertThrows(DocumentedException.class,
            () -> edit("messages/mapping/editConfigs/editConfig_merge_multiple_operations_4_delete-non-existing.xml"));
        assertEquals(ErrorSeverity.ERROR, ex.getErrorSeverity());
        assertEquals(ErrorTag.DATA_MISSING, ex.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, ex.getErrorType());

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
        assertEquals(1, nodeList.getLength());

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
        assertEquals(1, nodeList.getLength());

        String stringWithoutTarget =
                "<nc:rpc xmlns:nc=\"urn:ietf:params:xml:ns:netconf:base:1.0\" message-id=\"0\">\n"
                        + "  <nc:edit-config>\n"
                        + "    <nc:target>\n"
                        + "    </nc:target>\n"
                        + "  </nc:edit-config>\n"
                        + "</nc:rpc>";
        xe = getXmlElement(stringWithoutTarget);

        final NodeList targetKey = EditConfig.getElementsByTagName(xe, TARGET_KEY);
        assertThrows(DocumentedException.class,
            () -> XmlElement.fromDomElement((Element) targetKey.item(0)).getOnlyChildElement());
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

    @Ignore("Needs to have YIID parsing fixed, currently everything is a NodeIdentifier which breaks"
            + "SchemaInferenceStack")
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
        TestingGetConfig(final SessionIdType sessionId, final CurrentSchemaContext schemaContext,
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
