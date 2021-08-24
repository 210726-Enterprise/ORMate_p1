package com.revature.utils;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DaoManager {
    /**
     * Logger
     */
    private static Logger logger = LogManager.getLogger(DaoManager.class);

    /**
     * Holds list of all Daos that are instantiated
     */
    private static List<Dao<?>> daoList = new ArrayList<>();

    /**
     * Varargs for adding an unknown number of Daos to the daoList
     * @param dao An instantiated Dao
     */

    public static void addDao(Dao<?> ...dao) {
        daoList = new ArrayList<>();
        Stream.of(dao).forEach((d) -> daoList.add(d));
    }

    /**
     * Adds a single dao to the daoList
     * @param dao An instantiated Dao
     */
    public static void addDao(Dao<?> dao) {
        daoList.add(dao);
    }

    /**
     * Returns the daoList
     * @return List of all Daos
     */
    public static List<Dao<?>> getDaoList() {
        return daoList;
    }

}
