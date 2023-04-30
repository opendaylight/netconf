/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.RPC_REPLY_OK;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.SESSION_ID_FOR_REPORTING;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.executeOperation;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.verifyResponse;

import com.google.common.collect.ImmutableClassToInstanceMap;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opendaylight.mdsal.dom.api.DOMDataBroker;
import org.opendaylight.mdsal.dom.api.DOMDataTreeReadWriteTransaction;
import org.opendaylight.netconf.api.DocumentedException;
import org.opendaylight.netconf.server.mdsal.DOMDataTransactionValidator;
import org.opendaylight.netconf.server.mdsal.DOMDataTransactionValidator.ValidationFailedException;
import org.opendaylight.netconf.server.mdsal.TransactionProvider;
import org.opendaylight.yangtools.util.concurrent.FluentFutures;
import org.opendaylight.yangtools.yang.common.ErrorSeverity;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.w3c.dom.Document;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ValidateTest {
    @Mock
    private DOMDataTransactionValidator noopValidator;
    @Mock
    private DOMDataTransactionValidator failingValidator;
    @Mock
    private DOMDataTreeReadWriteTransaction readWriteTx;
    @Mock
    private DOMDataBroker dataBroker;

    @Before
    public void setUp() {
        doReturn(FluentFutures.immediateNullFluentFuture()).when(noopValidator).validate(any());
        doReturn(FluentFutures.immediateFailedFluentFuture(new ValidationFailedException("invalid data")))
            .when(failingValidator).validate(any());
        doReturn(readWriteTx).when(dataBroker).newReadWriteTransaction();
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void testValidateUnsupported() throws Exception {
        whenValidatorIsNotDefined();
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate.xml"));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, e.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, e.getErrorType());
    }

    @Test
    public void testSourceMissing() throws Exception {
        whenUsingValidator(noopValidator);
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate_no_source.xml"));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.MISSING_ELEMENT, e.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, e.getErrorType());
    }

    @Test
    public void testSourceRunning() throws Exception {
        whenUsingValidator(noopValidator);
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate_running.xml"));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, e.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, e.getErrorType());
    }

    @Test
    public void testValidateEmptyTx() throws Exception {
        whenUsingValidator(noopValidator);
        verifyResponse(validate("messages/mapping/validate/validate.xml"), RPC_REPLY_OK);
        verifyNoMoreInteractions(noopValidator);
    }

    @Test
    public void testValidate() throws Exception {
        whenUsingValidator(noopValidator);
        final TransactionProvider transactionProvider = initCandidateTransaction();
        verifyResponse(validate("messages/mapping/validate/validate.xml", transactionProvider), RPC_REPLY_OK);
        verify(noopValidator).validate(readWriteTx);
    }

    @Test
    public void testValidateFailed() throws Exception {
        whenUsingValidator(failingValidator);
        final TransactionProvider transactionProvider = initCandidateTransaction();
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate.xml", transactionProvider));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, e.getErrorTag());
        assertEquals(ErrorType.APPLICATION, e.getErrorType());
    }

    private void whenValidatorIsNotDefined() {
        doReturn(ImmutableClassToInstanceMap.of()).when(dataBroker).getExtensions();
    }

    private void whenUsingValidator(final DOMDataTransactionValidator validator) {
        doReturn(ImmutableClassToInstanceMap.of(DOMDataTransactionValidator.class, validator))
            .when(dataBroker).getExtensions();
    }

    private TransactionProvider initCandidateTransaction() {
        final TransactionProvider transactionProvider = new TransactionProvider(dataBroker, SESSION_ID_FOR_REPORTING);
        transactionProvider.getOrCreateTransaction();
        return transactionProvider;
    }

    private static Document validate(final String resource,  final TransactionProvider transactionProvider)
            throws Exception {
        final Validate validate = new Validate(SESSION_ID_FOR_REPORTING, transactionProvider);
        return executeOperation(validate, resource);
    }

    private Document validate(final String resource) throws Exception {
        return validate(resource, new TransactionProvider(dataBroker, SESSION_ID_FOR_REPORTING));
    }
}