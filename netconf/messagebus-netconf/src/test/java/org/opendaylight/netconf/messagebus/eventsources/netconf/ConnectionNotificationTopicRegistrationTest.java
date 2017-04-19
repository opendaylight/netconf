/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.messagebus.eventsources.netconf;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Set;
import javax.xml.transform.dom.DOMSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.dom.api.DOMNotification;
import org.opendaylight.controller.md.sal.dom.api.DOMNotificationListener;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventaggregator.rev141202.TopicId;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.messagebus.eventsource.rev141202.EventSourceStatus;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;

public class ConnectionNotificationTopicRegistrationTest {

    private ConnectionNotificationTopicRegistration registration;

    @Mock
    private DOMNotificationListener listener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        registration = new ConnectionNotificationTopicRegistration("candidate", listener);
    }

    @Test
    public void testClose() throws Exception {
        registration.setActive(true);
        registration.close();
        Assert.assertFalse(registration.isActive());
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
        final Set<TopicId> notificationTopicIds =
                registration.getTopicsForNotification(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH);
        Assert.assertNotNull(notificationTopicIds);
        Assert.assertThat(notificationTopicIds, hasItems(topic1, topic2, topic3));

        registration.unRegisterNotificationTopic(topic3);
        final Set<TopicId> afterUnregister =
                registration.getTopicsForNotification(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH);
        Assert.assertNotNull(afterUnregister);
        Assert.assertThat(afterUnregister, hasItems(topic1, topic2));
        Assert.assertFalse(afterUnregister.contains(topic3));
    }

    private TopicId registerTopic(String value) {
        final TopicId topic = TopicId.getDefaultInstance(value);
        registration.registerNotificationTopic(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH, topic);
        return topic;
    }


    /**
     * Checks status node of notification received by listener.
     *
     * @param listener listener
     * @param status   expected value
     */
    private static void checkStatus(DOMNotificationListener listener, EventSourceStatus status) {
        ArgumentCaptor<DOMNotification> notificationCaptor = ArgumentCaptor.forClass(DOMNotification.class);
        verify(listener).onNotification(notificationCaptor.capture());
        final DOMNotification value = notificationCaptor.getValue();
        Assert.assertEquals(ConnectionNotificationTopicRegistration.EVENT_SOURCE_STATUS_PATH, value.getType());
        final Collection<DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>> body = value.getBody()
                .getValue();
        Assert.assertEquals(1, body.size());
        final DOMSource source = (DOMSource) body.iterator().next().getValue();
        final String statusNodeValue = source.getNode().getFirstChild().getFirstChild().getNodeValue();
        Assert.assertEquals(status.toString(), statusNodeValue);
    }
}