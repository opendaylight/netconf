/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.sal.streams.listeners;

import static java.time.Instant.EPOCH;

import java.util.Optional;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.binding.test.AbstractDataBrokerTest;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.PatchCont;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1Builder;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1Key;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListenerAdapterTest extends AbstractDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapterTest.class);

    private static final String JSON_NOTIF_LEAVES_CREATE = withFakeDate("{\"notification\":{\"xmlns\":"
            + "\"urn:ietf:params:xml:ns:netconf:notification:1.0\",\"data-changed-notification\":"
            + "{\"xmlns\":\"urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote\",\"data-change-event\":"
            + "[{\"path\":\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1"
            + "[instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:my-leaf11\",\"data\":"
            + "{\"my-leaf11\":{\"xmlns\":\"instance:identifier:patch:module\",\"content\":\"Jed\"}},\"operation\":"
            + "\"created\"},{\"path\":\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:"
            + "my-list1[instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:name\""
            + ",\"data\":{\"name\":{\"xmlns\":\"instance:identifier:patch:module\",\"content\":\"Althea\"}},"
            + "\"operation\":\"created\"}]},\"eventTime\":\"2017-09-17T11:23:10.323+03:00\"}}");

    private static final String JSON_NOTIF_LEAVES_UPDATE = withFakeDate("{\"notification\":{\"xmlns\":"
            + "\"urn:ietf:params:xml:ns:netconf:notification:1.0\",\"data-changed-notification\":{\"xmlns\":"
            + "\"urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote\",\"data-change-event\":[{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:my-leaf12\",\"data\":"
            + "{\"my-leaf12\":{\"xmlns\":\"instance:identifier:patch:module\",\"content\":\"Bertha\"}},\"operation\":"
            + "\"created\"},{\"path\":\"/instance-identifier-patch-module:patch-cont/"
            + "instance-identifier-patch-module:my-list1[instance-identifier-patch-module:name='Althea']"
            + "/instance-identifier-patch-module:name\",\"data\":{\"name\":{\"xmlns\":"
            + "\"instance:identifier:patch:module\",\"content\":\"Althea\"}},\"operation\":\"updated\"}]},"
            + "\"eventTime\":\"2017-09-18T14:20:54.82+03:00\"}}");

    private static final String JSON_NOTIF_LEAVES_DEL = withFakeDate("{\"notification\":{\"xmlns\":"
            + "\"urn:ietf:params:xml:ns:netconf:notification:1.0\",\"data-changed-notification\":{\"xmlns\":"
            + "\"urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote\",\"data-change-event\":[{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:my-leaf11\","
            + "\"operation\":\"deleted\"},{\"path\":\"/instance-identifier-patch-module:patch-cont/"
            + "instance-identifier-patch-module:my-list1[instance-identifier-patch-module:name='Althea']"
            + "/instance-identifier-patch-module:name\",\"operation\":\"deleted\"},{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:my-leaf12\","
            + "\"operation\":\"deleted\"}]},\"eventTime\":\"2017-09-18T15:30:16.099+03:00\"}}");

    private static final String JSON_NOTIF_CREATE = withFakeDate("{\"notification\":{\"xmlns\":"
            + "\"urn:ietf:params:xml:ns:netconf:notification:1.0\",\"data-changed-notification\":{\"xmlns\":"
            + "\"urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote\",\"data-change-event\":[{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:my-leaf11\",\"data\":"
            + "{\"my-leaf11\":{\"xmlns\":\"instance:identifier:patch:module\",\"content\":\"Jed\"}},\"operation\":"
            + "\"created\"},{\"path\":\"/instance-identifier-patch-module:patch-cont/"
            + "instance-identifier-patch-module:my-list1[instance-identifier-patch-module:name='Althea']"
            + "/instance-identifier-patch-module:name\",\"data\":{\"name\":{\"xmlns\":"
            + "\"instance:identifier:patch:module\",\"content\":\"Althea\"}},\"operation\":\"created\"},{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont\",\"data\":{\"patch-cont\":{\"xmlns\":"
            + "\"instance:identifier:patch:module\",\"my-list1\":{\"name\":\"Althea\",\"my-leaf11\":\"Jed\"}}},"
            + "\"operation\":\"created\"},{\"path\":\"/instance-identifier-patch-module:patch-cont/"
            + "instance-identifier-patch-module:my-list1[instance-identifier-patch-module:name='Althea']\",\"data\":"
            + "{\"my-list1\":{\"xmlns\":\"instance:identifier:patch:module\",\"name\":\"Althea\",\"my-leaf11\":"
            + "\"Jed\"}},\"operation\":\"created\"}]},\"eventTime\":\"2017-09-17T13:32:03.586+03:00\"}}");

    private static final String JSON_NOTIF_UPDATE = withFakeDate("{\"notification\":{\"xmlns\":"
            + "\"urn:ietf:params:xml:ns:netconf:notification:1.0\",\"data-changed-notification\":{\"xmlns\":"
            + "\"urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote\",\"data-change-event\":[{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont\",\"data\":{\"patch-cont\":{\"xmlns\":"
            + "\"instance:identifier:patch:module\",\"my-list1\":{\"my-leaf12\":\"Bertha\",\"name\":\"Althea\","
            + "\"my-leaf11\":\"Jed\"}}},\"operation\":\"updated\"},{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']\",\"data\":{\"my-list1\":{\"xmlns\":"
            + "\"instance:identifier:patch:module\",\"my-leaf12\":\"Bertha\",\"name\":\"Althea\",\"my-leaf11\":"
            + "\"Jed\"}},\"operation\":\"updated\"},{\"path\":\"/instance-identifier-patch-module:patch-cont/"
            + "instance-identifier-patch-module:my-list1[instance-identifier-patch-module:name='Althea']"
            + "/instance-identifier-patch-module:my-leaf12\",\"data\":{\"my-leaf12\":{\"xmlns\":"
            + "\"instance:identifier:patch:module\",\"content\":\"Bertha\"}},\"operation\":\"created\"},"
            + "{\"path\":\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:name\",\"data\":"
            + "{\"name\":{\"xmlns\":\"instance:identifier:patch:module\",\"content\":\"Althea\"}},\"operation\":"
            + "\"updated\"}]},\"eventTime\":\"2017-09-18T15:52:25.213+03:00\"}}");

    private static final String JSON_NOTIF_DEL = withFakeDate("{\"notification\":{\"xmlns\":"
            + "\"urn:ietf:params:xml:ns:netconf:notification:1.0\",\"data-changed-notification\":{\"xmlns\":"
            + "\"urn:opendaylight:params:xml:ns:yang:controller:md:sal:remote\",\"data-change-event\":[{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont\",\"data\":{\"patch-cont\":{\"xmlns\":"
            + "\"instance:identifier:patch:module\"}},\"operation\":\"updated\"},{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']\",\"operation\":\"deleted\"},{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:name\",\"operation\":"
            + "\"deleted\"},{\"path\":\"/instance-identifier-patch-module:patch-cont/"
            + "instance-identifier-patch-module:my-list1[instance-identifier-patch-module:name='Althea']"
            + "/instance-identifier-patch-module:my-leaf12\",\"operation\":\"deleted\"},{\"path\":"
            + "\"/instance-identifier-patch-module:patch-cont/instance-identifier-patch-module:my-list1["
            + "instance-identifier-patch-module:name='Althea']/instance-identifier-patch-module:my-leaf11\","
            + "\"operation\":\"deleted\"}]},\"eventTime\":\"2017-09-17T14:18:53.404+03:00\"}}");

    private static YangInstanceIdentifier PATCH_CONT_YIID =
            YangInstanceIdentifier.create(new YangInstanceIdentifier.NodeIdentifier(PatchCont.QNAME));

    private DataBroker dataBroker;
    private DOMDataBroker domDataBroker;

    @Before
    public void setUp() throws Exception {
        dataBroker = getDataBroker();
        domDataBroker = getDomBroker();
        SchemaContext sc = YangParserTestUtils.parseYangSource(
                "/instanceidentifier/yang/instance-identifier-patch-module.yang");
        ControllerContext.getInstance().setGlobalSchema(sc);
    }

    class ListenerAdapterTester extends ListenerAdapter {

        private String lastNotification = null;

        ListenerAdapterTester(YangInstanceIdentifier path, String streamName,
                              NotificationOutputTypeGrouping.NotificationOutputType outputType, boolean leafNodesOnly) {
            super(path, streamName, outputType);
            setQueryParams(EPOCH, Optional.empty(), Optional.empty(), leafNodesOnly);
        }

        @Override
        protected void post(final Event event) {
            this.lastNotification = event.getData();
        }

        public void assertGot(String json) throws Exception {
            long start = System.currentTimeMillis();
            while (true) {
                if (lastNotification != null) {
                    break;
                }
                if (System.currentTimeMillis() - start > 1000) {
                    throw new Exception("TIMED OUT waiting for notification with " + json);
                }
                Thread.currentThread().sleep(200);
            }
            LOG.debug("Comparing {} {}", json, lastNotification);
            JSONAssert.assertEquals(json, withFakeDate(lastNotification), false);
            this.lastNotification = null;
        }
    }

    static String withFakeDate(String in) {
        JSONObject doc = new JSONObject(in);
        JSONObject notification = doc.getJSONObject("notification");
        if (notification == null) {
            return in;
        }
        notification.put("eventTime", "someDate");
        return doc.toString();
    }

    @Test
    public void testJsonNotifsLeaves() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                                        NotificationOutputTypeGrouping.NotificationOutputType.JSON, true);
        DOMDataTreeChangeService changeService = (DOMDataTreeChangeService)
                domDataBroker.getSupportedExtensions().get(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build(), true);
        writeTransaction.submit();
        adapter.assertGot(JSON_NOTIF_LEAVES_CREATE);

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().setKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build(), true);
        writeTransaction.submit();
        adapter.assertGot(JSON_NOTIF_LEAVES_UPDATE);

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.submit();
        adapter.assertGot(JSON_NOTIF_LEAVES_DEL);
    }

    @Test
    public void testJsonNotifs() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                NotificationOutputTypeGrouping.NotificationOutputType.JSON, false);
        DOMDataTreeChangeService changeService = (DOMDataTreeChangeService)
                domDataBroker.getSupportedExtensions().get(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build(), true);
        writeTransaction.submit();
        adapter.assertGot(JSON_NOTIF_CREATE);

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().setKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build(), true);
        writeTransaction.submit();
        adapter.assertGot(JSON_NOTIF_UPDATE);

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.submit();
        adapter.assertGot(JSON_NOTIF_DEL);
    }
}
