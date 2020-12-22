package com.archibus.app.reservation.domain;


/**
 * Enumeration for resource type.
 * 
 * The resource type can have three different values.
 * 
 * @author Bart Vanderschoot
 * 
 */
public enum ResourceType {
    /** resource type for limited resources. */
    LIMITED {
        /**
         * To string implementation to value used in schema.
         * @return string string representation
         */
        @Override
        public String toString() {
            return "Limited";
        }
    },
    /** resource type for unique resources. */
    UNIQUE {
        /**
         * To string implementation to value used in schema.
         * @return string string representation
         */
        @Override
        public String toString() {
            return "Unique";
        }
    }, 
    /** resource type for unlimited resources. */
    UNLIMITED {
        /**
         * To string implementation to value used in schema.
         * @return string string representation
         */
        @Override
        public String toString() {
            return "Unlimited";
        }
    }     
    
}
