package com.fg.mail.smtp.stats

import com.fasterxml.jackson.annotation.{JsonProperty, JsonCreator}
import scala.annotation.meta.field
import java.lang.String
import java.text.SimpleDateFormat
import java.util.{Date, Locale}
import com.fg.mail.smtp.Client
import scala.collection.mutable
import com.fasterxml.jackson.databind.{SerializerProvider, JsonSerializer}
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.annotation.JsonSerialize

/**
 * Subject for json serialization to provide brief status via http
 *
 * @author Jakub Liška (liska@fg.cz), FG Forrest a.s. (c) 2013
 * @version $Id: 6/26/13 11:55 PM u_jli Exp $
 */
case class LastIndexingStatus(
                   @JsonSerialize(using = classOf[DateSerializer])
                   @(JsonProperty@field)("Agent restarted at")
                   restartedAt: Long = System.currentTimeMillis(),

                   @JsonSerialize(using = classOf[DateSerializer])
                   @(JsonProperty@field)("Agent started at")
                   agentStartTime: Long = LastIndexingStatus.agentStarted,

                   @JsonSerialize(using = classOf[DateSerializer])
                   @(JsonProperty@field)("Last line received at")
                   var lastLineReceivedAt: Long = 0,

                   @(JsonProperty@field)("Count of indexed client-id log lines from backup")
                   var clientIdMessagesIndexedFromBackup: Long = 0,

                   @(JsonProperty@field)("Count of indexed message-id log lines from backup")
                   var messageIdMessagesIndexedFromBackup: Long = 0,

                   @(JsonProperty@field)("Count of indexed log entries (delivery attempts regardless of the status) from backup")
                   var indexedLogEntriesFromBackup: Long = 0,

                   @(JsonProperty@field)("Client statistics")
                   clientStatistics: mutable.Map[String, Statistics] = mutable.Map[String, Statistics]()
) {


  def cidLineIndexed(count: Long) {
    clientIdMessagesIndexedFromBackup = count
    lastLineReceivedAt = System.currentTimeMillis()
  }

  def midLineIndexed(count: Long) {
    messageIdMessagesIndexedFromBackup = count
    lastLineReceivedAt = System.currentTimeMillis()
  }

  def logEntry(count: Long) {
    indexedLogEntriesFromBackup = count
    lastLineReceivedAt = System.currentTimeMillis()
  }

  def clientRequest(r: Client) {
    val clientVersion = r.ctx.underlying("client-version")
    val clientId = r.filter.clientId
    clientStatistics.get(clientId) match {
      case Some(stats) => clientStatistics(clientId) = stats.modify(clientVersion)
      case None => clientStatistics(clientId) = new Statistics(clientId, clientVersion, 1, System.currentTimeMillis())
    }
  }

}

object LastIndexingStatus {
  val agentStarted = System.currentTimeMillis()
}

case class Statistics @JsonCreator() (
                                  @(JsonProperty@field)("clientId") clientId: String,
                                  @(JsonProperty@field)("clientVersion") clientVersion: String,
                                  @(JsonProperty@field)("requestsCount") requestsCount: Int,
                                  @(JsonProperty@field)("lastRequestAt") @JsonSerialize(using = classOf[DateSerializer]) lastRequestAt: Long
                              ) {

  def modify(clientVersion: String): Statistics = {
    new Statistics(clientId, clientVersion, requestsCount + 1, System.currentTimeMillis())
  }

}

class DateSerializer extends JsonSerializer[Long] {

  private def format(l: Long): String = new SimpleDateFormat("yyyy MMM dd HH:mm:ss.SSS", Locale.US).format(new Date(l))

  def serialize(value: Long, jgen: JsonGenerator, provider: SerializerProvider) = jgen.writeString(if (value == 0) "not yet" else format(value))
}
