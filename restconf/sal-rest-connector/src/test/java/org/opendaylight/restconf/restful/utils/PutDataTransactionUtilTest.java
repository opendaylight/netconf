package org.opendaylight.restconf.restful.utils;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.opendaylight.controller.md.sal.rest.common.TestRestconfUtils;
import org.opendaylight.netconf.sal.restconf.impl.ControllerContext;
import org.opendaylight.netconf.sal.restconf.impl.InstanceIdentifierContext;
import org.opendaylight.netconf.sal.restconf.impl.NormalizedNodeContext;
import org.opendaylight.restconf.common.references.SchemaContextRef;
import org.opendaylight.restconf.restful.transaction.TransactionVarsWrapper;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

public class PutDataTransactionUtilTest {

    private static final String PATH_FOR_NEW_SCHEMA_CONTEXT = "/streams";
    private static final ControllerContext controllerContext = ControllerContext.getInstance();


    private NormalizedNodeContext payload;
    private SchemaContextRef refSchemaCtx;
    private TransactionVarsWrapper wrapper;
    private YangInstanceIdentifier path;
    private NormalizedNode node;
    private QName containerQname;
    private QName leafQname;

    private InstanceIdentifierContext<? extends SchemaNode> iidContext;


    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        refSchemaCtx = new SchemaContextRef(TestRestconfUtils.loadSchemaContext(PATH_FOR_NEW_SCHEMA_CONTEXT));

        final SchemaContext schema = controllerContext.getGlobalSchema();
        final Module module = schema.findModuleByName("example-jukebox", null);

        final QName baseQName = QName.create("ns", "2013-12-21", "baseQname");

        containerQname = QName.create(baseQName, "list");
        leafQname = QName.create(baseQName, "leaf");

        //TODO payload
        //payload = new NormalizedNodeContext();

        controllerContext.setGlobalSchema(refSchemaCtx.get());
        iidContext = payload.getInstanceIdentifierContext();
    }

    @Test
    public void testValidInputData() throws Exception {
        PutDataTransactionUtil.validInputData(iidContext.getSchemaNode(), payload);
    }

    @Test
    public void testValidTopLevelNodeName() throws Exception {
        PutDataTransactionUtil.validTopLevelNodeName(iidContext.getInstanceIdentifier(), payload);
    }

    @Test
    public void testValidateListKeysEqualityInPayloadAndUri() throws Exception {
        PutDataTransactionUtil.validateListKeysEqualityInPayloadAndUri(payload);
    }

    @Test
    public void testPutData() throws Exception {
        PutDataTransactionUtil.putData(payload, refSchemaCtx, null);
    }

}