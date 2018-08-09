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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.xml.XmlUtil;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class CopyConfigUrlCapabilityTest extends AbstractNetconfOperationTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Override
    protected SchemaContext getSchemaContext() {
        return parseYangResources(CopyConfigUrlCapabilityTest.class,
            "/yang/mdsal-netconf-mapping-test.yang");
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
    public void testConfigFromInvalidUrl() throws Exception {
        try {
            copyConfig("messages/mapping/copyConfigs/copyConfig_invalid_url.xml");
            fail("Should have failed - provided <url> is not valid");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.INVALID_VALUE);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
            assertTrue(e.getCause() instanceof MalformedURLException);
        }
    }

    @Test
    public void testExternalConfigInvalid() throws Exception {
        try {
            // Ask class loader for URI of config file and use it as <url> in <copy-config> RPC:
            final String template = XmlFileLoader.fileToString("messages/mapping/copyConfigs/copyConfig_from_file.xml");
            final URI uri = getClass().getClassLoader()
                .getResource("messages/mapping/copyConfigs/config_file_invalid.xml").toURI();
            final String copyConfig = template.replaceFirst("URL", uri.toString());
            final Document request = XmlUtil.readXmlToDocument(copyConfig);
            copyConfig(request);
            fail("Should have failed - provided config is not valid XML");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_FAILED);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
            assertTrue(e.getCause() instanceof SAXException);
        }
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
    public void testUnsupportedTargetUrlProtocol() throws Exception {
        try {
            copyConfig("messages/mapping/copyConfigs/copyConfig_to_unsupported_url_protocol.xml");
            fail("Should have failed - exporting config to http server is not supported");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_NOT_SUPPORTED);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
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
    public void testRemoteToRemoteOperationIsNotSupported() throws Exception {
        try {
            copyConfig("messages/mapping/copyConfigs/copyConfig_url_remote_to_remote.xml");
            fail("Should have failed - remote to remote operations are not supported");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_NOT_SUPPORTED);
            assertTrue(e.getErrorType() == ErrorType.PROTOCOL);
        }
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
