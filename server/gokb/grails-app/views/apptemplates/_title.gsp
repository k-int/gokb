<g:if test="${d.id != null}">
	<dl class="dl-horizontal">

		<g:if test="${d.variantNames}">
			<div class="control-group">
				<dt>Alternate Titles</dt>
				<dd>
					<table class="table table-striped table-bordered">
						<thead>
							<tr>
								<th>Variant Title</th>
								<th>Status</th>
								<th>Variant Type</th>
								<th>Locale</th>
							</tr>
						</thead>
						<tbody>
							<g:each in="${d.variantNames}" var="v">
								<tr>
									<td>
										${v.variantName}
									</td>
									<td><g:xEditableRefData owner="${v}" field="status"
											config='KBComponent.Status' /></td>
									<td><g:xEditableRefData owner="${v}" field="variantType"
											config='VariantNameType' /></td>
									<td><g:xEditableRefData owner="${v}" field="locale"
											config='Locale' /></td>
								</tr>
							</g:each>
						</tbody>
					</table>
				</dd>
			</div>
		</g:if>
		<g:if test="${d.publisher}">
			<div class="control-group">

				<dt>Publishers</dt>
				<dd>
					<table class="table table-striped table-bordered">
						<thead>
							<tr>
								<th>Publisher Name</th>
								<th>Relationship Status</th>
								<th>Publisher From</th>
								<th>Publisher To</th>
							</tr>
						</thead>
						<tbody>
							<g:each in="${d.getCombosByPropertyName('publisher')}" var="p">
								<tr>
									<td><g:link controller="resource" action="show"
											id="${p.toComponent.class.name}:${p.toComponent.id}">
											${p.toComponent.name}
										</g:link></td>
									<td>
										${p.status.value}
									</td>
									<td><g:xEditable class="ipe" owner="${p}"
											field="startDate" type="date" /></td>
									<td><g:xEditable class="ipe" owner="${p}" field="endDate"
											type="date" /></td>
								</tr>
							</g:each>
						</tbody>
					</table>
				</dd>
			</div>

		</g:if>
		<g:if test="${d.tipps}">
			<div class="control-group">
				<dt>Package Appearances</dt>
				<dd>
					<table class="table table-striped table-bordered">
						<thead>
							<tr>
								<th>Package</th>
								<th>Platform</th>
								<th>Start Date</th>
								<th>Start Volume</th>
								<th>Start Issue</th>
								<th>End Date</th>
								<th>End Volume</th>
								<th>End Issue</th>
								<th>Embargo</th>
							</tr>
						</thead>
						<tbody>
							<g:each in="${d.tipps}" var="tipp">
								<tr>
									<td><g:link controller="resource" action="show"
											id="${tipp.pkg.getClassName()+':'+tipp.pkg.id}">
											${tipp.pkg.name}
										</g:link></td>
									<td><g:link controller="resource" action="show"
											id="${tipp.hostPlatform.getClassName()+':'+tipp.hostPlatform.id}">
											${tipp.hostPlatform.name}
										</g:link></td>
									<td><g:formatDate
											format="${session.sessionPreferences?.globalDateFormat}"
											date="${tipp.startDate}" /></td>
									<td>
										${tipp.startVolume}
									</td>
									<td>
										${tipp.startIssue}
									</td>
									<td><g:formatDate
											format="${session.sessionPreferences?.globalDateFormat}"
											date="${tipp.endDate}" /></td>
									<td>
										${tipp.endVolume}
									</td>
									<td>
										${tipp.endIssue}
									</td>
									<td>
										${tipp.embargo}
									</td>
								</tr>
							</g:each>
						</tbody>
					</table>
				</dd>
			</div>
		</g:if>
	</dl>
</g:if>
<script language="JavaScript">
	$(document).ready(function() {

		$.fn.editable.defaults.mode = 'inline';
		$('.ipe').editable();
	});
</script>