/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.broker.DOMMountPointServiceImpl;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.databind.DatabindContext;
import org.opendaylight.restconf.nb.rfc8040.legacy.ErrorTags;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@link ParserIdentifier}.
 */
public class ParserIdentifierTest {
    // mount point identifier
    private static final String MOUNT_POINT_IDENT = "mount-point:mount-container/point-number/yang-ext:mount";

    // test identifier + expected result
    private static final String TEST_IDENT =
            "parser-identifier:cont1/cont2/listTest/list-in-grouping=name/leaf-A.B";

    private static final String TEST_IDENT_RESULT = """
            /(parser:identifier?revision=2016-06-02)cont1/cont2/listTest/listTest/list-in-grouping/\
            list-in-grouping[{(parser:identifier?revision=2016-06-02)name=name}]/leaf-A.B""";

    // test identifier with nodes defined in other modules using augmentation + expected result
    private static final String TEST_IDENT_OTHERS =
            "parser-identifier-included:list-1=name,2016-06-02/parser-identifier:augment-leaf";

    private static final String TEST_IDENT_OTHERS_RESULT = """
        /(parser:identifier:included?revision=2016-06-02)list-1/list-1\
        [{(parser:identifier:included?revision=2016-06-02)name=name, \
        (parser:identifier:included?revision=2016-06-02)revision=2016-06-02}]\
        /(parser:identifier?revision=2016-06-02)augment-leaf""";

    // invalid test identifier
    private static final String INVALID_TEST_IDENT =
            "parser-identifier:cont2/listTest/list-in-grouping=name/leaf-A.B";

    private static final String INVOKE_RPC = "invoke-rpc-module:rpc-test";
    private static final String INVOKE_ACTION = "example-actions:interfaces/interface=eth0/reset";

    // schema context with test modules
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");
    // contains the same modules but it is different object (it can be compared with equals)
    // FIXME: we really should use a different context of mount point
    private static final EffectiveModelContext MODEL_CONTEXT_ON_MOUNT_POINT =
        YangParserTestUtils.parseYangResourceDirectory("/parser-identifier");

    // mount point and mount point service
    private final DOMMountPointService mountPointService = new DOMMountPointServiceImpl();
    private DOMMountPoint mountPoint;

    @BeforeEach
    void beforeEach() {
        // create and register mount point
        final var mountPointId = YangInstanceIdentifier.of(
            QName.create("mount:point", "2016-06-02", "mount-container"),
            QName.create("mount:point", "2016-06-02", "point-number"));

        mountPoint = mountPointService.createMountPoint(mountPointId)
            .addService(DOMSchemaService.class, FixedDOMSchemaService.of(MODEL_CONTEXT_ON_MOUNT_POINT))
            .register()
            .getInstance();
    }

    /**
     * {@link ParserIdentifier#toInstanceIdentifier(String, SchemaContext)} tests.
     */

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when all nodes are defined
     * in one module.
     */
    @Test
    void toInstanceIdentifierTest() {
        final var context = ParserIdentifier.toInstanceIdentifier(TEST_IDENT, MODEL_CONTEXT, null);
        assertEquals(TEST_IDENT_RESULT, context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating <code>InstanceIdentifierContext</code> from identifier when nodes are defined in
     * multiple modules.
     */
    @Test
    void toInstanceIdentifierOtherModulesTest() {
        final var context = ParserIdentifier.toInstanceIdentifier(TEST_IDENT_OTHERS, MODEL_CONTEXT, null);
        assertEquals(TEST_IDENT_OTHERS_RESULT, context.getInstanceIdentifier().toString());
    }

    /**
     * Positive test of creating {@code InstanceIdentifierContext} from identifier containing {@code yang-ext:mount}.
     */
    @Test
    void toInstanceIdentifierMountPointTest() {
        final var context = ParserIdentifier.toInstanceIdentifier(MOUNT_POINT_IDENT + "/" + TEST_IDENT, MODEL_CONTEXT,
            mountPointService);
        assertEquals(TEST_IDENT_RESULT.toString(), context.getInstanceIdentifier().toString());
        assertEquals(mountPoint, context.getMountPoint());
        assertSame(MODEL_CONTEXT_ON_MOUNT_POINT, context.databind().modelContext());
    }

    /**
     * Negative test of creating <code>InstanceIdentifierContext</code> when <code>SchemaContext</code> is
     * <code>null</code>. Test fails expecting <code>NullPointerException</code>.
     */
    @Test
    void toInstanceIdentifierNullSchemaContextNegativeTest() {
        assertThrows(NullPointerException.class, () -> ParserIdentifier.toInstanceIdentifier(TEST_IDENT, null, null));
    }

    /**
     * Api path can be empty. <code>YangInstanceIdentifier.EMPTY</code> is expected to be returned.
     */
    @Test
    void toInstanceIdentifierEmptyIdentifierTest() {
        final var context = ParserIdentifier.toInstanceIdentifier("", MODEL_CONTEXT, null);
        assertEquals(YangInstanceIdentifier.of(), context.getInstanceIdentifier());
    }

    /**
     * Negative test with invalid test identifier. Test should fail with <code>RestconfDocumentedException</code>.
     */
    @Test
    void toInstanceIdentifierInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier(INVALID_TEST_IDENT, MODEL_CONTEXT, null));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Schema for '(parser:identifier?revision=2016-06-02)cont2' not found", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test when identifier contains {@code yang-ext:mount} but identifier part is not valid. Test
     * should fail with {@link RestconfDocumentedException}.
     */
    @Test
    void toInstanceIdentifierMountPointInvalidIdentifierNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier("mount-point:point-number/yang-ext:mount", MODEL_CONTEXT,
                mountPointService));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Schema for '(mount:point?revision=2016-06-02)point-number' not found", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }

    /**
     * Negative test when <code>DOMMountPoint</code> cannot be found. Test is expected to fail with
     * <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    void toInstanceIdentifierMissingMountPointNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier("yang-ext:mount", MODEL_CONTEXT, mountPointService));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Mount point '' does not exist", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTags.RESOURCE_DENIED_TRANSPORT, error.getErrorTag());
    }

    /**
     * Negative test when <code>{@link DOMMountPointService}</code> is absent. Test is expected to fail with
     * <code>RestconfDocumentedException</code> error type, error tag and error status code are
     * compared to expected values.
     */
    @Test
    void toInstanceIdentifierMissingMountPointServiceNegativeTest() {
        final var ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.toInstanceIdentifier("yang-ext:mount", MODEL_CONTEXT, null));
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());
        final var error = errors.get(0);
        assertEquals("Mount point service is not available", error.getErrorMessage());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.OPERATION_FAILED, error.getErrorTag());
    }

    /**
     * Test invoke RPC. Verify if RPC schema node was found.
     */
    @Test
    void invokeRpcTest() throws Exception {
        final var result = ParserIdentifier.toInstanceIdentifier(INVOKE_RPC, MODEL_CONTEXT, null);

        // RPC schema node
        final QName rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(ApiPath.parse(INVOKE_RPC), DatabindContext.ofModel(MODEL_CONTEXT)),
            result.getInstanceIdentifier());
        assertEquals(null, result.getMountPoint());
        assertSame(MODEL_CONTEXT, result.databind().modelContext());
    }

    /**
     * Test invoke RPC on mount point. Verify if RPC schema node was found.
     */
    @Test
    void invokeRpcOnMountPointTest() throws Exception {
        final var result = ParserIdentifier.toInstanceIdentifier(MOUNT_POINT_IDENT + "/" + INVOKE_RPC, MODEL_CONTEXT,
            mountPointService);

        // RPC schema node
        final var rpcQName = result.getSchemaNode().getQName();
        assertEquals("invoke:rpc:module", rpcQName.getModule().getNamespace().toString());
        assertEquals("rpc-test", rpcQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(ApiPath.parse(INVOKE_RPC), DatabindContext.ofModel(MODEL_CONTEXT)),
            result.getInstanceIdentifier());
        assertEquals(mountPoint, result.getMountPoint());
        assertSame(MODEL_CONTEXT_ON_MOUNT_POINT, result.databind().modelContext());
    }

    /**
     * Test Action. Verify if Action schema node was found.
     */
    @Test
    void invokeActionTest() throws Exception {
        final var result = ParserIdentifier.toInstanceIdentifier(INVOKE_ACTION, MODEL_CONTEXT, null);

        // Action schema node
        final var actionQName = result.getSchemaNode().getQName();
        assertEquals("https://example.com/ns/example-actions", actionQName.getModule().getNamespace().toString());
        assertEquals("reset", actionQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(ApiPath.parse(INVOKE_ACTION), DatabindContext.ofModel(MODEL_CONTEXT)),
            result.getInstanceIdentifier());
        assertNull(result.getMountPoint());
        assertSame(MODEL_CONTEXT, result.databind().modelContext());
    }

    /**
     * Test invoke Action on mount point. Verify if Action schema node was found.
     */
    @Test
    void invokeActionOnMountPointTest() throws Exception {
        final var result = ParserIdentifier.toInstanceIdentifier(MOUNT_POINT_IDENT + "/" + INVOKE_ACTION,
            MODEL_CONTEXT, mountPointService);

        // Action schema node
        final QName actionQName = result.getSchemaNode().getQName();
        assertEquals("https://example.com/ns/example-actions", actionQName.getModule().getNamespace().toString());
        assertEquals("reset", actionQName.getLocalName());

        // other fields
        assertEquals(IdentifierCodec.deserialize(ApiPath.parse(INVOKE_ACTION), DatabindContext.ofModel(MODEL_CONTEXT)),
            result.getInstanceIdentifier());
        assertEquals(mountPoint, result.getMountPoint());
        assertSame(MODEL_CONTEXT_ON_MOUNT_POINT, result.databind().modelContext());
    }
}
