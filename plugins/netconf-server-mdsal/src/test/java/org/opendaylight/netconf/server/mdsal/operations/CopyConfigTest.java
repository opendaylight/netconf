/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URI;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.test.util.XmlFileLoader;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class CopyConfigTest extends AbstractNetconfOperationTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Test
    public void testTargetMissing() {
        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig("messages/mapping/copyConfigs/copyConfig_no_target.xml"));

        assertEquals(e.getErrorSeverity(),ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.MISSING_ATTRIBUTE);
        assertEquals(e.getErrorType(), ErrorType.PROTOCOL);
    }

    @Test
    public void testSourceMissing() {
        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig("messages/mapping/copyConfigs/copyConfig_no_source.xml"));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.MISSING_ELEMENT);
        assertEquals(e.getErrorType(), ErrorType.PROTOCOL);
    }

    @Test
    public void testConfigMissing() {
        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig("messages/mapping/copyConfigs/copyConfig_no_config.xml"));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.MISSING_ELEMENT);
        assertEquals(e.getErrorType(), ErrorType.PROTOCOL);
    }

    @Test
    public void testRunning() {
        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig("messages/mapping/copyConfigs/copyConfig_running.xml"));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.OPERATION_NOT_SUPPORTED);
        assertEquals(e.getErrorType(), ErrorType.PROTOCOL);
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

    @Test
    public void testConfigFromFile() throws Exception {
        // Ask class loader for URI of config file and use it as <url> in <copy-config> RPC:
        final String template = XmlFileLoader.fileToString("messages/mapping/copyConfigs/copyConfig_from_file.xml");
        final URI uri = getClass().getClassLoader()
            .getResource("messages/mapping/copyConfigs/config_file_valid.xml").toURI();
        final String copyConfig = template.replaceFirst("URL", uri.toString());
        final Document request = XmlUtil.readXmlToDocument(copyConfig);

        verifyResponse(copyConfig(request), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_from_file_control.xml"));
    }

    @Test
    public void testConfigFromInvalidUrl() {
        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig("messages/mapping/copyConfigs/copyConfig_invalid_url.xml"));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.INVALID_VALUE);
        assertEquals(e.getErrorType(), ErrorType.APPLICATION);
        assertTrue(e.getCause() instanceof MalformedURLException);
    }

    @Test
    public void testExternalConfigInvalid() throws Exception {
        // Ask class loader for URI of config file and use it as <url> in <copy-config> RPC:
        final String template = XmlFileLoader.fileToString("messages/mapping/copyConfigs/copyConfig_from_file.xml");
        final URI uri = getClass().getClassLoader()
                .getResource("messages/mapping/copyConfigs/config_file_invalid.xml").toURI();
        final String copyConfig = template.replaceFirst("URL", uri.toString());
        final Document request = XmlUtil.readXmlToDocument(copyConfig);

        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig(request));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.OPERATION_FAILED);
        assertEquals(e.getErrorType(), ErrorType.APPLICATION);
        assertTrue(e.getCause() instanceof SAXException);
    }

    @Test
    public void testCopyToFile() throws Exception {
        // Initialize config:
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_top_modules.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_top_modules_control.xml"));

        // Load copy-config template and replace URL with the URI of target file:
        final String template = XmlFileLoader.fileToString("messages/mapping/copyConfigs/copyConfig_to_file.xml");
        final File outFile = new File(tmpDir.getRoot(),"test-copy-to-file.xml");
        final String copyConfig = template.replaceFirst("URL", outFile.toURI().toString());
        final Document request = XmlUtil.readXmlToDocument(copyConfig);

        // Invoke copy-config RPC:
        verifyResponse(copyConfig(request), RPC_REPLY_OK);

        // Check if outFile was created with expected content:
        verifyResponse(XmlUtil.readXmlToDocument(new FileInputStream(outFile)),
            XmlFileLoader.xmlFileToDocument("messages/mapping/copyConfigs/copyConfig_to_file_control.xml"));
    }

    @Test
    public void testUnsupportedTargetUrlProtocol() {
        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig("messages/mapping/copyConfigs/copyConfig_to_unsupported_url_protocol.xml"));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.OPERATION_NOT_SUPPORTED);
        assertEquals(e.getErrorType(), ErrorType.PROTOCOL);
    }

    @Test
    public void testCopyToFileFromRunning() throws Exception {
        // Load copy-config template and replace URL with the URI of target file:
        final String template =
            XmlFileLoader.fileToString("messages/mapping/copyConfigs/copyConfig_to_file_from_running.xml");
        final File outFile = new File(tmpDir.getRoot(),"test-copy-to-file-from-running.xml");
        final String copyConfig = template.replaceFirst("URL", outFile.toURI().toString());
        final Document request = XmlUtil.readXmlToDocument(copyConfig);

        // Invoke copy-config RPC:
        verifyResponse(copyConfig(request), RPC_REPLY_OK);

        // Check if outFile was created with expected content:
        verifyResponse(XmlUtil.readXmlToDocument(new FileInputStream(outFile)),
            XmlFileLoader.xmlFileToDocument(
                "messages/mapping/copyConfigs/copyConfig_to_file_from_running_control.xml"));

    }

    @Test
    public void testRemoteToRemoteOperationIsNotSupported() {
        final DocumentedException e = assertThrows(DocumentedException.class,
                () -> copyConfig("messages/mapping/copyConfigs/copyConfig_url_remote_to_remote.xml"));

        assertEquals(e.getErrorSeverity(), ErrorSeverity.ERROR);
        assertEquals(e.getErrorTag(), ErrorTag.OPERATION_NOT_SUPPORTED);
        assertEquals(e.getErrorType(), ErrorType.PROTOCOL);
    }

    private Document copyConfig(final String resource) throws Exception {
        final CopyConfig copyConfig = new CopyConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
            getTransactionProvider());
        return executeOperation(copyConfig, resource);
    }

    private Document copyConfig(final Document request) throws Exception {
        final CopyConfig copyConfig = new CopyConfig(SESSION_ID_FOR_REPORTING, getCurrentSchemaContext(),
            getTransactionProvider());
        return executeOperation(copyConfig, request);
    }
}
