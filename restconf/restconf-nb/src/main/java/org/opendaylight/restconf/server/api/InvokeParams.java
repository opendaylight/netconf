/*
 * Copyright (c) 2021 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import java.util.Map;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.restconf.api.FormatParameters;
import org.opendaylight.restconf.api.query.PrettyPrintParam;

/**
 * Supported query parameters of {@code POST} HTTP operation when the request is to invoke a YANG operation, be it
 * {@code rpc} or {@code action}. There is no such thing in RFC8040, but we support pretty-printing of the resulting
 * {@code output} container.
 */
public record InvokeParams(@NonNull PrettyPrintParam prettyPrint) implements FormatParameters {
    public static final @NonNull InvokeParams EMPTY = new InvokeParams(PrettyPrintParam.FALSE);

    public InvokeParams {
        requireNonNull(prettyPrint);
    }

    /**
     * Return {@link InvokeParams} for specified query parameters.
     *
     * @param queryParameters Parameters and their values
     * @return A {@link InvokeParams}
     * @throws NullPointerException if {@code queryParameters} is {@code null}
     * @throws IllegalArgumentException if the parameters are invalid
     */
    public static @NonNull InvokeParams ofQueryParameters(final Map<String, String> queryParameters) {
        return FormatParametersHelper.ofQueryParameters(queryParameters, InvokeParams::new, EMPTY);
    }
}
