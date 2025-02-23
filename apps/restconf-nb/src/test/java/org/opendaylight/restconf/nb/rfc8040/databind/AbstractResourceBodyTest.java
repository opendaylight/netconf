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

import java.io.InputStream;
import java.text.ParseException;
import java.util.function.Function;
import org.eclipse.jdt.annotation.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPoint;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.netconf.common.ErrorMessage;
import org.opendaylight.restconf.api.ApiPath;
import org.opendaylight.restconf.mdsal.spi.data.MdsalRestconfStrategy;
import org.opendaylight.restconf.server.api.DatabindContext;
import org.opendaylight.restconf.server.api.ResourceBody;
import org.opendaylight.restconf.server.api.ServerError;
import org.opendaylight.restconf.server.api.ServerException;
import org.opendaylight.restconf.server.mdsal.MdsalMountPointResolver;
import org.opendaylight.restconf.server.mdsal.MdsalServerStrategy;
import org.opendaylight.restconf.server.spi.ApiPathNormalizer;
import org.opendaylight.restconf.server.spi.NotSupportedServerActionOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerModulesOperations;
import org.opendaylight.restconf.server.spi.NotSupportedServerRpcOperations;
import org.opendaylight.restconf.server.spi.ServerStrategy.StrategyAndPath;
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
    DOMDataBroker dataBroker;
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
        final var testFiles = loadFiles("/instance-identifier");
        testFiles.addAll(loadFiles("/modules"));
        testFiles.addAll(loadFiles("/foo-xml-test/yang"));
        DATABIND = DatabindContext.ofModel(YangParserTestUtils.parseYangFiles(testFiles));
    }

    final @NonNull NormalizedNode parse(final String uriPath, final String patchBody) throws ServerException {
        final ApiPath apiPath;
        try {
            apiPath = ApiPath.parse(uriPath);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }

        final var strategy = new MdsalServerStrategy(DATABIND, new MdsalMountPointResolver(mountPointService),
            NotSupportedServerActionOperations.INSTANCE, new MdsalRestconfStrategy(IID_DATABIND, dataBroker),
            NotSupportedServerModulesOperations.INSTANCE, NotSupportedServerRpcOperations.INSTANCE);
        final StrategyAndPath stratAndPath;
        try {
            stratAndPath = strategy.resolveStrategy(apiPath);
        } catch (ServerException e) {
            throw new AssertionError(e);
        }

        try (var body = bodyConstructor.apply(stringInputStream(patchBody))) {
            return body.toNormalizedNode(new ApiPathNormalizer(DATABIND).normalizeDataPath(stratAndPath.path()));
        }
    }

    static final ServerError assertError(final Executable executable) {
        final var ex = assertThrows(ServerException.class, executable);
        final var errors = ex.errors();
        assertEquals(1, errors.size());
        return errors.get(0);
    }

    static final void assertRangeViolation(final Executable executable) {
        final var error = assertError(executable);
        assertEquals(ErrorType.APPLICATION, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
        assertEquals("bar error app tag", error.appTag());
        assertEquals(new ErrorMessage("bar error message"), error.message());
    }
}
