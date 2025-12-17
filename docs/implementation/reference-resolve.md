## Reference Resolve

Torch needs to resolve references in the extracted data to ensure that all resources are correctly extracted and linked.
This is done in the **Reference Resolve** step of the Torch pipeline.

### Resolving References

Torch manages for each Attribute Group the Resources that have been assigned to it.
This assignment is called a ResourceGroup (e.g. AttributeGroup_Observation,Observation1).

Resolving  core resources and patient resources works the same in principle, but is a little more complex on patient resources.

#### Resolving Purely Core Resources
1. **Get all valid ResourceGroups**: Torch initially retrieves all ResourceGroups (RG's) that are valid for the current extraction.
2. **Extract References**: For each ResourceGroup, Torch extracts the references (as string) from the resources.
   - If the specified field for which referenced groups are defined is a reference, extract directly.
   - Otherwise, a recursive reference extract is done.
3. **Rearrange by linked group**: The unordered set of RG's is separated into collections containing all RG's for each linked group.
4. **Fetch references per linked group**: For each linked group, the unordered set of references are fetched from
the FHIRServer. There is one FHIRSearch per linked group (assuming they fit into one chunk). Since filters are defined
in an attribute group and all references of that query belong to the same linked group (i.e. the same attribute group),
they all have the same filter, which can thus be appended to the query.
5. **Cache new resources and mark missing resources**: The unordered set of fetched resources is put into the core bundle
to be retrieved by resource-ID later. Resources that are missing might either not exist on the FHIRServer (if there was
no referential integrity on the server) or - more probable - the referenced resource has not satisfied the filter of its
(linked) attribute group. In either case, missing references are marked as invalid for later use [[1](#notes)].
6. **Handle References**: Broad term for creating parent<->child relationship info for cascading delete, checking must-have
violations and accordingly marking resource attributes (RA's) and RG's as invalid [[2](#notes)].
7. **Recursively resolve references**: Steps 2 to 7 are done recursively, meaning that now the newly fetched resources
are resolved.

##### Notes:

\[1\]: If the referenced resource (call it ref-res) is missing at this referenced field (call it ref-1), it might still be fetched 
from another reference field (ref-2) and put into the core bundle. If the ReferenceHandler looked for ref-res in the bundle
to see if ref-1 is satisfied, then it would find ref-res and think ref-1 is satisfied, although it did not satisfy the filter
for ref-1. Therefore, the attribute of ref-1 is marked as invalid, making the ReferenceHandler later ignore ref-res when
handling ref-1.

\[2\]: In order to "handle" a "parent" RG, all of its references (i.e. of all of its linked groups) must have been 
fetched. Therefore, one must wait until all linked groups have been fetched, cached and marked as described in step 5 
before going on to check if the references of the parent RG are valid, i.e. "handling" the parent RG.


---

#### Resolving Patient Resources

Resolving patient resources means resolving references of resources that initially only come from resources in the patient
compartment.
When a core resource is referenced by a patient resource, this core resource is still handled in this procedure.

1. **Get all valid ResourceGroups**: Same as for core resources, but RG's are now ordered/ collected per patient.
2. **Extract References**: Same as for core resources (see above).
3. **Rearrange by linked group**: Same as for core resources, but with new complexity: information to patients is lost [[1](#notes-1)]
4. **Fetch references per linked group**: For each linked group, the unordered set of references are fetched from
   the FHIRServer. Filters are applied by the same principle as for core resources (see above).
5. **Cache new resources and mark missing resources**: The unordered set of fetched resources is put into the patient
bundles and core bundles. Since the newly fetched resources might belong to any patient (as described in [[1](#notes-1)]),
a helping structure was created before, mapping from reference to patient-ID's so each references it put into the correct
patient bundles (or the core bundle of the batch if the resource is a core resource). Same goes for attribute validity.
6. **Handle References**: Same as for core resources (see above). A helping structure is not needed in this step, because
the necessary resources and RG-validity was already set in the correct patient bundles in step 5. So when checking if
the original RG's, that should initially be resolved, are valid, the reference strings can directly be used to retrieve
the resources and RG-validity from the patient bundles.
7. **Recursively resolve references**: Same as for core resources (see above).


#### Notes:

[1]: References are collected patient-independent by linked group, meaning that one collection of linked-group references
can contain ref-1 and ref-2, where ref-1 is referenced by a resource of pat-1 and ref-2 is referenced by a resource of pat-2.

---

#### Detailed Example

* CRTDL (simplified):
```yaml
Attribute Groups:
  G1:
    - profile: MedicationAdministration
    - attribute-1:
      - path: MedicationAdministration.performer.actor
      - linkedGroup: linked-group-1    
    - attribute-2:
      - path: MedicationAdministration.encounter
      - linkedGroup: linked-group-2
  G2:
     - profile: Condition
     - attribute-1:
          - path: Condition.recorder
          - linkedGroup: linked-group-3
     - attribute-2:
          - path: Condition.encounter
          - linkedGroup: linked-group-2
  Linked-Group-1:
     - profile: Practitioner
  Linked-Group-2:
     - profile: Encounter
  Linked-Group-3:
     - profile: Practitioner
     - filter: [some-filter] #prac-1 does not satisfy this filter
```

The following steps are numbered differently than the steps above, as this example is more detailed.

1. Initial fetch:
```yaml
MedAdm-1:
  - subject: pat-1
  - performer.actor: prac-1
  - encounter: enc-1
MedAdm-2:
   - subject: pat-2
   - performer.actor: prac-1
   - encounter: enc-2
Cond-1:
   - subject: pat-1
   - recorder: prac-1
   - encounter: enc-1
Cond-2:
   - subject: pat-2
   - recorder: prac-1
   - encounter: enc-2
```
 * only the two medication administrations and conditions were fetched so far
 * so the referenced resources (practitioners and encounters) must be fetched (i.e. resolved) 

2. Torch-intern RG's (and corresponding references) per patient:
```yaml
Pat-1: 
  - (MedAdm-1, G1): [prac-1 (LG-1), enc-1 (LG-2)]
  - (Cond-1, G2): [prac-1 (LG-3), enc-2 (LG-2)]
Pat-2:
   - (MedAdm-2, G1): [prac-1 (LG-1), enc-1 (LG-2)]
   - (Cond-2, G2): [prac-1 (LG-3), enc-2 (LG-2)]
```
3. Rearranged references by linked group:
```yaml
LinkedGroup-1: [prac-1]
LinkedGroup-2: [enc-1, enc-2]
LinkedGroup-3: [prac-1]
```
4. Fetched resources (with filter of linked groups applied):
```yaml
LinkedGroup-1: [prac-1]
LinkedGroup-2: [enc-1, enc-2]
LinkedGroup-3: []               # prac-1 got filtered out by filter of linked-group-3
```
5. Put fetched resources into patient bundles and mark missing as invalid:
   * in code this is implemented with a helper map that tells which newly fetched resources is used by which patient:
   ```yaml
   (prac-1, LG-1): [pat-1, pat-2] # -> put into both patient bundles
   (prac-1, LG-3): [pat-1, pat-2] # -> mark as invalid in both patient bundles
   (enc-1, LG-2): [pat-1, pat-2] # -> put into both patient bundles
   (enc-2, LG-2): [pat-2, pat-2] # -> put into both patient bundles 
   ```
6. Handle references: Working on the same structure as in step 2:
```yaml
Pat-1: 
  - (MedAdm-1, G1): [prac-1 (LG-1), enc-1 (LG-2)]   # -> both are in bundle and not marked invalid -> (MedAdm-1, G1) is valid
  - (Cond-1, G2): [prac-1 (LG-3), enc-2 (LG-2)]     # -> prac-1 is in bundle but (prac-1, LG-3) is marked invalid -> (Cond-1, G2) is invalid
Pat-2:
   - (MedAdm-2, G1): [prac-1 (LG-1), enc-1 (LG-2)]  # -> both are in bundle and not marked invalid -> (MedAdm-2, G1) is valid
   - (Cond-2, G2): [prac-1 (LG-3), enc-2 (LG-2)]    # -> prac-1 is in bundle but (prac-1, LG-3) is marked invalid -> (Cond-2, G2) is invalid
```
7. Return new RG's for recursive resolve:
```yaml
newValidRGs:        # the new RG's that need to be resolved starting at step 2 again
 - (prac-1, LG-1)
 - (enc-1, LG-2)
 - (enc-2, LG-2)
```

More examples can be seen in the `ReferenceResolveBlackBoxIT.java`.
