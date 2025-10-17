# Cascading Delete in Torch

Cascading delete is a critical feature for the Torch FHIR data extraction system that ensures referential integrity
and proper cleanup when resources are removed during the extraction process.
It is done in a queue based bidirectional manner, in the directions (parent → child) and (child → parent).

## Problem Context

In FHIR systems, resources often reference each other through various relationship types:

- **Patient → Observation, DiagnosticReport, Condition, etc.**
- **Encounter → Observation, Procedure, MedicationAdministration**
- **DiagnosticReport → Observation (results)**
- **Specimen → Observation (derived-from)**

When resources fail validation checks (such as must-have conditions, consent requirements, or profile compliance),
they need to be removed from the extraction batch.
However, simply removing a resource can break referential integrity and leave "orphaned" resources that reference the
deleted resource.

## Parent Child Relationships

A parent-child relationship in Torch is defined by the extraction process.
A parent resource is one that is extracted first and may have child resources that depend on it (i.e. a referential
chain).

- **Parent**: A resource that is referencing another reference.
- **Child**: A resource that is referenced by a parent resource, such as a Patient or Encounter resource (child) being referenced by an Observation (parent).

## Goal of Cascading Delete

Remove all resources whose referential chains are broken due to the deletion of a resource.

## Approach

1. **Identify Parent-Child Relationships**:
   During the reference resolve process, Torch identifies parent-child relationships based on the FHIR resource references in
   the reference resolve.
2. **Mark Resources for Deletion**:
   When a resource fails validation checks (consent,must-have), it is marked for deletion.
3. **Bi-directional Deletion**
    - **Parent to Child**:
        - If a parent resource is marked for deletion, all connections to its child resources are removed.
        - If a child has no other parent, it is also marked for deletion.
        - Directly loaded resources have a parent by default, so they are not deleted by this step.
    - **Child to Parent**:
    - If a child resource is marked for deletion, it removes its reference to the parent.
    - If the parent has no other children:
        - the reference has to be checked for must-have condition
      - if the parent has no other children, and it was a must-have reference it is marked for deletion.
4. **Cleanup**:
    - After all resources are processed, Torch performs a cleanup step to remove all resources marked for deletion.
    - This ensures that no orphaned resources remain in the system.

## Cascading Delete Referential Chain Examples

✅ = kept
❌ = deleted
★ = must-have reference

### Example 1: Child Deleted, Parent Kept

```
Patient/123 
 └─ Encounter/abc 
     ├─ Observation/obs1  ❌ deleted
     └─ Observation/obs2★ 
 ```

```
Patient/123 ✅ kept
 └─ Encounter/abc ✅ kept
     ├─ Observation/obs1  ❌ deleted
     └─ Observation/obs2★ ✅ kept
 ```

### Example 2: Must Have Violation Results in Parent Deleted

```
Patient/456
 └─ Encounter/def 
     ├─ Observation/obs3  
     └─ Observation/obs5★ ❌ deleted
 ```

```
Patient/456 ✅ kept
 └─ Encounter/def ❌ deleted
     ├─ Observation/obs3  ❌ deleted
     └─ Observation/obs5★ ❌ deleted
 ```

### Example 3: One Parent Deleted results in all children kept

```
Patient/456
├─ Encounter/def ❌ deleted
│ ├─ Observation/obs3 
│ └─ Observation/obs5 
└─ Encounter/xyz
  ├─ Observation/obs3 
  └─ Observation/obs5 
 ```

```
Patient/456 ✅ kept
├─ Encounter/def ❌ deleted
│ ├─ Observation/obs3 ✅ kept
│ └─ Observation/obs5 ✅ kept
└─ Encounter/xyz ✅ kept
  ├─ Observation/obs3 ✅ kept
  └─ Observation/obs5 ✅ kept
 ```

### Example 3: All Parents Deleted results in all children kept

```
Patient/456 ✅ kept
├─ Encounter/def ❌ deleted
│ ├─ Observation/obs3 
│ └─ Observation/obs5 
└─ Encounter/xyz ❌ deleted
  ├─ Observation/obs3 
  └─ Observation/obs5 
 ```

```
Patient/456 ✅ kept
├─ Encounter/def ❌ deleted
│ ├─ Observation/obs3 ❌ deleted
│ └─ Observation/obs5 ❌ deleted
└─ Encounter/xyz ❌ deleted
  ├─ Observation/obs3 ❌ deleted
  └─ Observation/obs5  ❌ deleted
 ```
