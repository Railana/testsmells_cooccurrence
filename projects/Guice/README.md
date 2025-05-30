Guice
====

-   **Latest release: [5.1.0](https://github.com/google/guice/wiki/Guice510) (supports `javax.{inject,servlet,persistence}`)**
-   **Latest pre-releases:**
    * **[6.0.0-rc2](https://github.com/google/guice/wiki/Guice600) (supports `javax.{inject,servlet,persistence}`, [mostly supports](https://github.com/google/guice/wiki/Guice600#jee-jakarta-transition) `jakarta.inject`)**
    * **[7.0.0-rc1](https://github.com/google/guice/wiki/Guice700) (supports `jakarta.{inject,servlet,persistence}`)**
-   **Documentation:**
    * [User Guide](https://github.com/google/guice/wiki/Motivation),
    * [5.1.0 javadocs](https://google.github.io/guice/api-docs/5.1.0/javadoc/index.html)
    * [6.0.0 javadocs](https://google.github.io/guice/api-docs/6.0.0-rc2/javadoc/index.html)
    * [7.0.0 javadocs](https://google.github.io/guice/api-docs/7.0.0-rc1/javadoc/index.html)
    * [Latest Snapshot javadocs](https://google.github.io/guice/api-docs/latest/javadoc/index.html)
-   **Continuous Integration:**
    [![Build Status](https://github.com/google/guice/workflows/continuous-integration/badge.svg)](https://github.com/google/guice/actions)
-   **Mailing Lists:** [User Mailing List](http://groups.google.com/group/google-guice) <br/>
-   **License:** [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0)

Overview
====

Put simply, Guice alleviates the need for factories and the use of new in your Java code. Think of Guice's @Inject as the new new. You will still need to write factories in some cases, but your code will not depend directly on them. Your code will be easier to change, unit test and reuse in other contexts.

Guice embraces Java's type safe nature. You might think of Guice as filling in missing features for core Java. Ideally, the language itself would provide most of the same features, but until such a language comes along, we have Guice.

Guice helps you design better APIs, and the Guice API itself sets a good example. Guice is not a kitchen sink. We justify each feature with at least three use cases. When in doubt, we leave it out. We build general functionality which enables you to extend Guice rather than adding every feature to the core framework.

Guice aims to make development and debugging easier and faster, not harder and slower. In that vein, Guice steers clear of surprises and magic. You should be able to understand code with or without tools, though tools can make things even easier. When errors do occur, Guice goes the extra mile to generate helpful messages.

For an introduction to Guice and a comparison to new and the factory pattern, see [Bob Lee's video presentation](https://www.youtube.com/watch?v=hBVJbzAagfs). After that, check out our [user's guide](https://github.com/google/guice/wiki/Motivation).

We've been running Guice in mission critical applications since 2006, and now you can, too. We hope you enjoy it as much as we do.


Installation Instructions
====
Guice Core (Maven)
```xml
<dependency>
  <groupId>com.google.inject</groupId>
  <artifactId>guice</artifactId>
  <!-- {version} can be 5.1.0, 6.0.0-rc2, 7.0.0-rc1, etc. -->
  <version>{version}</version>
</dependency>
```

Guice Extension (Maven)
```xml
<dependency>
  <groupId>com.google.inject.extensions</groupId>
  <!-- {extension-name} can be one of: assistedinject, dagger-adapter,
       grapher, jmx, jndi, persist, spring, testlib or throwingproviders -->
  <artifactId>guice-{extension-name}</artifactId>
  <!-- {version} must match the guice core version. -->
  <version>{version}</version>
</dependency>
```

See [Maven Central](https://central.sonatype.com/artifact/com.google.inject/guice/) for more details, including snippets for other build systems such as Gradle, Ivy, sbt, and more.

---
[![jolt award](https://user-images.githubusercontent.com/1885701/52603534-0d620380-2e1c-11e9-8cd5-95f0e141fcb0.png)](http://www.drdobbs.com/tools/winners-of-the-18th-jolt-product-excelle/207600666?pgno=6)
