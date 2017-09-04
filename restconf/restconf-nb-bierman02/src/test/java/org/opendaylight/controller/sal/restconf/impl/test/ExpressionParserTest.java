/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.sal.restconf.impl.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.streams.listeners.ListenerAdapter;
import org.opendaylight.netconf.sal.streams.listeners.Notificator;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;

public class ExpressionParserTest {

    private Collection<File> xmls;

    @Before
    public void setup() throws Exception {
        this.xmls = TestRestconfUtils.loadFiles("/notifications/xml/output/");
    }

    @Test
    public void trueDownFilterTest() throws Exception {
        final boolean parser =
                parser("notification/data-changed-notification/data-change-event/data/toasterStatus='down'",
                        "data_change_notification_toaster_status_DOWN.xml");
        Assert.assertTrue(parser);
    }

    @Test
    public void falseDownFilterTest() throws Exception {
        final boolean parser =
                parser("notification/data-changed-notification/data-change-event/data/toasterStatus='up'",
                        "data_change_notification_toaster_status_DOWN.xml");
        Assert.assertFalse(parser);
    }

    @Test
    public void trueNumberEqualsFilterTest() throws Exception {
        final boolean parser = parser(
                "notification/data-changed-notification/data-change-event/data/toasterStatus=1",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertTrue(parser);
    }

    @Test
    public void falseNumberEqualsFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus=0",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertFalse(parser);
    }

    @Test
    public void trueNumberLessFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus<2",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertTrue(parser);
    }

    @Test
    public void falseNumberLessFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus<0",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertFalse(parser);
    }

    @Test
    public void trueNumberLessEqualsFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus<=2",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertTrue(parser);
    }

    @Test
    public void falseNumberLessEqualsFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus<=-1",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertFalse(parser);
    }

    @Test
    public void trueNumberGreaterFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus>0",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertTrue(parser);
    }

    @Test
    public void falseNumberGreaterFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus>5",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertFalse(parser);
    }

    @Test
    public void trueNumberGreaterEqualsFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus>=0",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertTrue(parser);
    }

    @Test
    public void falseNumberGreaterEqualsFilterTest() throws Exception {
        final boolean parser = parser("notification/data-changed-notification/data-change-event/data/toasterStatus>=5",
                "data_change_notification_toaster_status_NUMBER.xml");
        Assert.assertFalse(parser);
    }

    private boolean parser(final String filter, final String fileName) throws Exception {
        File xml = null;
        for (final File file : this.xmls) {
            if (file.getName().equals(fileName)) {
                xml = file;
            }
        }
        final YangInstanceIdentifier path = Mockito.mock(YangInstanceIdentifier.class);
        final PathArgument pathValue = NodeIdentifier.create(QName.create("module", "2016-14-12", "localName"));
        Mockito.when(path.getLastPathArgument()).thenReturn(pathValue);
        final ListenerAdapter listener = Notificator.createListener(path, "streamName", NotificationOutputType.JSON);
        listener.setQueryParams(Instant.now(), Optional.empty(), Optional.ofNullable(filter), false);

        // FIXME: do not use reflection here
        final Class<?> superclass = listener.getClass().getSuperclass().getSuperclass();
        Method method = null;
        for (final Method met : superclass.getDeclaredMethods()) {
            if (met.getName().equals("parseFilterParam")) {
                method = met;
            }
        }
        if (method == null) {
            throw new Exception("Methode parseFilterParam doesn't exist in " + superclass.getName());
        }
        method.setAccessible(true);
        return (boolean) method.invoke(listener, readFile(xml));
    }

    private static String readFile(final File xml) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(xml))) {
            final StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        }
    }

}
