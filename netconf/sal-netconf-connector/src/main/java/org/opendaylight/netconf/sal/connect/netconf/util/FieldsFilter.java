/*
 * Copyright © 2020 FRINX s.r.o. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netconf.sal.connect.netconf.util;

import java.util.List;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;

/**
 * Definition of the subtree filter with single parent path and possibly multiple field sub-paths that are used
 * for reading/selection of specific entities.
 */
public final class FieldsFilter {
    private final YangInstanceIdentifier path;
    private final List<YangInstanceIdentifier> fields;

    private FieldsFilter(final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        this.path = path;
        this.fields = fields;
    }

    /**
     * Creation of new fields filter using parent path and fields. Field paths are relative to parent path.
     *
     * @param path   parent query path
     * @param fields list of specific selection fields
     * @return instance of {@link FieldsFilter}
     */
    public static FieldsFilter from(final YangInstanceIdentifier path, final List<YangInstanceIdentifier> fields) {
        return new FieldsFilter(path, fields);
    }

    /**
     * Get parent path.
     *
     * @return instance of {@link YangInstanceIdentifier}
     */
    public YangInstanceIdentifier getPath() {
        return path;
    }

    /**
     * Get list of paths that narrows the filter for specific fields. Field paths are relative to parent path.
     *
     * @return {@link List} of field paths.
     */
    public List<YangInstanceIdentifier> getFields() {
        return fields;
    }
}