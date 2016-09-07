package org.opendaylight.restconf.restful.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.MockitoAnnotations.initMocks;

import com.google.common.util.concurrent.Futures;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.controller.md.sal.dom.api.DOMTransactionChain;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHContext;
import org.opendaylight.netconf.sal.restconf.impl.PATCHEntity;
import org.opendaylight.netconf.sal.restconf.impl.PATCHStatusContext;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class PatchDataTransactionUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/jukebox";
    private static final ControllerContext controllerContext = ControllerContext.getInstance();

    @Mock
    private DOMTransactionChain transactionChain;

    @Mock
    private DOMDataReadWriteTransaction rWTransaction;

    private SchemaContextRef refSchemaCtx;
    private YangInstanceIdentifier iIDToGap;
    private YangInstanceIdentifier iIDMerge;
    private SchemaContext schema;
    private ContainerNode buildBaseContainerForTests;
    private YangInstanceIdentifier targetNodeForTests;
    private YangInstanceIdentifier targetNodeMerge;
    private MapNode buildArtistList;

    @Before
    public void setUp() throws Exception {
        initMocks(this);

        refSchemaCtx = new SchemaContextRef(TestRestconfUtils.loadSchemaContext(PATH_FOR_NEW_SCHEMA_CONTEXT));
        controllerContext.setGlobalSchema(refSchemaCtx.get());
        schema = controllerContext.getGlobalSchema();
        final QName baseQName = QName.create("http://example.com/ns/example-jukebox", "2015-04-04", "jukebox");
        final QName containerPlayerQName = QName.create(baseQName, "player");
        final QName leafGapQName = QName.create(baseQName, "gap");

        final QName containerLibraryQName = QName.create(baseQName, "library");
        final QName listArtistQName = QName.create(baseQName, "artist");
        final QName leafNameQName = QName.create(baseQName, "name");
        final YangInstanceIdentifier.NodeIdentifierWithPredicates nodeWithKey =
                new YangInstanceIdentifier.NodeIdentifierWithPredicates(listArtistQName, leafNameQName, "name of artist");

        /** instance identifier for accessing leaf node "gap" */
        iIDToGap = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerPlayerQName)
                .node(leafGapQName)
                .build();

        /** values that are used for creating leaf for testPatchDataCreateAndDelete, testPatchDataCreateAndReplace and testPatchDataCreateAndRemove test */
        final LeafNode buildGapLeaf = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(leafGapQName))
                .withValue(0.2)
                .build();

        final ContainerNode buildPlayerContainer = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(containerPlayerQName))
                .withChild(buildGapLeaf)
                .build();

        buildBaseContainerForTests = Builders.containerBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(baseQName))
                .withChild(buildPlayerContainer)
                .build();

        targetNodeForTests = YangInstanceIdentifier.builder(iIDToGap)
                .node(containerPlayerQName)
                .node(leafGapQName)
                .build();

        /** instance identifier for accessing leaf node "name" in list "artist" */
        iIDMerge = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerLibraryQName)
                .node(listArtistQName)
                .nodeWithKey(listArtistQName, QName.create(listArtistQName, "name"), "name of artist")
                .node(leafNameQName)
                .build();

        /** values that are used for creating leaf for testPatchDataMerge test */
        final LeafNode<Object> contentName = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "name")))
                .withValue("name of artist")
                .build();

        final LeafNode<Object> contentDescription = Builders.leafBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(QName.create(baseQName, "description")))
                .withValue("description of artist")
                .build();

        final MapEntryNode mapEntryNode = Builders.mapEntryBuilder()
                .withNodeIdentifier(nodeWithKey)
                .withChild(contentName)
                .withChild(contentDescription)
                .build();

        buildArtistList = Builders.mapBuilder()
                .withNodeIdentifier(new YangInstanceIdentifier.NodeIdentifier(listArtistQName))
                .withChild(mapEntryNode)
                .build();

        targetNodeMerge = YangInstanceIdentifier.builder()
                .node(baseQName)
                .node(containerLibraryQName)
                .node(listArtistQName)
                .nodeWithKey(listArtistQName, leafNameQName, "name of artist")
                .build();

        /** Mocks */
        doReturn(rWTransaction).when(transactionChain).newReadWriteTransaction();
        doReturn(Futures.immediateCheckedFuture(false)).doReturn(Futures.immediateCheckedFuture(true))
                .when(rWTransaction).exists(LogicalDatastoreType.CONFIGURATION, targetNodeForTests);
        doReturn(Futures.immediateCheckedFuture(false))
                .when(rWTransaction).exists(LogicalDatastoreType.CONFIGURATION, targetNodeMerge);
        doReturn(Futures.immediateCheckedFuture(null)).when(rWTransaction).submit();
    }


    @Test
    public void testPatchDataMerge() {
        final PATCHEntity entityMerge1 = new PATCHEntity("edit1", "merge", targetNodeMerge, buildArtistList);
        final PATCHEntity entityMerge2 = new PATCHEntity("edit2", "merge", targetNodeMerge, buildArtistList);
        final List<PATCHEntity> entities = new ArrayList<>();

        entities.add(entityMerge1);
        entities.add(entityMerge2);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iIDMerge, null, null, schema);
        final PATCHContext patchContext = new PATCHContext(iidContext, entities, "patchM");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, transactionChain);
        final PATCHStatusContext patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, wrapper, refSchemaCtx);

        assertTrue(patchStatusContext.isOk());
        assertEquals("patchM", patchStatusContext.getPatchId());
    }

    @Test
    public void testPatchDataCreateAndDelete() throws Exception {
        final PATCHEntity entityCreate = new PATCHEntity("edit1", "create", targetNodeForTests, buildBaseContainerForTests);
        final PATCHEntity entityDelete = new PATCHEntity("edit2", "delete", targetNodeForTests);
        final List<PATCHEntity> entities = new ArrayList<>();

        entities.add(entityCreate);
        entities.add(entityDelete);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iIDToGap, null, null, schema);
        final PATCHContext patchContext = new PATCHContext(iidContext, entities, "patchCD");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, transactionChain);
        final PATCHStatusContext patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, wrapper, refSchemaCtx);

        assertTrue(patchStatusContext.isOk());
        assertEquals("patchCD", patchStatusContext.getPatchId());
    }

    @Test
    public void testPatchDataCreateAndReplace() throws Exception {
        final PATCHEntity entityCreate = new PATCHEntity("edit1", "create", targetNodeForTests, buildBaseContainerForTests);
        final PATCHEntity entityReplace = new PATCHEntity("edit2", "replace", targetNodeForTests, buildBaseContainerForTests);

        final List<PATCHEntity> entities = new ArrayList<>();

        entities.add(entityCreate);
        entities.add(entityReplace);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iIDToGap, null, null, schema);
        final PATCHContext patchContext = new PATCHContext(iidContext, entities, "patchCR");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, transactionChain);
        final PATCHStatusContext patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, wrapper, refSchemaCtx);

        assertTrue(patchStatusContext.isOk());
        assertEquals("patchCR", patchStatusContext.getPatchId());
    }

    @Test
    public void testPatchDataCreateAndRemove() throws Exception {
        final PATCHEntity entityCreate = new PATCHEntity("edit1", "create", targetNodeForTests, buildBaseContainerForTests);
        final PATCHEntity entityReplace = new PATCHEntity("edit2", "remove", targetNodeForTests, buildBaseContainerForTests);

        final List<PATCHEntity> entities = new ArrayList<>();

        entities.add(entityCreate);
        entities.add(entityReplace);

        final InstanceIdentifierContext<? extends SchemaNode> iidContext = new InstanceIdentifierContext<>(iIDToGap, null, null, schema);
        final PATCHContext patchContext = new PATCHContext(iidContext, entities, "patchCRm");
        final TransactionVarsWrapper wrapper = new TransactionVarsWrapper(iidContext, null, transactionChain);
        final PATCHStatusContext patchStatusContext = PatchDataTransactionUtil.patchData(patchContext, wrapper, refSchemaCtx);

        assertTrue(patchStatusContext.isOk());
        assertEquals("patchCRm", patchStatusContext.getPatchId());
    }

}