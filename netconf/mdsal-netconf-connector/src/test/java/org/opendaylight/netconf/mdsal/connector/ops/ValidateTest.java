/*
 * Copyright (c) 2018 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netconf.mdsal.connector.ops;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opendaylight.netconf.mdsal.connector.ops.AbstractNetconfOperationTest.RPC_REPLY_OK;
import static org.opendaylight.netconf.mdsal.connector.ops.AbstractNetconfOperationTest.SESSION_ID_FOR_REPORTING;
import static org.opendaylight.netconf.mdsal.connector.ops.AbstractNetconfOperationTest.executeOperation;
import static org.opendaylight.netconf.mdsal.connector.ops.AbstractNetconfOperationTest.verifyResponse;

import com.google.common.util.concurrent.Futures;
import java.util.Collections;
import org.custommonkey.xmlunit.XMLUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opendaylight.controller.config.util.xml.DocumentedException;
import org.opendaylight.controller.md.sal.dom.api.DOMDataBroker;
import org.opendaylight.controller.md.sal.dom.api.DOMDataReadWriteTransaction;
import org.opendaylight.netconf.mdsal.connector.DOMDataTransactionValidator;
import org.opendaylight.netconf.mdsal.connector.DOMDataTransactionValidator.ValidationFailedException;
import org.opendaylight.netconf.mdsal.connector.TransactionProvider;
import org.w3c.dom.Document;

public class ValidateTest {
    @Mock
    private DOMDataTransactionValidator noopValidator;
    @Mock
    private DOMDataTransactionValidator failingValidator;
    @Mock
    private DOMDataReadWriteTransaction readWriteTx;
    @Mock
    private DOMDataBroker dataBroker;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        doReturn(Futures.immediateCheckedFuture(null)).when(noopValidator).validate(any());
        doReturn(Futures.immediateFailedCheckedFuture(new ValidationFailedException("invalid data")))
            .when(failingValidator).validate(any());
        doReturn(readWriteTx).when(dataBroker).newReadWriteTransaction();
        XMLUnit.setIgnoreWhitespace(true);
    }

    @Test
    public void testValidateUnsupported() throws Exception {
        whenValidatorIsNotDefined();
        try {
            validate("messages/mapping/validate/validate.xml");
            fail("Should have failed - <validate> not supported");
        } catch (final DocumentedException e) {
            assertEquals(DocumentedException.ErrorSeverity.ERROR, e.getErrorSeverity());
            assertEquals(DocumentedException.ErrorTag.OPERATION_NOT_SUPPORTED, e.getErrorTag());
            assertEquals(DocumentedException.ErrorType.PROTOCOL, e.getErrorType());
        }
    }

    @Test
    public void testSourceMissing() throws Exception {
        whenUsingValidator(noopValidator);
        try {
            validate("messages/mapping/validate/validate_no_source.xml");
            fail("Should have failed - <source> element is missing");
        } catch (final DocumentedException e) {
            assertEquals(DocumentedException.ErrorSeverity.ERROR, e.getErrorSeverity());
            assertEquals(DocumentedException.ErrorTag.MISSING_ELEMENT, e.getErrorTag());
            assertEquals(DocumentedException.ErrorType.PROTOCOL, e.getErrorType());
        }
    }

    @Test
    public void testSourceRunning() throws Exception {
        whenUsingValidator(noopValidator);
        try {
            validate("messages/mapping/validate/validate_running.xml");
            fail("Should have failed - <running/> is not supported");
        } catch (final DocumentedException e) {
            assertEquals(DocumentedException.ErrorSeverity.ERROR, e.getErrorSeverity());
            assertEquals(DocumentedException.ErrorTag.OPERATION_NOT_SUPPORTED, e.getErrorTag());
            assertEquals(DocumentedException.ErrorType.PROTOCOL, e.getErrorType());
        }
    }

    @Test
    public void testValidateEmptyTx() throws Exception {
        whenUsingValidator(noopValidator);
        verifyResponse(validate("messages/mapping/validate/validate.xml"), RPC_REPLY_OK);
        verifyZeroInteractions(noopValidator);
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
        try {
            validate("messages/mapping/validate/validate.xml", transactionProvider);
            fail("Should have failed - operation failed");
        } catch (final DocumentedException e) {
            assertEquals(DocumentedException.ErrorSeverity.ERROR, e.getErrorSeverity());
            assertEquals(DocumentedException.ErrorTag.OPERATION_FAILED, e.getErrorTag());
            assertEquals(DocumentedException.ErrorType.APPLICATION, e.getErrorType());
        }
    }

    private void whenValidatorIsNotDefined() {
        doReturn(Collections.emptyMap()).when(dataBroker).getSupportedExtensions();
    }

    private void whenUsingValidator(final DOMDataTransactionValidator validator) {
        doReturn(Collections.singletonMap(DOMDataTransactionValidator.class, validator))
            .when(dataBroker).getSupportedExtensions();
    }

    private TransactionProvider initCandidateTransaction() {
        final TransactionProvider transactionProvider = new TransactionProvider(dataBroker, SESSION_ID_FOR_REPORTING);
        transactionProvider.getOrCreateTransaction();
        return transactionProvider;
    }

    private Document validate(final String resource,
                              final TransactionProvider transactionProvider) throws Exception {
        final Validate validate = new Validate(SESSION_ID_FOR_REPORTING, transactionProvider);
        return executeOperation(validate, resource);
    }

    private Document validate(final String resource) throws Exception {
        return validate(resource, new TransactionProvider(dataBroker, SESSION_ID_FOR_REPORTING));
    }
}