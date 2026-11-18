/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
/**
 * The phases of a resolve in the order they run: {@code DiscoveryPhase} builds the graph from a resolve context,
 * {@code FragmentAttachmentPhase} handles fragments and hosted capabilities, {@code ClassSpacePhase} builds the indices
 * and rules, {@code ResolvabilityPhase} decides which resources can be resolved and what they select, {@code ConflictPhase}
 * lets a {@code UsesConflictStrategy} resolve the detected class space conflicts on a {@code ResolverUsesSession}, and
 * {@code FailurePhase} and {@code WiringPhase} produce the result.
 * A {@code PhasedResolver} calls them in that order, a subclass can replace any of them.
 */
package org.eclipse.osgi.technology.resolver.phases;
