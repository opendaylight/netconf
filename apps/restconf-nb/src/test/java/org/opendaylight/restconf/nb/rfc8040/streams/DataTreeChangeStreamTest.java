/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.streams;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.dom.adapter.test.AbstractConcurrentDataBrokerTest;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataBroker.DataTreeChangeExtension;
import org.opendaylight.netconf.databind.DatabindProvider;
import org.opendaylight.netconf.sal.remote.impl.DataTreeChangeSource;
import org.opendaylight.restconf.api.query.ChangedLeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.ChildNodesOnlyParam;
import org.opendaylight.restconf.api.query.LeafNodesOnlyParam;
import org.opendaylight.restconf.api.query.SkipNotificationDataParam;
import org.opendaylight.restconf.server.api.EventStreamGetParams;
import org.opendaylight.restconf.server.api.testlib.AbstractInstanceIdentifierTest;
import org.opendaylight.restconf.server.api.testlib.CompletingServerRequest;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfStreamRegistry;
import org.opendaylight.restconf.server.spi.RestconfStream;
import org.opendaylight.restconf.server.spi.RestconfStream.EncodingName;
import org.opendaylight.restconf.server.spi.RestconfStream.Sender;
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
import org.opendaylight.yang.gen.v1.urn.sal.restconf.event.subscription.rev231103.NotificationOutputTypeGrouping.NotificationOutputType;
import org.opendaylight.yangtools.binding.util.BindingMap;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.tree.api.DataTreeCandidate;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlunit.assertj.XmlAssert;

// FIXME: disable replay
@Ignore
// TODO: Migrate this test to JUnit5 after migrating the mdsal tests.
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class DataTreeChangeStreamTest extends AbstractConcurrentDataBrokerTest {
    private static final class TestHandler implements Sender {
        private CountDownLatch notificationLatch = new CountDownLatch(1);
        private volatile String lastNotification;

        @Override
        public void endOfStream() {
            // No-op
        }

        @Override
        public void sendDataMessage(final String data) {
            lastNotification = data;
            notificationLatch.countDown();
        }

        void assertGot(final String json) throws Exception {
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

        void assertXmlSimilar(final String xml) throws Exception {
            awaitUntilNotification(xml);

            LOG.info("lastNotification: {}", lastNotification);
            final String withFakeDate = withFakeXmlDate(lastNotification);
            LOG.info("Comparing: \n{}\n{}", xml, withFakeDate);

            XmlAssert.assertThat(xml).and(withFakeDate).ignoreWhitespace().ignoreChildNodesOrder().areSimilar();
            lastNotification = null;
            notificationLatch = new CountDownLatch(1);
        }

        String awaitUntilNotification(final String xml) {
            // FIXME: use awaitility
            if (!Uninterruptibles.awaitUninterruptibly(notificationLatch, 500, TimeUnit.SECONDS)) {
                fail("Timed out waiting for notification for: " + xml);
            }
            return lastNotification;
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(DataTreeChangeStreamTest.class);

    private static final String JSON_NOTIF_LEAVES_CREATE = "/listener-adapter-test/notif-leaves-create.json";
    private static final String JSON_NOTIF_LEAVES_UPDATE =  "/listener-adapter-test/notif-leaves-update.json";
    private static final String JSON_NOTIF_LEAVES_DELETE = "/listener-adapter-test/notif-leaves-delete.json";
    private static final String JSON_NOTIF_CHANGED_LEAVES_CREATE =
            "/listener-adapter-test/notif-changed-leaves-create.json";
    private static final String JSON_NOTIF_CHANGED_LEAVES_UPDATE =
            "/listener-adapter-test/notif-changed-leaves-update.json";
    private static final String JSON_NOTIF_CHANGED_LEAVES_DELETE =
            "/listener-adapter-test/notif-changed-leaves-delete.json";
    private static final String JSON_NOTIF_CHILD_NODES_ONLY_CREATE =
            "/listener-adapter-test/notif-child-nodes-only-create.json";
    private static final String JSON_NOTIF_CHILD_NODES_ONLY_UPDATE1 =
        "/listener-adapter-test/notif-child-nodes-only-update1.json";
    private static final String JSON_NOTIF_CHILD_NODES_ONLY_UPDATE2 =
        "/listener-adapter-test/notif-child-nodes-only-update2.json";
    private static final String JSON_NOTIF_CHILD_NODES_ONLY_DELETE =
            "/listener-adapter-test/notif-child-nodes-only-delete.json";

    private static final String XML_NOTIF_LEAVES_CREATE = "/listener-adapter-test/notif-leaves-create.xml";
    private static final String XML_NOTIF_LEAVES_UPDATE =  "/listener-adapter-test/notif-leaves-update.xml";
    private static final String XML_NOTIF_LEAVES_DELETE =  "/listener-adapter-test/notif-leaves-delete.xml";
    private static final String XML_NOTIF_CHANGED_LEAVES_CREATE =
            "/listener-adapter-test/notif-changed-leaves-create.xml";
    private static final String XML_NOTIF_CHANGED_LEAVES_UPDATE =
            "/listener-adapter-test/notif-changed-leaves-update.xml";
    private static final String XML_NOTIF_CHANGED_LEAVES_DELETE =
            "/listener-adapter-test/notif-changed-leaves-delete.xml";
    private static final String XML_NOTIF_CHILD_NODES_ONLY_CREATE =
        "/listener-adapter-test/notif-child-nodes-only-create.xml";
    private static final String XML_NOTIF_CHILD_NODES_ONLY_UPDATE1 =
        "/listener-adapter-test/notif-child-nodes-only-update1.xml";
    private static final String XML_NOTIF_CHILD_NODES_ONLY_UPDATE2 =
        "/listener-adapter-test/notif-child-nodes-only-update2.xml";
    private static final String XML_NOTIF_CHILD_NODES_ONLY_DELETE =
        "/listener-adapter-test/notif-child-nodes-only-delete.xml";

    private static final String JSON_NOTIF_CONT_CREATE = "/listener-adapter-test/notif-cont-create.json";
    private static final String JSON_NOTIF_CONT_UPDATE = "/listener-adapter-test/notif-cont-update.json";
    private static final String JSON_NOTIF_CONT_DELETE = "/listener-adapter-test/notif-cont-delete.json";
    private static final String JSON_NOTIF_LIST_CREATE = "/listener-adapter-test/notif-list-create.json";
    private static final String JSON_NOTIF_LIST_UPDATE = "/listener-adapter-test/notif-list-update.json";
    private static final String JSON_NOTIF_LIST_DELETE = "/listener-adapter-test/notif-list-delete.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_CONT_CREATE =
            "/listener-adapter-test/notif-without-data-cont-create.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_CONT_UPDATE =
            "/listener-adapter-test/notif-without-data-cont-update.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_CONT_DELETE =
            "/listener-adapter-test/notif-without-data-cont-delete.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_LIST_CREATE =
            "/listener-adapter-test/notif-without-data-list-create.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_LIST_UPDATE =
            "/listener-adapter-test/notif-without-data-list-update.json";
    private static final String JSON_NOTIF_WITHOUT_DATA_LIST_DELETE =
            "/listener-adapter-test/notif-without-data-list-delete.json";

    private static final String XML_NOTIF_CONT_CREATE = "/listener-adapter-test/notif-cont-create.xml";
    private static final String XML_NOTIF_CONT_UPDATE = "/listener-adapter-test/notif-cont-update.xml";
    private static final String XML_NOTIF_CONT_DELETE = "/listener-adapter-test/notif-cont-delete.xml";
    private static final String XML_NOTIF_LIST_CREATE = "/listener-adapter-test/notif-list-create.xml";
    private static final String XML_NOTIF_LIST_UPDATE = "/listener-adapter-test/notif-list-update.xml";
    private static final String XML_NOTIF_LIST_DELETE = "/listener-adapter-test/notif-list-delete.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_CONT_CREATE =
            "/listener-adapter-test/notif-without-data-cont-create.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_CONT_UPDATE =
            "/listener-adapter-test/notif-without-data-cont-update.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_CONT_DELETE =
            "/listener-adapter-test/notif-without-data-cont-delete.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_LIST_CREATE =
            "/listener-adapter-test/notif-without-data-list-create.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_LIST_UPDATE =
            "/listener-adapter-test/notif-without-data-list-update.xml";
    private static final String XML_NOTIF_WITHOUT_DATA_LIST_DELETE =
            "/listener-adapter-test/notif-without-data-list-delete.xml";

    private static final YangInstanceIdentifier PATCH_CONT_YIID = YangInstanceIdentifier.of(PatchCont.QNAME);

    private static final YangInstanceIdentifier MY_LIST1_YIID = YangInstanceIdentifier.builder()
            .node(PatchCont.QNAME)
            .node(MyList1.QNAME)
            .nodeWithKey(MyList1.QNAME, QName.create(PatchCont.QNAME.getModule(), "name"), "Althea")
            .build();

    private final CompletingServerRequest<RestconfStream<List<DataTreeCandidate>>> request =
        new CompletingServerRequest<>();

    private DataBroker dataBroker;
    private DOMDataBroker domDataBroker;
    private DatabindProvider databindProvider;
    private RestconfStream.Registry streamRegistry;

    @Before
    public void setUp() throws Exception {
        dataBroker = getDataBroker();
        domDataBroker = getDomBroker();
        databindProvider = () -> AbstractInstanceIdentifierTest.IID_DATABIND;
        streamRegistry = new MdsalRestconfStreamRegistry(domDataBroker, x -> x);
    }

    TestHandler createHandler(final YangInstanceIdentifier path, final String streamName,
            final NotificationOutputType outputType, final boolean leafNodesOnly, final boolean skipNotificationData,
            final boolean changedLeafNodesOnly, final boolean childNodesOnly) throws Exception {
        streamRegistry.createLegacyStream(request, URI.create("baseURI"),
            new DataTreeChangeSource(databindProvider,
                domDataBroker.extension(DataTreeChangeExtension.class),
                LogicalDatastoreType.CONFIGURATION, path), "test");

        final var stream = request.getResult();
        final var handler = new TestHandler();
        stream.addSubscriber(handler,
            switch (outputType) {
                case JSON -> EncodingName.RFC8040_JSON;
                case XML -> EncodingName.RFC8040_XML;
            },
            new EventStreamGetParams(null, null, null,
                leafNodesOnly ? LeafNodesOnlyParam.of(true) : null,
                skipNotificationData ? SkipNotificationDataParam.of(true) : null,
                changedLeafNodesOnly ? ChangedLeafNodesOnlyParam.of(true) : null,
                childNodesOnly ? ChildNodesOnlyParam.of(true) : null));
        return handler;
    }

    @Test
    public void testJsonNotifsLeaves() throws Exception {
        final var handler = createHandler(PATCH_CONT_YIID, "Casey", NotificationOutputType.JSON,
            true, false, false, false);

        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        final var iid = InstanceIdentifier.create(PatchCont.class);
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceLeaf").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceUpdate").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf12("Bertha").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_LEAVES_DELETE));
    }

    @Test
    public void testJsonNotifsChangedLeaves() throws Exception {
        final var handler = createHandler(PATCH_CONT_YIID, "Casey", NotificationOutputType.JSON, false, false, true,
            false);

        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        final var iid = InstanceIdentifier.create(PatchCont.class);
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
        handler.assertGot(getNotifJson(JSON_NOTIF_CHANGED_LEAVES_CREATE));

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
        handler.assertGot(getNotifJson(JSON_NOTIF_CHANGED_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_CHANGED_LEAVES_DELETE));
    }

    @Test
    public void testJsonChildNodesOnly() throws Exception {
        final var handler = createHandler(PATCH_CONT_YIID, "Casey", NotificationOutputType.JSON, false, false, false,
            true);

        final var iid = InstanceIdentifier.create(PatchCont.class).child(MyList1.class, new MyList1Key("Althea"));
        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid,
            new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build());
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_CHILD_NODES_ONLY_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid,
            new MyList1Builder().setMyLeaf11("Bertha").setName("Althea").build());
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_CHILD_NODES_ONLY_UPDATE1));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid,
            new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build());
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_CHILD_NODES_ONLY_UPDATE2));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        handler.assertGot(getNotifJson(JSON_NOTIF_CHILD_NODES_ONLY_DELETE));
    }

    @Test
    public void testXmlLeavesOnly() throws Exception {
        final var handler = createHandler(PATCH_CONT_YIID, "Casey", NotificationOutputType.XML, true, false, false,
            false);

        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        final var iid = InstanceIdentifier.create(PatchCont.class);
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceLeaf").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_LEAVES_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, new PatchContBuilder()
            .addAugmentation(new PatchCont1Builder()
                .setPatchChoice1(new PatchCase1Builder().setCaseLeaf1("ChoiceUpdate").build())
                .setLeaf1("AugmentLeaf")
                .build())
            .setMyList1(BindingMap.of(new MyList1Builder().setMyLeaf12("Bertha").setName("Althea").build()))
            .build());
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();

        // xmlunit cannot compare deeper children it seems out of the box so just check the iid encoding
        final String notification = handler.awaitUntilNotification("");
        assertThat(notification, allOf(
            containsString("<path xmlns:a=\"instance:identifier:patch:module\">/a:patch-cont"
                + "/a:my-list1[a:name='Althea']/a:my-leaf11</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\">/a:patch-cont"
                + "/a:my-list1[a:name='Althea']/a:my-leaf12</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\">/a:patch-cont"
                + "/a:my-list1[a:name='Althea']/a:name</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\" "
                + "xmlns:b=\"augment:instance:identifier:patch:module\">/a:patch-cont/b:leaf1</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\" "
                + "xmlns:b=\"augment:instance:identifier:patch:module\">/a:patch-cont/b:case-leaf1</path>")));
    }

    @Test
    public void testXmlChangedLeavesOnly() throws Exception {
        final var handler = createHandler(PATCH_CONT_YIID, "Casey", NotificationOutputType.XML, false, false, true,
            false);

        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        final var iid = InstanceIdentifier.create(PatchCont.class);
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
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_CHANGED_LEAVES_CREATE));

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
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_CHANGED_LEAVES_UPDATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();

        // xmlunit cannot compare deeper children it seems out of the box so just check the iid encoding
        final String notification = handler.awaitUntilNotification("");
        assertThat(notification, allOf(
            containsString("<path xmlns:a=\"instance:identifier:patch:module\">/a:patch-cont"
                + "/a:my-list1[a:name='Althea']/a:my-leaf11</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\">/a:patch-cont"
                + "/a:my-list1[a:name='Althea']/a:my-leaf12</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\">/a:patch-cont"
                + "/a:my-list1[a:name='Althea']/a:name</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\" "
                + "xmlns:b=\"augment:instance:identifier:patch:module\">/a:patch-cont/b:leaf1</path>"),
            containsString("<path xmlns:a=\"instance:identifier:patch:module\" "
                + "xmlns:b=\"augment:instance:identifier:patch:module\">/a:patch-cont/b:case-leaf11</path>")));
    }

    @Test
    public void testXmlChildNodesOnly() throws Exception {
        final var handler = createHandler(PATCH_CONT_YIID, "Casey", NotificationOutputType.XML, false, false, false,
            true);

        final var iid = InstanceIdentifier.create(PatchCont.class).child(MyList1.class, new MyList1Key("Althea"));
        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid,
            new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build());
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_CHILD_NODES_ONLY_CREATE));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid,
            new MyList1Builder().setMyLeaf11("Bertha").setName("Althea").build());
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_CHILD_NODES_ONLY_UPDATE1));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid,
            new MyList1Builder().setMyLeaf11("Jed").setName("Althea").build());
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_CHILD_NODES_ONLY_UPDATE2));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(XML_NOTIF_CHILD_NODES_ONLY_DELETE));
    }

    @Test
    public void testJsonContNotifications() throws Exception {
        jsonNotifications(PATCH_CONT_YIID, false, JSON_NOTIF_CONT_CREATE,
                JSON_NOTIF_CONT_UPDATE, JSON_NOTIF_CONT_DELETE);
    }

    @Test
    public void testJsonListNotifications() throws Exception {
        jsonNotifications(MY_LIST1_YIID, false, JSON_NOTIF_LIST_CREATE,
                JSON_NOTIF_LIST_UPDATE, JSON_NOTIF_LIST_DELETE);
    }

    @Test
    public void testJsonContNotificationsWithoutData() throws Exception {
        jsonNotifications(PATCH_CONT_YIID, true, JSON_NOTIF_WITHOUT_DATA_CONT_CREATE,
                JSON_NOTIF_WITHOUT_DATA_CONT_UPDATE, JSON_NOTIF_WITHOUT_DATA_CONT_DELETE);
    }

    @Test
    public void testJsonListNotificationsWithoutData() throws Exception {
        jsonNotifications(MY_LIST1_YIID, true, JSON_NOTIF_WITHOUT_DATA_LIST_CREATE,
                JSON_NOTIF_WITHOUT_DATA_LIST_UPDATE, JSON_NOTIF_WITHOUT_DATA_LIST_DELETE);
    }

    @Test
    public void testXmlContNotifications() throws Exception {
        xmlNotifications(PATCH_CONT_YIID, false, XML_NOTIF_CONT_CREATE,
                XML_NOTIF_CONT_UPDATE, XML_NOTIF_CONT_DELETE);
    }

    @Test
    public void testXmlListNotifications() throws Exception {
        xmlNotifications(MY_LIST1_YIID, false, XML_NOTIF_LIST_CREATE,
                XML_NOTIF_LIST_UPDATE, XML_NOTIF_LIST_DELETE);
    }

    @Test
    public void testXmlContNotificationsWithoutData() throws Exception {
        xmlNotifications(PATCH_CONT_YIID, true, XML_NOTIF_WITHOUT_DATA_CONT_CREATE,
                XML_NOTIF_WITHOUT_DATA_CONT_UPDATE, XML_NOTIF_WITHOUT_DATA_CONT_DELETE);
    }

    @Test
    public void testXmlListNotificationsWithoutData() throws Exception {
        xmlNotifications(MY_LIST1_YIID, true, XML_NOTIF_WITHOUT_DATA_LIST_CREATE,
                XML_NOTIF_WITHOUT_DATA_LIST_UPDATE, XML_NOTIF_WITHOUT_DATA_LIST_DELETE);
    }

    static String withFakeDate(final String in) throws JSONException {
        final JSONObject doc = new JSONObject(in);
        final JSONObject notification = doc.getJSONObject("ietf-restconf:notification");
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

    private void jsonNotifications(final YangInstanceIdentifier pathYiid, final boolean skipData,
            final String jsonNotifCreate, final String jsonNotifUpdate, final String jsonNotifDelete) throws Exception {
        final var handler = createHandler(pathYiid, "Casey", NotificationOutputType.JSON, false, skipData, false,
            false);

        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        var builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        final var iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        handler.assertGot(getNotifJson(jsonNotifCreate));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        handler.assertGot(getNotifJson(jsonNotifUpdate));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        handler.assertGot(getNotifJson(jsonNotifDelete));
    }

    private void xmlNotifications(final YangInstanceIdentifier pathYiid, final boolean skipData,
            final String xmlNotifCreate, final String xmlNotifUpdate, final String xmlNotifDelete) throws Exception {
        final var handler = createHandler(pathYiid, "Casey", NotificationOutputType.XML, false, skipData, false, false);

        var writeTransaction = dataBroker.newWriteOnlyTransaction();
        var builder = new MyList1Builder().setMyLeaf11("Jed").setName("Althea");
        final var iid = InstanceIdentifier.create(PatchCont.class)
                .child(MyList1.class, new MyList1Key("Althea"));
        writeTransaction.put(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(xmlNotifCreate));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        builder = new MyList1Builder().withKey(new MyList1Key("Althea")).setMyLeaf12("Bertha");
        writeTransaction.merge(LogicalDatastoreType.CONFIGURATION, iid, builder.build());
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(xmlNotifUpdate));

        writeTransaction = dataBroker.newWriteOnlyTransaction();
        writeTransaction.delete(LogicalDatastoreType.CONFIGURATION, iid);
        writeTransaction.commit();
        handler.assertXmlSimilar(getResultXml(xmlNotifDelete));
    }
}
