/*******************************************************************************
 * Copyright (c) 2026 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
/*
 * The semantic versioning rules of the OSGi white paper (https://docs.osgi.org/whitepaper/semantic-versioning/).
 * A major change breaks compatibility, a minor change adds to the API, a micro change (and the qualifier) has no API meaning.
 * A consumer of an API accepts every later version of the same major (range [1.2,2)),
 * a provider of an API implementation accepts only the same major and minor (range [1.2,1.3)).
 */
module semantic_versions

open versions

// the range a consumer of the API at version v uses: [v, next major)
pred consumerRange[r : Range, v : Version] {
    r.floor = v and r in InclusiveFloor and r not in InclusiveCeiling
    some r.ceiling and r.ceiling.major = plus[v.major, 1] and r.ceiling.minor = 0 and r.ceiling.micro = 0
}

// the range a provider of the API at version v uses: [v, next minor)
pred providerRange[r : Range, v : Version] {
    r.floor = v and r in InclusiveFloor and r not in InclusiveCeiling
    some r.ceiling and r.ceiling.major = v.major and r.ceiling.minor = plus[v.minor, 1] and r.ceiling.micro = 0
}

// a version that a consumer of old can use: same major, not older
pred backwardCompatible[old, new : Version] {
    new.major = old.major and not older[new, old]
}

// a version that a provider of old can use: same major and minor, not older
pred providerCompatible[old, new : Version] {
    new.major = old.major and new.minor = old.minor and not older[new, old]
}
