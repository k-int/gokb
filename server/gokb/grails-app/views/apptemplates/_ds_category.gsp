
<form class="form" role="form">
  <div class="form-group">
    <label for="code"><g:annotatedLabel owner="${d}" property="code">Category Code</g:annotatedLabel></label>
    <g:xEditable owner="${d}" field="code" class="form-control" />
  </div>

  <div class="form-group">
    <label for="description"><g:annotatedLabel owner="${d}" property="description">Category Description</g:annotatedLabel></label>
    <g:xEditable owner="${d}" field="description" class="form-control" />
  </div>
</form>

<g:if test="${d.id != null}">
  <table class="table table-striped">
    <thead>
      <tr>
        <th>Criterion Name</th>
        <th>Description</th>
        <th>Explanation</th>
        <th>Actions</th>
      </tr>
    </thead>
    <tbody>
      <g:each in="${d.criterion}" var="c"> 
        <tr>
          <td>${c.title}</td>
          <td>${c.description}</td>
          <td>${c.explanation}</td>
          <td></td>
        </tr>
      </g:each>
      <tr><td colspan=4"><h3>Quick add new criterion</h3></td></tr>
      <tr>
        <g:form controller="ajaxSupport" action="addToCollection">
          <input type="hidden" name="__context" value="${d.class.name}:${d.id}" /> 
          <input type="hidden" name="__newObjectClass" value="org.gokb.cred.DSCriterion" />
          <input type="hidden" name="__addToColl" value="criterion" />
          <input type="hidden" name="__recip" value="owner" />
          <td><input type="text" name="title" class="form-control" placeholder="Label for criterion"/></td>
          <td><input type="text" name="description"class="form-control"  placeholder="Short Description"/></td>
          <td><input type="text" name="explanation" class="form-control" placeholder="Explanation of criterion"/></td>
          <td><button type="submit" class="btn btn-success">Add</button></td>
        </g:form>
      </tr>
    </tbody>
  </table>
</g:if>
