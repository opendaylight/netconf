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
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYangResources;

import org.junit.Test;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorSeverity;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorTag;
import org.opendaylight.controller.config.util.xml.DocumentedException.ErrorType;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.w3c.dom.Document;

public class CopyConfigTest extends AbstractNetconfOperationTest {

    @Override
    protected SchemaContext getSchemaContext() {
        return parseYangResources(CopyConfigTest.class,
            "/yang/mdsal-netconf-mapping-test.yang");
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
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_ordered_list_setup.xml"),
            RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_ordered_list_setup_control.xml"));

        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_ordered_list_update.xml"),
            RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_ordered_list_update_control.xml"));
    }

    @Test
    public void testToplevelList() throws Exception {
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_toplevel_list_setup.xml"),
            RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_toplevel_list_setup_control.xml"));

        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_toplevel_list_update.xml"),
            RPC_REPLY_OK);
        verifyResponse(commit(), RPC_REPLY_OK);
        verifyResponse(getConfigRunning(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_toplevel_list_update_control.xml"));
    }

    @Test
    public void testEmptyContainer() throws Exception {
        // Check that empty non-presence container is removed.
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_empty_container.xml"),
            RPC_REPLY_OK);
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
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_top_augmentation.xml"),
            RPC_REPLY_OK);
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

    private Document copyConfig(final String resource) throws Exception {
        final CopyConfig copyConfig = new CopyConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
            getTransactionProvider());
        return executeOperation(copyConfig, resource);
    }
}