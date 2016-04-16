package search.solr.client

import org.apache.solr.client.solrj.impl.CloudSolrClient
import search.solr.client.impl.SolJSolrCloudClient

import scala.reflect.ClassTag

/**
  * Created by soledede on 2015/11/16.
  */
private[search] trait SolrClient {
  def deleteByQuery(query: String, collection: String): Boolean = false

  def searchByQuery[T: ClassTag](query: T,collection:String = "searchcloud"): AnyRef = null

  def updateIndices[D: ClassTag](doc: D,collection:String = "searchcloud"): Unit = {}

  def addIndices[D: ClassTag](doc: D,collection:String = "searchcloud"): Unit = {}

  def delete(list: java.util.ArrayList[java.lang.String],collection: String = "searchcloud"): Boolean = false

  def close(): Unit = {}

  def closeKw(): Unit = {}

  def setSolrServer(server: CloudSolrClient):Unit = {}

}

private[search] object SolrClient {

  def apply(conf: SolrClientConf, cType: String = "solrJSolrCloud"):SolrClient = {
    cType match {
      case "solrJSolrCloud" => SolJSolrCloudClient(conf)
      case _ => null
    }
  }
}
