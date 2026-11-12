# Felix Resolver Harness

Runs the [test suite](../test-suite) against the [Apache Felix resolver](https://github.com/apache/felix-dev/tree/master/resolver).

## Purpose

The Felix resolver is the resolver of many OSGi frameworks and tools.
This module checks it against the test suite and serves as a baseline for other implementations.

## Architecture

The module contains only test code and uses the released `org.apache.felix.resolver` artifact.

- `FelixEngine` - the `ResolverEngine` that resolves with `org.apache.felix.resolver.ResolverImpl`.
- `RunCucumberTest` - the JUnit platform suite that runs the features of the test suite.
- `META-INF/services/org.eclipse.osgi.technology.resolver.testsuite.ResolverEngine` - registers the engine.

## Coverage

The module has no classes of its own.
To make the coverage report show which parts of the Felix resolver the scenarios exercise, the build unpacks the released classes and the matching sources into the module.
The numbers of this module are therefore the coverage of Felix by the test suite.

## How to extend

Scenarios are added to the [test suite](../test-suite), this module only needs changes if the way Felix is invoked or how it reports failures changes.

## References

- [Apache Felix](https://felix.apache.org/)
- [Felix resolver source](https://github.com/apache/felix-dev/tree/master/resolver)
- [OSGi Resolver Service Specification](https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html)
