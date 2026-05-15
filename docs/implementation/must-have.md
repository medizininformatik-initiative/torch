## Must-Have Checking in TORCH Pipeline

The must-have check ensures that extracted data meets the criteria defined in the CRTDL.
Required elements are declared in the CRTDL using the `mustHave` flag on individual attributes.

Must-have checking is performed after resources are loaded from the FHIR server and again during the reference-resolve step.

## When `mustHave` affects patient filtering

An attribute group has an active must-have constraint only when **at least one user-declared attribute** in that group carries `mustHave: true`.

When an attribute group has an active must-have constraint:

- A resource satisfies the group's must-have requirement if its `meta.profile` matches the group's `groupReference` **and** every `mustHave: true` attribute has a value — i.e., the FHIRPath expression for that attribute returns at least one result (including values present via extensions).
- A patient is **retained** in the result if at least one resource of that group satisfies the requirement.
- A patient is **dropped** from the result if none of their resources satisfy the requirement for that group.

When a group has no user-declared `mustHave: true` attribute, the group is optional: all patients are retained regardless of whether they have any resources for that group.

### Standard attributes and `mustHave`

Standard attributes (`id`, `meta.profile`, and patient compartment references such as `subject`) are always injected by TORCH for functional reasons — they are required for internal processing, referential integrity, and profile-aware handling.

Standard attributes are **technically enforced at pipeline level**: resources that lack `id` or `meta.profile` are filtered out before must-have checking runs. Because of this, standard attributes are always generated with `mustHave: false` and never count towards the group's must-have constraint. They are never the reason a patient is dropped.

The `mustHave` flag is purely user-driven: it expresses a **data quality requirement** — that a certain clinical field must have a value for the resource to be meaningful to the requester.

Declaring a standard attribute with `mustHave: true` in the CRTDL is a validation error, because standard attributes are not user-controlled data quality requirements and cannot be used to drive patient filtering.
Declaring a standard attribute with `mustHave: false` is redundant and silently skipped.

## 1. Field Must-Have Check

Based on FHIRPath expressions, the must-have check verifies that all `mustHave: true` attributes have a value in a resource.
If a resource fails the check, it is still loaded but not counted towards satisfying the group's must-have requirement.

## 2. Reference Resolve

A reference attribute in the CRTDL can be marked as `mustHave: true` and linked to other attribute groups that define requirements for the referenced resource.

During [Reference Resolve](reference-resolve.md), TORCH checks both that the reference can be resolved and that the resolved resource passes the must-have check for the linked attribute groups.

If any must-have condition is violated, the referencing resource is marked for deletion and removed during the [cascading delete](cascading-delete.md) step.
