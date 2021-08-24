package com.revature.utils;

import com.revature.annotations.Primary;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public abstract class BaseClass {
    /**
     * Logger
     */
    private static Logger logger = LogManager.getLogger(BaseClass.class);

    /**
     * Primary key for all child classes
     */
    @Primary(columnName = "id")
    private int id = 0;

    /**
     * BaseClass constructor
     */
    public BaseClass() {
        this.id = id;
    }

    /**
     * Gets id
     * @return id
     */
    public int getId() {
        return id;
    }

    /**
     * Sets id
     * @param id int of new Id
     */
    public void setId(int id) {
        this.id = id;
    }
}
