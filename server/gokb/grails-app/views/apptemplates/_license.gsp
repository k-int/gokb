<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>${d.id ? d.getNiceName() + ': ' + (d.name ?: d.id) : 'Create New ' + d.getNiceName()}</h1>

<g:render template="kbcomponent" contextPath="../apptemplates" model="${[d:displayobj, rd:refdata_properties, dtype:'KBComponent']}" />

<g:if test="${d.id != null}">
  <dl class="dl-horizontal">

    <div class="control-group">
      <dt>License URL</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="url"/></dd>
    </div>

    <div class="control-group">
      <dt>License Doc</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="file"/></dd>
    </div>

   <div class="control-group">
      <dt>License Type</dt>
      <dd><g:xEditableRefData owner="${d}" field="type" config='License.Type' /></dd>
    </div>
  </dl>

  <g:if test="${((d.summaryStatement != null) && (d.summaryStatement.length() > 0 ) )}">
    <h4>Summary Of License</h4>
    ${d.summaryStatement}
  </g:if>


</g:if>


<script language="JavaScript" src="http://gokb.k-int.com/wz_tooltip.js"/>

<script language="JavaScript">
  $(document).ready(function() {

    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
