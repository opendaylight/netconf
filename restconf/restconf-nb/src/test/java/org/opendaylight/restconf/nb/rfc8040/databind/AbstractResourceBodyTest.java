/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static java.util.Objects.requireNonNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.BeforeClass;
import org.junit.function.ThrowingRunnable;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.utils.parser.ParserIdentifier;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

abstract class AbstractResourceBodyTest extends AbstractBodyTest {
    static final NodeIdentifier CONT_NID = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont"));
    static final NodeIdentifier CONT1_NID = new NodeIdentifier(QName.create(INSTANCE_IDENTIFIER_MODULE_QNAME, "cont1"));

    static final QName LST11 = QName.create("augment:module", "2014-01-17", "lst11");
    static final QName KEYVALUE111 = QName.create(LST11, "keyvalue111");
    static final QName KEYVALUE112 = QName.create(LST11, "keyvalue112");

    static final QName LF111 = QName.create("augment:augment:module", "2014-01-17", "lf111");
    static final NodeIdentifier LF112_NID = new NodeIdentifier(QName.create(LF111, "lf112"));

    static final QName LF11 = QName.create("augment:module:leaf:list", "2014-01-27", "lf11");
    static final QName LFLST11 = QName.create(LF11, "lflst11");

    static EffectiveModelContext MODEL_CONTEXT;

    private final Function<InputStream, ResourceBody> bodyConstructor;
    private final DOMMountPointService mountPointService;
    private final DOMMountPoint mountPoint;

    AbstractResourceBodyTest(final Function<InputStream, ResourceBody> bodyConstructor) {
        this.bodyConstructor = requireNonNull(bodyConstructor);

        mountPointService = mock(DOMMountPointService.class);
        mountPoint = mock(DOMMountPoint.class);
        doReturn(Optional.of(mountPoint)).when(mountPointService).getMountPoint(any(YangInstanceIdentifier.class));
        doReturn(Optional.of(FixedDOMSchemaService.of(IID_SCHEMA))).when(mountPoint)
            .getService(DOMSchemaService.class);

    }

    @BeforeClass
    public static final void initModelContext() throws Exception {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        testFiles.addAll(loadFiles("/foo-xml-test/yang"));
        MODEL_CONTEXT = YangParserTestUtils.parseYangFiles(testFiles);
    }

    // FIXME: migrate callers to use string literals
    @Deprecated
    final @NonNull NormalizedNode parseResource(final String uriPath, final String resourceName) throws IOException {
        return parse(uriPath, AbstractResourceBodyTest.class.getResourceAsStream(resourceName));
    }

    final @NonNull NormalizedNode parse(final String uriPath, final String patchBody) throws IOException {
        return parse(uriPath, stringInputStream(patchBody));
    }

    private @NonNull NormalizedNode parse(final String uriPath, final InputStream patchBody) throws IOException {
        try (var body = bodyConstructor.apply(patchBody)) {
            final var context = ParserIdentifier.toInstanceIdentifier(uriPath, MODEL_CONTEXT, mountPointService);
            return body.toNormalizedNode(context.getInstanceIdentifier(), context.inference(), context.getSchemaNode());
        }
    }

    static final void assertRangeViolation(final ThrowingRunnable runnable) {
        final var ex = assertThrows(RestconfDocumentedException.class, runnable);
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());

        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
        assertEquals("bar error app tag", error.getErrorAppTag());
        assertEquals("bar error message", error.getErrorMessage());
    }
}
