package org.gokb

import org.gokb.cred.*

class ReviewRequestService {

  def raise(KBComponent forComponent, String actionRequired, String cause = null, User raisedBy = null, refineProject = null, additionalInfo = null, RefdataValue stdDesc = null, CuratoryGroup group = null) {

    // Create a request.
    ReviewRequest req = new ReviewRequest (
        status : RefdataCategory.lookupOrCreate('ReviewRequest.Status', 'Open'),
        raisedBy : (raisedBy),
        descriptionOfCause : (cause),
        reviewRequest : (actionRequired),
        refineProject : (refineProject),
        stdDesc : (stdDesc),
        additionalInfo : (additionalInfo),
        componentToReview : (forComponent)
        ).save(flush:true, failOnError:true);

    if (req) {
      if (raisedBy) {
        new ReviewRequestAllocationLog(allocatedTo: raisedBy, rr: req).save(flush:true,failOnError:true)
      }

      if (group) {
        AllocatedReviewGroup.create(group, req)
      }
      else if (KBComponent.has(forComponent, 'curatoryGroups')) {
        log.debug("Using Component groups for ${forComponent} -> ${forComponent.class?.name}..")
        Package.withNewSession {
          def comp = KBComponent.get(forComponent.id)
          comp.curatoryGroups?.each { gr ->
            CuratoryGroup cg = CuratoryGroup.get(gr.id)
            log.debug("Allocating Package Group ${gr} to review")
            AllocatedReviewGroup.create(cg, req)
          }
        }
      }
      else if (forComponent.class == TitleInstancePackagePlatform && forComponent.pkg?.curatoryGroups?.size() > 0) {
        log.debug("Using TIPP pkg groups ..")
        TitleInstancePackagePlatform.withNewSession {
          forComponent.pkg?.curatoryGroups?.each { gr ->
            CuratoryGroup cg = CuratoryGroup.get(gr.id)
            log.debug("Allocating TIPP Pkg Group ${gr} to review")
            AllocatedReviewGroup.create(cg, req)
          }
        }
      }
      else if (raisedBy?.curatoryGroups?.size() > 0) {
        log.debug("Using User groups ..")
        User.withNewSession {
          raisedBy.curatoryGroups.each { gr ->
            CuratoryGroup cg = CuratoryGroup.get(gr.id)
            log.debug("Allocating User Group ${gr} to review")
            AllocatedReviewGroup.create(cg, req)
          }
        }
      }
    }

    req
  }
}
