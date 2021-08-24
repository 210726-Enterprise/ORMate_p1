package com.revature.utils;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionSource {
    private static Logger logger = LogManager.getLogger(ConnectionSource.class);
    /**
     * Database connection credentials
     */
    private String URL;
    private String USERNAME;
    private String PASSWORD;

    /**
     * Connection object
     */
    private Connection connection;

    /**
     * Constructor
     */
    public ConnectionSource() {
        this.URL = "jdbc:postgresql://database-1.c5mz1ul4etaa.us-east-2.rds.amazonaws.com:5432/postgres?currentSchema=tasker";
        this.USERNAME = "jared";
        this.PASSWORD = "p4ssw0rd";
    }

    /**
     * Gets the connection to the database
     * @return
     */
    public Connection connect() {
        try {
            Class.forName("org.postgresql.Driver");
            connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
        } catch (SQLException | ClassNotFoundException throwables) {
            throwables.printStackTrace();
        }

        return connection;
    }
}
