package gokbg3.rest

import grails.converters.JSON
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import org.gokb.cred.KBComponent
import org.gokb.cred.Office
import org.gokb.cred.Org
import org.gokb.cred.Platform
import org.gokb.TitleLookupService
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source

@Integration
@Rollback
class OrgTestSpec extends AbstractAuthSpec {

  private RestBuilder rest = new RestBuilder()

  def setupSpec() {
  }

  def setup() {
    def new_plt = Platform.findByName("TestOrgPlt") ?: new Platform(name: "TestOrgPlt").save(flush: true)
    def new_plt_upd = Platform.findByName("TestOrgPltUpdate") ?: new Platform(name: "TestOrgPltUpdate").save(flush: true)
    def new_source = Source.findByName("TestOrgPatchSource") ?: new Source(name: "TestOrgPatchSource").save(flush: true)
    def new_office = Office.findByName("firstTestOffice") ?: new Office(name: "firstTestOffice", language: RefdataCategory.lookup(KBComponent.RD_LANGUAGE, "ger"))
        new_office.save(flush: true)
    def patch_org = Org.findByName("TestOrgPatch") ?: new Org(name: "TestOrgPatch", source: new_source, offices:[new_office])
        patch_org.save(flush: true)
  }

  def cleanup() {
    if (Platform.findByName("TestOrgPlt")) {
      Platform.findByName("TestOrgPlt")?.refresh().expunge()
    }
    if (Platform.findByName("TestOrgPltUpdate")) {
      Platform.findByName("TestOrgPltUpdate")?.refresh().expunge()
    }
    Office.list().each {
      it.expunge()
    }
    if (Org.findByName("TestOrgPost")) {
        Org.findByName("TestOrgPost")?.refresh().expunge()
    }
    if (Org.findByName("TestOrgUpdateNew")) {
      Org.findByName("TestOrgUpdateNew")?.refresh().expunge()
    }
    if (Org.findByName("TestOrgUpdateSource")) {
      Org.findByName("TestOrgUpdateSource")?.refresh().expunge()

      if (Source.findByName("TestOrgPatchSource")) {
        Source.findByName("TestOrgPatchSource")?.refresh().expunge()
      }
    }
  }

  void "test /rest/orgs without token"() {
    given:

    def urlPath = getUrlPath()

    when:

    RestResponse resp = rest.get("${urlPath}/rest/orgs") {
      accept('application/json')
    }

    then:

    resp.status == 200 // OK
  }

  void "test /rest/orgs/<id> with valid token"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    RestResponse resp = rest.get("${urlPath}/rest/orgs/${Org.findByName("TestOrgPatch").id}") {
      accept('application/json')
      auth("Bearer $accessToken")
    }

    then:

    resp.status == 200 // OK
    resp.json.name == "TestOrgPatch"
  }

  void "test insert new org"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def json_record = [
      name             : "TestOrgPost",
      ids              : [
        [namespace: "global", value: "test-org-id-val"]
      ],
      providedPlatforms: ["TestOrgPlt"],
      offices: [
          [name: "TestOffice1",
           language:"ger",
           function: "Technical Support"],
          [name: "TestOffice2",
           language:"epo",
           function: "other"],
          [name: "TestOffice3",
           language:"hun"]
      ],
    ]

    when:

    RestResponse resp = rest.post("${urlPath}/rest/orgs") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(json_record as JSON)
    }

    then:

    resp.status == 201 // Created

    expect:
    resp.json?.name == "TestOrgPost"
    resp.json?._embedded?.ids?.size() == 1
    resp.json?._embedded?.offices?.size()==3
    resp.json?._embedded?.offices*.function.name.count("Technical Support")==2
    resp.json?._embedded?.offices*.function.name.count("Other")
    resp.json?._embedded?.offices*.language.name.containsAll(["hun", "ger", "epo"])
  }

  void "test org index"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()

    when:

    RestResponse resp = rest.get("${urlPath}/rest/orgs") {
      accept('application/json')
      auth("Bearer $accessToken")
    }

    then:

    resp.status == 200 // OK

    expect:

    resp.json?.data?.size() > 0
  }

  void "test org update"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def updated_plt = Platform.findByName("TestOrgPltUpdate")
    def id = Org.findByName("TestOrgPatch")?.id

    def update_record = [
      name             : "TestOrgUpdateNew",
      ids              : [
        [namespace: "global", value: "test-org-id-val-new"]
      ],
      providedPlatforms: [updated_plt.id],
      offices: [[name: "2ndTestOffice1",
                 language:"ger",
                 function:"Technical Support"],
                [name: "2ndTestOffice2",
                 language:RefdataCategory.lookup(KBComponent.RD_LANGUAGE, "eng").id,
                 function: "other"]
      ]
    ]

    when:

    RestResponse resp = rest.put("${urlPath}/rest/orgs/$id?_embed=providedPlatforms,ids,offices") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(update_record as JSON)
    }

    then:

    resp.status == 200 // OK

    expect:

    resp.json.name == "TestOrgUpdateNew"
    resp.json._embedded?.ids?.size() == 1
    resp.json._embedded?.providedPlatforms?.size() == 1
    resp.json._embedded?.offices.size() == 2
    resp.json._embedded?.offices*.function.name.contains("Other")
  }

  void "test source delete"() {
    given:

    def urlPath = getUrlPath()
    String accessToken = getAccessToken()
    def id = Org.findByName("TestOrgPatch")?.id

    def update_record = [
      name             : "TestOrgUpdateSource",
      ids              : [
        [namespace: "global", value: "test-org-id-val-new"]
      ],
      source           : null
    ]

    when:

    RestResponse resp = rest.put("${urlPath}/rest/provider/$id") {
      accept('application/json')
      auth("Bearer $accessToken")
      body(update_record as JSON)
    }

    then:

    resp.status == 200 // OK

    expect:

    resp.json.name == "TestOrgUpdateSource"
    resp.json.source == null
    resp.json._embedded?.ids?.size() == 1
//    resp.json._embedded?.providedPlatforms?.size() == 1
  }
}
