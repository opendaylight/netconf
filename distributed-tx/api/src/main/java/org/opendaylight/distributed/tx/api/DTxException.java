/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.distributed.tx.api;

import java.util.Collection;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Distributed transaction exceptions.
 */
public class DTxException extends RuntimeException {

    public DTxException(final String s, final Throwable e) {
        super(s, e);
    }

    public DTxException(final String s) {
        super(s);
    }

    /**
     * Distributed transaction initialization failure.
     */
    public static class DTxInitializationFailedException extends DTxException {
        public DTxInitializationFailedException(final String s) {
            super(s);
        }
    }

    /**
     * Edit operation failure for one or more devices.
     */
    public static class EditFailedException extends DTxException {
        public EditFailedException(final String s) {
            super(s);
        }
        public EditFailedException(final String s, final Throwable e){super(s, e);}
    }

    /**
     * Rollback failure for distributed transaction. This indicates unknown resulting state.
     */
    public static class RollbackFailedException extends DTxException {

        public RollbackFailedException(final String s) {
            super(s);
        }

        public RollbackFailedException(final Exception input) {
            super("Unable to perform rollback. Nodes are in unknown state", input);
        }

        public RollbackFailedException(final String format, final Exception e) {
            super(format, e);
        }
    }

    /**
     * Submit operation failure for one or more devices.
     */
    public static class SubmitFailedException extends DTxException {

        private final Collection<InstanceIdentifier<?>> failedSubmits;

        public SubmitFailedException(final Collection<InstanceIdentifier<?>> failedSubmits) {
            super("Failed to submit for nodes: " + failedSubmits);
            this.failedSubmits = failedSubmits;
        }

        public SubmitFailedException(final Collection<InstanceIdentifier<?>> failedSubmits, final Exception e) {
            super("Failed to submit for nodes: " + failedSubmits, e);
            this.failedSubmits = failedSubmits;
        }

        public Collection<InstanceIdentifier<?>> getFailedSubmits() {
            return failedSubmits;
        }
    }

    /**
     * Read operation failure for one or more devices.
     */
    public static class ReadFailedException extends  DTxException{
        public ReadFailedException(String s, Throwable e) {
            super(s, e);
        }
        public ReadFailedException(String s) {
            super(s);
        }
    }
}
