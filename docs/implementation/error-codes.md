# Error Codes <Badge type="warning" text="Since 1.0.0-alpha.13"/>

The following table lists the error codes used in the TORCH system.
Each error code is unique and provides information about the type of error, its severity, and recommended actions for users and developers.

Error Code – A unique identifier for the error, structured as MODULE_SUBMODULE_NUMBER.

Meaning – A brief description of the error and its context.

Severity – Indicates the impact of the error (Info, Warning, Error, Critical).

User Action / Fix – Recommended steps the end user can take to resolve the issue.

Developer Notes – Additional technical information for developers to troubleshoot and address the error.

This table is intended to support both end users and developers in quickly identifying, diagnosing, and resolving issues encountered in the system.

| Error Code             | Meaning                                                                          | Severity | User Action / Fix                                                                         | Developer Notes                                                                                       |
|------------------------|----------------------------------------------------------------------------------|----------|-------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|
| RESULT_0001            | Could not create results directory                                               | Error    | Check directory path and permissions                                                      | Thrown when `Files.createDirectories` fails in constructor                                            |
| RESULT_0002            | Results directory is not writable                                                | Error    | Adjust filesystem permissions                                                             | Triggered when directory exists but is not writable                                                   |
| RESULT_0003            | Error reading job directory                                                      | Warning  | None; system will skip unreadable directory                                               | Happens when listing or reading a job directory fails                                                 |
| RESULT_0004            | Error loading existing results                                                   | Warning  | None; system continues but previous jobs may be missing                                   | Thrown when scanning the results root directory fails                                                 |
| RESULT_0005            | Async write failed for job error file                                            | Error    | Retry operation or inspect logs                                                           | Indicates failure inside async `saveErrorToJson` write operation                                      |
| RESULT_0006            | Failed to load bundles for job                                                   | Warning  | Inspect job directory structure                                                           | Thrown when listing files in a job directory for output generation fails                              |
| CONSENT_VALIDATOR_0001 | A Resource was loaded that does not belong to any Patient belonging to the batch | Warning  | Manually check check if resource is referenced by a resource belonging to another patient | Thrown due to referenced Patient not being part of the batch from which the resource was loaded       | 
| CONSENT_VALIDATOR_0002 | Consent check was done on an unsupported ResourceType                            | Warning  | Contact developers if it is a patient resource type that should be supported              | Thrown due to resource type not being in the resourceType to field mapping for consent                |
| FHIR_CONTROLLER_0001   | Bad Request received                                                             | Error    | Fix Parameters File or CRTDL                                                              | Thrown due to input validation or parsing fail                                                        |
| FHIR_CONTROLLER_0002   | Some internal server error i.e. the complete extraction crashed                  | Error    | Inspect logs and contact developers for potential bugs                                    | Thrown due to resource type not being in the resourceType to field mapping for consent                |
| FHIR_CONTROLLER_0003   | Trying to look up error status failed                                            | Error    | Retry operation or inspect logs                                                           | Thrown due error job status existing but no error operation outcome found in dir                      |
| DATASTORE_0001         | 	FHIR server returned an OperationOutcome during search	                         | Error	   | Inspect OperationOutcome details and fix query parameters	                                | Logged when executing .search() and an OperationOutcome is returned instead of a Bundle               |
| DATASTORE_0002         | 	Error executing resource query (search)	                                        | Error	   | Retry or inspect server availability and feasibility query syntax	                        | Logged in .search() in doOnError, wraps any exception during search                                   |
| DATASTORE_0003	        | Error executing a transaction                                                    | 	Error   | 	Retry; if persistent, review the transaction bundle                                      | 	Logged in .transact() when POST / fails                                                              |
| DATASTORE_0004         | 	Error evaluating a Measure                                                      | 	Error   | 	Inspect the Measure parameter set; retry; contact developers if persists	                | Logged in .evaluateMeasure() when sync or async-polling evaluation fails                              |
| DATASTORE_0005	        | Unexpected resource type returned during polling                                 | 	Error   | 	Contact developers; fix FHIR endpoint returning unexpected structure	                    | Happens in async poll response mapping in .pollStatus() when resource is not MeasureReport or unknown 
| DATASTORE_0006	        | Missing Content-Location header for async operation                              | 	Error   | 	Retry operation; report misconfigured FHIR server                                        | 	Thrown in MissingContentLocationException when server returns 202 without a status URL               |
| DATASTORE_0007	        | Async polling missing response or outcome field	                                 | Error	   | Contact developers; server likely misbehaving	                                            | From .pollStatus() when bundle entry response fields are unexpectedly missing                         |
| DATASTORE_0008	        | Error deserializing FHIR JSON payload	                                           | Error    | 	Verify FHIR server output; ensure correct FHIR version	                                  | Thrown from parseResource() on DataFormatException                                                    |
| DATASTORE_0009         | 	Unexpected resource type in batch search response                               | Warning  | 	None required; system ignores unexpected resources                                       | 	Logged in extractResourcesFromBundle() when resource is not a Bundle                                 |

