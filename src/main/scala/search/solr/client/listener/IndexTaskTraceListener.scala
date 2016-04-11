package search.solr.client.listener

import search.solr.client.SolrClientConf
import search.solr.client.redis.Redis
import search.solr.client.view.control.ControlWebPage

import scala.collection.mutable

/**
  * Created by soledede on 2016/4/8.
  */
class IndexTaskTraceListener() extends TraceListener {
  val redis = Redis()

  override def onAddIndex(content: AddIndex): Unit = {
    //add current indexing data to local queue
    ControlWebPage.currentIndexDatas.offer(content.content)
    redis.putToSet(IndexTaskTraceListener.SET_KEY, content.content)
  }

  override def onDelLastIndex(): Unit = {
    redis.delKey(Seq(IndexTaskTraceListener.SET_KEY))
  }
}

object IndexTaskTraceListener {

  val SET_KEY: String = "last_index"

  def main(args: Array[String]) {


  }

}
