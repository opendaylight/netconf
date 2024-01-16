/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.nb.jaxrs.AbstractRestconfTest.assertEntity;
import static org.opendaylight.restconf.nb.jaxrs.AbstractRestconfTest.assertError;

import com.google.common.io.CharStreams;
import java.io.Reader;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@code RestconfSchemaService}.
 */
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfSchemaServiceMountTest {
    private static final ApiPath MOUNT_POINT = AbstractRestconfTest.apiPath("mount-point-1:cont/yang-ext:mount");
    private static final ApiPath NULL_MOUNT_POINT = AbstractRestconfTest.apiPath("mount-point-2:cont/yang-ext:mount");
    private static final ApiPath NOT_EXISTING_MOUNT_POINT =
        AbstractRestconfTest.apiPath("mount-point-3:cont/yang-ext:mount");

    // schema context with modules behind mount point
    private static final EffectiveModelContext SCHEMA_CONTEXT_BEHIND_MOUNT_POINT =
        YangParserTestUtils.parseYangResourceDirectory("/modules/modules-behind-mount-point");
    // schema context with mount points
    private static final EffectiveModelContext SCHEMA_CONTEXT_WITH_MOUNT_POINTS =
        YangParserTestUtils.parseYangResourceDirectory("/modules/mount-points");

    // handlers
    @Mock
    private DOMSchemaService schemaService;
    @Mock
    private YangTextSourceExtension sourceProvider;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMActionService actionService;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private YangTextSource yangSource;
    @Mock
    private Reader yangReader;

    // service under test
    private JaxRsRestconf restconf;

    @Before
    public void setup() throws Exception {
        final var mountPointService = new DOMMountPointServiceImpl();
        // create and register mount points
        mountPointService
                .createMountPoint(YangInstanceIdentifier.of(QName.create("mount:point:1", "2016-01-01", "cont")))
                .addService(DOMSchemaService.class, new FixedDOMSchemaService(SCHEMA_CONTEXT_BEHIND_MOUNT_POINT))
                .addService(DOMDataBroker.class, dataBroker)
                .register();
        mountPointService
                .createMountPoint(YangInstanceIdentifier.of(QName.create("mount:point:2", "2016-01-01", "cont")))
                .register();

        doCallRealMethod().when(schemaService).extension(any());
        doReturn(List.of(sourceProvider)).when(schemaService).supportedExtensions();
        doReturn(SCHEMA_CONTEXT_WITH_MOUNT_POINTS).when(schemaService).getGlobalContext();

        restconf = new JaxRsRestconf(new MdsalRestconfServer(schemaService, dataBroker, rpcService, actionService,
            mountPointService));
    }

    /**
     * Get schema with identifier of existing module behind mount point and check if correct module was found.
     */
    @Test
    public void getSchemaMountPointTest() throws Exception {
        final var reader = assertEntity(Reader.class, 200,
            ar -> restconf.modulesYangGET(MOUNT_POINT, "module1-behind-mount-point", "2014-02-03", ar));
        assertEquals("""
            module module1-behind-mount-point {
              namespace module:1:behind:mount:point;
              prefix mod1bemopo;
              revision 2014-02-03;
              rpc rpc-behind-module1;
            }
            """, CharStreams.toString(reader));
    }

    /**
     * Get schema with identifier of not-existing module behind mount point. Trying to create
     * <code>SchemaExportContext</code> with not-existing module behind mount point should result in error.
     */
    @Test
    public void getSchemaForNotExistingModuleMountPointTest() {
        final var error = assertError(ar -> restconf.modulesYangGET(MOUNT_POINT, "not-existing", "2016-01-01", ar));
        assertEquals("Source not-existing@2016-01-01 not found", error.getErrorMessage());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
    }

    /**
     * Try to get schema with <code>null</code> <code>SchemaContext</code> behind mount point when using
     * <code>NULL_MOUNT_POINT</code>. Test is expected to fail with <code>NullPointerException</code>.
     */
    @Test
    public void getSchemaNullSchemaContextBehindMountPointTest() {
        // make test - call service on mount point with null schema context
        // NULL_MOUNT_POINT contains null schema context
        final var error = assertError(
            ar -> restconf.modulesYangGET(NULL_MOUNT_POINT, "module1-behind-mount-point", "2014-02-03", ar));
        assertEquals("Mount point 'mount-point-2:cont' does not expose DOMSchemaService", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTags.RESOURCE_DENIED_TRANSPORT, error.getErrorTag());
    }

    /**
     * Try to get schema with empty (not valid) identifier behind mount point catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithEmptyIdentifierMountPointTest() {
        final var error = assertError(ar -> restconf.modulesYangGET(MOUNT_POINT, "", null, ar));
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema behind mount point with not-parsable identifier catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithNotParsableIdentifierMountPointTest() {
        final var error = assertError(ar -> restconf.modulesYangGET(MOUNT_POINT, "01_module", "2016-01-01", ar));
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema with wrong (not valid) identifier behind mount point catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     *
     * <p>
     * Not valid identifier contains only revision without module name.
     */
    @Test
    public void getSchemaWrongIdentifierMountPointTest() {
        final var error = assertError(ar -> restconf.modulesYangGET(MOUNT_POINT, "2014-01-01", null, ar));
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema behind mount point with identifier when does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithoutRevisionMountPointTest() {
        final var error = assertError(ar -> restconf.modulesYangGET(MOUNT_POINT, "module", null, ar));
        assertEquals("Source module not found", error.getErrorMessage());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test when mount point module is not found in current <code>SchemaContext</code> for mount points.
     * <code>IllegalArgumentException</code> exception is expected.
     */
    @Test
    public void getSchemaContextWithNotExistingMountPointTest() {
        final var error = assertError(
            ar -> restconf.modulesYangGET(NOT_EXISTING_MOUNT_POINT, "module1-behind-mount-point", "2014-02-03", ar));
        assertEquals("Failed to lookup for module with name 'mount-point-3'.", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.UNKNOWN_ELEMENT, error.getErrorTag());
    }
}
