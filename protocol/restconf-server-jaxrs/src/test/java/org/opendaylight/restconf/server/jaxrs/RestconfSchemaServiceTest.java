/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.jaxrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.server.jaxrs.AbstractRestconfTest.assertEntity;
import static org.opendaylight.restconf.server.jaxrs.AbstractRestconfTest.assertError;

import com.google.common.util.concurrent.Futures;
import java.io.IOException;
import java.io.Reader;
import javax.ws.rs.core.SecurityContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.mdsal.dom.spi.FixedDOMSchemaService;
import org.opendaylight.netconf.common.ErrorMessage;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.server.mdsal.MdsalDatabindProvider;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
import org.opendaylight.restconf.server.spi.ErrorTagMapping;
import org.opendaylight.restconf.server.spi.RestconfStream.Registry;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Revision;
import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.api.source.YangTextSource;
import org.opendaylight.yangtools.yang.test.util.YangParserTestUtils;

/**
 * Unit tests for {@code RestconfSchemaService}.
 */
@ExtendWith(MockitoExtension.class)
class RestconfSchemaServiceTest {
    // schema context with modules
    private static final EffectiveModelContext MODEL_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/modules");

    @Mock
    private YangTextSourceExtension sourceProvider;
    @Mock
    private DOMDataBroker dataBroker;
    @Mock
    private DOMActionService actionService;
    @Mock
    private DOMRpcService rpcService;
    @Mock
    private DOMMountPointService mountPointService;
    @Mock
    private YangTextSource yangSource;
    @Mock
    private Reader yangReader;
    @Mock
    private SecurityContext sc;
    @Mock
    private Registry streamRegistry;
    @Mock
    private SSESenderFactory senderFactory;

    // service under test
    private JaxRsRestconf restconf;

    @BeforeEach
    void setup() {
        restconf = new JaxRsRestconf(
            new MdsalRestconfServer(new MdsalDatabindProvider(
                new FixedDOMSchemaService(() -> MODEL_CONTEXT, sourceProvider)), dataBroker, rpcService, actionService,
                mountPointService),
            streamRegistry, senderFactory, ErrorTagMapping.RFC8040, PrettyPrintParam.FALSE);
    }

    /**
     * Get schema with identifier of existing module and check if correct module was found.
     */
    @Test
    void getSchemaTest() throws Exception {
        doReturn(Futures.immediateFuture(yangSource)).when(sourceProvider)
            .getYangTexttSource(new SourceIdentifier("module1", Revision.of("2014-01-01")));
        doReturn(yangReader).when(yangSource).openStream();

        assertSame(yangReader, assertEntity(Reader.class, 200,
            ar -> restconf.modulesYangGET("module1", "2014-01-01", sc, ar)));
    }

    /**
     * Get schema with identifier of not-existing module. Trying to create <code>SchemaExportContext</code> with
     * not-existing module should result in error.
     */
    @Test
    void getSchemaForNotExistingModuleTest() {
        final var error = assertError(409, ar -> restconf.modulesYinGET("not-existing", "2016-01-01", sc, ar));
        assertEquals(new ErrorMessage("Source not-existing@2016-01-01 not found"), error.message());
        assertEquals(ErrorTag.DATA_MISSING, error.tag());
        assertEquals(ErrorType.APPLICATION, error.type());
    }

    /**
     * Try to get schema with empty (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    void getSchemaWithEmptyIdentifierTest() {
        final var error = assertError(400, ar -> restconf.modulesYangGET("", null, sc, ar));
        assertEquals(new ErrorMessage("Identifier must start with character from set 'a-zA-Z_"), error.message());
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
    }

    /**
     * Try to get schema with not-parsable identifier catching <code>RestconfDocumentedException</code>. Error type,
     * error tag and error status code are compared to expected values.
     */
    @Test
    void getSchemaWithNotParsableIdentifierTest() {
        final var error = assertError(400, ar -> restconf.modulesYangGET("01_module", "2016-01-01", sc, ar));
        assertEquals(new ErrorMessage("Identifier must start with character from set 'a-zA-Z_"), error.message());
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
    }

    /**
     * Try to get schema with wrong (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     *
     * <p>Not valid identifier contains only revision without module name.
     */
    @Test
    void getSchemaWrongIdentifierTest() {
        final var error = assertError(400, ar -> restconf.modulesYangGET("2014-01-01", null, sc, ar));
        assertEquals(new ErrorMessage("Identifier must start with character from set 'a-zA-Z_"), error.message());
        assertEquals(ErrorType.PROTOCOL, error.type());
        assertEquals(ErrorTag.INVALID_VALUE, error.tag());
    }

    /**
     * Try to get schema with identifier which does not contain revision catching and check if the correct module
     * was found.
     */
    @Test
    void getSchemaWithoutRevisionTest() throws IOException {
        doReturn(Futures.immediateFuture(yangSource)).when(sourceProvider)
            .getYangTexttSource(new SourceIdentifier("module-without-revision", (Revision) null));
        doReturn(yangReader).when(yangSource).openStream();
        assertSame(yangReader, assertEntity(Reader.class, 200,
            ar -> restconf.modulesYangGET("module-without-revision", null, sc, ar)));
    }
}
