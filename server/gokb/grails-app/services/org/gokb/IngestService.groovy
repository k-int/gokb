package org.gokb

import com.k_int.ConcurrencyManagerService
import com.k_int.RefineUtils
import grails.converters.JSON
import groovy.json.JsonSlurper

import java.text.SimpleDateFormat

import org.apache.commons.collections.map.CaseInsensitiveMap
import org.apache.commons.compress.archivers.*
import org.apache.commons.compress.archivers.tar.*
import org.apache.commons.compress.compressors.gzip.*
import org.codehaus.groovy.grails.web.json.JSONObject
import org.gokb.cred.*
import org.gokb.refine.*
import org.gokb.validation.Validation
import org.gokb.validation.types.A_ValidationRule
import org.hibernate.Session
import org.joda.time.format.*
import org.springframework.transaction.TransactionStatus
import org.apache.commons.logging.LogFactory

class IngestService {

  // Automatically injected services from grails-app/services
  def grailsApplication
  def titleLookupService
  ComponentLookupService componentLookupService
  def packageService
  def sessionFactory
  ConcurrencyManagerService concurrencyManagerService
  def propertyInstanceMap = org.codehaus.groovy.grails.plugins.DomainClassGrailsPlugin.PROPERTY_INSTANCE_MAP
  def possible_date_formats = [
    new SimpleDateFormat('yyyy-MM-dd'), // Default format Owen is pushing ATM.
    new SimpleDateFormat('yyyy/MM/dd'),
    new SimpleDateFormat('dd/MM/yyyy'),
    new SimpleDateFormat('dd/MM/yy'),
    new SimpleDateFormat('yyyy/MM'),
    new SimpleDateFormat('yyyy')
  ];

  // ISO Date parser.
  DateTimeFormatter ISODateParser = ISODateTimeFormat.dateTimeParser()

  /** Field prefixes ***/
  public static final String IDENTIFIER_PREFIX = 'title.identifier.'
  public static final String TI_FIELD_PREFIX = 'gokb.title.'
  public static final String TIPP_FIELD_PREFIX = 'gokb.tipp.'

  /*** Supported field names ***/
  public static final String PUBLICATION_TITLE = 'PublicationTitle'
  public static final String DATE_FIRST_PACKAGE_ISSUE = 'DateFirstPackageIssue'
  public static final String VOLUME_FIRST_PACKAGE_ISSUE = 'VolumeFirstPackageIssue'
  public static final String NUMBER_FIRST_PACKAGE_ISSUE = 'NumberFirstPackageIssue'
  public static final String DATE_LAST_PACKAGE_ISSUE = 'DateLastPackageIssue'
  public static final String VOLUME_LAST_PACKAGE_ISSUE = 'VolumeLastPackageIssue'
  public static final String NUMBER_LAST_PACKAGE_ISSUE = 'NumberLastPackageIssue'

  //  public static final String PRINT_IDENTIFIER = "${IDENTIFIER_PREFIX}issn"
  //  public static final String ONLINE_IDENTIFIER = "${IDENTIFIER_PREFIX}eissn"
  public static final String HOST_PLATFORM_NAME = 'platform.host.name'
  // public static final String HOST_PLATFORM_URL = 'platform.host.url'
  public static final String HOST_PLATFORM_URL = 'tipp.url'

  public static final String COVERAGE_DEPTH = 'CoverageDepth'
  public static final String COVERAGE_NOTES = 'CoverageNotes'
  public static final String EMBARGO_INFO = 'KBARTEmbargo'

  public static final String PACKAGE_NAME = 'package.name'
  public static final String PUBLISHER_NAME = 'org.publisher.name'

  /** Missing fields **/
  public static final String DELAYED_OA = "delayedOA"
  public static final String DELAYED_OA_EMBARGO = "delayedOAEmbargo"
  public static final String HYBRID_OA = "hybridOA"
  public static final String HYBRID_OA_URL = "hybridOAUrl"
  public static final String PRIMARY_TIPP = "PrimaryTIPP"
  public static final String TIPP_PAYMENT = "TIPPPayment"
  public static final String TIPP_STATUS = "TIPPStatus"
  public static final String TITLE_OA_STATUS = "title.oastatus"
  public static final String TITLE_IMPRINT = "title.imprint"

  /**
   *  Validate a parsed project. 
   *  @param project_data Parsed map of project data
   */
  def validate(project_data) {
    log.debug("Validate");

    def result = Validation.doValidate(project_data)

    if (!result.status) {
      log.debug("Project is invalid.")
    } else {
    
      if ( result.messages?.size() > 0 ) {
        log.debug("Project is valid but has warnings.")
      }
      else {
        log.debug("Project is fully valid");
      }
    }

    result
  }

  /**
   * Estimate the number of each component that would be Created/Updated as a result of ingesting this data.
   */
  def estimateChanges(project_data, project_id = null, boolean incremental) {

    final def recon_data = project_data.recon
    
    // The current component status value.
    RefdataValue current = RefdataCategory.lookupOrCreate(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)

    // The result object.
    def result = []

    // Default all our counters.
    long ctr             = 0
    long titleRows       = 0
    long existingTitles  = 0
    long newPkgs         = 0
    long existingPlats   = 0
    long newPubs		     = 0
    long existingPubs	   = 0

    // Read in the column positions, and supplied Identifiers
    CaseInsensitiveMap col_positions = [:]
    def identifiers = []
    project_data.columnDefinitions.each { cd ->
      def cn = cd.name?.toLowerCase()
      if (cn) {
        // Add to column positions
        col_positions[cn] = cd.cellIndex;

        // Check to see if it's an identifier.
        if (cn.startsWith(IDENTIFIER_PREFIX) ) {
          def idparts = cn.split(/\./)
          if ( idparts.length == 3 ) {
            // Add to the IDs.
            identifiers.add([type:idparts[2],colno:cd.cellIndex])
          }
        }
      }
    }

    log.debug("Using col positions: ${col_positions}, identifiers: ${identifiers}")

    // Package identifier.
    def default_pkg_identifier = null

    // If a project id has been supplied
    if (project_id != null) {
      log.debug("Using refine project id ${project_id}.")

      // Check the package.
      RefineProject project = RefineProject.get(project_id)

      // Check that the project exists...
      if (project) {

        log.debug("Refine project exists. Use to generate the default pkg_id.")

        // The provider.
        Org provider = project.provider

        // Set the default pkg id to use when no value supplied.
        default_pkg_identifier = "${provider.name}:${project_id}"
      }
    }

    // Could not create default package id. Assume same new package for each blank row.
    if (!default_pkg_identifier) {
      log.debug("No refine project id supplied. Assuming blank rows are added to the same new package.")
    }

    // Create the sets for processing after run through.
    Set platformNames   	= []
    Set packageIdentifiers 	= []
    Set publisher_orgs		= []

    log.debug("Finding existing titles...");
    
    Map<String, KBComponent> comps = [:].withDefault { String k ->
      componentLookupService.lookupComponent(k)
    }

    // Go through each row and build up the tipp criteria.
    def tiCrit = ComboCriteria.createFor(TitleInstance.createCriteria())
    existingTitles = tiCrit.get {

      or {

        project_data.rowData.each { datarow ->
          if ( getRowValue(datarow,col_positions,PUBLICATION_TITLE) ) {

            def host_platform_name = getRowValue(datarow,col_positions,HOST_PLATFORM_NAME)
            //			def host_norm_platform_name = host_platform_name ? host_platform_name.toLowerCase().trim() : null;

            // Just add the normname to the platforms list.
            platformNames << host_platform_name

            // Package ID
            def pkg_id	= getRowValue(datarow,col_positions,PACKAGE_NAME, recon_data)
            pkg_id = pkg_id?.trim()
            if (!pkg_id || pkg_id == "") {
              pkg_id = default_pkg_identifier
            }

            packageIdentifiers << pkg_id.toString()

            // Lookup a publisher ID if present.
            def pub = comps["${getRowValue(datarow,col_positions,PUBLISHER_NAME, recon_data)}"]
            if (pub) publisher_orgs << pub

            // Each identifier type.
            identifiers.each { ai ->

              // The value.
              def val = jsonv(datarow.cells[ai.colno])

              if (val) {
                and {
                  tiCrit.add ("ids.namespace.value", "eq", ai.type)
                  tiCrit.add ("ids.value", "eq", val)
                }
              }
            }

            // increment the titleRows counter.
            titleRows ++
          }

          // Increment the row counter.
          ctr ++
        }
      }

      projections {
        countDistinct("id")
      }
    }

    // Try and find a package for the provider with the name entered.
    def existingPkgs = componentLookupService.lookupComponents(packageIdentifiers).size()

    // New packages.
    newPkgs = packageIdentifiers.size() - existingPkgs

    if (!incremental) {
      // A new package will be created for each existing package too.
      newPkgs += existingPkgs
    }

    result << [ type : "packages", "new" : (newPkgs), "updated" : existingPkgs ]

    // We should now have a query that we can execute to determine (roughly) how many Tipps will be added.
    long newTitles = (titleRows - existingTitles)
    if (newTitles < 0) {

      // Offset the existing titles as some ids point to multiple components.
      existingTitles = existingTitles + newTitles

      // Now make 0.
      newTitles -= newTitles
    }

    result << [ type : "titles", "new" : (newTitles), "updated" : existingTitles ]

    // Run a count.
    existingPlats = platformNames.size()
    result << [ type : "platforms", "new" : 0, "updated" : existingPlats ]

    // Distinct listed publishers.
    if (publisher_orgs.size() > 0) {

      // Check ones that haven't yet published.
      publisher_orgs.each { Org publisher ->
        if (publisher.getPublishedTitles().size() == 0) {
          newPubs ++
        }
      }
    }

    // Existing publishers
    existingPubs = publisher_orgs.size() - newPubs
    result << [ type : "publishers", "new" : newPubs, "updated" : existingPubs ]

    log.debug("Estimate changes complete...${result}");
    // Return the result.
    result
  }

  /**
   * Update the project with the supplied values. Do this in a session of it own.
   * @param project_id
   * @param progress
   * @param status
   */
  private void updateProjectStatus (long project_id, int progress, RefineProject.Status status = null) {
    log.debug("UpdateProjectStatus");
    RefineProject.withNewSession { Session s ->
      s.beginTransaction()
        log.debug ("Trying to update the refine project in a new transaction.")
        RefineProject project = RefineProject.get(project_id)

        log.debug ("Project ${project}")

        project.progress = progress

        if (status) {
          project.projectStatus = status
        }

        project.save(failOnError:true, flush:true)

        log.debug ("Updated the project.")
      s.getTransaction().commit()
    }
    log.debug("leaving UpdateProjectStatus");
  }

  private handleNonePresentTipps(final old_tipps, user, project = null) {

    // Soft delete the TIPPs not updated here.
    RefineProject.withTransaction { t ->

      for (Set<Long> tipps : old_tipps.values()) {
        for (Long tipp_id : tipps) {

          // Ensure the tipp is in this transaction.
          TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

          if (tipp && tipp.isCurrent()) {
            ReviewRequest.raise(
                tipp,
                "TIPP[${tipp.getId()}] for \"${tipp.title?.name}\" in package [${tipp.package?.getId()}] \"${tipp.package?.name}\" - Not present in file when performing package update",
                "This TIPP was not present when ingesting a package update. Please check to see if it should be deleted",
                user, project
                )
            // Save.
            tipp.save(failOnError:true, flush:true)
            log.debug ("Raised review request for TIPP ${tipp_id}.")
          } else {
            // Ignoring this title as it's not a current TIPP.
            log.debug ("Ignoring TIPP ${tipp_id} as it's not marked as a Current or the lookup returned null.")
          }
        }
      }
    }
  }

  private boolean addDatatRow(result, long project_id, boolean incremental, col_positions, identifiers, gokb_additional_ti_props, gokb_additional_tipp_props, datarow, final old_tipps, retire_packages, skipped_titles, user, final recon_data) {

    // Transaction for each row.
    RefineProject.withNewTransaction { TransactionStatus status ->

      int preDates = -1
      int postDates = -1
      
      RefineProject project = RefineProject.get(project_id)
      if ( getRowValue(datarow,col_positions,PUBLICATION_TITLE,recon_data) ) {

        try {

          def ids = []
          for (ai in identifiers) {
            // The value.
            def val = jsonv(datarow.cells[ai.colno])
            if (val) {
              ids.add([type:ai.type, value:(val)])
            }
          }

          // Title Instance
          log.debug("Looking up title...(ids: ${ids})")

          // Lookup the title.
          TitleInstance title_info = titleLookupService.find(
            getRowValue(datarow,col_positions,PUBLICATION_TITLE,recon_data),
            getRowValue(datarow,col_positions,PUBLISHER_NAME,recon_data),
            ids,
            user,
            project
          );

          // If we match a title then ingest...
          if (title_info != null) {

            // Set TITLE OA STATUS if it's not null and different to the current value.. This might cause title OA status
            // to oscillate between different values - raised as a concern but dismissed as unlikely in weekly calls.
            def title_oa_status = getRowValue(datarow,col_positions,TITLE_OA_STATUS,recon_data)
            if ( title_oa_status != null ) {
              if ( title_info.OAStatus?.value != title_oa_status ) {
                //titleOAStatus:getRowRefdataValue('TitleInstance.OAStatus', datarow, col_positions, TITLE_OA_STATUS)
                title_info.OAStatus = RefdataCategory.lookupOrCreate('TitleInstance.OAStatus', title_oa_status)
              }
            }
            
            // Handle the imprint.
            def imprint = componentLookupService.lookupComponent(
              getRowValue(datarow,col_positions,TITLE_IMPRINT, recon_data)
            )
            
            if ( imprint != null && (title_info.imprint == null || title_info.imprint.id != imprint.id )) {
              // Add/Modify the imprint.
              title_info.imprint = imprint
            } 

            // Additional TI properties.
            for (apd in gokb_additional_ti_props) {
              title_info.appendToAdditionalProperty(
                  apd.name.toLowerCase(), jsonv(datarow.cells[apd.col])
                  )
            }

            // Save any changes to the title here.
            title_info.save(failOnError:true, flush:true)

            // Platforms must already exist in GOKb, so just to the lookup.
            
            Platform platform_info = componentLookupService.lookupComponent( getRowValue(datarow,col_positions,HOST_PLATFORM_NAME, recon_data),true)
            if (platform_info == null) {
              throw new Exception("Host platform could not be found. This should not happen, as all platforms must pre-exist in GOKb. Datarow was ${datarow}");
            }

            // The package.
            String pkg_name = getRowValue(datarow,col_positions,PACKAGE_NAME, recon_data)
            Package pkg = packageService.findCorrectPackage(
                retire_packages,
                pkg_name,
                incremental
                );

            // Set the propvider of the package to that on the project.
            // only try and set the package provider if it's different to what we have in our hand
            if ( pkg.provider != project.provider ) {
              Org provider = Org.lock(project.provider.id)
              pkg.setProvider (provider)
            }
            
            // Set the source.
            if ( pkg.source != project.getSource() ) {
              pkg.setSource(project.getSource())
            }

            // Set the latest project.
            if ( pkg.lastProject != project ) {
              pkg.setLastProject(project)
            }

            pkg.lastUpdateComment = "Updated by Refine on ${new Date()} ${user}"
            pkg.lastUpdatedBy = user

            // Save the Package changes.
            pkg.save(failOnError:true, flush:true)

            // Populate the tipp attribute map.
            def tipp_values = [
              title:title_info,
              pkg:pkg,
              hostPlatform:platform_info,
              startDate:parseDate(getRowValue(datarow,col_positions,DATE_FIRST_PACKAGE_ISSUE,recon_data)),
              startVolume:getRowValue(datarow,col_positions,VOLUME_FIRST_PACKAGE_ISSUE,recon_data),
              startIssue:getRowValue(datarow,col_positions,NUMBER_FIRST_PACKAGE_ISSUE,recon_data),
              endDate:parseDate(getRowValue(datarow,col_positions,DATE_LAST_PACKAGE_ISSUE,recon_data)),
              endVolume:getRowValue(datarow,col_positions,VOLUME_LAST_PACKAGE_ISSUE,recon_data),
              endIssue:getRowValue(datarow,col_positions,NUMBER_LAST_PACKAGE_ISSUE,recon_data),
              embargo:getRowValue(datarow,col_positions,EMBARGO_INFO,recon_data),
              coverageDepth:getRowRefdataValue("TitleInstancePackagePlatform.CoverageDepth", datarow, col_positions, COVERAGE_DEPTH),
              coverageNote:getRowValue(datarow,col_positions,COVERAGE_NOTES,recon_data),
              url:getRowValue(datarow,col_positions,HOST_PLATFORM_URL),
//              delayedOA:getRowRefdataValue("TitleInstancePackagePlatform.DelayedOA", datarow, col_positions, DELAYED_OA),
//              delayedOAEmbargo:getRowValue(datarow, col_positions, DELAYED_OA_EMBARGO),
//              hybridOA:getRowRefdataValue("TitleInstancePackagePlatform.hybridOA", datarow, col_positions, HYBRID_OA),
//              hybridOAUrl:getRowValue(datarow, col_positions, HYBRID_OA_URL),
              primary:getRowRefdataValue("TitleInstancePackagePlatform.Primary", datarow, col_positions, PRIMARY_TIPP),
              paymentType:getRowRefdataValue("TitleInstancePackagePlatform.PaymentType", datarow, col_positions, TIPP_PAYMENT),
              status:getRowRefdataValue(KBComponent.RD_STATUS, datarow, col_positions, TIPP_STATUS)
            ]

            def tipp = null

            // Check incrmental.
            if (incremental) {
              // TODO: THIS DOES NOT WORK!!!!
              // Incremental... Lookup the TIPP
              //          def crit = ComboCriteria.createFor(TitleInstancePackagePlatform.createCriteria())
              //          tipp = crit.get {
              //          and {
              //            crit.add ("title.id", "eq", title_info.id)
              //            crit.add ("pkg.id", "eq", pkg.id)
              //            crit.add ("hostPlatform.id", "eq", platform_info.id)
              //          }
              //          }

              // Get the tipps from the title.

              tipp = title_info.getTipps().find { def the_tipp ->
                // Filter tipps for matching pkg and platform.
                boolean matched = the_tipp.pkg == pkg
                matched = matched && the_tipp.hostPlatform == platform_info
                matched
              }
            }

            // Create or update the tipp.
            if ( !tipp ) {
              log.debug("Create new tipp")
              // tipp = new TitleInstancePackagePlatform(tipp_values)
              tipp = TitleInstancePackagePlatform.tiplAwareCreate(tipp_values)
            }
            else {
              // We have a TIPP (only incremental would result in this).
              log.debug("TIPP already present, attempting update");

              // Remove from the list.
              getPackageTipps(old_tipps, pkg_name, pkg).remove(tipp.id)

              // Set all properties on the object.
              tipp_values.each { prop, value ->
                // Only set the property if we have a value.
                if (value != null && value != "") {
                  tipp."${prop}" = value
                }
              }
            }

            // Add each TIPP custom property in turn.
            gokb_additional_tipp_props.each { apd ->
              tipp.appendToAdditionalProperty(
                  apd.name.toLowerCase(), jsonv(datarow.cells[apd.col])
                  )
            }

            log.debug("Save tipp");
            
            // Need to ensure everything is saved.
            tipp.save(failOnError:true, flush:true)
            
            // If we have them we should compare TIPP dates with those of the TI.

            // Tipp start date should not be before publishedFrom date on the title
            boolean date_diff
            Date tipp_date = tipp.startDate
            Date ti_date = title_info.publishedFrom
            if (tipp_date != null && ti_date != null) {
              // Compare the title date to the tipp date. It should be before - so the result should be positive or zero
              int diff = ti_date.compareTo(tipp_date)
              date_diff = (diff > 0)
              preDates = (date_diff ? tipp.id : -1)
            }

            // Tipp end date should not be after the publishedTo date on the title
            tipp_date = tipp.endDate
            ti_date = title_info.publishedTo
            if (tipp_date != null && ti_date != null) {
              int diff = ti_date.compareTo(tipp_date)
              date_diff = (diff < 0)
              postDates = (date_diff ? tipp.id : -1)
            }

            if (preDates > -1) {
              // Raise end date conflict.
              ReviewRequest.raise(
                  TitleInstancePackagePlatform.get(preDates),
                  "TIPP for \"${title_info.name}\"(${title_info.id}) start date(${tipp.startDate}) pre-dates that of the related Title(${title_info.publishedFrom})",
                  "The TIPP declares a start date that occurs before the start date of its title. Please review the dates.",
                  user, project
                  )
            }

            if (postDates > -1) {
              ReviewRequest.raise(
                  TitleInstancePackagePlatform.get(postDates),
                  "TIPP for \"${title_info.name}\"(${title_info.id}) end date (${tipp.endDate}) post-dates that of the Title (${title_info.publishedTo})",
                  "The TIPP declares an end date that occurs after the end date of its title. Please review the dates.",
                  user, project
                  )
            }

          } else {

            // Skip this row. Need to log this and save against the project.
            log.debug("Row has been skipped as the data needs to be rectified in the system before it can be ingested.")

            // We store a hash of title joined with package. This isn't ideal.
            // TODO:Review this.

            def val = (
                (getRowValue(datarow, col_positions, PUBLICATION_TITLE) ?: "") +
                (getRowValue(datarow, col_positions, PACKAGE_NAME) ?: "")
                )

            skipped_titles << val.toString()
          }

        }
        catch ( Exception e ) {
          log.error("Row level exception",e)
          result.messages.add([text:"Problem processing row ${e}"])

          // Rollback the transaction.
          status.setRollbackOnly()
          return false
        }
      
      }
    }
    
    true
  }

  /**
   *  Ingest a parsed project. 
   *  @param project_data Parsed map of project data
   */
  def ingest(project_data, project_id, boolean incremental = true, user_id = null, job = null) {
    
    // Return result.
    def result = [
      "status"    : (project_data ? true : false),
      "messages"  : []
    ]
    
    final def recon_data = project_data?.recon

    // Skipped titles.
    Set<String> skipped_titles = []
    try {
      log.debug("Ingest")
  
      // Load the user.
      User user = User.get(user_id)

      // Set the status of this project.
      updateProjectStatus(project_id, 0, RefineProject.Status.INGESTING)
      job?.setProgress(0)

      // Track the old tipps here.
      final Map<String, Set<Long>> old_tipps = [:]

      // Track the packages in need of retiring here.
      final Map<String, Long> retire_packages = [:]

      // Ignore the case of the map key that is used to store the field positions.
      final CaseInsensitiveMap col_positions = [:]
      final def identifiers = []
      final def gokb_additional_tipp_props = []
      final def gokb_additional_ti_props = []

      // Create a new transaction for data examination.
      for (cd in project_data.columnDefinitions) {

        // Column name.
        def cn = cd.name

        if (cn) {
          // Add to column positions
          col_positions[cn] = cd.cellIndex;

          switch (cn.toLowerCase()) {
            case {it.startsWith(IDENTIFIER_PREFIX.toLowerCase())} :

            // Identifier.
              def idparts = cn.split(/\./)
              if ( idparts.length == 3 ) {
                // Add to the IDs.
                identifiers.add([type:idparts[2],colno:cd.cellIndex])
              }
              break

            case {it.startsWith(TI_FIELD_PREFIX.toLowerCase())} :

            // Additional property on TI
              def prop_name = cn.substring(TI_FIELD_PREFIX.length())
              gokb_additional_ti_props.add([name:prop_name, col:cd.cellIndex])
              break

            case {it.startsWith(TIPP_FIELD_PREFIX.toLowerCase())} :

            // Additional property on TIPP
              def prop_name = cn.substring(TIPP_FIELD_PREFIX.length())
              gokb_additional_tipp_props.add([name:prop_name, col:cd.cellIndex])
              break
          }
        }
      }
      log.debug("Using col positions: ${col_positions}, identifiers: ${identifiers}");
      log.debug("Addition TI props: ${gokb_additional_ti_props}");
      log.debug("Addition TIPP props: ${gokb_additional_tipp_props}");

      // Go through each row.
      int ctr = 0
      long total = project_data.rowData.size()
      
      // We want to handle a chunk of rows at a time.
      int chunk_size = 100
      int chunk_count = (total / chunk_size) + (total % chunk_size == 0 ? -1 : 0)

      log.debug("Processing ${chunk_count} chunks");
      
      for (chunk_ctr in (0..chunk_count)) {

        log.debug("process chunk ${chunk_ctr}");

        int end = ((chunk_ctr + 1) * chunk_size)
        end = (end > total ? total : end) - 1
        
        // Get a sub list of the data.
        def chunked_rows = project_data.rowData[(chunk_ctr * chunk_size)..end]
        
        for (datarow in chunked_rows) {
          
          RefineProject.withNewSession { ses ->
            // Add each row to the database.
            log.debug("Row ${ctr} ${datarow}")
            if ( !addDatatRow(result, project_id, incremental, col_positions, identifiers, gokb_additional_ti_props, gokb_additional_tipp_props, datarow, old_tipps, retire_packages, skipped_titles, user, recon_data) ) {
              log.error("\n\n\n***** There were row level exceptions *****\n\n\n");
            }
            log.debug("Row processing complete ${ctr}");
          }
  
          ctr ++
          
          if (ctr % 25 == 0) {
            // Every chunk of records we update the progress.
            log.debug("Chunk complete, update status ${ctr} ${total}");
            updateProjectStatus(project_id, (ctr / total * 100) as int, RefineProject.Status.INGESTING)
            log.debug("Update progress");
            job?.setProgress((ctr / total * 100) as int)
            log.debug("Chunk processing complete ${ctr}");
          } 
        }
      }

      // Read in the project.
      RefineProject project = RefineProject.get(project_id)

      // Handle none-present TIPPs
      handleNonePresentTipps(old_tipps, user, project)

      // If any rows with data have been skipped then we need to set them against the,
      // project here, for reporting back into refine.
      if (skipped_titles.size() > 0) {

        // Partially ingested
        project.setProjectStatus (RefineProject.Status.PARTIALLY_INGESTED)

        // Update the skipped rows and the progress.
        project.getSkippedTitles().addAll(skipped_titles)

      } else {

        // Set to ingested.
        project.setProjectStatus (RefineProject.Status.INGESTED)
      }

      log.debug("Updating job progress to 100 percent");
      // Update the progress.
      project.progress = 100
      job?.setProgress(100)

      // Save the project.
      project.save(failOnError:true, flush:true)
    }
    catch ( Exception e ) {
      def project_info = RefineProject.get(project_id)
      log.error("Problem processing project ingest.",e);
      result.messages.add([text:"Problem processing project ingest. ${e}"])
      project_info.progress = 100;
      project_info.setProjectStatus (RefineProject.Status.INGEST_FAILED)
      project_info.save(failOnError:true);
      // ToDo: Steve.. can you figure out a way to log the exception and pass it back to refine?
    }
    finally {
      log.debug("Ingest process completed");
    }

    log.debug("Ingest returning");

    result
  }

  def getRowRefdataValue (ref_cat, datarow, col_positions, colname) {

    // Read in the value.
    String value = getRowValue (datarow, col_positions, colname)

    // We should return null if a blank value has been supplied,
    // and the default value will be used instead.
    if (value == null || value.trim() == "") return null

    // lookup or create the value.
    RefdataCategory.lookupOrCreate(ref_cat, value)
  }

  def getRowValue(datarow, col_positions, colname, recon_data = null) {
    RefineUtils.getRowValue(datarow, col_positions, colname, recon_data)
  }

  def jsonv(v, recon_data = null) {
    RefineUtils.jsonv(v, recon_data)
  }

  /**
   *  Read an uploaded refine .tar.gz file, uncompress and create a map containing all the data. This is in memory,
   *  but our package files should never be large enough to cause a problem.
   *  @param zipFilename .tar.gz file to extract.
   *  @return Map containing parsed project data
   */
  def extractRefineproject(String zipFilename) {
    def result = [:]

    try {
      def full_filename = grailsApplication.config.project_dir + zipFilename

      log.debug("Extract ${full_filename}");

      FileInputStream fin = new FileInputStream(full_filename);
      GzipCompressorInputStream gzIn = new GzipCompressorInputStream(fin);
      TarArchiveInputStream tin = new TarArchiveInputStream(gzIn)
      TarArchiveEntry ae = tin.getNextTarEntry()
      while ( ae ) {
        log.debug("Processing archive entry: ${ae} ${ae.name} isFile:${ae.isFile()}");
        switch ( ae.name ) {
          case 'metadata.json':
            log.debug("Handle metadata");
            
            def json = new JsonSlurper()
            def bai = new ByteArrayOutputStream()
            int bytes_to_read = ae.getSize()
            byte[] buffer = new byte[4096]
            while (bytes_to_read) {
              int bytes_read = tin.read(buffer,0,4096)
              log.debug("Copying ${bytes_read} bytes to byte array");
              bai.write(buffer, 0, bytes_read)
              bytes_to_read -= bytes_read
            }
          
            result.metadata = json.parseText(bai.toString());
            break;
          case 'data.zip':
            def temp_data_zipfile
            try {
              log.debug("Handle Data.. create zipfile. need to copy ${ae.getSize()} bytes from tin to a buffer and re-read as a zip file");

              // Copy bytes from tar stream into temp zip file
              temp_data_zipfile = File.createTempFile('gokb_','_refinedata.zip',null)
              FileOutputStream fos = new FileOutputStream(temp_data_zipfile);
              int bytes_to_read = ae.getSize()
              byte[] buffer = new byte[4096]
              while (bytes_to_read) {
                int bytes_read = tin.read(buffer,0,4096)
                log.debug("Copying ${bytes_read} bytes to temp file");
                fos.write(buffer, 0, bytes_read)
                bytes_to_read -= bytes_read
              }
              fos.flush()
              fos.close();

              extractRefineDataZip(temp_data_zipfile, result)
            }
            finally {
              if ( temp_data_zipfile ) {
                try {
                  temp_data_zipfile.delete();
                }
                catch ( Throwable t ) {
                }
              }
            }
            break;
          default:
            break;
        }

        ae = tin.getNextTarEntry()
      }
      tin.close();
      gzIn.close();
      fin.close();
    }
    catch ( Exception e ) {
      log.error("Unexpected error trying to extract refine data.",e);
      e.printStackTrace();
      return null
    }

    result
  }

  def extractRefineDataZip (def zip_file, def result) {

    // Open temp zip file as a zip object
    if ( zip_file ) {
      java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip_file)
      
      try {
        log.debug("Getting data.txt")
        java.util.zip.ZipEntry ze = zf.getEntry('data.txt')
        if ( ze ) {
          log.debug("Got data.txt")
          result.processingCompleted = false;
          processData(result, zf.getInputStream(ze));
          
          // Now we should try and get any pool data also.
          ze = zf.getEntry('pool.txt')
          if (ze) {
            processReconData(result, zf.getInputStream(ze));
          } else {
            log.error("Problem getting recon.txt");
          }
        }
        else {
          log.error("Problem getting data.txt");
        }
      } finally {
        zf.close()
      }
    }
    else {
      log.debug("extractRefineDataZip: zip file is null");
    }

    result
  }
  
  def processReconData(result, is) {
    log.debug("processing refine pool.txt");
    def bis = new BufferedReader(new InputStreamReader(is));
    
    // First line is the refine version
    String refineVersion = bis.readLine()
    
    if (result.refineVersion == refineVersion) {
      log.debug("Reported refine version matches data (${refineVersion}).")
    } else {
      log.debug("Differeing refine version (Data: ${result.refineVersion} / Recon $refineVersion}).")
    }
    
    // Header info
    result.reconCount=Integer.decode(valuePart(bis.readLine()))
    log.debug ("Found ${result.reconCount} recon entries")
    
    // Our recon map.
    result.recon = [:]
    
    for (int i=0; i<result.reconCount; i++) {
      def jsonSlurper = new JsonSlurper()
      def jso = jsonSlurper.parseText(bis.readLine())
      
      result.recon["${jso['id']}"] = jso
    }
    
    if (bis.readLine() != null) {
      log.debug("Reported recon count not correct.")
    }
    
    bis.close()
  }

  def processData(result, is) {
    log.debug("processing refine data.txt");
    def bis = new BufferedReader(new InputStreamReader(is));

    // First line is the refine version
    result.refineVersion = bis.readLine()
    log.debug("Reported refine version is ${result.refineVersion}");

    // 2.5

    // Header info
    result.columnModel=valuePart(bis.readLine())
    result.maxCellIndex=valuePart(bis.readLine())
    result.keyColumnIndex=valuePart(bis.readLine())
    result.columnCount=Integer.decode(valuePart(bis.readLine()))

    log.debug("Setting up column definitions");
    result.columnDefinitions = []
    for ( int i=0; i<result.columnCount; i++ ) {
      log.debug("Reading column ${i}");
      result.columnDefinitions.add(JSON.parse(bis.readLine()));
    }

    result.columnGroupCount = Integer.decode(valuePart(bis.readLine()))
    for (int i=0; i<result.columnGroupCount; i++ ) {
      // Skipping column group info
      def row = bis.readLine()
    }

    if ( bis.readLine() != '/e/' ) {
      log.error("Unexpected row!");
    }

    result.history = valuePart(bis.readLine())

    result.pastEntryCount = Integer.decode(valuePart(bis.readLine()))
    result.pastEntryList = []
    for (int i=0; i<result.pastEntryCount; i++ ) {
      // Skipping past entry
      result.pastEntryList.add(JSON.parse(bis.readLine()));
    }

    result.futureEntryCount = Integer.decode(valuePart(bis.readLine()))
    for (int i=0; i<result.futureEntryCount; i++ ) {
      // Skipping future entry
      def row = bis.readLine()
    }

    if ( bis.readLine() != '/e/' ) {
      log.error("Unexpected row!");
    }
    else {
      log.debug("Encountered second /e/ in correct position, in to overlay model and rowCount");
    }

    def next_param = bis.readLine()
    if ( next_param.startsWith('overlayModel') ) {
      result.overlayModel = valuePart(bis.readLine())
      next_param = bis.readLine()
    }

    if (  next_param.startsWith('rowCount') ) {
      result.rowCount = Integer.decode(valuePart(next_param))
    }
    else {
      log.error("expected row count, got row ${next_param}");
      result.rowCount = 0;
    }

    result.rowData = []

    for (int i=0; i<result.rowCount; i++ ) {
      def row = bis.readLine()
      // log.debug("Row ${i}");
      result.rowData.add(JSON.parse(row))
      // Skipping row
    }

    result.processingCompleted = true;

    bis.close();
  }

  def valuePart(s) {
    int equalsPos = s.indexOf('=');
    def result = s.substring(equalsPos+1, s.length());
    log.debug("valuePart(${s})=${result}");
    result;
  }

  def cleanUpGorm() {
    log.debug("Clean up GORM");

    // Get the current session.
    def session = sessionFactory.currentSession

    // flush and clear the session.
    session.flush()
    session.clear()

    // Clear the property instance map.
    propertyInstanceMap.get().clear()
  }

  /**
   * Dates from refine are no supplied in the ISO format.
   * @param datestr
   * @return standard java Date
   */
  Date parseDate(String datestr) {

    // Parse the date.
    Date the_date = null

    if (datestr && datestr.trim() != "") {

      log.debug ("Trying to parse date from ${datestr}")
      try {
        the_date = ISODateParser.parseLocalDateTime(datestr).toDate()

      } catch (Throwable t) {

        log.debug ("Error parsing date. '${datestr}' resulted in null date.")

        // Ensure null date.
        the_date = null
      }
    }

    the_date

    //	def parsed_date = null;
    //	if ( datestr && ( datestr.length() > 0 ) )
    //	  for(Iterator<SimpleDateFormat> i = possible_date_formats.iterator(); ( i.hasNext() && ( parsed_date == null ) ); ) {
    //		try {
    //		  parsed_date = i.next().parse(datestr.replaceAll('-','/'));
    //		}
    //		catch ( Exception e ) {
    //		}
    //	  }
    //	parsed_date
  }

  def extractRules(parsed_data, project) {
    if ( ( project.provider != null ) &&
    ( parsed_data != null ) ) {
      log.debug("extracting rules, provider is ${project.provider.id}");
      def provider = Org.get(project.provider.id)
      // finally, rules extraction
      parsed_data.pastEntryList.each { r ->
        log.debug("Consider rule: ${r}");
        def fingerprint = null
        def scope = "provider" // default scope to provider
        if ( r.operation ) {
          switch ( r.operation.op ) {
            case 'core/column-rename':
            // Column Rename
              fingerprint = "${r.operation.op}:${r.operation.oldColumnName}"
              break;
            case 'core/text-transform':
            case 'core/mass-edit':
              fingerprint = "${r.operation.op}:${r.operation.columnName}"
              break
            default:
              log.debug("Generic rules handling");
              break;
          }
        }

        if ( fingerprint ) {
          def rule_in_db = Rule.findByScopeAndProviderAndFingerprint('provider',provider,fingerprint)
          if ( !rule_in_db ) {
            rule_in_db = new Rule(
                scope:scope,
                provider: provider,
                fingerprint: fingerprint,
                ruleJson: "${r.operation as JSON}",
                description: "${r.operation.description}"
                )
            if ( rule_in_db.save(failOnError:true) ) {
            }
            else {
              rule_in_db.errors.each { e ->
                log.error("${e}");
              }
            }
          }
        }
      }
    }
    else {
      log.error("Provider or parsed data not set, cannot establish rules!");
    }
  }

  /**
   *  Look at the project header, extract fingerprints, try to find matching rules that could be applied
   *  to this upload
   */

  def findRules(parsed_project_file, provider) {

    def result = []
    def extracted_fingerprints = []

    // Iterate over columns
    parsed_project_file.columnDefinitions.each { cd ->
      log.debug("Considering column ${cd.name}");
      def fingerprint="core/column-rename:${cd.name}"
      extracted_fingerprints.add(fingerprint)
    }

    extracted_fingerprints.each { fp ->
      if (provider) findRulesByFingerprint('provider',provider,fp,result)
      findRulesByFingerprint('global',null,fp,result)
    }

    result
  }

  def findRulesByFingerprint(scope,provider,fp,ruleset) {
    def rule_in_db
    if ( provider ) {
      log.debug("Looking for rules ${scope}:${provider}:${fp}")
      rule_in_db = Rule.findByScopeAndProviderAndFingerprint(scope,provider,fp)

    } else {
      rule_in_db = Rule.findByScopeAndFingerprint(scope,fp)
    }
    if ( rule_in_db ) {
      log.debug("got matching rule ${rule_in_db}");
      ruleset.add(rule_in_db)
    }
  }

  def getOrCreatePackage(String identifier, project_id) {

    log.debug("Get or create the package ${identifier}");

    // Read the project.
    RefineProject project = RefineProject.get(project_id)

    // The provider.
    Org provider = project.provider

    // Try and find a package for the provider with the name entered.
    log.debug("identifier is ${identifier}")
    def pkg = null;

    def q = ComboCriteria.createFor(Package.createCriteria())
    def pkg_list = q.list {
      and {
        q.add ("ids.namespace.value", "eq", 'gokb-pkgid')
        q.add ("ids.value", "eq", identifier)
      }
    }

    log.debug("Lookup of package with identifier ${identifier} returns ${pkg_list.size()} entries");

    if ( pkg_list.size() == 0 ) {
      log.debug("New package")
    }
    else if (  pkg_list.size() == 1 ) {
      log.debug("Identified a package")
      pkg = pkg_list.get(0);
    }
    else {
      throw new Exception("Multiple packages with specififed identifier. This should never happen");
    }

    // Package found?
    if (!pkg) {

      log.debug("New package with identifier ${identifier} for ${provider.name}");

      // Create a new package.
      pkg = new Package(
          provider:   (provider)
          )

      // Add a new identifier to the package.
      Identifier new_identifier = Identifier.lookupOrCreateCanonicalIdentifier('gokb-pkgid', identifier)
      log.debug("create new combo to link package to identifier. pkg=${pkg.id}, new_id:${new_identifier.id}");
      pkg.ids.add (new_identifier)

      // Need to set the name to mirror the Identifier.
      pkg.name = new_identifier.value
      pkg.lastUpdateComment = "Created by Refine ingest on ${new Date()}"
      pkg.lastUpdatedBy = user
    }
    else {
      pkg.lastUpdateComment = "Updated by Refine ingest on ${new Date()}"
      log.debug("Got existing package ${pkg.id}");
      pkg.lastUpdatedBy = user
    }

    // Set the latest project.
    pkg.setLastProject(project)


    // Save and return
    pkg.save(failOnError:true, flush:true)
    pkg
  }

  private static Set<Long> getPackageTipps (final Map<String, Set<Long>> packageTippLists, String pkgName, Package pkg) {

    // Get from the map.
    Set<Long> tipps = packageTippLists.get(pkgName)

    // If it's null then we haven't initialised it yet.
    if (tipps == null) {
      LogFactory.getLog(this).debug("Loading tipps for package ${pkgName} from db and caching locally");
      tipps = []
      for (def tipp: pkg.getTipps()) {
        tipps << tipp.id
      }

      // Ensure we add to the map.
      packageTippLists.put(pkgName, tipps)
    }

    // Return the TIPPs.
    tipps
  }
}
