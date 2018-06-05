---
layout: home
title:  "Home"
section: "home"
position: 1
---
Scala-Cass is a library that makes working with the [Cassandra Java driver](https://github.com/datastax/java-driver) in 
Scala better. It provides type class instances for all of Cassandra's types to Scala types so that you can get a 
retrieve data from the db easier. It also uses the Shapeless library to provide type class instances for case classes so 
you can read and write data more conveniently.

# Getting Scala-Cass

[you can find it on bintray](https://bintray.com/thurstonsand/maven/scala-cass).

Supports **Scala 2.10**, **Scala 2.11**, and **Scala 2.12** and

* Cassandra 2.1 on Java 8
* Cassandra 3.0+ on Java 8

#### SBT

Add the jcenter resolver

```scala
resolvers += Resolver.jcenterRepo
```

Add the appropriate version of the library

##### Cassandra 3.0+

```scala
libraryDependencies += "com.github.thurstonsand" %% "scala-cass" % "3.0.0-3.5.0"
```

##### Cassandra 2.1

```scala
libraryDependencies += "com.github.thurstonsand" %% "scala-cass" % "3.0.0-2.1.10.3"
```

#### Maven

Add the jcenter resolver

```xml
<repositories>
    <repository>
        <id>central</id>
        <name>bintray</name>
        <url>http://jcenter.bintray.com</url>
    </repository>
</repositories>
```

Pick a version

##### Cassandra 3.0+

```xml
<properties>
    <scalaCass.version>3.0.0-3.5.0</scalaCassVersion>
</properties>
```

##### Cassandra 2.1

```xml
<properties>
    <scalaCass.version>3.0.0-2.1.10.3</scalaCassVersion>
</properties>
```

Include the repo

```xml
<dependency>
  <groupId>com.github.thurstonsand</groupId>
  <artifactId>scala-cass_${scala.version}</artifactId>
  <version>${scalaCass.version}</version>
  <type>pom</type>
</dependency>
```
