Eclipse Postfix Code Completion Plugin
======================================

Extension to the Eclipse template system to support postfix code completion

Please see https://bugs.eclipse.org/bugs/show_bug.cgi?id=433500

In order to try out the feature, add https://raw.githubusercontent.com/trylimits/Eclipse-Postfix-Code-Completion/master/org.eclipse.jdt.postfixcompletion.updateSite/target/site/ to the
p2 sites of your Luna Eclipse installation and install both features in the category /Postfix code completion/.

The plug-in installs the following Postfix Code Completion templates by default:

- .beg
- .cast
- .constpriv
- .constpub
- .dowhile
- .else
- .field
- .for
- .fori
- .forr
- .nnull
- .null
- .sif
- .snnull
- .snull
- .sysout
- .throw
- .var
- .while
- .withinregion

Demonstration
-------------

<h3>.var template</h3>

The .var template allows you to assign local variables out of an already typed expression.

![Demo of var template](/demonstration/var.gif?raw=true)

<h3>.field template</h3>

Similar to the .var template, the .field template allows to assign an expression to a newly defined field.

![Demo of field template](/demonstration/field.gif?raw=true)

<h3>.for template</h3>

The .for template shows up in the content assist if and only if an expression resolves to a java.lang.Iterable or array and allows to create a for-loop out of the expression.

![Demo of for template](/demonstration/for.gif?raw=true)

<h3>.const template</h3>

Postfix Code Completion templates also allow to directly extract constants while coding, without using your mouse or scroll up to the member declaration area of the class. The templates .constpub and .constpriv extract a public, respectively a private constant.

![Demo of const template](/demonstration/const.gif?raw=true)
