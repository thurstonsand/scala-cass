---
layout: docs
title: "Type Mappings"
section: "c3"
---

# Type Mappings

## Cassandra 3.0+ on Java 8

| Cassandra Type |             Scala Type                 |
|:--------------:|:--------------------------------------:|
| varchar        | String                                 |
| uuid           | java.util.UUID                         |
| inet           | java.net.InetAddress                   |
| int            | Int                                    |
| bigint         | Long                                   |
| boolean        | Boolean                                |
| double         | Double                                 |
| varint         | BigInt                                 |
| decimal        | BigDecimal                             |
| float          | Float                                  |
| blob           | Array[Byte]                            |
| list           | List                                   |
| map            | Map                                    |
| set            | Set                                    |
| tuple          | Tuple*
| **timestamp**  | **java.util.Date**                     |
| **date**       | **com.datastax.driver.core.LocalDate** |
| **time**       | **Time**                               |

* Time is a type specific to this library so as not to conflict with `bigint` and `Long`. it is defined as
  
```scala
final case class Time(millis: Long)
```

* There are overrides for both the Joda library and Jdk8 Time library that take advantage of Cassandra's new codecs. 
These codecs have to be registered with your `Cluster` instance; [See date codecs](/30/date-codecs.html) for more
