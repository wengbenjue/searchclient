package search.solr.client.searchInterface

import java.util

import org.apache.solr.client.solrj.SolrQuery
import org.apache.solr.client.solrj.SolrQuery.ORDER
import org.apache.solr.client.solrj.response.QueryResponse
import search.solr.client.entity.searchinterface._
import search.solr.client.util.{Util, Logging}
import search.solr.client.{SolrClientConf, SolrClient}
import scala.StringBuilder
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer


/**
  * Created by soledede on 2016/2/20.
  */
object SearchInterface extends Logging {

  val spellcheckSeparator = "_____"


  val solrClient = SolrClient(new SolrClientConf())


  /**
    *
    * search by keywords,must record searchlog for log analysis
    * @param keyWords eg:螺丝钉
    * @param cityId eg:111
    * @param sorts  eg:Map(price->desc,sales->desc,score->desc)
    * @param start eg:0
    * @param rows eg:10
    * @return SearchResult
    */
  def searchByKeywords(keyWords: java.lang.String, cityId: java.lang.Integer, sorts: java.util.Map[java.lang.String, java.lang.String], start: java.lang.Integer, rows: java.lang.Integer): SearchResult = {
    val msg = new Msg()
    val searchResult = new SearchResult()
    if (keyWords == null && cityId == null) {
      msg.setMsg("keyWords and cityId not null")
      searchResult.setMsg(msg)
      return searchResult
    }
    else if (keyWords == null) {
      msg.setMsg("keyWords not null")
      searchResult.setMsg(msg)
      return searchResult
    }
    else if (cityId == null) {
      msg.setMsg("cityId not null")
      searchResult.setMsg(msg)
      return searchResult
    }

    //page
    var sStart: Int = 0
    var sRows: Int = 10

    if (start != null && start > 0) sStart = start
    if (rows != null && rows > 0) sRows = rows


    val keyWord = keyWords.trim.toLowerCase
    val keyWordsModel = s"(original:$keyWord^50) OR (sku:$keyWord^50) OR (brandZh_ps$keyWord^30) OR (brandEn_ps:$keyWord^30) OR (sku:*$keyWord*^11) OR (original:*$keyWord*^10) OR (text:$keyWord^2) OR (pinyin:$keyWord^0.002)"

    val fq = s"deliveryTime:0 OR cityId:$cityId"


    val query: SolrQuery = new SolrQuery
    query.set("qt", "/select")
    query.setQuery(keyWordsModel)
    query.setFilterQueries(fq)

    //sort
    if (sorts != null && sorts.size() > 0) {
      // eg:  query.addSort("price", SolrQuery.ORDER.desc)
      sorts.foreach { sortOrder =>
        val field = sortOrder._1
        val orderString = sortOrder._2.trim
        var order: ORDER = null
        orderString match {
          case "desc" => order = SolrQuery.ORDER.desc
          case "asc" => order = SolrQuery.ORDER.asc
          case _ => SolrQuery.ORDER.desc
        }
        query.addSort(field, order)
      }
    }

    //page
    query.setStart(sStart)
    query.setRows(sRows)

    val r = solrClient.searchByQuery(query, "mergescloud")
    var result: QueryResponse = null
    if (r != null) result = r.asInstanceOf[QueryResponse]

    getSearchResultByResponse(msg, searchResult, result)
    searchResult
  }

  /**
    *
    * get result spellcheck highlightini once
    * @param msg
    * @param searchResult
    * @param result
    */
  private def getSearchResultByResponse(msg: Msg, searchResult: SearchResult, result: QueryResponse): Unit = {
    val resultSearch = getSearchResult(result) //get response result
    if (resultSearch != null && resultSearch.size > 0) searchResult.setResult(resultSearch) //set response resut

    //  highlighting
    val highlighting = getHighlightingList(result)
    if (highlighting != null && highlighting.size() > 0) {
      val filterHighlightins = highlighting.filter(!_._2.isEmpty)
      if (filterHighlightins != null && !filterHighlightins.isEmpty && filterHighlightins.size > 0)
        searchResult.setHighlighting(filterHighlightins)
    }

    //spellcheck
    val spellchecks = getSpellCheckList(result)

    if (spellchecks != null && spellchecks.size() > 0) searchResult.setSpellChecks(spellchecks)

    msg.setMsg("success!")
    msg.setCode(0)
    searchResult.setMsg(msg)
  }

  /**
    *
    * get spellcheck list
    * @param result
    * @return
    */
  private def getSpellCheckList(result: QueryResponse): java.util.HashMap[java.lang.String, java.util.List[java.lang.String]] = {
    if (result != null) {
      val spellChecks = new java.util.HashMap[java.lang.String, java.util.List[java.lang.String]]()
      val spellcheckResponse = result.getSpellCheckResponse
      if (spellcheckResponse != null) {
        val collateResults = spellcheckResponse.getCollatedResults
        val spellcheckCorrectionsSet = new util.HashSet[java.lang.String]()
        if (collateResults != null && collateResults.size() > 0) {
          collateResults.foreach { collation =>
            val missspellingCorrection = collation.getMisspellingsAndCorrections
            if (missspellingCorrection != null && missspellingCorrection.size() > 0) {
              missspellingCorrection.foreach { correction =>
                val originalWord = correction.getOriginal
                val correctionWord = correction.getCorrection
                spellcheckCorrectionsSet.add(originalWord + spellcheckSeparator + correctionWord)
              }
            }
          }
        }
        if (spellcheckCorrectionsSet.size() > 0) {
          spellcheckCorrectionsSet.foreach { spellcheck =>
            val spellcheckArray = spellcheck.split(spellcheckSeparator)
            val original = spellcheckArray(0)
            val correct = spellcheckArray(1)
            if (spellChecks.contains(original)) {
              val correctList = spellChecks.get(original)
              correctList.add(correct)
              spellChecks.put(original, correctList)
            } else {
              val initialCorrectList = new java.util.ArrayList[java.lang.String]()
              initialCorrectList.add(correct)
              spellChecks.put(original, initialCorrectList)
            }
          }
        }

      }
      spellChecks
    } else null
  }

  /**
    *
    * get highlighting list
    * @param result
    * @return
    */
  private def getHighlightingList(result: QueryResponse): java.util.Map[java.lang.String, java.util.Map[java.lang.String, java.util.List[java.lang.String]]] = {
    if (result != null) {
      return result.getHighlighting
    } else null
  }

  /**
    *
    * get response Result
    * @param result
    * @return
    */
  private def getSearchResult(result: QueryResponse): java.util.List[util.Map[java.lang.String, Object]] = {
    val resultList: java.util.List[util.Map[java.lang.String, Object]] = new java.util.ArrayList[util.Map[java.lang.String, Object]]() //search result
    //get Result
    if (result != null) {
      val response = result.getResults
      response.foreach { doc =>
        val resultMap: java.util.Map[java.lang.String, Object] = new java.util.HashMap[java.lang.String, Object]()
        val fields = doc.getFieldNames
        fields.foreach { fieldName =>
          resultMap.put(fieldName, doc.getFieldValue(fieldName))
        }
        if (!resultMap.isEmpty)
          resultList.add(resultMap)
      }
    }
    resultList
  }

  /**
    *
    * who where when what
    * @param keyWords
    * @param clientIp
    * @param userAgent
    * @param sourceType
    * @param userId
    */
  def recordSearchLog(keyWords: java.lang.String, clientIp: java.lang.String, userAgent: java.lang.String, sourceType: java.lang.String, userId: java.lang.String): Unit = {
    val currentTime = null
  }

  /**
    *
    * get filter atrtributes by catagoryid
    * @param catagoryId
    * @param cityId
    * @return FilterAttribute
    */
  def searchFilterAttributeByCatagoryId(catagoryId: java.lang.Integer, cityId: java.lang.Integer): java.util.List[FilterAttribute] = {
    if (catagoryId != null && cityId != null) {
      val q = s"catid_s:$catagoryId"

      val fl = "filterId_s,attDescZh_s,range_s"

      val query: SolrQuery = new SolrQuery
      query.set("qt", "/select")
      query.setQuery(q)


      query.setFields(fl)

      query.addSort("attSort_ti", SolrQuery.ORDER.desc) //sort

      val r = solrClient.searchByQuery(query, "screencloud")
      var result: QueryResponse = null
      if (r != null) result = r.asInstanceOf[QueryResponse]
      val resultSearch = getSearchResult(result) //get response result

      var filterAttributeSearchResult: FilterAttributeSearchResult = null


      if (resultSearch != null) {
        val filterFieldsValues = new util.HashMap[java.lang.String, util.List[java.lang.String]]()
        resultSearch.foreach { doc =>
          val attributeId = doc.get("filterId_s").toString
          val attributeName = doc.get("attDescZh_s").toString
          setAttributeNameById(attributeId, attributeName) //set cache

          //add facet and facet.query
          val ranges = doc.get("range_s")
          if (ranges != null && !ranges.toString.trim.equalsIgnoreCase("")) {
            //add facet query
            val rangesArray = ranges.toString.split("\\|")
            val rangeList = new util.ArrayList[String]()
            var count: Int = 0
            if (rangesArray.size > 0 && !rangesArray(0).trim.equalsIgnoreCase("") && !rangesArray(0).trim.equalsIgnoreCase("\"\"")) {
              rangesArray.foreach { query =>
                if (count == 0) {
                  val lU = query.split("-")
                  val minV = lU(0).trim
                  rangeList.add(s"[* TO ${minV}}")

                } else if (count == rangesArray.length - 1) {
                  val lU = query.split("-")
                  val maxV = lU(1).trim
                  rangeList.add(s"[${maxV} TO *}")
                }
                val rangeQ = query.replaceAll("-", " TO ")
                rangeList.add(s"[${rangeQ.trim}}")
                count += 1
              }
            }
            if (rangeList.size() > 0)
              filterFieldsValues.put(attributeId, rangeList)
            else filterFieldsValues.put(attributeId, null)

          } else {
            //just facet.field
            filterFieldsValues.put(attributeId, null)
          }
        }

        filterAttributeSearchResult = attributeFilterSearch(null, catagoryId, cityId, null, null, filterFieldsValues, null, null)
      }

      if (filterAttributeSearchResult == null) return null
      else return filterAttributeSearchResult.getFilterAttributes

    } else null
  }


  /**
    *
    * Tips: front should keep the attributeName cache by searchFilterAttributeByCatagoryId
    * @param keyWords
    * @param catagoryId
    * @param cityId
    * @param sorts   eg:Map(price->desc,sales->desc,score->desc)
    * @param filters eg:Map("t89_s"->"一恒","t214_tf"->"[300 TO *]")  fq
    * @param filterFieldsValues  facet.field and facet.querys  eg: Map(
    *                            "t89_s"=>null,
    *                            "t214_tf"=>List("* TO 100","100 TO 200","200 TO *")
    *                            )
    * @param start eg:0
    * @param rows eg:10
    * @return   FilterAttributeSearchResult
    */
  def attributeFilterSearch(keyWords: java.lang.String, catagoryId: java.lang.Integer, cityId: java.lang.Integer, sorts: java.util.Map[java.lang.String, java.lang.String], filters: java.util.Map[java.lang.String, java.lang.String], filterFieldsValues: java.util.Map[java.lang.String, java.util.List[java.lang.String]], start: java.lang.Integer, rows: java.lang.Integer): FilterAttributeSearchResult = {
    if (catagoryId != null && cityId != null) {
      val filterAttributeSearchResult = new FilterAttributeSearchResult()

      val msg = new Msg()
      val searchResult = new SearchResult()
      //page
      var sStart: Int = 0
      var sRows: Int = 10

      if (start != null && start > 0) sStart = start
      if (rows != null && rows > 0) sRows = rows


      var keyWord: String = null
      if (keyWords != null && !keyWords.trim.equalsIgnoreCase(""))
        keyWord = keyWords.trim.toLowerCase
      var keyWordsModel = "*:*"
      if (keyWord != null)
        keyWordsModel = s"(original:$keyWord^50) OR (sku:$keyWord^50) OR (brandZh_ps$keyWord^30) OR (brandEn_ps:$keyWord^30) OR (sku:*$keyWord*^11) OR (original:*$keyWord*^10) OR (text:$keyWord^2) OR (pinyin:$keyWord^0.002)"

      val fqGeneral = s"(deliveryTime:0 OR cityId:$cityId)"
      val fqCataId = s"(categoryId3:$catagoryId OR categoryId4:$catagoryId)"

      val query: SolrQuery = new SolrQuery
      query.set("qt", "/select")
      query.setQuery(keyWordsModel)

      query.addFilterQuery(fqGeneral)
      query.addFilterQuery(fqCataId)


      if (filters != null && filters.size() > 0) {
        filters.foreach { fV =>
          val field = fV._1
          val value = fV._2
          //fq=t89_s:(memmert OR Memmert)
          if (Util.regex(value, "^[A-Za-z]+$")) {
            val v1 = value.charAt(0).toUpper + value.substring(1)
            val v2 = value.charAt(0).toLower + value.substring(1)
            val fq = s"$field:($v1 OR $v2)"
            query.addFilterQuery(fq)
          } else {
            query.addFilterQuery(s"$field:$value")
          }
        }
      }


      //sort
      if (sorts != null && sorts.size() > 0) {
        // eg:  query.addSort("price", SolrQuery.ORDER.desc)
        sorts.foreach { sortOrder =>
          val field = sortOrder._1
          val orderString = sortOrder._2.trim
          var order: ORDER = null
          orderString match {
            case "desc" => order = SolrQuery.ORDER.desc
            case "asc" => order = SolrQuery.ORDER.asc
            case _ => SolrQuery.ORDER.desc
          }
          query.addSort(field, order)
        }
      }


      //facet and facet query
      query.setFacet(true)
      query.setFacetMinCount(1)
      query.setFacetMissing(false)

      if (filterFieldsValues != null && filterFieldsValues.size() > 0) {
        filterFieldsValues.foreach { facet =>
          val field = facet._1
          val ranges = facet._2
          if (ranges != null && ranges.size() > 0) {
            //range facet.query
            ranges.foreach(range => query.addFacetQuery(s"$field:$range"))
          } else {
            //facet.field
            query.addFacetField(field)
          }
        }
      }


      //page
      query.setStart(sStart)
      query.setRows(sRows)




      val r = solrClient.searchByQuery(query, "mergescloud")
      var result: QueryResponse = null
      if (r != null) result = r.asInstanceOf[QueryResponse]
      getSearchResultByResponse(msg, searchResult, result) //get searchResult

      filterAttributeSearchResult.setSearchResult(searchResult) //set searchResult


      if (result != null) {
        val filterAttributes = new java.util.ArrayList[FilterAttribute]()

        val facetFields = result.getFacetFields
        if (facetFields != null && facetFields.size() > 0) {
          //facet.field
          facetFields.foreach { facetField =>
            val filterAttribute = new FilterAttribute()

            val facetFieldName = facetField.getName
            val facetFieldValues = facetField.getValues
            filterAttribute.setAttrId(facetFieldName)
            filterAttribute.setAttrName(getAttributeNameById(facetFieldName))

            if (facetFieldValues != null && facetFieldValues.size() > 0) {
              val attributeCountMap = new util.HashMap[java.lang.String, java.lang.Integer]()
              facetFieldValues.foreach { facetcount =>
                val attributeValue = facetcount.getName
                val count = facetcount.getCount.toInt
                attributeCountMap.put(attributeValue, count)
              }
              filterAttribute.setAttrValues(attributeCountMap)
              filterAttribute.setRangeValue(false)
              filterAttributes.add(filterAttribute)
            }
          }
        }

        //facet.query
        val facetQuerys = result.getFacetQuery

        if (facetQuerys != null && !facetQuerys.isEmpty) {
          //facet.query
          /**
            * "t87_tf:[* TO 0}":0,
              "t87_tf:[0 TO 10}":0,
              "t87_tf:[10 TO 20}":1,
              "t87_tf:[20 TO 30}":2,
              "t87_tf:[30 TO *}":4},
            */
          val facetQueryCountMap = new util.HashMap[java.lang.String, java.util.Map[java.lang.String, java.lang.Integer]]()

          facetQuerys.foreach { facetQuery =>
            val query = facetQuery._1
            val count = facetQuery._2
            if (count > 0) {
              val queryFields = query.split(":")
              val field = queryFields(0)
              val attrValue = queryFields(1)
              if (!facetQueryCountMap.contains(field.trim)) {
                val countMap = new java.util.HashMap[java.lang.String, java.lang.Integer]()
                countMap.put(attrValue, count)
                facetQueryCountMap.put(field.trim, countMap)
              } else {
                facetQueryCountMap.get(field.trim).put(attrValue, count)
              }
            }

          }
          if (!facetQueryCountMap.isEmpty) {
            facetQueryCountMap.foreach { facetQuery =>
              val attributeId = facetQuery._1
              val attributeName = getAttributeNameById(attributeId)
              val attributeValues = facetQuery._2
              val isRangeValue = true
              val filterAttribute = new FilterAttribute(attributeId, attributeName, attributeValues, isRangeValue)
              filterAttributes.add(filterAttribute)
            }

          }

        }


        if (filterAttributes.size() != 0) filterAttributeSearchResult.setFilterAttributes(filterAttributes)
      }
      filterAttributeSearchResult
    } else null
  }

  /**
    *
    * @param catagoryId
    * @param sorts   eg:Map(price->desc,sales->desc,score->desc)
    * @param start eg:0
    * @param rows eg:10
    * @return java.util.List[Brand]
    */
  def searchBrandsByCatoryId(catagoryId: java.lang.Integer, sorts: java.util.Map[java.lang.String, java.lang.String], start: java.lang.Integer, rows: java.lang.Integer): java.util.List[Brand] = {
    null
  }


  /**
    *
    * this for autoSuggest in search
    * @param keyWords search keyword
    * @return  java.util.Map[java.lang.String,java.lang.Integer]   eg:Map("soledede"=>10004)  represent counts of document  the keywords  side in
    */
  def suggestByKeyWords(keyWords: java.lang.String): java.util.Map[java.lang.String, java.lang.Integer] = {
    null

  }

  /**
    *
    * data dictionary attribute id to attribute name
    * @param attributeId
    * @return
    */
  def getAttributeNameById(attributeId: String): String = {
    if (attributeId == null) return null
    val attrCache = FilterAttribute.attrIdToattrName
    var attrName: String = null
    if (attrCache.contains(attributeId.trim)) {
      attrName = attrCache.get(attributeId.trim)
    }
    attrName
  }


  /**
    *
    * set data dictionary attribute id to attribute name
    * @param attributeId
    * @param attributeName
    * @return
    */
  private def setAttributeNameById(attributeId: String, attributeName: String): Unit = {
    val attrCache = FilterAttribute.attrIdToattrName
    if (!attrCache.contains(attributeId.trim)) {
      attrCache.put(attributeId.trim, attributeName.trim)
    }
  }


}

object testSearchInterface {
  def main(args: Array[String]) {
    // searchByKeywords


    //testRegex
    testSearchFilterAttributeByCatagoryId
    testAttributeFilterSearch

    //testSplit
  }

  def searchByKeywords = {
    val sorts = new java.util.HashMap[java.lang.String, java.lang.String]
    sorts.put("price", "asc")
    sorts.put("score", "desc")
    SearchInterface.searchByKeywords("防护口罩", 456, sorts, 0, 10)
  }

  def testSearchFilterAttributeByCatagoryId() = {
    val result = SearchInterface.searchFilterAttributeByCatagoryId(1001739, 456)
    println(result)
  }

  def testAttributeFilterSearch = {
    //keywords catid cityid sorts filters filterFieldsValues start rows
    val sorts = new java.util.HashMap[java.lang.String, java.lang.String]
    sorts.put("price", "asc")
    sorts.put("score", "desc")

    val filters = new java.util.HashMap[java.lang.String, java.lang.String]()
    filters.put("t89_s", "Memmert")
    filters.put("t87_tf", "[0 TO *}")

    val filterFieldsValues = new util.HashMap[java.lang.String, util.List[java.lang.String]]()
    filterFieldsValues.put("t89_s", null)
    val rangeList = new util.ArrayList[String]()
    rangeList.add("[* TO 0}")
    rangeList.add("[0 TO 10}")
    rangeList.add("[10 TO 20}")
    rangeList.add("[20 TO 30}")
    rangeList.add("[30 TO *}")
    filterFieldsValues.put("t87_tf", rangeList)
    //SearchInterface.attributeFilterSearch(null, 1001739, 456, sorts, null, filterFieldsValues, 0, 10)
    // val result = SearchInterface.attributeFilterSearch(null, 1001739, 456, sorts, filters, filterFieldsValues, 0, 10)
    val result = SearchInterface.attributeFilterSearch("防护口罩", 1001739, 456, sorts, filters, filterFieldsValues, 0, 10)
    println(result)
  }

  def testSplit() = {
    val testString = "t87_tf:[* TO 0}"
    val array = testString.split(":")
    println(array)
  }

  def testRegex() = {
    // val value = "中Mmemert"
    //val value = "[Mmemert"
    var value = "mmemert"
    if (Util.regex(value, "^[A-Za-z]+$")) println("true") else println("false")

    if (Util.regex(value, "^[A-Za-z]+$")) {
      val v1 = value.charAt(0).toUpper + value.substring(1)
      val v2 = value.charAt(0).toLower + value.substring(1)
      println(v1 + "=" + v2)
    }

  }
}
