package org.gokb

import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.web.ControllerUnitTestMixin} for usage instructions
 */
@TestFor(GlobalSearchController)
class GlobalSearchControllerSpec extends Specification {

    def setup() {
    }

    def cleanup() {
    }

    void "test GlobalSearchController"() {
      expect:
        1==1
    }
}
