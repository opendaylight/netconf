/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.communicator.impl;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.restconfsb.communicator.api.parser.Parser;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.XmlParser;
import org.opendaylight.restconfsb.mountpoint.sal.RestconfNotificationService;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.spi.source.SourceException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangStatementSourceImpl;

public class RestconfStreamsHandlerTest {

    @Mock
    private RestconfNotificationService notificationService;

    @Test
    public void onMessageTest() throws FileNotFoundException, ReactorException {
        MockitoAnnotations.initMocks(this);
        final List<InputStream> sources = new ArrayList<>();

        sources.add(getClass().getResourceAsStream("/yang/module-0@2016-03-01.yang"));
        sources.add(getClass().getResourceAsStream("/yang/ietf-yang-types@2010-09-24.yang"));
        final SchemaContext schemaContext = parseYangSources(sources);
        final Parser parser = new XmlParser(schemaContext);
        final RestconfStreamsHandler handler = new RestconfStreamsHandler(parser);
        handler.registerListener(notificationService);

        final Scanner s = new Scanner(getClass().getResourceAsStream("/xml/notification.xml")).useDelimiter("\\A");
        final String message = s.next();
        handler.onMessage(message);
        final DOMNotification notification = parser.parseNotification(message);
        Mockito.verify(notificationService).onNotification(notification);
    }

    private static SchemaContext parseYangSources(final Collection<InputStream> testFiles)
            throws SourceException, ReactorException, FileNotFoundException {
        final CrossSourceStatementReactor.BuildAction reactor = YangInferencePipeline.RFC6020_REACTOR
                .newBuild();
        for (final InputStream testFile : testFiles) {
            reactor.addSource(new YangStatementSourceImpl(testFile));
        }
        return reactor.buildEffective();
    }
}
