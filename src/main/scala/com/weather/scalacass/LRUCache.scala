package com.weather.scalacass

class LRUCache[K, V](cacheSize: Int) extends java.util.LinkedHashMap[K, V](cacheSize, 0.75f, true) {
  override protected def removeEldestEntry(eldest: java.util.Map.Entry[K, V]): Boolean = size >= cacheSize

  def get(key: K, fn: => V): V =
    if(containsKey(key)) get(key)
    else {
      val res = fn
      put(key, res)
      res
    }
}
