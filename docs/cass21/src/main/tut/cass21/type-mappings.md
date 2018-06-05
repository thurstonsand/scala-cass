---
layout: docs
title: "Type Mappings"
section: "c21"
---

# Type Mappings

## Cassandra 2.1

| Cassandra Type |      Scala Type      |
|:--------------:|:--------------------:|
| varchar        | String               |
| uuid           | java.util.UUID       |
| inet           | java.net.InetAddress |
| int            | Int                  |
| bigint         | Long                 |
| boolean        | Boolean              |
| double         | Double               |
| varint         | BigInt               |
| decimal        | BigDecimal           |
| float          | Float                |
| blob           | Array[Byte]          |
| list           | List                 |
| map            | Map                  |
| set            | Set                  |
| tuple          | Tuple*               |
| **timestamp**  | **java.util.Date**   |

* There are overrides for the joda library and jdk8 time library that take advantage of Cassandra's new codecs. 
[See date codecs](/21/date-codecs.html) for more
