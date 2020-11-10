package org.gokb

import groovyx.net.http.RESTClient
import org.gokb.cred.Package

import static groovyx.net.http.Method.GET

class AutoUpdatePackagesJob {

  // Allow only one run at a time.
  static concurrent = false

  static triggers = {
    // Cron timer.            
    cron name: 'AutoUpdatePackageTrigger', cronExpression: "0 0 6 * * ? *" // daily at 6:00 am
  }

  def execute() {
    if (!concurrent) {
      concurrent = true;
      if (grailsApplication.config.gokb.packageUpdate_enabled && grailsApplication.config.gokb.ygorUrl) {
        log.debug("Beginning scheduled auto update packages job.")
        def endpoint = grailsApplication.config.gokb.ygorUrl
        def target_service
        def respData
        // find all updateable packages
        def updPacks = Package.executeQuery("from Package p where p.source != null and p.source.automaticUpdates = true and (p.source.lastRun = null or p.source.lastRun < current_date)")
        updPacks.each { Package p ->
          def error = false
          def path = "/enrichment/processGokbPackage?pkgId=${p.id}&updateToken=${p.updateToken}"
          target_service = new RESTClient(endpoint + path)
          try {
            target_service.request(GET) { request ->
              response.success = { resp, data ->
                respData = data
              }
              response.failure = { resp ->
                log.error("Error - ${resp}");
                error = true
              }
            }
          }
          catch (Exception e) {
            e.printStackTrace();
          }
          // TODO: asynchronous calls to avoid timeouts followed by waiting for status=finished before next package
          if (!error) {
            p.source.lastRun = new Date()
          }
        }
        log.info("auto update packages job completed.")
      } else {
        log.debug("automatic package update is not enabled - set config.gokb.packageUpdate_enabled = true and config.gokb.ygorUrl in config to enable");
      }
    }
    concurrent = false
  }
}
