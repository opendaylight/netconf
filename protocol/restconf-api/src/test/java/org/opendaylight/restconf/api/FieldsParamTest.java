/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.text.ParseException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.opendaylight.restconf.api.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.api.query.FieldsParam;
import org.opendaylight.restconf.api.query.FieldsParam.NodeSelector;

class FieldsParamTest {
    // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.3:
    //    ";" is used to select multiple nodes.  For example, to retrieve only
    //    the "genre" and "year" of an album, use "fields=genre;year".
    @Test
    void testGenreYear() {
        final var selectors = assertValidFields("genre;year");
        assertEquals(2, selectors.size());

        var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier(null, "genre")), selector.path());
        assertEquals(List.of(), selector.subSelectors());

        selector = selectors.get(1);
        assertEquals(List.of(new ApiIdentifier(null, "year")), selector.path());
        assertEquals(List.of(), selector.subSelectors());
    }

    // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.3:
    //    "/" is used in a path to retrieve a child node of a node.  For
    //    example, to retrieve only the "label" of an album, use
    //    "fields=admin/label".
    @Test
    void testAdminLabel() {
        final var selectors = assertValidFields("admin/label");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier(null, "admin"), new ApiIdentifier(null, "label")), selector.path());
        assertEquals(List.of(), selector.subSelectors());
    }

    // https://www.rfc-editor.org/rfc/rfc8040#section-4.8.3:
    //    For example, assume that the target resource is the "album" list.  To
    //    retrieve only the "label" and "catalogue-number" of the "admin"
    //    container within an album, use
    //    "fields=admin(label;catalogue-number)".
    @Test
    void testAdminLabelCatalogueNumber() {
        final var selectors = assertValidFields("admin(label;catalogue-number)");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier(null, "admin")), selector.path());

        final var subSelectors = selector.subSelectors();
        assertEquals(2, subSelectors.size());

        var subSelector = subSelectors.get(0);
        assertEquals(List.of(new ApiIdentifier(null, "label")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());


        subSelector = subSelectors.get(1);
        assertEquals(List.of(new ApiIdentifier(null, "catalogue-number")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());
    }

    // https://www.rfc-editor.org/rfc/rfc8040#appendix-B.3.3:
    //    In this example, the client is retrieving the datastore resource in
    //    JSON format, but retrieving only the "modules-state/module" list, and
    //    only the "name" and "revision" nodes from each list entry.  Note that
    //    the top node returned by the server matches the target resource node
    //    (which is "data" in this example).  The "module-set-id" leaf is not
    //    returned because it is not selected in the fields expression.
    //
    //       GET /restconf/data?fields=ietf-yang-library:modules-state/\
    //           module(name;revision) HTTP/1.1
    @Test
    void testModulesModuleNameRevision() {
        final var selectors = assertValidFields("ietf-yang-library:modules-state/module(name;revision)");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(
            List.of(new ApiIdentifier("ietf-yang-library", "modules-state"), new ApiIdentifier(null, "module")),
            selector.path());

        final var subSelectors = selector.subSelectors();
        assertEquals(2, subSelectors.size());

        var subSelector = subSelectors.get(0);
        assertEquals(List.of(new ApiIdentifier(null, "name")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());

        subSelector = subSelectors.get(1);
        assertEquals(List.of(new ApiIdentifier(null, "revision")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());
    }

    @Test
    void testModulesSimple() {
        final var selectors = assertValidFields("ietf-yang-library:modules-state");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier("ietf-yang-library", "modules-state")), selector.path());
        assertEquals(List.of(), selector.subSelectors());
    }

    @Test
    void testUnqualifiedSubQualified() {
        final var selectors = assertValidFields("a(b:c)");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier(null, "a")), selector.path());

        final var subSelectors = selector.subSelectors();
        assertEquals(1, subSelectors.size());

        final var subSelector = subSelectors.get(0);
        assertEquals(List.of(new ApiIdentifier("b", "c")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());
    }

    @Test
    void testQualifiedSubUnqualified() {
        final var selectors = assertValidFields("a:b(c)");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier("a", "b")), selector.path());

        final var subSelectors = selector.subSelectors();
        assertEquals(1, subSelectors.size());

        final var subSelector = subSelectors.get(0);
        assertEquals(List.of(new ApiIdentifier(null, "c")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());
    }

    @Test
    void testDeepNesting() {
        final var selectors = assertValidFields("a(b(c(d)));e(f(g(h)));i(j(k(l)))");
        assertEquals(3, selectors.size());
    }

    @Test
    void testInvalidIdentifier() {
        assertInvalidFields(".", "Expecting [a-ZA-Z_], not '.'", 0);
        assertInvalidFields("a+", "Expecting [a-zA-Z_.-/(:;], not '+'", 1);
        assertInvalidFields("a:.", "Expecting [a-ZA-Z_], not '.'", 2);
        assertInvalidFields("a:b+", "Expecting [a-zA-Z_.-/(:;], not '+'", 3);
        assertInvalidFields("a;)", "Expecting [a-ZA-Z_], not ')'", 2);
        assertInvalidFields("*", "Expecting [a-ZA-Z_], not '*'", 0);
    }

    @Test
    void testUnexpectedEnds() {
        assertInvalidFields("a;", "Unexpected end of input", 2);
        assertInvalidFields("a(", "Unexpected end of input", 2);
        assertInvalidFields("a(a", "Unexpected end of input", 3);
        assertInvalidFields("library(", "Unexpected end of input", 8);
        assertInvalidFields("library(album);", "Unexpected end of input", 15);
    }

    @Test
    void testUnexpectedRightParent() {
        assertInvalidFields("a)", "Expecting ';', not ')'", 1);
        assertInvalidFields("library(album)player", "Expecting ';', not 'p'", 14);
    }

    private static void assertInvalidFields(final String str, final String message, final int errorOffset) {
        final var ex = assertThrows(ParseException.class, () -> FieldsParam.parse(str));
        assertEquals(message, ex.getMessage());
        assertEquals(errorOffset, ex.getErrorOffset());
    }

    private static List<NodeSelector> assertValidFields(final String str) {
        final FieldsParam param;
        try {
            param = FieldsParam.parse(str);
        } catch (ParseException e) {
            throw new AssertionError(e);
        }

        assertEquals(str, param.paramValue());
        return param.nodeSelectors();
    }
}
