package search.solr.client.consume

import java.util

import search.solr.client.index.manager.IndexManager
import search.solr.client.product.Producter
import search.solr.client.queue.MessageQueue
import search.solr.client.queue.impl.KafkaMessageQueue
import search.solr.client.util.Logging
import scala.collection.JavaConversions._

/**
  * Created by soledede on 2016/2/14.
  */
object Consumer extends Logging {
  val indexer = IndexManager()

  def main(args: Array[String]) {
    MessageQueue().start() //start recieve message,default we use kafka
    receive
  }

  def receive(): Unit = {
    while (true) {
      try {
        val message = KafkaMessageQueue.kafkaBlockQueue.take()
        if (message != null && !message.trim.equalsIgnoreCase("") && message.length > 0) {
          val collection = message.split(Producter.separator)(0)
          val data = indexer.requestData(message)
          //generate xml for data
          val xmlBool = indexer.geneXml(data)
          if (xmlBool != null) {
            indexData(collection, xmlBool)
            /* if (xmlBool.isInstanceOf[java.util.ArrayList[java.lang.String]]) {
               deleteIndexData(collection, xmlBool)
             }
             else {
               indexData(collection, xmlBool)
             }*/
          }
        }
      } catch {
        case e: Exception => logError("manager index faield!", e)
      }
    }
  }

  /**
    * delete index data
    * @param collection
    * @param xmlBool
    */
  def deleteIndexData(collection: String, xmlBool: AnyRef): Unit = {
    val delData = xmlBool.asInstanceOf[util.ArrayList[String]]
    if (indexer.delete(delData, collection)) {
      logInfo("delete index success!")
    } else {
      logError("delete index faield!Ids:")
      delData.foreach(id => logInfo(s"delete faield id:\t${id}"))
    }
  }

  /**
    * index data
    * @param collection
    * @param xmlBool
    */
  def indexData(collection: String, xmlBool: AnyRef): Unit = {
    try {
      val indexData = xmlBool.asInstanceOf[util.ArrayList[util.Map[String, Object]]]
      if (indexer.indexData(indexData, collection)) logInfo(" index success!")
      else {
        logError("index faield!Ids:")
        indexData.foreach { doc =>
          logInfo(s"index faield id:\t${doc.get("id")}")
        }
      }
    } catch {
      case castEx: java.lang.ClassCastException => deleteIndexData(collection, xmlBool)
      case e: Exception => logError("index faield", e)
    }

  }
}
