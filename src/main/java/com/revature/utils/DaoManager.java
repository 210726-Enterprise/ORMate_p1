package com.revature.utils;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class DaoManager {
    private static Logger logger = LogManager.getLogger(DaoManager.class);

    private static List<Dao<?>> daoList = new ArrayList<>();

    public static void addDaos(Dao<?> ...dao) {
        daoList = new ArrayList<>();
        Stream.of(dao).forEach((d) -> daoList.add(d));
    }

    public static void addDao(Dao<?> dao) {
        daoList.add(dao);
    }

    public static List<Dao<?>> getDaoList() {
        return daoList;
    }

}
