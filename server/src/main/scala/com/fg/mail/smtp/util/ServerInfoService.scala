package com.fg.mail.smtp.util

import scala.util.control.Exception._
import scala.collection.immutable.{StringOps, ListMap}
import java.text.{NumberFormat, SimpleDateFormat}
import java.util.{Locale, Date}
import java.lang.management.{GarbageCollectorMXBean, ManagementFactory}
import java.lang.System.getProperty
import java.nio.charset.Charset
import scala.collection.JavaConverters._
import scala.Predef._
import com.fg.mail.smtp.util.MemoryFootprintAnalyzer._
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.annotation.JsonGetter
import scala.annotation.meta.field
import com.fasterxml.jackson.databind.{SerializerProvider, JsonSerializer}
import com.fasterxml.jackson.core.JsonGenerator
import com.fg.mail.smtp.index.{DbManager, Index}
import com.fg.mail.smtp.Options

/**
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 10/21/13 4:29 PM u_jli Exp $
 */
class ServerInfoService(val o: Options) extends Profilable {

  def getEnvironment: Environment = {
    profile(1000, "Getting environment") {
      new Environment(
        getGeneralInfo ++
        getMemoryUsage.memoryPool.asStringMap() ++
        ListMap[String, String]("---" -> "") ++
        getGarbageCollectorUsage.asStringMap() ++
        ListMap[String, String]("----" -> "") ++
        getThreadsInfo.asStringMap()
    )
    }
  }

  def getGeneralInfo = {
    val runtime = Runtime.getRuntime
    val max = runtime.maxMemory
    val total = runtime.totalMemory
    val free = runtime.freeMemory
    val used = total - free
    ListMap[String, String](
      "wdVersion" -> "custom",
      "wdStatus" -> "OK",
      "Date" -> new SimpleDateFormat("EEE MMM dd HH:mm:ss z yyyy").format(new Date),
      "hostname" -> catching(classOf[Exception]).either(Option(java.net.InetAddress.getLocalHost.getHostName)).fold(ex => s"- exception: ${ex.getMessage} -", v => v.getOrElse("- null -")),
      "serverInfo" -> Option(getClass.getPackage.getImplementationVersion).getOrElse("- null -"),
      "java.version" -> getProperty("java.version", "- not defined -"),
      "java.runtime.version" -> getProperty("java.runtime.version", "- not defined -"),
      "java.home" -> getProperty("java.home", "- not defined -"),
      "java.library.path" -> getProperty("java.library.path", "- not defined -"),
      "jvm.uptime" -> ManagementFactory.getRuntimeMXBean.getUptime.toString,
      "catalina.home" -> "- not defined -",
      "catalina.base" -> "- not defined -",
      "node.path" -> getProperty("node.path", "- not defined -"),
      "maxMemory" -> max.toString,
      "totalMemory" -> total.toString,
      "freeMemory" -> free.toString,
      "-" -> "",
      "env.locale" -> Locale.getDefault.toString,
      "env.locale.language" -> Locale.getDefault.getLanguage,
      "env.locale.country" -> Locale.getDefault.getCountry,
      "env.locale.variant" -> Locale.getDefault.getVariant,
      "env.charset" -> Charset.defaultCharset.toString,
      "--" -> "",
      "Heap" -> s"$used / $total / $max / ${(used.toDouble / max.toDouble * 100).toLong}%"
    )
  }

  /**
   *
    <h2>Young generation collectors</h2>
      <dl>
        <dt><strong>Copy (enabled with -XX:+UseSerialGC)</strong> -</dt><dd>the serial copy collector, uses one thread to copy surviving objects from Eden to Survivor spaces and between Survivor spaces until it decides they've been there long enough, at which point it copies them into the old generation.</dd>
        <dt><strong>PS Scavenge (enabled with -XX:+UseParallelGC)</strong> -</dt><dd>the parallel scavenge collector, like the <strong>Copy</strong> collector, but uses multiple threads in parallel and has some knowledge of how the old generation is collected (essentially written to work with the serial and PS old gen collectors).</dd>
        <dt><strong>ParNew (enabled with -XX:+UseParNewGC)</strong> -</dt><dd>the parallel copy collector, like the <strong>Copy</strong> collector, but uses multiple threads in parallel and has an internal 'callback' that allows an old generation collector to operate on the objects it collects (really written to work with the concurrent collector).</dd>
        <dt><strong>G1 Young Generation (enabled with -XX:+UseG1GC)</strong> -</dt><dd>the garbage first collector, uses the 'Garbage First' algorithm which splits up the heap into lots of smaller spaces, but these are still separated into Eden and Survivor spaces in the young generation for G1.</dd>
      </dl>

    <h2>Old generation collectors</h2>
      <dl>
        <dt><strong>MarkSweepCompact (enabled with -XX:+UseSerialGC)</strong> -</dt><dd>the serial mark-sweep collector, the daddy of them all, uses a serial (one thread) full mark-sweep garbage collection algorithm, with optional compaction.</dd>
        <dt><strong>PS MarkSweep (enabled with -XX:+UseParallelOldGC)</strong> -</dt><dd>the parallel scavenge mark-sweep collector, parallelised version (i.e. uses multiple threads) of the <strong>MarkSweepCompact</strong>.</dd>
        <dt><strong>ConcurrentMarkSweep (enabled with -XX:+UseConcMarkSweepGC)</strong> -</dt><dd>the concurrent collector, a garbage collection algorithm that attempts to do most of the garbage collection work in the background without stopping application threads while it works (there are still phases where it has to stop application threads, but these phases are attempted to be kept to a minimum). Note if the concurrent collector fails to keep up with the garbage, it fails over to the serial <strong>MarkSweepCompact</strong> collector for (just) the next GC.</dd>
        <dt><strong>G1 Mixed Generation (enabled with -XX:+UseG1GC)</strong> -</dt><dd>the garbage first collector, uses the 'Garbage First' algorithm which splits up the heap into lots of smaller spaces.</dd>
      </dl>

    <table border="1">
      <tbody>
        <tr><th>Command Options*</th><th>Resulting Collector Combination</th></tr>
        <tr><td><strong>-XX:+UseSerialGC</strong></td><td>young <strong>Copy</strong> and old <strong>MarkSweepCompact</strong></td></tr>
        <tr><td><strong>-XX:+UseG1GC</strong></td><td>young <strong>G1 Young</strong> and old <strong>G1 Mixed</strong></td></tr>
        <tr><td><strong>-XX:+UseParallelGC -XX:+UseParallelOldGC -XX:+UseAdaptiveSizePolicy</strong></td><td>young <strong>PS Scavenge</strong> old <strong>PS MarkSweep</strong> with adaptive sizing</td></tr>
        <tr><td><strong>-XX:+UseParallelGC -XX:+UseParallelOldGC -XX:-UseAdaptiveSizePolicy</strong></td><td>young <strong>PS Scavenge</strong> old <strong>PS MarkSweep</strong>, no adaptive sizing</td></tr>
        <tr><td><strong>-XX:+UseParNewGC</strong></td><td>young <strong>ParNew</strong> old <strong>MarkSweepCompact</strong></td></tr>
        <tr><td><strong>-XX:+UseConcMarkSweepGC -XX:+UseParNewGC</strong></td><td>young <strong>ParNew</strong> old <strong>ConcurrentMarkSweep</strong>**</td></tr>
        <tr><td><strong>-XX:+UseConcMarkSweepGC -XX:-UseParNewGC</strong></td><td>young <strong>Copy</strong> old <strong>ConcurrentMarkSweep</strong>**</td></tr>
        <tr><td colspan="2">*All the combinations listed here will fail to let the JVM start if you add another GC algorithm not listed, with the exception of -XX:+UseParNewGC which is only combinable with -XX:+UseConcMarkSweepGC</td></tr>
        <tr><td colspan="2">**there are many many options for use with -XX:+UseConcMarkSweepGC which change the algorithm, e.g.
        <ul>
          <li>-XX:+/-CMSIncrementalMode - uses or disables an incremental concurrent GC algorithm</li>
          <li>-XX:+/-CMSConcurrentMTEnabled - uses or disables parallel (multiple threads) concurrent GC algorithm</li>
          <li>-XX:+/-UseCMSCompactAtFullCollection - uses or disables a compaction when a full GC occurs</li>
        </ul>
        </td></tr>
      </tbody>
    </table>
   */
  def getGarbageCollectorUsage: GCCombination = {
    def build(young: GarbageCollectorMXBean, old: GarbageCollectorMXBean) =
      new GCCombination(
        new Young(young.getName, young.getCollectionCount, young.getCollectionTime),
        new Old(old.getName, old.getCollectionCount, old.getCollectionTime),
        new Total(young.getCollectionCount + old.getCollectionCount, young.getCollectionTime + old.getCollectionTime)
      )
    val youngAndOld = ManagementFactory.getGarbageCollectorMXBeans.iterator()
    build(youngAndOld.next(), youngAndOld.next())
  }

  def getMemoryUsage: OverallMemory = profile(500, "Measuring MemoryUsage") {
    val memMXBean = ManagementFactory.getMemoryMXBean
    val hu = memMXBean.getHeapMemoryUsage
    val nhu = memMXBean.getNonHeapMemoryUsage

    val pool = (ManagementFactory.getMemoryPoolMXBeans.asScala map {
      x => {
        val usage = x.getUsage
        (x.getName.replace(" ", ""), new Usage(usage.getInit, usage.getUsed, usage.getCommitted, usage.getMax))
      }
    }).toMap

    new OverallMemory(
      new Usage(hu.getInit, hu.getUsed, hu.getCommitted, hu.getMax),
      new Usage(nhu.getInit, nhu.getUsed, nhu.getCommitted, nhu.getMax),
      new MemoryPool(pool)
    )
  }

  def getIndexFootprint(dbManager: DbManager, index: Index): IndexFootprint =
    profile(1000, "Measuring index footprint") {
      catching(classOf[Throwable])
        .either(new IndexFootprint(0, 0, 0, 0)) match { //TODO
          case Left(ex) =>
            log.warn("Unable to measure index size", ex)
            new IndexFootprint(0, 0, 0, 0)
          case Right(v) => v
        }
    }

  def getThreadsInfo: ThreadsInfo = {
    val mbean = ManagementFactory.getThreadMXBean
    val infos = mbean.getAllThreadIds.toList.map(mbean.getThreadInfo)
    val countsByState = ListMap[String, Int](Thread.State.values.toList.map(s => "cnt.thread.state." + s.toString.toLowerCase -> 0): _*) ++
                        infos.map("cnt.thread.state." + _.getThreadState.toString.toLowerCase).groupBy(identity).mapValues(_.length)
    val blocked = infos.filter(_.getThreadState.toString.toLowerCase == "blocked").map(i => i.getThreadState.toString.toLowerCase -> i.toString.replaceAll("[\r\n]+", " ")).toMap
    val total = countsByState.values.reduce(_ + _)
    ThreadsInfo(countsByState, total, blocked)
  }

}

case class ThreadsInfo(counts: ListMap[String, Int], total: Int, blocked: Map[String, String]) {
  def asStringMap() = ListMap[String, String]( "cnt.thread.total" -> total.toString ) ++ counts.mapValues(_.toString) ++ blocked
}

case class OverallMemory(heapMemory: Usage, nonHeapMemory: Usage, memoryPool: MemoryPool)

class MemoryPool(val memoryPool: Map[String, Usage]) {
  def asStringMap() = memoryPool.mapValues(v => s"${v.used} / ${v.committed} / ${v.max} / ${(v.used.toDouble / v.max.toDouble * 100).toLong}%")
}

case class Usage(
                  @JsonSerialize(using = classOf[NumericSerializer]) init: Long,
                  @JsonSerialize(using = classOf[NumericSerializer]) used: Long,
                  @JsonSerialize(using = classOf[NumericSerializer]) committed: Long,
                  @JsonSerialize(using = classOf[NumericSerializer]) max: Long) {

  @(JsonGetter@field)("free")
  @JsonSerialize(using = classOf[NumericSerializer])
  private def getFree = committed - used
}

class NumericSerializer extends JsonSerializer[Long] {
  val format = NumberFormat.getInstance()

  def serialize(value: Long, jgen: JsonGenerator, provider: SerializerProvider) {
    jgen.writeString(format.format(value / 1024) + " KB")
  }
}

case class GCCombination(young: Young, old: Old, total: Total) {
  def asStringMap() =
    ListMap[String, String](
      "gc.young.name" -> young.name,
      "gc.old.name" -> old.name,
      "gc.total.count" -> total.count.toString,
      "gc.total.time" -> total.time.toString,
      "gc.young.count" -> young.count.toString,
      "gc.young.time" -> young.time.toString,
      "gc.old.count" -> old.count.toString,
      "gc.old.time" -> old.time.toString
    )
}
case class Young(name: String, count: Long, time: Long)
case class Old(name: String, count: Long, time: Long)
case class Total(count: Long, time: Long)

class Environment(val underlying: ListMap[String, String]) {
  require(underlying != null)
  assume(!underlying.isEmpty)

  override def toString = {
    val emptyLineRegex = """-{1,6}""".r
    val maxKeyLength = underlying.keys.foldLeft(0)( (acc, key) => if (key.size > acc) key.size else acc )
    val result = underlying.map {
          case (key, value) =>
            key match {
              case emptyLineRegex() => ""
              case _ => key + ": " + new StringOps(" ").*(maxKeyLength - key.size) + value
            }
        } mkString "\n"
    "\nOK - alive\n\n" + result
  }

}

case class IndexFootprint(
                           @JsonSerialize(using = classOf[NumericSerializer]) index: Long,
                           indexSize: Long,
                           @JsonSerialize(using = classOf[NumericSerializer]) queue: Long,
                           queueSize: Long)
