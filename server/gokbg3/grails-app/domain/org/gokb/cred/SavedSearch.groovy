package org.gokb.cred

import javax.persistence.Transient
import groovy.json.JsonSlurper

class SavedSearch {

  String name
  User owner
  String searchDescriptor
  Date dateCreated

  static constraints = {
    name blank: false, nullable:false
    owner blank: false, nullable: false
    searchDescriptor blank: false, nullable:false
  }

  static mapping = {
    id column: 'ss_id'
    name column: 'ss_name'
    owner column: 'ss_owner_fk'
    searchDescriptor column: 'ss_search_descriptor', type:'text'
    dateCreated column: 'ss_date_created'
  }

  public def toParam() {
    def jsonSlurper = new JsonSlurper().parseText(searchDescriptor)
    return jsonSlurper
  }

}
