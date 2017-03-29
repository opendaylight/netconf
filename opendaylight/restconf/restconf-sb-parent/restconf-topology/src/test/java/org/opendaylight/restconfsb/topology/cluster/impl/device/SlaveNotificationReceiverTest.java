/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconfsb.topology.cluster.impl.device;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.restconfsb.communicator.impl.xml.draft04.XmlParser;
import org.opendaylight.restconfsb.topology.cluster.impl.messages.NotificationMessage;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.spi.source.SourceException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangStatementSourceImpl;

public class SlaveNotificationReceiverTest {

    @Mock
    private NotificationAdapter adapter;
    private ActorRef notificationReceiver;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        final ActorSystem system = ActorSystem.apply();
        notificationReceiver = system.actorOf(SlaveNotificationReceiver.create(adapter));
    }

    @Test
    public void onReceiveNotificationTest() throws FileNotFoundException, ReactorException {
        final List<InputStream> sources = new ArrayList<>();
        sources.add(this.getClass().getResourceAsStream("/yang/module-0@2016-03-01.yang"));
        final SchemaContext schemaContext = parseYangSources(sources);
        final XmlParser parser = new XmlParser(schemaContext);
        final Scanner s = new Scanner(this.getClass().getResourceAsStream("/xml/notification.xml")).useDelimiter("\\A");
        final DOMNotification notification = parser.parseNotification(s.next());
        final NotificationMessage message = new NotificationMessage(notification);

        doNothing().when(adapter).onNotification((DOMNotification) any());
        notificationReceiver.tell(message, ActorRef.noSender());
        final ArgumentCaptor<DOMNotification> argCaptor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(adapter, timeout(2000).atLeastOnce()).onNotification(argCaptor.capture());
        assertEquals(notification.getBody(), argCaptor.getValue().getBody());
        assertEquals(notification.getType(), argCaptor.getValue().getType());
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
