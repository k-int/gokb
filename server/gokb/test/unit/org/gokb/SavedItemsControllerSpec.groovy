package org.gokb

import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.web.ControllerUnitTestMixin} for usage instructions
 */
@TestFor(SavedItemsController)
class SavedItemsControllerSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test SavedItemsController"() {
      expect:
        1==1
    }
}
