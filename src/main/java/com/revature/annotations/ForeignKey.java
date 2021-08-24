package com.revature.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ForeignKey {
    /**
     * Column name
     * @return String of column's name
     */
    String colName();

    /**
     * Class of the model being referenced
     * @return
     */
    Class<?> refClass();
}
