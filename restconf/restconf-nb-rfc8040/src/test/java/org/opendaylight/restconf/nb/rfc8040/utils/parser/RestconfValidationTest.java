/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040.utils.parser;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.Iterators;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.opendaylight.restconf.common.errors.RestconfDocumentedException;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.common.Revision;

/**
 * Unit test for {@link ParserIdentifier}'s validate methods.
 */
public class RestconfValidationTest {
    private static final List<String> REVISIONS = Arrays.asList("2014-01-01", "2015-01-01", "2016-01-01");
    private static final List<String> NAMES = Arrays.asList("_module-1", "_module-2", "_module-3");

    /**
     * Test of successful validation of module revision.
     */
    @Test
    public void validateAndGetRevisionTest() {
        final Revision revision = ParserIdentifier.validateAndGetRevision(REVISIONS.iterator());
        assertNotNull("Correct module revision should be validated", revision);
        assertEquals(Revision.of("2014-01-01"), revision);
    }

    /**
     * Negative test of module revision validation when there is no revision. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetRevisionNotSuppliedTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.validateAndGetRevision(Collections.emptyIterator()));

        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of module revision validation when supplied revision is not parsable as revision. Test fails
     * catching <code>RestconfDocumentedException</code>.
     */
    @Test
    public void validateAndGetRevisionNotParsableTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.validateAndGetRevision(Iterators.singletonIterator("not-parsable-as-date")));
        assertThat(ex.getMessage(), containsString("Supplied revision is not in expected date format YYYY-mm-dd"));
    }

    /**
     * Test of successful validation of module name.
     */
    @Test
    public void validateAndGetModulNameTest() {
        final String moduleName = ParserIdentifier.validateAndGetModulName(NAMES.iterator());
        assertNotNull("Correct module name should be validated", moduleName);
        assertEquals("_module-1", moduleName);
    }

    /**
     * Negative test of module name validation when there is no module name. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModulNameNotSuppliedTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.validateAndGetModulName(Collections.emptyIterator()));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of module name validation when supplied name is not parsable as module name on the first
     * character. Test fails catching <code>RestconfDocumentedException</code> and checking for correct error type,
     * error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameNotParsableFirstTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.validateAndGetModulName(Iterators.singletonIterator(
                "01-not-parsable-as-name-on-firts-char")));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of module name validation when supplied name is not parsable as module name on any of the
     * characters after the first character. Test fails catching <code>RestconfDocumentedException</code> and checking
     * for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameNotParsableNextTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.validateAndGetModulName(Iterators.singletonIterator(
                "not-parsable-as-name-after-first-char*")));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of module name validation when supplied name begins with 'XML' ignore case. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameNotParsableXmlTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.validateAndGetModulName(Iterators.singletonIterator("xMl-module-name")));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }

    /**
     * Negative test of module name validation when supplied name is empty. Test fails catching
     * <code>RestconfDocumentedException</code> and checking for correct error type, error tag and error status code.
     */
    @Test
    public void validateAndGetModuleNameEmptyTest() {
        final RestconfDocumentedException ex = assertThrows(RestconfDocumentedException.class,
            () -> ParserIdentifier.validateAndGetModulName(Iterators.singletonIterator("")));
        assertEquals(ErrorType.PROTOCOL, ex.getErrors().get(0).getErrorType());
        assertEquals(ErrorTag.INVALID_VALUE, ex.getErrors().get(0).getErrorTag());
    }
}
