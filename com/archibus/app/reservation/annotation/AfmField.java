package com.archibus.app.reservation.annotation;

import java.lang.annotation.*;

/**
 * The Interface AfmField.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.FIELD })
@Inherited
public @interface AfmField {
    
    /**
     * Table name.
     */
    String tableName() default "";
    
    /**
     * Field name.
     * 
     */
    String fieldName();
    
    /**
     * Nullable.
     * 
     */
    boolean nullable() default false;
}
