/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams.listeners;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.net.URISyntaxException;
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
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeChangeService;
import org.opendaylight.mdsal.dom.api.DOMDataTreeIdentifier;
import org.opendaylight.restconf.api.query.ChangedLeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.LeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.api.query.StartTimeParam;
import org.opendaylight.restconf.nb.rfc8040.NotificationQueryParams;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindProvider;
import org.opendaylight.yang.gen.v1.augment.instance.identifier.patch.module.rev220218.PatchCont1Builder;
import org.opendaylight.yang.gen.v1.augment.instance.identifier.patch.module.rev220218.patch.cont.patch.choice1.PatchCase1Builder;
import org.opendaylight.yang.gen.v1.augment.instance.identifier.patch.module.rev220218.patch.cont.patch.choice2.PatchCase11Builder;
import org.opendaylight.yang.gen.v1.augment.instance.identifier.patch.module.rev220218.patch.cont.patch.choice2.patch.case11.patch.sub.choice11.PatchSubCase11Builder;
import org.opendaylight.yang.gen.v1.augment.instance.identifier.patch.module.rev220218.patch.cont.patch.choice2.patch.case11.patch.sub.choice11.patch.sub.case11.patch.sub.sub.choice11.PatchSubSubCase11Builder;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.PatchCont;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.PatchContBuilder;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1Builder;
import org.opendaylight.yang.gen.v1.instance.identifier.patch.module.rev151121.patch.cont.MyList1Key;
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev140708.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.util.BindingMap;
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
    private static final String JSON_NOTIF_CHANGED_LEAVES_CREATE =
            "/listener-adapter-test/notif-changed-leaves-create.json";
    private static final String JSON_NOTIF_CHANGED_LEAVES_UPDATE =
            "/listener-adapter-test/notif-changed-leaves-update.json";
    private static final String JSON_NOTIF_CHANGED_LEAVES_DEL =
            "/listener-adapter-test/notif-changed-leaves-del.json";
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
    private static final String XML_NOTIF_CHANGED_LEAVES_CREATE =
            "/listener-adapter-test/notif-changed-leaves-create.xml";
    private static final String XML_NOTIF_CHANGED_LEAVES_UPDATE =
            "/listener-adapter-test/notif-changed-leaves-update.xml";
    private static final String XML_NOTIF_CHANGED_LEAVES_DEL =
            "/listener-adapter-test/notif-changed-leaves-delete.xml";

    private static final String XML_NOTIF_WITHOUT_DATA_CREATE =
            "/listener-adapter-test/notif-without-data-create.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_UPDATE =
            "/listener-adapter-test/notif-without-data-update.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_DELETE =
            "/listener-adapter-test/notif-without-data-delete.xml";

    private static final YangInstanceIdentifier PATCH_CONT_YIID = YangInstanceIdentifier.of(PatchCont.QNAME);

    private static EffectiveModelContext SCHEMA_CONTEXT;

    private DataBroker dataBroker;
    private DOMDataBroker domDataBroker;
    private DatabindProvider databindProvider;

    @BeforeClass
    public static void beforeClass() {
        SCHEMA_CONTEXT = YangParserTestUtils.parseYangResourceDirectory("/instanceidentifier/yang");
    }

    @AfterClass
    public static void afterClass() {
        SCHEMA_CONTEXT = null;
    }

    @Before
    public void setUp() throws Exception {
        dataBroker = getDataBroker();
        domDataBroker = getDomBroker();
        databindProvider = () -> DatabindContext.ofModel(SCHEMA_CONTEXT);
    }

    class ListenerAdapterTester extends ListenerAdapter {

        private volatile String lastNotification;
        private CountDownLatch notificationLatch = new CountDownLatch(1);

        ListenerAdapterTester(final YangInstanceIdentifier path, final String streamName,
                              final NotificationOutputType outputType,
                              final boolean leafNodesOnly, final boolean skipNotificationData) {
            super(path, streamName, outputType);
            setQueryParams(NotificationQueryParams.of(StartTimeParam.forUriValue("1970-01-01T00:00:00Z"), null, null,
                LeafNodesOnlyParam.of(leafNodesOnly), SkipNotificationDataParam.of(skipNotificationData), null));
        }

        ListenerAdapterTester(final YangInstanceIdentifier path, final String streamName,
                              final NotificationOutputType outputType,
                              final boolean changedLeafNodesOnly) {
            super(path, streamName, outputType);
            setQueryParams(NotificationQueryParams.of(StartTimeParam.forUriValue("1970-01-01T00:00:00Z"), null, null,
                    null, null, ChangedLeafNodesOnlyParam.of(changedLeafNodesOnly)));
        }

        @Override
        protected void post(final String data) {
            lastNotification = data;
            notificationLatch.countDown();
        }

        public void assertGot(final String json) throws JSONException {
            // FIXME: use awaitility
            if (!Uninterruptibles.awaitUninterruptibly(notificationLatch, 500, TimeUnit.SECONDS)) {
                fail("Timed out waiting for notification for: " + json);
            }

            LOG.info("lastNotification: {}", lastNotification);
            final String withFakeDate = withFakeDate(lastNotification);
            LOG.info("Comparing: \n{}\n{}", json, withFakeDate);

            JSONAssert.assertEquals(json, withFakeDate, false);
            lastNotification = null;
            notificationLatch = new CountDownLatch(1);
        }

        public void assertXmlSimilar(final String xml) {
            awaitUntilNotification(xml);

            LOG.info("lastNotification: {}", lastNotification);
            final String withFakeDate = withFakeXmlDate(lastNotification);
            LOG.info("Comparing: \n{}\n{}", xml, withFakeDate);

            XmlAssert.assertThat(xml).and(withFakeDate).ignoreWhitespace().ignoreChildNodesOrder().areSimilar();
            lastNotification = null;
            notificationLatch = new CountDownLatch(1);
        }

        public String awaitUntilNotification(final String xml) {
            // FIXME: use awaitility
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
        final JSONObject notification =
                doc.getJSONObject("urn-ietf-params-xml-ns-netconf-notification-1.0:notification");
        if (notification == null) {
            return in;
        }
        notification.put("event-time", "someDate");
        return doc.toString();
    }

    static String withFakeXmlDate(final String in) {
        return in.replaceAll("<eventTime>.*</eventTime>", "<eventTime>someDate</eventTime>");
    }

    private String getNotifJson(final String path) throws IOException, URISyntaxException, JSONException {
        return withFakeDate(Files.readString(Paths.get(getClass().getResource(path).toURI())));
    }

    private String getResultXml(final String path) throws IOException, URISyntaxException, JSONException {
        return withFakeXmlDate(Files.readString(Paths.get(getClass().getResource(path).toURI())));
    }

    @Test
    public void testJsonNotifsLeaves() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.JSON,
            true, false);
        adapter.setCloseVars(domDataBroker, databindProvider);

        final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        final DOMDataTreeIdentifier root =
                new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<PatchCont> iid = InstanceIdentifier.create(PatchCont.class);
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceLeaf").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceUpdate").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf12("Bertha").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_LEAVES_DEL));
    }

    @Test
    public void testJsonNotifsChangedLeaves() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.JSON,
                true);
        adapter.setCloseVars(domDataBroker, databindProvider);

        final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        final DOMDataTreeIdentifier root =
                new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<PatchCont> iid = InstanceIdentifier.create(PatchCont.class);
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice2(new PatchCase11Builder()
                    .setPatchSubChoice11(new PatchSubCase11Builder()
                        .setPatchSubSubChoice11(new PatchSubSubCase11Builder().setCaseLeaf11("ChoiceLeaf").build())
                        .build())
                    .build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_CHANGED_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice2(new PatchCase11Builder()
                    .setPatchSubChoice11(new PatchSubCase11Builder()
                        .setPatchSubSubChoice11(new PatchSubSubCase11Builder().setCaseLeaf11("ChoiceUpdate").build())
                        .build())
                    .build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf12("Bertha").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_CHANGED_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_CHANGED_LEAVES_DEL));
    }

    @Test
    public void testJsonNotifs() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.JSON,
            false, false);
        adapter.setCloseVars(domDataBroker, databindProvider);

        final DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        final DOMDataTreeIdentifier root =
                new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);

        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        final InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_DEL));
    }

    @Test
    public void testJsonNotifsWithoutData() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.JSON,
            false, true);
        adapter.setCloseVars(domDataBroker, databindProvider);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_WITHOUT_DATA_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_WITHOUT_DATA_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertGot(getNotifJson(JSON_NOTIF_WITHOUT_DATA_DELETE));
    }

    @Test
    public void testXmlNotifications() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.XML,
            false, false);
        adapter.setCloseVars(domDataBroker, databindProvider);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_DEL));
    }

    @Test
    public void testXmlSkipData() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.XML,
            false, true);
        adapter.setCloseVars(domDataBroker, databindProvider);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        MyList1Builder builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        InstanceIdentifier<MyList1> iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_WITHOUT_DATA_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_WITHOUT_DATA_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_WITHOUT_DATA_DELETE));
    }

    @Test
    public void testXmlLeavesOnly() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.XML,
            true, false);
        adapter.setCloseVars(domDataBroker, databindProvider);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<PatchCont> iid = InstanceIdentifier.create(PatchCont.class);
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceLeaf").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceUpdate").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf12("Bertha").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();

        // xmlunit cannot compare deeper children it seems out of the box so just check the iid encoding
        final String notification = adapter.awaitUntilNotification("");
        assertTrue(notification.contains("instance-identifier-patch-module:my-leaf12"));
        assertTrue(notification.contains("instance-identifier-patch-module:my-leaf11"));
        assertTrue(notification.contains("instance-identifier-patch-module:name"));
        assertTrue(notification.contains("augment-instance-identifier-patch-module:case-leaf1"));
        assertTrue(notification.contains("augment-instance-identifier-patch-module:leaf1"));
    }

    @Test
    public void testXmlChangedLeavesOnly() throws Exception {
        ListenerAdapterTester adapter = new ListenerAdapterTester(PATCH_CONT_YIID, "Casey", NotificationOutputType.XML,
                true);
        adapter.setCloseVars(domDataBroker, databindProvider);

        DOMDataTreeChangeService changeService = domDataBroker.getExtensions()
                .getInstance(DOMDataTreeChangeService.class);
        DOMDataTreeIdentifier root = new DOMDataTreeIdentifier(LogicalDatastoreType.CONFIGURATION, PATCH_CONT_YIID);
        changeService.registerDataTreeChangeListener(root, adapter);
        WriteTransaction writeTransaction = dataBroker.newWriteOnlyTransaction();
        final InstanceIdentifier<PatchCont> iid = InstanceIdentifier.create(PatchCont.class);
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice2(new PatchCase11Builder()
                    .setPatchSubChoice11(new PatchSubCase11Builder()
                        .setPatchSubSubChoice11(new PatchSubSubCase11Builder().setCaseLeaf11("ChoiceLeaf").build())
                        .build())
                    .build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_CHANGED_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice2(new PatchCase11Builder()
                    .setPatchSubChoice11(new PatchSubCase11Builder()
                        .setPatchSubSubChoice11(new PatchSubSubCase11Builder().setCaseLeaf11("ChoiceUpdate").build())
                        .build())
                    .build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf12("Bertha").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        adapter.assertXmlSimilar(getResultXml(XML_NOTIF_CHANGED_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        // xmlunit cannot compare deeper children it seems out of the box so just check the iid encoding
        final String notification = adapter.awaitUntilNotification("");

        assertTrue(notification.contains("instance-identifier-patch-module:my-leaf11"));
        assertTrue(notification.contains("instance-identifier-patch-module:my-leaf12"));
        assertTrue(notification.contains("instance-identifier-patch-module:name"));
        assertTrue(notification.contains("augment-instance-identifier-patch-module:case-leaf11"));
        assertTrue(notification.contains("augment-instance-identifier-patch-module:patch-choice2"));
        assertTrue(notification.contains("augment-instance-identifier-patch-module:patch-sub-choice11"));
        assertTrue(notification.contains("augment-instance-identifier-patch-module:patch-sub-sub-choice11"));
        assertTrue(notification.contains("augment-instance-identifier-patch-module:leaf1"));
    }
}