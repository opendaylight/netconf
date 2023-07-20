package org.opendaylight.restconf.openapi.impl;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import javax.ws.rs.core.UriInfo;
import org.glassfish.jersey.internal.util.collection.MultivaluedStringMap;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.restconf.openapi.DocGenTestHelper;
import org.opendaylight.restconf.openapi.api.OpenApiService;
import org.opendaylight.restconf.openapi.model.OpenApiObject;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

public class RequestTest {
    private static final String TOASTER = "toaster";
    private static final String TOASTER_REVISION = "2009-11-20";
    private static final String DEVICE_ID = "1";
    private static final String DEVICE_NAME = "123";
    private static final YangInstanceIdentifier INSTANCE_ID = YangInstanceIdentifier.builder()
            .node(QName.create("", "nodes"))
            .node(QName.create("", "node"))
            .nodeWithKey(QName.create("", "node"), QName.create("", "id"), DEVICE_NAME).build();

    private static OpenApiService service;
    private static UriInfo uriInfoAll;
    private static UriInfo uriInfoToaster;

    @Before
    public void before() throws Exception {
        DOMSchemaService schemaService = mock(DOMSchemaService.class);
        EffectiveModelContext context = YangParserTestUtils.parseYangResourceDirectory("/yang");
        when(schemaService.getGlobalContext()).thenReturn(context);

        final DOMMountPoint mountPoint = mock(DOMMountPoint.class);
        when(mountPoint.getService(DOMSchemaService.class)).thenReturn(Optional.of(schemaService));

        final DOMMountPointService mountPointService = mock(DOMMountPointService.class);
        when(mountPointService.getMountPoint(INSTANCE_ID)).thenReturn(Optional.of(mountPoint));

        uriInfoAll = DocGenTestHelper.createMockUriInfo("http://localhost:8181/openapi/api/v3/mounts/" + DEVICE_ID);
        when(uriInfoAll.getQueryParameters()).thenReturn(new MultivaluedStringMap());

        uriInfoToaster = DocGenTestHelper.createMockUriInfo(
            "http://localhost:8181/openapi/api/v3/mounts/%s/%s(%s)".formatted(DEVICE_ID, TOASTER, TOASTER_REVISION));
        when(uriInfoToaster.getQueryParameters()).thenReturn(new MultivaluedStringMap());

        final MountPointOpenApiGeneratorRFC8040 mountPointRFC8040 =
                new MountPointOpenApiGeneratorRFC8040(schemaService, mountPointService);
        mountPointRFC8040.getMountPointOpenApi().onMountPointCreated(INSTANCE_ID);
        final OpenApiGeneratorRFC8040 openApiGeneratorRFC8040 = new OpenApiGeneratorRFC8040(schemaService);

        service = new OpenApiServiceImpl(mountPointRFC8040, openApiGeneratorRFC8040);
    }

    @Test
    public void testOpenApiObjForMountedDevice() {
        // this method is called when this URL is requested: http://localhost:8181/openapi/api/v3/mounts/1
        final var res = service.getMountDoc(DEVICE_ID, uriInfoAll);

        final var summary = ((OpenApiObject)res.getEntity()).paths().values().iterator().next().post().summary();
        assertTrue(summary.contains(DEVICE_NAME));
    }

    @Test
    public void testOpenApiObjForMountedDeviceForSpecificModule() {
        final var res = service.getMountDocByModule(DEVICE_ID, TOASTER, TOASTER_REVISION , uriInfoToaster);

        final var summary = ((OpenApiObject)res.getEntity()).paths().values().iterator().next().post().summary();
        assertTrue(summary.contains(DEVICE_NAME));
    }
}
