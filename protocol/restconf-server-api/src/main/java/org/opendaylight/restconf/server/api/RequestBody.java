/*
 * Copyright (c) 2023 PANTHEON.tech, s.r.o. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.restconf.server.api;

import static java.util.Objects.requireNonNull;

import com.google.gson.JsonIOException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.netconf.common.DatabindContext;
import org.opendaylight.netconf.common.ErrorMessage;
import org.opendaylight.restconf.api.ConsumableBody;
import org.opendaylight.yangtools.yang.common.ErrorTag;
import org.opendaylight.yangtools.yang.common.ErrorType;
import org.opendaylight.yangtools.yang.data.api.YangNetconfErrorAware;

/**
 * An abstract request body backed by an {@link InputStream}. In controls the access to input stream, so that it can
 * only be taken once.
 */
@NonNullByDefault
abstract sealed class RequestBody extends ConsumableBody
        permits ChildBody, DataPostBody, OperationInputBody, PatchBody, ResourceBody {
    RequestBody(final InputStream inputStream) {
        super(inputStream);
    }

    static final Exception unmaskIOException(final Exception ex) {
        return ex instanceof JsonIOException jsonIO && jsonIO.getCause() instanceof IOException io ? io : ex;
    }

    /**
     * Return a new {@link ServerException} constructed from the combination of a message and a caught exception.
     * Provided exception and its causal chain will be examined for well-known constructs in an attempt to extract
     * error information. If no such information is found an error with type {@link ErrorType#PROTOCOL} and tag
     * {@link ErrorTag#MALFORMED_MESSAGE} will be reported.
     *
     * @param messagePrefix exception message prefix
     * @param caught caught exception
     * @return A new {@link ServerException}
     */
    protected static final ServerException newProtocolMalformedMessageServerException(final DatabindPath path,
            final String messagePrefix, final Exception caught) {
        return newServerParseException(path, ErrorType.PROTOCOL, ErrorTag.MALFORMED_MESSAGE, messagePrefix, caught);
    }

    private static ServerException newServerParseException(final DatabindPath path, final ErrorType type,
            final ErrorTag tag, final String messagePrefix, final Exception caught) {
        final var message = requireNonNull(messagePrefix) + ": " + caught.getMessage();
        final var errors = exceptionErrors(path.databind(), caught);
        return new ServerException(message, errors != null ? errors : List.of(new ServerError(type, tag, message)),
            caught);
    }

    private static @Nullable List<ServerError> exceptionErrors(final DatabindContext databind, final Exception caught) {
        Throwable cause = caught;
        do {
            if (cause instanceof YangNetconfErrorAware infoAware) {
                return infoAware.getNetconfErrors().stream()
                    .map(error -> {
                        final var message = error.message();
                        final var path = error.path();

                        return new ServerError(error.type(), error.tag(),
                            message != null ? new ErrorMessage(message) : null, error.appTag(),
                            path != null ? new ServerErrorPath(databind, path) : null,
                            // FIXME: pass down error.info()
                            null);
                    })
                    .collect(Collectors.toList());
            }
            cause = cause.getCause();
        } while (cause != null);

        return null;
    }

}
