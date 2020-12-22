package com.archibus.app.reservation.domain;


/**
 * Enumeration for Resource Nature.
 * 
 * The resource nature is a field in the resource standard.
 * 
 * @author Bart Vanderschoot
 *
 */
public enum ResourceNature {
    /** resource standard nature for catering. */
    CATERING {
        /**
         * To string implementation to value used in schema. 
         * @return string string representation
         */
        @Override
        public String toString() {
            return "Catering";
        }
    },
    /** resource standard nature for technology and equipment. */
    EQUIPMENT {
        /**
         * To string implementation to value used in schema.
         * @return string string representation
         */
        @Override
        public String toString() {
            return "Technology";
        }
    },
    /** resource standard nature for furniture. */
    FURNITURE {
        /**
         * To string implementation to value used in schema.
         * @return string string representation
         */
        @Override
        public String toString() {
            return "Furniture";
        }
    }
    
}
