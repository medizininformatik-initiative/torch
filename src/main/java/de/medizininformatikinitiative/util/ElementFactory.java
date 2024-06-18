package de.medizininformatikinitiative.util;

import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Factory;

public class ElementFactory {

    Factory factory = new Factory();
        public Base createElement(String type) {
            if(type!="BackboneElement"){
                return (Base) factory.create(type);
            }
            else{
                //TODO Custom Backbone Element handler
                return null;
            }

        }

}
