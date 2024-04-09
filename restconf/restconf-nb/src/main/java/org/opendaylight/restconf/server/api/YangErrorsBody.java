/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.common.base.MoreObjects.ToStringHelper;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.opendaylight.restconf.api.query.PrettyPrintParam;
import org.opendaylight.restconf.common.errors.RestconfError;

/**
 * A {@link DatabindFormattableBody} of <a href="https://www.rfc-editor.org/rfc/rfc8040#section-3.9">yang-errors</a>
 * data template.
 */
@NonNullByDefault
public final class YangErrorsBody extends DatabindFormattableBody {
    private final List<RestconfError> errors;

    private YangErrorsBody(final DatabindContext databind, final List<RestconfError> errors) {
        super(databind);
        this.errors = requireNonNull(errors);
    }

    public static YangErrorsBody of(final DatabindContext databind, final RestconfError error) {
        return new YangErrorsBody(databind, List.of(error));
    }

    public static YangErrorsBody of(final DatabindContext databind, final List<RestconfError> errors) {
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("empty errors");
        }
        return new YangErrorsBody(databind, errors);
    }

    @Override
    protected void formatToJSON(final DatabindContext databind, final PrettyPrintParam prettyPrint,
            final OutputStream out) throws IOException {

    }

    @Override
    protected void formatToXML(final DatabindContext databind, final PrettyPrintParam prettyPrint,
            final OutputStream out) throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    protected ToStringHelper addToStringAttributes(final ToStringHelper helper) {
        return helper.add("errors", errors);
    }
}
