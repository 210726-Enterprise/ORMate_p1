package com.revature.utils;

import com.revature.annotations.Column;
import com.revature.annotations.Entity;
import com.revature.annotations.ForeignKey;
import com.revature.annotations.Primary;
import org.apache.log4j.Logger;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class Dao<T> {


    /**
     * Logger
     */
    private static Logger logger = Logger.getLogger(Dao.class);

    /**
     * Holds the Class Type of the dao object
     */
    private Class<?> daoClass;
    /**
     * Holds the table name
     */
    private String tableName;
    /**
     * Holds the column names for the table
     */
    private List<String> columnNames = new ArrayList<>();
    /**
     * Holds the field names of the class the dao is holding
     */
    private List<String> fieldNames = new ArrayList<>();
    /**
     * Holds the sql data types for each column
     */
    private List<String> columnTypes = new ArrayList<>();
    /**
     * Holds the constraints that are created for the table
     */
    private List<String> constraints = new ArrayList<>();


    /**
     * Dao constructor
     * Gets table name from Entity Annotation on the model
     * Dao class is taken from teh class that is passed into the constructor
     * Iterates through each field to check for annotations and parses accordingly
     * ArrayLists of columns and column types are filled and each column index in the columnName list matches the index of its type in the columnType List
     * @param clazz Class Type of the Dao
     */
    public Dao(Class<?> clazz) {
        tableName = getTableName(clazz);
        daoClass = clazz;
        Field[] fields = clazz.getDeclaredFields();
        for(Field field: fields) {
            fieldNames.add(field.getName());
            Annotation annotation = getFieldAnnotation(field);
            if(annotation instanceof Primary) {
                Primary primary = (Primary) annotation;
                String colName = primary.columnName();
                columnNames.add(colName);
                columnTypes.add(0, "SERIAL PRIMARY KEY");
            } else if (annotation instanceof Column) {
                Column c = (Column) annotation;
                String colName = c.columnName();
                columnNames.add(colName);
                String type = getSqlType(field);
                columnTypes.add(type);
            } else if (annotation instanceof ForeignKey) {
                ForeignKey fk = (ForeignKey) annotation;
                String colName = fk.colName();
                columnNames.add(colName);
                columnTypes.add("int");
                constraints.add("FOREIGN KEY (" + colName + ") REFERENCES " +
                        getTableName(fk.refClass()) + "(id)" +
                        " ON DELETE CASCADE ON UPDATE CASCADE");
            }

        }
    }

    /**
     * Creates a table based on the model passed into the dao instance
     * @param connectionSource instance of the database connection
     * @return returns true if a table was created; false otherwise
     */
    private boolean createTable(ConnectionSource connectionSource) {
        StringBuilder tableStatements = new StringBuilder();
        tableStatements.append("id SERIAL PRIMARY KEY, ");
        for(int i = 0; i < columnNames.size(); i++) {
            if(i < columnNames.size() - 1) {
                tableStatements.append(columnNames.get(i)).append(" ").append(columnTypes.get(i)).append(", ");
            } else {
                tableStatements.append(columnNames.get(i)).append(" ").append(columnTypes.get(i));
            }
        }
        if(constraints.size() > 0) {
            tableStatements.append(", ").append(String.join(", ", constraints));
        }
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" + tableStatements + ")";
        try (Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
            return true;
        } catch (SQLException e) {
            logger.error("Could not create table.", e);
        }
        return false;
    }

    /**
     * Inserts a new row into the table
     * @param connectionSource instance of the database connection
     * @param object Instance of the class the dao instance is holding
     * @return returns the object that has been inserted into the DB
     */
    public T insert(ConnectionSource connectionSource, T object) {
        createTable(connectionSource);
        Object fieldValue = null;
        List<String> psConditions = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            psConditions.add("?");
        }
        String sql = "INSERT INTO " + tableName + "(" + String.join(", ", columnNames) + ") VALUES (" +
                String.join(", ", psConditions) + ")";
        try (Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            int colIndex = 0;
            int conditionIndex = 1;
            for (String condition : psConditions) {
                Class<?> clazz = object.getClass();
                Field field = clazz.getDeclaredField(fieldNames.get(colIndex));
                field.setAccessible(true);
                if (getFieldAnnotation(field).annotationType().getSimpleName().equals("ForeignKey")) {
                    Object o = field.get(object);
                    Class<?> cl = o.getClass().getSuperclass();
                    Field f = cl.getDeclaredField("id");
                    f.setAccessible(true);
                    fieldValue = f.get(o);
                } else {
                    fieldValue = field.get(object);
                }
                ps.setObject(conditionIndex, fieldValue);
                colIndex++;
                conditionIndex++;
            }
            ps.execute();
            object = getLastRecordOf(connectionSource);
            return object;

        } catch (SQLException | NoSuchFieldException | IllegalAccessException e) {
            logger.error(e.getMessage(), e);
        }
        return (T) object;
    }

    /**
     * Selects an object by its ID
     * @param connectionSource instance of the database connection
     * @param id id number of the object being retrieved
     * @return object with the given id
     */
    public T getById(ConnectionSource connectionSource, int id) {
        Object object = null;
        String sql = "SELECT * FROM " + tableName + " WHERE id = " + id;
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<Method> setters = getSetters(daoClass);
            List<Field> fields = new ArrayList<>();
            fields.add(BaseClass.class.getDeclaredFields()[0]);
            Field[] fs = daoClass.getDeclaredFields();
            Collections.addAll(fields, fs);
            while(rs.next()) {
                object = daoClass.newInstance();
                int index = 1;
                for(int i = 0; i < fields.size(); i++) {
                    if(fields.get(i).getAnnotations().length > 0 && fields.get(i).getAnnotations()[0] instanceof ForeignKey) {
                        ForeignKey fk = (ForeignKey) fields.get(i).getAnnotations()[0];
                        int reference = rs.getInt(index);
                        for(int j = 0; j < DaoManager.getDaoList().size(); j++) {
                            if(DaoManager.getDaoList().get(j).getDaoClass().equals(fk.refClass())) {
                                Object refObject = DaoManager.getDaoList().get(j).getById(connectionSource, reference);
                                setters.get(i).invoke(object, refObject);
                                index++;
                            }
                        }
                    } else {
                        setters.get(i).invoke(object, rs.getObject(index));
                        index++;
                    }
                }
            }
            return (T) object;
        } catch (SQLException | IllegalAccessException | InstantiationException | InvocationTargetException throwables) {
            logger.error("Could not find what you were looking for.", throwables);
        }
        return null;
    }

    /**
     * Retrieves the most recently created object from the table
     * @param connectionSource instance of the database connection
     * @return the last row of the table as an object
     */
    public T getLastRecordOf(ConnectionSource connectionSource) {
        Object object = null;
        String sql = "SELECT * FROM " + tableName + " ORDER BY id DESC LIMIT 1;";
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<Method> setters = getSetters(daoClass);
            List<Field> fields = new ArrayList<>();
            fields.add(BaseClass.class.getDeclaredFields()[0]);
            Field[] fs = daoClass.getDeclaredFields();
            Collections.addAll(fields, fs);
            while(rs.next()) {
                object = daoClass.newInstance();
                int index = 1;
                for(int i = 0; i < fields.size(); i++) {
                    if(fields.get(i).getAnnotations().length > 0 && fields.get(i).getAnnotations()[0] instanceof ForeignKey) {
                        ForeignKey fk = (ForeignKey) fields.get(i).getAnnotations()[0];
                        int reference = rs.getInt(index);
                        for(int j = 0; j < DaoManager.getDaoList().size(); j++) {
                            if(DaoManager.getDaoList().get(j).getDaoClass().equals(fk.refClass())) {
                                Object refObject = DaoManager.getDaoList().get(j).getById(connectionSource, reference);
                                setters.get(i).invoke(object, refObject);
                                index++;
                            }
                        }
                    } else {
                        setters.get(i).invoke(object, rs.getObject(index));
                        index++;
                    }
                }
            }
            return (T) object;
        } catch (SQLException | IllegalAccessException | InstantiationException | InvocationTargetException throwables) {
            logger.error("Could not find what you were looking for.", throwables);
        }
        return null;
    }

    /**
     * Gets all rows from the table
     * @param connectionSource instance of the database connection
     * @return List of objects based on the table rows
     */
    public ArrayList<T> getAll(ConnectionSource connectionSource) {
        ArrayList<T> allElements = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName;
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<Method> setters = getSetters(daoClass);
            List<Field> fields = new ArrayList<>();
            fields.add(BaseClass.class.getDeclaredFields()[0]);
            Field[] fs = daoClass.getDeclaredFields();
            Collections.addAll(fields, fs);
            while(rs.next()) {
                Object object = daoClass.newInstance();
                int index = 1;
                for(int i = 0; i < fields.size(); i++) {
                    if(fields.get(i).getAnnotations().length > 0 && fields.get(i).getAnnotations()[0] instanceof ForeignKey) {
                        ForeignKey fk = (ForeignKey) fields.get(i).getAnnotations()[0];
                        int reference = rs.getInt(index);
                        for(int j = 0; j < DaoManager.getDaoList().size(); j++) {
                            if(DaoManager.getDaoList().get(j).getDaoClass().equals(fk.refClass())) {
                                Object refObject = DaoManager.getDaoList().get(j).getById(connectionSource, reference);
                                setters.get(i).invoke(object, refObject);
                                index++;
                            }
                        }
                    } else {
                        setters.get(i).invoke(object, rs.getObject(index));
                        index++;
                    }
                }
                allElements.add((T) object);
            }
            return allElements;
        } catch (SQLException | IllegalAccessException | InstantiationException | InvocationTargetException throwables) {
            throwables.printStackTrace();
        }
        return allElements;
    }

    /**
     * Updates a row by passing in the id and the updated object
     * @param connectionSource instance of the database connection
     * @param id primary key
     * @param obj updated object
     * @return the newly updated object
     */
    public T updateById(ConnectionSource connectionSource, int id, T obj) {
        List<Method> getters = getGetters(daoClass);
        List<Object> updates = new ArrayList<>();
        Field[] fields = daoClass.getDeclaredFields();
        for(Field field: fields) {
            for(Method m: getters) {
                if(m.getName().toLowerCase().contains(field.getName().toLowerCase())) {
                    if(field.getAnnotations()[0] instanceof ForeignKey) {
                        ForeignKey fk = (ForeignKey) field.getAnnotations()[0];
                        try {
                            field.setAccessible(true);
                            Object refObject = field.get(obj);
                            Method method = refObject.getClass().getMethod("getId");
                            int refId = (int) method.invoke(refObject);
                            updates.add(refId);
                        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            Class<?> returnType = m.getReturnType();
                            if(returnType.equals(java.lang.String.class)) {
                                String s = (String) m.invoke(obj);
                                s = "'" + s + "'";
                                updates.add(s);
                            } else if (returnType.equals(boolean.class)) {
                                boolean b = (boolean) m.invoke(obj);
                                updates.add(b);
                            } else if (returnType.equals(int.class)) {
                                int num = (int) m.invoke(obj);
                                updates.add(num);
                            } else if (returnType.equals(long.class)) {
                                long bigNum = (long) m.invoke(obj);
                                updates.add(bigNum);
                            } else if (returnType.equals(char.class)) {
                                char c = (char) m.invoke(obj);
                                updates.add(c);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        String updateStatement = Arrays.toString(updates.toArray()).replace("[", "").replace("]", "");

        String sql = "UPDATE " + tableName + " SET (" + String.join(", ", columnNames) + ") = (" +
                updateStatement + ") WHERE id = " + id;
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            obj = getById(connectionSource, id);

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return obj;
    }

    /**
     * Deletes an object with the given id
     * @param connectionSource instance of the database connection
     * @param id primary key
     * @return true if successfully deleted; false otherwise
     */
    public boolean deleteById(ConnectionSource connectionSource, int id) {
        int rowsDeleted = 0;
        String sql = "DELETE FROM " + tableName + " WHERE id = " + id;
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            rowsDeleted = ps.executeUpdate();
            if(rowsDeleted > 0) {
                return true;
            } else {
                return false;
            }
        } catch (SQLException throwables) {
            logger.error("Could not delete record", throwables);
        }
        return false;
    }

    /**
     * Gets the annotation from the field
     * @param field Field whose annotation will be returned
     * @return the annotation of the field
     */
    private Annotation getFieldAnnotation(Field field) {
        Annotation[] annotations = field.getAnnotations();
        return annotations[0];
    }

    /**
     * Gets the annotation from the class
     * @param clazz Class whose annotation will be returned
     * @return the annotation of the Class
     */
    private Annotation getClassAnnotation(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        return annotations[0];
    }

    /**
     * Returns table name
     * @param clazz Class the dao is holding
     * @return String table name
     */
    private String getTableName(Class<?> clazz) {
        Annotation annotation = getClassAnnotation(clazz);
        if(annotation instanceof Entity) {
            Entity tName = (Entity) annotation;
            return tName.tableName();
        }
        return "";
    }

    /**
     * Gets fields from the class
     * @param clazz class the dao is holding
     * @return all declared fields
     */
    private Field[] getClassFields(Class<?> clazz) {
        return clazz.getDeclaredFields();
    }

    /**
     * gets the name of the primary column
     * @param clazz class the dao is holding
     * @return String name of the primary column
     */
    private String getPrimaryColName (Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for(Field field: fields) {
            Annotation annotation = getFieldAnnotation(field);
            if(annotation instanceof Primary) {
                Primary primary = (Primary) annotation;
                return primary.columnName();
            }
        }
        return "";
    }

    /**
     * Takes in a field and converts its type to its SQL datatype equivalent
     * @param field field whose datatype needs to be parsed
     * @return String of the SQL datatype name
     */
    private String getSqlType(Field field) {
        String type = field.getGenericType().getTypeName().toLowerCase();
        if(type.equals("byte") || type.equals("short")) {
            return "int";
        } else if (type.equals("long")) {
            return "bigint";
        } else if (type.equals("char") || type.equals("java.lang.string")) {
            return "varchar";
        } else if (type.equals("double")) {
            return "float";
        } else {
            return type;
        }
    }

    /**
     * Gets all getter methods of a class
     * @param clazz class the dao is holding
     * @return List of all class getter methods
     */
    private List<Method> getGetters(Class<?> clazz) {
        List<Method> getters = new ArrayList<>();
        Method[] superMethods = clazz.getSuperclass().getDeclaredMethods();
        for(Method m:superMethods) {
            if(m.getName().contains("get")) {
                getters.add(m);
            }
        }
        Field[] fields = clazz.getDeclaredFields();
        for(Field field:fields) {
            Method[] methods = clazz.getDeclaredMethods();
            for(Method method:methods) {
                if(method.getName().contains("get") && method.getName().toLowerCase().contains(field.getName().toLowerCase())) {
                    getters.add(method);
                }
            }
        }
        return getters;
    }

    /**
     * Get's the class the dao is holding
     * @return class the doa is holding
     */
    public Class<?> getDaoClass() {
        return daoClass;
    }

    /**
     * Gets all setter methods on a class
     * @param clazz class the dao is holding
     * @return all class setter methods
     */
    private List<Method> getSetters(Class<?> clazz) {
        List<Method> setters = new ArrayList<>();
        Method[] superMethods = clazz.getSuperclass().getDeclaredMethods();
        for(Method m:superMethods) {
            if(m.getName().contains("set")) {
                setters.add(m);
            }
        }
        Field[] fields = clazz.getDeclaredFields();
        for(Field field:fields) {
            Method[] methods = clazz.getDeclaredMethods();
            for(Method method:methods) {
                if(method.getName().toLowerCase().contains("set" + field.getName().toLowerCase()) && method.getName().toLowerCase().contains(field.getName().toLowerCase())) {
                    setters.add(method);
                }
            }
        }
        return setters;
    }

    /**
     * Gets table name
     * @return table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Gets column names
     * @return list of column names
     */
    public List<String> getColumnNames() {
        return columnNames;
    }

    /**
     * gets column types
     * @return list of column types
     */
    public List<String> getColumnTypes() {
        return columnTypes;
    }

    /**
     * gets constraints
     * @return list of constraints
     */
    public List<String> getConstraints() {
        return constraints;
    }
}
