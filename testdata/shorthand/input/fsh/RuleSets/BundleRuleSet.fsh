RuleSet: AddBundleEntry(resource, base)
* entry[+].fullUrl = "https://www.medizininformatik-initiative.de/{base}/{resource}"
* entry[=].resource = {resource}
* entry[=].request.method = #PUT
* entry[=].request.url = "{base}/{resource}"