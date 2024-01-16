/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.jaxrs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.opendaylight.restconf.nb.jaxrs.AbstractRestconfTest.assertEntity;
import static org.opendaylight.restconf.nb.jaxrs.AbstractRestconfTest.assertError;

import com.google.common.util.concurrent.Futures;
import java.io.Reader;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMActionService;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMMountPointService;
import org.opendaylight.mdsal.dom.api.DOMRpcService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService;
import org.opendaylight.mdsal.dom.api.DOMSchemaService.YangTextSourceExtension;
import org.opendaylight.restconf.server.mdsal.MdsalRestconfServer;
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
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class RestconfSchemaServiceTest {
    // schema context with modules
    private static final EffectiveModelContext SCHEMA_CONTEXT =
        YangParserTestUtils.parseYangResourceDirectory("/modules");

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
    private DOMMountPointService mountPointService;
    @Mock
    private YangTextSource yangSource;
    @Mock
    private Reader yangReader;

    // service under test
    private JaxRsRestconf restconf;

    @Before
    public void setup() throws Exception {
        doReturn(SCHEMA_CONTEXT).when(schemaService).getGlobalContext();
        doCallRealMethod().when(schemaService).extension(any());
        doReturn(List.of(sourceProvider)).when(schemaService).supportedExtensions();

        restconf = new JaxRsRestconf(new MdsalRestconfServer(schemaService, dataBroker, rpcService, actionService,
            mountPointService));
    }

    /**
     * Get schema with identifier of existing module and check if correct module was found.
     */
    @Test
    public void getSchemaTest() throws Exception {
        doReturn(Futures.immediateFuture(yangSource)).when(sourceProvider)
            .getYangTexttSource(new SourceIdentifier("module1", Revision.of("2014-01-01")));
        doReturn(yangReader).when(yangSource).openStream();

        assertSame(yangReader, assertEntity(Reader.class, 200,
            ar -> restconf.modulesYangGET("module1", "2014-01-01", ar)));
    }

    /**
     * Get schema with identifier of not-existing module. Trying to create <code>SchemaExportContext</code> with
     * not-existing module should result in error.
     */
    @Test
    public void getSchemaForNotExistingModuleTest() {
        final var error = assertError(ar -> restconf.modulesYinGET("not-existing", "2016-01-01", ar));
        assertEquals("Source not-existing@2016-01-01 not found", error.getErrorMessage());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
    }

    /**
     * Try to get schema with empty (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     */
    @Test
    public void getSchemaWithEmptyIdentifierTest() {
        final var error = assertError(ar -> restconf.modulesYangGET("", null, ar));
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema with not-parsable identifier catching <code>RestconfDocumentedException</code>. Error type,
     * error tag and error status code are compared to expected values.
     */
    @Test
    public void getSchemaWithNotParsableIdentifierTest() {
        final var error = assertError(ar -> restconf.modulesYangGET("01_module", "2016-01-01", ar));
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema with wrong (not valid) identifier catching <code>RestconfDocumentedException</code>. Error
     * type, error tag and error status code are compared to expected values.
     *
     * <p>
     * Not valid identifier contains only revision without module name.
     */
    @Test
    public void getSchemaWrongIdentifierTest() {
        final var error = assertError(ar -> restconf.modulesYangGET("2014-01-01", null, ar));
        assertEquals("Identifier must start with character from set 'a-zA-Z_", error.getErrorMessage());
        assertEquals(ErrorType.PROTOCOL, error.getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, error.getErrorTag());
    }

    /**
     * Try to get schema with identifier which does not contain revision catching
     * <code>RestconfDocumentedException</code>. Error type, error tag and error status code are compared to expected
     * values.
     */
    @Test
    public void getSchemaWithoutRevisionTest() {
        final var error = assertError(ar -> restconf.modulesYinGET("module", null, ar));
        assertEquals("Source module not found", error.getErrorMessage());
        assertEquals(ErrorType.APPLICATION, error.getErrorType());
        assertEquals(ErrorTag.DATA_MISSING, error.getErrorTag());
    }
}
