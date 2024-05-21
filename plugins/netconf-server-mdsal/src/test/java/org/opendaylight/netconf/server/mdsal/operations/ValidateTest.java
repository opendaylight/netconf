/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.server.mdsal.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.RPC_REPLY_OK;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.SESSION_ID_FOR_REPORTING;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.executeOperation;
import static org.opendaylight.netconf.server.mdsal.operations.AbstractNetconfOperationTest.verifyResponse;

import java.util.List;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
public class ValidateTest {
    @Mock
    private DOMDataTransactionValidator noopValidator;
    @Mock
    private DOMDataTransactionValidator failingValidator;
    @Mock
    private DOMDataTreeReadWriteTransaction readWriteTx;
    @Mock(answer = Answers.CALLS_REAL_METHODS)
    private DOMDataBroker dataBroker;

    @BeforeAll
    static void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    void testValidateUnsupported() {
        whenValidatorIsNotDefined();
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate.xml"));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, e.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, e.getErrorType());
    }

    @Test
    void testSourceMissing() {
        whenUsingValidator(noopValidator);
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate_no_source.xml"));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.MISSING_ELEMENT, e.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, e.getErrorType());
    }

    @Test
    void testSourceRunning() {
        whenUsingValidator(noopValidator);
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate_running.xml"));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_NOT_SUPPORTED, e.getErrorTag());
        assertEquals(ErrorType.PROTOCOL, e.getErrorType());
    }

    @Test
    void testValidateEmptyTx() throws Exception {
        whenUsingValidator(noopValidator);
        verifyResponse(validate("messages/mapping/validate/validate.xml"), RPC_REPLY_OK);
        verifyNoMoreInteractions(noopValidator);
    }

    @Test
    void testValidate() throws Exception {
        doReturn(FluentFutures.immediateNullFluentFuture()).when(noopValidator).validate(any());
        doReturn(readWriteTx).when(dataBroker).newReadWriteTransaction();
        whenUsingValidator(noopValidator);
        final TransactionProvider transactionProvider = initCandidateTransaction();
        verifyResponse(validate("messages/mapping/validate/validate.xml", transactionProvider), RPC_REPLY_OK);
        verify(noopValidator).validate(readWriteTx);
    }

    @Test
    void testValidateFailed() {
        doReturn(FluentFutures.immediateFailedFluentFuture(new ValidationFailedException("invalid data")))
                .when(failingValidator).validate(any());
        doReturn(readWriteTx).when(dataBroker).newReadWriteTransaction();
        whenUsingValidator(failingValidator);
        final TransactionProvider transactionProvider = initCandidateTransaction();
        final DocumentedException e = assertThrows(DocumentedException.class,
            () -> validate("messages/mapping/validate/validate.xml", transactionProvider));
        assertEquals(ErrorSeverity.ERROR, e.getErrorSeverity());
        assertEquals(ErrorTag.OPERATION_FAILED, e.getErrorTag());
        assertEquals(ErrorType.APPLICATION, e.getErrorType());
    }

    private void whenValidatorIsNotDefined() {
        doReturn(List.of()).when(dataBroker).supportedExtensions();
    }

    private void whenUsingValidator(final DOMDataTransactionValidator validator) {
        doReturn(List.of(validator)).when(dataBroker).supportedExtensions();
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