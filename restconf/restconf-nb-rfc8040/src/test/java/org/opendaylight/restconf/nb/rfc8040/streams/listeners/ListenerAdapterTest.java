/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static java.time.Instant.EPOCH;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.nb.rfc8040.handlers.SchemaContextHandler;
import org.opendaylight.restconf.nb.rfc8040.handlers.TransactionChainHandler;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.PatchCont;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1Builder;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1Key;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.assertj.XmlAssert;

public class ListenerAdapterTest extends AbstractConcurrentDataBrokerTest {
    private static final Logger LOG = LoggerFactory.getLogger(ListenerAdapterTest.class);

    private static final String JSON_NOTIF_LEAVES_CREATE = "/listener-adapter-test/notif-leaves-create.json";
    private static final String JSON_NOTIF_LEAVES_UPDATE =  "/listener-adapter-test/notif-leaves-update.json";
    private static final String JSON_NOTIF_LEAVES_DEL =  "/listener-adapter-test/notif-leaves-del.json";
    private static final String JSON_NOTIF_CREATE = "/listener-adapter-test/notif-create.json";
    private static final String JSON_NOTIF_UPDATE = "/listener-adapter-test/notif-update.json";
    private static final String JSON_NOTIF_DEL = "/listener-adapter-test/notif-del.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_CREATE =
            "/listener-adapter-test/notif-without-data-create.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_UPDATE =
            "/listener-adapter-test/notif-without-data-update.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_DELETE =
            "/listener-adapter-test/notif-without-data-del.json";

    private static final String XML_NOTIF_CREATE = "/listener-adapter-test/notif-create.xml";
    private static final String XML_NOTIF_UPDATE =  "/listener-adapter-test/notif-update.xml";
    private static final String XML_NOTIF_DEL =  "/listener-adapter-test/notif-delete.xml";

    private static final String XML_NOTIF_LEAVES_CREATE = "/listener-adapter-test/notif-leaves-create.xml";
    private static final String XML_NOTIF_LEAVES_UPDATE =  "/listener-adapter-test/notif-leaves-update.xml";
    private static final String XML_NOTIF_LEAVES_DEL =  "/listener-adapter-test/notif-leaves-delete.xml";

    private static final String XML_NOTIF_WITHOUT_DATA_CREATE =
            "/listener-adapter-test/notif-without-data-create.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_UPDATE =
            "/listener-adapter-test/notif-without-data-update.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_DELETE =
            "/listener-adapter-test/notif-without-data-delete.xml";

    private static final YangInstanceIdentifier PATCH_CONT_YIID =
            YangInstanceIdentifier.create(new YangInstanceIdentifier.NodeIdentifier(PatchCont.QNAME));

    private static EffectiveModelContext SCHEMA_CONTEXT;

    private DataBroker dataBroker;
    private DOMDataBroker domDataBroker;
    private TransactionChainHandler transactionChainHandler;
    private SchemaContextHandler schemaContextHandler;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResource(
                "/instanceidentifier/yang/instance-identifier-patch-module.yang");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        dataBroker = getDataBroker();
        domDataBroker = getDomBroker();

        transactionChainHandler = new TransactionChainHandler(domDataBroker);
        schemaContextHandler = new SchemaContextHandler(transactionChainHandler, Mockito.mock(DOMSchemaService.class));
        schemaContextHandler.onModelContextUpdated(SCHEMA_CONTEXT);
    }

    class ListenerAdapterTester extends ListenerAdapter {

        private volatile String lastNotification;
        private CountDownLatch notificationLatch = new CountDownLatch(1);

        ListenerAdapterTester(final YangInstanceIdentifier path, final String streamName,
                              final NotificationOutputTypeGrouping.NotificationOutputType outputType,
                              final boolean leafNodesOnly, final boolean skipNotificationData) {
            super(path, streamName, outputType);
            setQueryParams(EPOCH, null, null, leafNodesOnly, skipNotificationData);
        }

        @Override
        protected void post(final String data) {
            this.lastNotification = data;
            notificationLatch.countDown();
        }

        public void assertGot(final String json) throws JSONException {
            if (!Uninterruptibles.awaitUninterruptibly(notificationLatch, 500, TimeUnit.SECONDS)) {
                fail("Timed out waiting for notification for: " + json);
            }

            LOG.info("lastNotification: {}", lastNotification);
            final String withFakeDate = withFakeDate(lastNotification);
            LOG.info("Comparing: \n{}\n{}", json, withFakeDate);

            JSONAssert.assertEquals(json, withFakeDate, false);
            this.lastNotification = null;
            notificationLatch = new CountDownLatch(1);
        }

        public void assertXmlSimilar(String xml) {
            awaitUntillNotification(xml);

            LOG.info("lastNotification: {}", lastNotification);
            final String withFakeDate = withFakeXmlDate(lastNotification);
            LOG.info("Comparing: \n{}\n{}", xml, withFakeDate);

            XmlAssert.assertThat(xml).and(withFakeDate).ignoreWhitespace().ignoreChildNodesOrder().areSimilar();
            this.lastNotification = null;
            notificationLatch = new CountDownLatch(1);
        }

        public String awaitUntillNotification(String xml) {
            if (!Uninterruptibles.awaitUninterruptibly(notificationLatch, 500, TimeUnit.SECONDS)) {
                fail("Timed out waiting for notification for: " + xml);
            }
            return lastNotification;
        }

        public void resetLatch() {
            notificationLatch = new CountDownLatch(1);
        }
    }

    static String withFakeDate(final String in) throws JSONException {
        final JSONObject doc = new JSONObject(in);
        final JSONObject notification = doc.getJSONObject("notification");
        if (notification == null) {
            return in;
        }
        notification.put("event-time", "someDate");
        return doc.toString();
    }

    static String withFakeXmlDate(String in) {
        return in.replaceAll("<eventTime>.*</eventTime>", "<eventTime>someDate</eventTime>");
    }

    private String getNotifJson(final String path) throws IOException, URISyntaxException, JSONException {
        final URL url = getClass().getResource(path);
        final byte[] bytes = Files.readAllBytes(Paths.get(url.toURI()));
        return withFakeDate(new String(bytes, StandardCharsets.UTF_8));
    }

    private String getResultXml(final String path) throws IOException, URISyntaxException, JSONException {
        final URL url = getClass().getResource(path);
        final byte[] bytes = Files.readAllBytes(Paths.get(url.toURI()));
        return withFakeXmlDate(new String(bytes, StandardCharsets.UTF_8));
    }

    @Test
    public void testJsonNotifsLeaves() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                NotificationOutputTypeGrouping.NotificationOutputType.JSON, true, false);
        adapter.setCloseVars(transactionChainHandler, schemaContextHandler);

        final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        final DOMDataTreeIdentifier root =
                new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        final InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.mergeParentStructurePut(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_LEAVES_DEL));
    }

    @Test
    public void testJsonNotifs() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                NotificationOutputTypeGrouping.NotificationOutputType.JSON, false, false);
        adapter.setCloseVars(transactionChainHandler, schemaContextHandler);

        final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        final DOMDataTreeIdentifier root =
                new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        final InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.mergeParentStructurePut(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_DEL));
    }

    @Test
    public void testJsonNotifsWithoutData() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                NotificationOutputTypeGrouping.NotificationOutputType.JSON, false, true);
        adapter.setCloseVars(transactionChainHandler, schemaContextHandler);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.mergeParentStructurePut(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_WITHOUT_DATA_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_WITHOUT_DATA_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_WITHOUT_DATA_DELETE));
    }

    @Test
    public void testXmlNotifications() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                NotificationOutputTypeGrouping.NotificationOutputType.XML, false, false);
        adapter.setCloseVars(transactionChainHandler, schemaContextHandler);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.mergeParentStructurePut(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_DEL));
    }

    @Test
    public void testXmlSkipData() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                NotificationOutputTypeGrouping.NotificationOutputType.XML, false, true);
        adapter.setCloseVars(transactionChainHandler, schemaContextHandler);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.mergeParentStructurePut(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_WITHOUT_DATA_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_WITHOUT_DATA_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_WITHOUT_DATA_DELETE));
    }

    @Test
    public void testXmlLeavesOnly() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey",
                NotificationOutputTypeGrouping.NotificationOutputType.XML, true, false);
        adapter.setCloseVars(transactionChainHandler, schemaContextHandler);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.mergeParentStructurePut(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.mergeParentStructureMerge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();

        // xmlunit cannot compare deeper children it seems out of the box so just check the iid encoding
        final String notification = adapter.awaitUntillNotification("");
        assertTrue(notification.contains("instance-identifier-patch-module:my-leaf12"));
        assertTrue(notification.contains("instance-identifier-patch-module:my-leaf11"));
        assertTrue(notification.contains("instance-identifier-patch-module:name"));
    }
}
