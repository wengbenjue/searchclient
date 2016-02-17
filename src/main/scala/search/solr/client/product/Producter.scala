package search.solr.client.product

import search.solr.client.config.Configuration
import search.solr.client.queue.MessageQueue
import search.solr.client.util.Logging
import scala.collection.JavaConversions._

/**
  * Created by soledede on 2016/2/14.
  */
object Producter extends Logging with Configuration {

  val separator = "-"
  val DELETE = "delete"


  def send(startUpdateTime: Long, endUpdataTime: Long, totalNum: Int): Boolean = {
    send(collection, startUpdateTime, endUpdataTime, totalNum)
  }

  def send(minUpdateTime: Long, totalNum: Int): Boolean = {
    send(collection, minUpdateTime, totalNum)
  }


  def delete(id: String): Boolean = {
    delete(collection, id)
  }

  def delete(ids: java.util.List[String]): Boolean = {
    delete(collection, ids)
  }

  /**
    * @param collection
    * @param startUpdateTime
    * @param endUpdataTime
    * @param totalNum
    * eg: mergeCloud-2343433212-234343211-34
    * @return
    */
  def send(collection: String, startUpdateTime: Long, endUpdataTime: Long, totalNum: Int): Boolean = {
    logInfo(s"sendMessage-collection:$collection-startTime:$startUpdateTime-endTime:$endUpdataTime-totalNum:$totalNum")
    if (MessageQueue().sendMsg(collection + separator + startUpdateTime + separator + endUpdataTime + separator + totalNum)) true
    else false
  }

  /**
    * @param collection
    * @param minUpdateTime
    * @param totalNum
    * eg:mergeCloud-234343211-34
    * @return
    */
  def send(collection: String, minUpdateTime: Long, totalNum: Int) = {
    logInfo(s"sendMessage-collection:$collection-minUpdateTime:$minUpdateTime-totalNum:$totalNum")
    if (MessageQueue().sendMsg(collection + separator + minUpdateTime + separator + totalNum)) true
    else false
  }


  /**
    * @param collection
    * @param id
    * delete single id
    * eg: mergeCloud-delete-124343455
    */
  def delete(collection: String, id: String) = {
    logInfo(s"deleteMessage-collection:$collection-id:$id")
    if (MessageQueue().sendMsg(collection + separator + DELETE + separator + id)) true
    else false
  }

  /**
    * @param collection
    * @param ids
    * delete multiple ids
    * eg:mergeCloud-delete-132423-3465453-235345
    */
  def delete(collection: String, ids: java.util.List[String]) = {
    val idMsg = new StringBuilder()
    if (ids != null && ids.size() > 0) {
      ids.foreach(idMsg.append(_).append(separator))
      idMsg.deleteCharAt(idMsg.length - 1)
      logInfo(s"deleteMessage--collection:$collection-id:${idMsg.toString()}")
      if (MessageQueue().sendMsg(collection + separator + DELETE + separator + idMsg.toString())) true
      else false
    } else false
  }


  /**
    *
    * this is generate  inteface for product message,whenever you want to expand your function
    * @param msg
    * @return
    * eg: test-234-3423-445
    */
  def sendMsg(msg: String): Boolean = {
    logInfo(s"customSendMessage-message:$msg")
    if (MessageQueue().sendMsg(msg)) true
    else false
  }
}
