package com.revature.utils;

import com.revature.annotations.Primary;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public abstract class BaseClass {
    private static Logger logger = LogManager.getLogger(BaseClass.class);
    @Primary(columnName = "id")
    private int id = 0;

    public BaseClass() {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
