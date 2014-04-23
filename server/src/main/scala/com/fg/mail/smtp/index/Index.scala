package com.fg.mail.smtp.index

import scala.collection.IterableView
import java.util
import org.mapdb._
import java.util.{Comparator, NavigableSet}
import java.io.{DataInput, DataOutput}
import scala.collection.JavaConverters._

/**
 * Index is a set of [clientId, date, indexRecord]
 * Client id log entry is preceding following log entries with the same queue id
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/24/13 7:18 PM u_jli Exp $
 */
class Index(val records: NavigableSet[Fun.Tuple3[String, Long, IndexRecord]]) {

  def isEmpty = records.isEmpty

/*
  def sizeFor(clientId: String): Int =
    profile(500, "Counting db records for clientId", "clientId") {
      records.subSet(Fun.t3(clientId, 0L, null), Fun.t3(clientId, Fun.HI[Long], Fun.HI[IndexRecord])).size()
    }
*/

  def addRecord(cir: ClientIndexRecord) = {
    val clientId = cir.clientId
    val r = cir.ir
    records.add(Fun.t3(clientId, r.date, r))
  }

  /**
   * @return lazy view of records by clientId
   * @note that you can iterate Map values only once because iteration over millions takes more than a few seconds on a single-core machine with NFS
   */
  def getAsMapWithDisposableValues(interval: Interval): Map[String, IterableView[IndexRecord, Iterable[IndexRecord]]] = {
    new Iterable[(String, IterableView[IndexRecord, Iterable[IndexRecord]])] {
      def iterator: Iterator[(String, IterableView[IndexRecord, Iterable[IndexRecord]])] =
        new Iterator[(String, IterableView[IndexRecord, Iterable[IndexRecord]])] {
          val clientIt = getClientIds.iterator
          def hasNext: Boolean = clientIt.hasNext
          def next(): (String, IterableView[IndexRecord, Iterable[IndexRecord]]) = {
            val next = clientIt.next()
            val subIter = records.subSet(
                                    Fun.t3(next, interval.from.getOrElse(0L), null),
                                    Fun.t3(next, if (interval.to.isEmpty) Fun.HI[Long] else interval.to.get, Fun.HI[IndexRecord])
                                  ).iterator()
            (
              next,
              new Iterable[IndexRecord] {
                def iterator: Iterator[IndexRecord] = {
                  new Iterator[IndexRecord] {
                    def hasNext: Boolean = subIter.hasNext
                    def next(): IndexRecord = subIter.next().c
                  }
                }
              }.view
            )
          }
        }
    }.toMap
  }

  /**
   * @return lazy view of all tuple[clientId, record] - in case it would be really necessary to iterate through possibly tens of millions of records, it better be lazy (such an iteration takes more than a few seconds on a single-core machine with NFS)
   * @note that you can iterate it only once because iteration over millions takes more than a few seconds on a single-core machine with NFS
   */
  def getClientIdRecordTuples: IterableView[(String, IndexRecord), Iterable[(String, IndexRecord)]] = {
    new Iterable[(String, IndexRecord)] {
      def iterator: Iterator[(String, IndexRecord)] = {
        new Iterator[(String, IndexRecord)] {
          val iter = records.iterator
          def hasNext: Boolean = iter.hasNext
          def next(): (String, IndexRecord) = {
            val next = iter.next()
            (next.a, next.c)
          }
        }
      }
    }.view
  }

  /**
   * @return lazy view of records - in case it would be really necessary to iterate through possibly tens of millions of records, it better be lazy (such an iteration takes more than a few seconds on a single-core machine with NFS)
   * @note that you can iterate it only once because iteration over millions takes more than a few seconds on a single-core machine with NFS
   */
  def getRecords: IterableView[IndexRecord, Iterable[IndexRecord]] = {
    new Iterable[IndexRecord] {
      def iterator: Iterator[IndexRecord] = {
        new Iterator[IndexRecord] {
          val iter = records.iterator
          def hasNext: Boolean = iter.hasNext
          def next(): IndexRecord = iter.next().c
        }
      }
    }.view
  }

  /**
   * @param interval time constraint - if it contains None values, it means the interval is open
   * @param reverse true means descending order, false ascending
   * @return lazy view of all records for particular clientId constrained by specified interval
   * @note that you can iterate it only once because iteration over millions takes more than a few seconds on a single-core machine with NFS
   */
  def getRecordsFor(clientId: String, interval: Interval, reverse: Boolean = false): IterableView[IndexRecord, Iterable[IndexRecord]] = {
    new Iterable[IndexRecord] {
      def iterator: Iterator[IndexRecord] = {
        new Iterator[IndexRecord] {
          val iter = records.subSet(
                              Fun.t3(clientId, interval.from.getOrElse(0L), null),
                              Fun.t3(clientId, if (interval.to.isEmpty) Fun.HI[Long] else interval.to.get, Fun.HI[IndexRecord])
                          ) match {
                              case ss: util.NavigableSet[Fun.Tuple3[String, Long, IndexRecord]] if reverse => ss.descendingIterator()
                              case ss: util.NavigableSet[Fun.Tuple3[String, Long, IndexRecord]] if !reverse => ss.iterator()
                              case _ => throw new IllegalStateException("BTreeMap KeySet's subset ain't NavigableSet")
                          }
          def hasNext: Boolean = iter.hasNext
          def next(): IndexRecord = iter.next.c
        }
      }
    }.view
  }

  /** Resolving client ids from MapDb would have O(n) complexity which is deadly for millions of records (such an iteration takes more than a few seconds on a single-core machine with NFS) */
  def getClientIds: Iterable[String] = {
    val s = new java.util.TreeSet[Fun.Tuple3[String, Long, IndexRecord]](
                new Comparator[Fun.Tuple3[String, Long, IndexRecord]] {
                      def compare(o1: Fun.Tuple3[String, Long, IndexRecord], o2: Fun.Tuple3[String, Long, IndexRecord]): Int = o1.a.compare(o2.a)
                }
            )
    s.addAll(records)
    s.asScala.map(_.a)
  }
}


/**
 * It tells MapDb how to serialize IndexRecord for it to perform better
 */
class IndexRecordSerializer extends Serializer[IndexRecord] with Serializable {

  def serialize(out: DataOutput, r: IndexRecord) {
    out.writeLong(r.date)
    out.writeUTF(r.queueId)
    out.writeUTF(r.msgId)
    out.writeUTF(r.rcptEmail)
    out.writeUTF(r.senderEmail)
    out.writeUTF(r.status)
    out.writeUTF(r.info)
    out.writeInt(r.state)
    out.writeUTF(r.stateInfo)
  }

  def deserialize(in: DataInput, available: Int): IndexRecord = {
    IndexRecord(in.readLong(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readUTF(), in.readInt(), in.readUTF())
  }

  def fixedSize(): Int = -1
}

/** Time constraint for getting records that occurred at a period of time 'from - to'. None means the interval is open. */
case class Interval(from: Option[Long], to: Option[Long])

object Index {

  val serializer = new BTreeKeySerializer.Tuple3KeySerializer[String, java.lang.Long, IndexRecord](null, null, Serializer.STRING, Serializer.LONG, new IndexRecordSerializer)

  def apply(db: DB, name: String) = {
    /** Node size 6 proved to be the most optimal value for IndexRecord persistence */
    new Index(db.createTreeSet(name).counterEnable().nodeSize(6).serializer(serializer).makeOrGet())
  }

}