/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.nb.rfc8040;

import static org.junit.Assert.assertEquals;

import java.text.ParseException;
import java.util.List;
import org.junit.Test;
import org.opendaylight.restconf.nb.rfc8040.ApiPath.ApiIdentifier;
import org.opendaylight.restconf.nb.rfc8040.FieldsParameter.NodeSelector;

public class FieldsParameterTest {
    // https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.3:
    //    "/" is used in a path to retrieve a child node of a node.  For
    //    example, to retrieve only the "label" of an album, use
    //    "fields=admin/label".
    @Test
    public void testAdminLabel() throws ParseException {
        final var selectors = assertValidFields("admin/label");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier("admin"), new ApiIdentifier("label")), selector.path());
        assertEquals(List.of(), selector.subSelectors());
    }

    // https://datatracker.ietf.org/doc/html/rfc8040#section-4.8.3:
    //    For example, assume that the target resource is the "album" list.  To
    //    retrieve only the "label" and "catalogue-number" of the "admin"
    //    container within an album, use
    //    "fields=admin(label;catalogue-number)".
    @Test
    public void testAdminLabelCatalogueNumber() throws ParseException {
        final var selectors = assertValidFields("admin(label;catalogue-number)");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier("admin")), selector.path());

        final var subSelectors = selector.subSelectors();
        assertEquals(2, subSelectors.size());

        var subSelector = subSelectors.get(0);
        assertEquals(List.of(new ApiIdentifier("label")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());


        subSelector = subSelectors.get(1);
        assertEquals(List.of(new ApiIdentifier("catalogue-number")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());
    }

    // https://datatracker.ietf.org/doc/html/rfc8040#appendix-B.3.3:
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
    public void testModulesModuleNameRevision() {
        final var selectors = assertValidFields("ietf-yang-library:modules-state/module(name;revision)");
        assertEquals(1, selectors.size());

        final var selector = selectors.get(0);
        assertEquals(List.of(new ApiIdentifier("ietf-yang-library", "modules-state"), new ApiIdentifier("module")),
            selector.path());

        final var subSelectors = selector.subSelectors();
        assertEquals(2, subSelectors.size());

        var subSelector = subSelectors.get(0);
        assertEquals(List.of(new ApiIdentifier("name")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());

        subSelector = subSelectors.get(1);
        assertEquals(List.of(new ApiIdentifier("revision")), subSelector.path());
        assertEquals(List.of(), subSelector.subSelectors());
    }

    private static List<NodeSelector> assertValidFields(final String str) {
        try {
            return FieldsParameter.parse(str).nodeSelectors();
        } catch (ParseException e) {
            throw new AssertionError(e);
        }
    }
}
