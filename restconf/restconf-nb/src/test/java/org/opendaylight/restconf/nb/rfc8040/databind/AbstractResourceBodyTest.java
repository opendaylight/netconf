/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.restconf.nb.rfc8040.legacy.InstanceIdentifierContext;
import org.opendaylight.restconf.server.api.DataPutPath;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

@ExtendWith(MockitoExtension.class)
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

    private static DatabindContext DATABIND;

    @Mock
    DOMMountPointService mountPointService;
    @Mock
    DOMMountPoint mountPoint;

    private final Function<InputStream, ResourceBody> bodyConstructor;

    AbstractResourceBodyTest(final Function<InputStream, ResourceBody> bodyConstructor) {
        assertNotNull(bodyConstructor);
        this.bodyConstructor = bodyConstructor;
    }

    @BeforeAll
    static final void initModelContext() throws Exception {
        final var testFiles = loadFiles("/instanceidentifier/yang");
        testFiles.addAll(loadFiles("/modules"));
        testFiles.addAll(loadFiles("/foo-xml-test/yang"));
        DATABIND = DatabindContext.ofModel(YangParserTestUtils.parseYangFiles(testFiles));
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
        final ApiPath apiPath;
        try {
            apiPath = ApiPath.parse(uriPath);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }

        try (var body = bodyConstructor.apply(patchBody)) {
            final var context = InstanceIdentifierContext.ofApiPath(apiPath, DATABIND, mountPointService);
            return body.toNormalizedNode(
                new DataPutPath(context.databind(), context.inference(), context.getInstanceIdentifier()),
                context.getSchemaNode());
        }
    }

    static final void assertRangeViolation(final Executable executable) {
        final var ex = assertThrows(RestconfDocumentedException.class, executable);
        final var errors = ex.getErrors();
        assertEquals(1, errors.size());

        final var error = errors.get(0);
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
        assertEquals("bar error app tag", error.getErrorAppTag());
        assertEquals("bar error message", error.getErrorMessage());
    }
}
