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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import akka.actor.ActorContext;
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
import org.opendaylight.restconfsb.mountpoint.sal.RestconfNotificationService;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.spi.source.SourceException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangInferencePipeline;
import org.opendaylight.yangtools.yang.parser.stmt.rfc6020.YangStatementSourceImpl;

public class ClusterNotificationDistributorTest {

    private ClusterNotificationDistributor distributor;
    @Mock
    private ActorContext context;
    @Mock
    private NotificationAdapter adapter;
    @Mock
    private RestconfNotificationService notificationService1;
    @Mock
    private RestconfNotificationService notificationService2;
    private ActorRef subscriber;
    private ActorSystem mainActor;
    private DOMNotification notification;

    @Before
    public void setUp() throws FileNotFoundException, ReactorException {
        MockitoAnnotations.initMocks(this);
        doReturn(ActorRef.noSender()).when(context).watch((ActorRef) any());
        mainActor = ActorSystem.apply();
        subscriber = mainActor.actorOf(SlaveNotificationReceiver.create(adapter));
        distributor = new ClusterNotificationDistributor(context);

        final List<InputStream> sources = new ArrayList<>();
        sources.add(this.getClass().getResourceAsStream("/yang/module-0@2016-03-01.yang"));
        final SchemaContext schemaContext = parseYangSources(sources);
        final XmlParser parser = new XmlParser(schemaContext);
        final Scanner s = new Scanner(this.getClass().getResourceAsStream("/xml/notification.xml")).useDelimiter("\\A");
        notification = parser.parseNotification(s.next());
    }

    @Test
    public void addSubscriberTets() {
        distributor.addSubscriber(mainActor.actorOf(SlaveNotificationReceiver.create(notificationService1)));
        distributor.addSubscriber(mainActor.actorOf(SlaveNotificationReceiver.create(notificationService2)));
        distributor.addSubscriber(subscriber);

        doNothing().when(adapter).onNotification((DOMNotification) any());
        doNothing().when(notificationService1).onNotification((DOMNotification) any());
        doNothing().when(notificationService2).onNotification((DOMNotification) any());
        final ArgumentCaptor<DOMNotification> argCaptor = ArgumentCaptor.forClass(DOMNotification.class);

        distributor.onNotification(notification);

        verify(adapter, timeout(2000).times(1)).onNotification(argCaptor.capture());
        verify(notificationService1, timeout(1000).times(1)).onNotification(argCaptor.capture());
        verify(notificationService2, timeout(1000).times(1)).onNotification(argCaptor.capture());
        assertEquals(notification.getBody(), argCaptor.getValue().getBody());
        assertEquals(notification.getType(), argCaptor.getValue().getType());
    }

    @Test
    public void removeSubscriberTets() {
        distributor.addSubscriber(subscriber);
        for (int i = 0; i < 4; i++) {
            distributor.addSubscriber(mainActor.actorOf(SlaveNotificationReceiver.create(new RestconfNotificationService())));
        }

        doNothing().when(adapter).onNotification((DOMNotification) any());
        final ArgumentCaptor<DOMNotification> argCaptor = ArgumentCaptor.forClass(DOMNotification.class);

        distributor.onNotification(notification);
        distributor.removeSubscriber(subscriber);
        distributor.onNotification(notification);
        verify(adapter, timeout(2000).times(1)).onNotification(argCaptor.capture());
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
