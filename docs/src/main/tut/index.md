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

[you can find it on bintray](https://bintray.com/thurstonsand/maven/scalacass).

Supports **scala 2.10** and **scala 2.11** and

* Cassandra 2.1 on Java 7
* Cassandra 3.0+ on Java 8

#### SBT

Add the jcenter resolver

```scala
resolvers += Resolver.jcenterRepo
```

Add the appropriate version of the library

##### Cassandra 3.0+ with Java 8

```scala
libraryDependencies += "com.github.thurstonsand" %% "scalacass" % "0.6.13"
```

##### Cassandra 2.1 with Java 7

```scala
libraryDependencies += "com.github.thurstonsand" %% "scalacass" % "0.5.13"
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

##### Cassandra 2.1 with Java 7

```xml
<properties>
    <scalaCass.version>0.5.13</scalaCassVersion>
</properties>
```

##### Cassandra 3.0+ with Java 8

```xml
<properties>
    <scalaCass.version>0.6.13</scalaCassVersion>
</properties>
```

Include the repo

```xml
<dependency>
  <groupId>com.github.thurstonsand</groupId>
  <artifactId>scalacass_${scala.version}</artifactId>
  <version>${scalaCass.version}</version>
  <type>pom</type>
</dependency>
```
