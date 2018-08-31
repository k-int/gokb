package com.k_int

import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.client.*
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.sort.SortOrder;



class ESSearchService{
// Map the parameter names we use in the webapp with the ES fields
  def reversemap = ['subject':'subject', 
                    'provider':'provid',
                    'type':'rectype',
                    'endYear':'endYear',
                    'startYear':'startYear',
                    'consortiaName':'consortiaName',
                    'cpname':'cpname',
                    'availableToOrgs':'availableToOrgs',
                    'isPublic':'isPublic',
                    'componentType':'componentType',
                    'lastModified':'lastModified']

  def ESWrapperService
  def grailsApplication

  def search(params){
    search(params,reversemap)
  }

  def search(params, field_map){
    log.debug("ESSearchService::search - ${params}")

   def result = [:]

   Client esclient = ESWrapperService.getClient()
  
    try {
      if ( (params.q && params.q.length() > 0) || params.rectype) {

       if ((!params.all) || (!params.all?.equals("yes"))) {
         params.max = Math.min(params.max ? params.max : 15, 100)
        }

        params.offset = params.offset ? params.offset : 0

        def query_str = buildQuery(params,field_map)

        if (params.tempFQ) {
          log.debug("found tempFQ, adding to query string")
          query_str = query_str + " AND ( " + params.tempFQ + " ) "
          params.remove("tempFQ") //remove from GSP access
        }

        
        def es_index = grailsApplication.config.gokb_es_index ?: "gokbg3"
        log.debug("index:${es_index} query: ${query_str}");

        def search_results = null
        
        try {
          log.debug("start to build srb with index: " + es_index)
          SearchRequestBuilder srb = esclient.prepareSearch(es_index)
          log.debug("srb built: ${srb} sort=${params.sort}");
          if (params.sort) {
            SortOrder order = SortOrder.ASC
            if (params.order) {
              order = SortOrder.valueOf(params.order?.toUpperCase())
            }
            srb = srb.addSort("${params.sort}".toString(), order)
          }
          log.debug("srb start to add query and aggregration query string is ${query_str}")
    
          srb.setQuery(QueryBuilders.queryStringQuery(query_str))//QueryBuilders.wrapperQuery(query_str)
             .addAggregation(AggregationBuilders.terms('curatoryGroup').size(25).field('curatoryGroup'))
             .addAggregation(AggregationBuilders.terms('cpname').size(25).field('cpname.keyword'))
             .addAggregation(AggregationBuilders.terms('type').field('rectype.keyword'))
             .addAggregation(AggregationBuilders.terms('startYear').size(25).field('startYear.keyword'))
             .addAggregation(AggregationBuilders.terms('endYear').size(25).field('endYear.keyword'))
             .setFrom(params.offset)
             .setSize(params.max)
             
          // log.debug("finished srb and aggregrations: " + srb)
          search_results = srb.get()
          // log.debug("search results: " + search_results)
        }
        catch (Exception ex) {
          log.error("Error processing ${es_index} ${query_str}",ex);
        }
        
        //TODO: change this part to represent what we really need if this is not it, see the final part of this method where hits are done
        if (search_results) {
          def search_hits = search_results.getHits()
          result.hits = search_hits.getHits()
          result.firstrec = params.offset + 1
          result.resultsTotal = search_hits.totalHits
          result.lastrec = Math.min ( params.offset + params.max + 1, result.resultsTotal)
          
          if (search_results.getAggregations()) {
            result.facets = [:]
            search_results.getAggregations().each { entry ->
              log.debug("Aggregation entry ${entry} ${entry.getName()}");
              def facet_values = []
              entry.buckets.each { bucket ->
                bucket.each { bi ->
                  facet_values.add([term:bi.getKey(),display:bi.getKey(),count:bi.getDocCount()])
                }
              }
              result.facets[entry.getName()] = facet_values
            }
          }
        }
        log.debug("finished results facets")
      }
      else {
        log.debug("No query.. Show search page")
      }
    }
    finally {
      try {
        log.debug("in finally")
      }
      catch ( Exception e ) {
        log.error("problem",e);
      }
    }
    result
  }

  def buildQuery(params,field_map) {

    log.debug("BuildQuery... with params ${params}. ReverseMap: ${field_map}");

    StringWriter sw = new StringWriter()

    if ( params?.q != null ){
      sw.write(params.q)
    }
      
    if(params?.rectype){
      if(sw.toString()) sw.write(" AND ");
      sw.write(" rectype.keyword:${params.rectype} ")
    } 

    field_map.each { mapping ->

      if ( params[mapping.key] != null ) {

        if ( params[mapping.key].class == java.util.ArrayList) {
          log.debug("mapping is an arraylist: ${mapping} ${mapping.key} ${params[mapping.key]}")
          if(sw.toString()) sw.write(" AND ");
          sw.write(" ( ( ( NOT _type:\"com.k_int.kbplus.Subscription\" ) AND ( NOT _type:\"com.k_int.kbplus.License\" )) OR ( ")

          params[mapping.key].each { p ->  
            if ( p ) {
                sw.write(mapping.value?.toString())
                sw.write(":".toString())
                sw.write(p.toString())
                if(p == params[mapping.key].last()) {
                  sw.write(" ) ) ")
                }else{
                  sw.write(" OR ")
                }
            }
          }
        }
        else {
          // Only add the param if it's length is > 0 or we end up with really ugly URLs
          // II : Changed to only do this if the value is NOT an *

          log.debug("Processing - scalar value : ${params[mapping.key]}");

          try {
            if ( params[mapping.key].length() > 0 && ! ( params[mapping.key].equalsIgnoreCase('*') ) ) {

                if(sw.toString()) sw.write(" AND ");

                sw.write(mapping.value)
                sw.write(":")

                if(params[mapping.key].startsWith("[") && params[mapping.key].endsWith("]")){
                  sw.write(params[mapping.key])
                }else{
                  sw.write(params[mapping.key])
                }
            }
          }
          catch ( Exception e ) {
            log.error("Problem procesing mapping, key is ${mapping.key} value is ${params[mapping.key]}",e);
          }
        }
      }
    }

    def result = sw.toString();
    log.debug("Result of buildQuery is ${result}");

    result;
  }

  @javax.annotation.PreDestroy
  def destroy() {
    log.debug("Destroy");
  }

}
