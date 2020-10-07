/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMNotification;
import org.opendaylight.mdsal.dom.api.DOMNotificationListener;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.EventSourceStatus;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ConnectionNotificationTopicRegistrationTest {

    private ConnectionNotificationTopicRegistration registration;

    @Mock
    private DOMNotificationListener listener;

    @Before
    public void setUp() {
        registration = new ConnectionNotificationTopicRegistration("candidate", listener);
    }

    @Test
    public void testClose() throws Exception {
        registration.setActive(true);
        registration.close();
        assertFalse(registration.isActive());
        checkStatus(listener, EventSourceStatus.Deactive);
    }

    @Test
    public void testActivateNotificationSource() throws Exception {
        registration.activateNotificationSource();
        checkStatus(listener, EventSourceStatus.Active);
    }

    @Test
    public void testDeActivateNotificationSource() throws Exception {
        registration.deActivateNotificationSource();
        checkStatus(listener, EventSourceStatus.Inactive);
    }

    @Test
    public void testReActivateNotificationSource() throws Exception {
        registration.reActivateNotificationSource();
        checkStatus(listener, EventSourceStatus.Active);
    }

    @Test
    public void testRegisterAndUnregisterNotificationTopic() throws Exception {
        final TopicId topic1 = registerTopic("topic1");
        final TopicId topic2 = registerTopic("topic2");
        final TopicId topic3 = registerTopic("topic3");
        final Set<TopicId> notificationTopicIds = registration.getTopicsForNotification(
            ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH.asSchemaPath());
        assertNotNull(notificationTopicIds);
        assertThat(notificationTopicIds, hasItems(topic1, topic2, topic3));

        registration.unRegisterNotificationTopic(topic3);
        final Set<TopicId> afterUnregister = registration.getTopicsForNotification(
            ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH.asSchemaPath());
        assertNotNull(afterUnregister);
        assertThat(afterUnregister, hasItems(topic1, topic2));
        assertFalse(afterUnregister.contains(topic3));
    }

    private TopicId registerTopic(final String value) {
        final TopicId topic = TopicId.getDefaultInstance(value);
        registration.registerNotificationTopic(
            ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH.asSchemaPath(), topic);
        return topic;
    }


    /**
     * Checks status node of notification received by listener.
     *
     * @param listener listener
     * @param status   expected value
     */
    private static void checkStatus(final DOMNotificationListener listener, final EventSourceStatus status) {
        ArgumentCaptor<DOMNotification> notificationCaptor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(listener).onNotification(notificationCaptor.capture());
        final DOMNotification value = notificationCaptor.getValue();
        assertEquals(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH, value.getType());
        final Collection<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> body =
            value.getBody().getValue();
        assertEquals(1, body.size());
        final DOMSource source = (DOMSource) body.iterator().next().getValue();
        final String statusNodeValue = source.getNode().getFirstChild().getFirstChild().getNodeValue();
        assertEquals(status.toString(), statusNodeValue);
    }
}