<!DOCTYPE html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!--> <html lang="en" class="no-js"><!--<![endif]-->
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <title><g:layoutTitle default="GoKB"/></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon">
    <g:layoutHead/>
    <r:layoutResources />
  </head>
  <body>
    <div class="navbar navbar-fixed-top">
      <div class="navbar-inner navbar-public">
        <div class="container">
          <div class="nav-collapse">
            <ul class="nav">
              <li>
                <g:link controller="home" action="index">Home</g:link>
              </li>
            </ul>           
          </div>
        </div>           
      </div>
    </div>

    <g:layoutBody/>

    <g:javascript library="application"/>
    <r:layoutResources />
  </body>
</html>
