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
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.opendaylight.netconf.api.xml.XmlNetconfConstants.SOURCE_KEY;
import static org.opendaylight.netconf.mdsal.connector.ops.AbstractConfigOperation.getConfigElement;
import static org.opendaylight.yangtools.yang.test.util.YangParserTestUtils.parseYangResources;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.api.DocumentedException.ErrorSeverity;
import org.opendaylight.netconf.api.DocumentedException.ErrorTag;
import org.opendaylight.netconf.api.DocumentedException.ErrorType;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.util.test.XmlFileLoader;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.w3c.dom.Document;

@RunWith(PowerMockRunner.class)
@PrepareForTest({URL.class, URLConnection.class, AbstractConfigOperation.class} )
public class CopyConfigUrlCapabilityTest2 extends AbstractNetconfOperationTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    @Override
    protected SchemaContext getSchemaContext() {
        return parseYangResources(CopyConfigUrlCapabilityTest2.class,
            "/yang/mdsal-netconf-mapping-test.yang");
    }

    @Test
    public void testCopyFromURLConnectionFails() throws Exception {
        try {
            final URL url = PowerMockito.mock(URL.class, RETURNS_SMART_NULLS);
            final URLConnection urlConnection = PowerMockito.mock(URLConnection.class, RETURNS_SMART_NULLS);
            PowerMockito.doReturn(urlConnection).when(url).openConnection();
            PowerMockito.doThrow(new IOException("connection failed")).when(urlConnection).getInputStream();

            AbstractConfigOperation.getDocumentFromUrl(url);
            fail("Should have failed - url is not reachable");
        } catch (final DocumentedException e) {
            assertTrue(e.getErrorSeverity() == ErrorSeverity.ERROR);
            assertTrue(e.getErrorTag() == ErrorTag.OPERATION_FAILED);
            assertTrue(e.getErrorType() == ErrorType.APPLICATION);
            assertTrue(e.getCause() instanceof IOException);
        }
    }

    @Test
    public void testCopyFromHttp() throws Exception {
        // 1. Test that if url contains credentials, then auth property is set

        /// 1) test getConfigElement and use whenNew...

//        final Document request = XmlFileLoader.xmlFileToDocument("messages/mapping/copyConfigs/copyConfig_url_http.xml");
//
////        XmlFileLoader.xmlFileToNetconfMessage().getDocument().
//        final XmlElement operationElement = XmlElement.fromDomElement(request.getDocumentElement())
//            .getOnlyChildElementOptionally("copy-config").get();
//        final XmlElement source = operationElement.getOnlyChildElementOptionally("source").get();
//        final XmlElement configElement = getConfigElement(source);



        // FIXME mock web server!
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_url_http.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_from_file_control.xml"));
    }

    @Test
    public void testCopyFromHttpWithCredentials() throws Exception {
        // FIXME mock web server!
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_url_http_with_credentials.xml"),
            RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_from_file_control.xml"));
    }

    @Test
    public void testCopyFromHttps() throws Exception {
        // https://stackoverflow.com/questions/2113117/web-server-for-testing-on-linux

        // FIXME mock web server!
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_url_https.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_from_file_control.xml"));
    }

    @Test
    public void testCopyFromFtp() throws Exception {
        // FIXME mock ftp server!
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_url_ftp.xml"), RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_from_file_control.xml"));
    }

    @Test
    public void testCopyFromFtpWithCredentials() throws Exception {
        // FIXME mock ftp server!
        verifyResponse(copyConfig("messages/mapping/copyConfigs/copyConfig_url_ftp_with_credentials.xml"),
            RPC_REPLY_OK);
        verifyResponse(getConfigCandidate(), XmlFileLoader.xmlFileToDocument(
            "messages/mapping/copyConfigs/copyConfig_from_file_control.xml"));
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

