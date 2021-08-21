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
import java.util.Collections;
import java.util.List;

public class Dao<T> {

    private static Logger logger = Logger.getLogger(Dao.class);

    private Class<?> daoClass;
    private String tableName;
    private List<String> columnNames = new ArrayList<>();
    private List<String> fieldNames = new ArrayList<>();
    private List<String> columnTypes = new ArrayList<>();
    private List<String> constraints = new ArrayList<>();

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

    public boolean createTable(ConnectionSource connectionSource) {
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

    public T insert(ConnectionSource connectionSource, T object) {
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

    public ArrayList<T> getAll(ConnectionSource connectionSource) {
        ArrayList<T> allElements = new ArrayList<>();
        String sql = "SELECT * FROM " + tableName;
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();
            List<Method> setters = getSetters(daoClass);
            while(rs.next()) {
                Object object = daoClass.newInstance();
                int index = 1;
                for(int i = 0; i < setters.size(); i++) {
                    setters.get(i).invoke(object, rs.getObject(index));
                    index++;
                }
                allElements.add((T) object);
            }
            return allElements;
        } catch (SQLException | IllegalAccessException | InstantiationException | InvocationTargetException throwables) {
            throwables.printStackTrace();
        }
        return allElements;
    }

    public T updateById(ConnectionSource connectionSource, int id, T obj) {
        List<Method> getters = getGetters(daoClass);
        List<String> updates = new ArrayList<>();
        Field[] fields = daoClass.getDeclaredFields();
        for(Field field: fields) {
            for(Method m: getters) {
                if(m.getName().toLowerCase().contains(field.getName().toLowerCase())) {
                    if(field.getAnnotations()[0] instanceof ForeignKey) {
                        ForeignKey fk = (ForeignKey) field.getAnnotations()[0];
                        try {
                            field.setAccessible(true);
                            Object refObject = field.get(obj);
                            Field refField = refObject.getClass().getSuperclass().getDeclaredField("id");
                            refField.setAccessible(true);
                            int refId = (Integer) refField.get(refObject);
                            String s = String.valueOf(refId);
                            s = "'" + s + "'";
                            updates.add(s);
                        } catch (IllegalAccessException | NoSuchFieldException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            String s = (String) m.invoke(obj);
                            s = "'" + s + "'";
                            updates.add(s);
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
        String sql = "UPDATE " + tableName + " SET (" + String.join(", ", columnNames) + ") = (" +
                String.join(", ", updates) + ") WHERE id = " + id;
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.executeUpdate();
            obj = getById(connectionSource, id);

        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return obj;
    }

    public boolean deleteById(ConnectionSource connectionSource, int id) {
        String sql = "DELETE FROM " + tableName + " WHERE id = " + id;
        try(Connection conn = connectionSource.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
            return true;
        } catch (SQLException throwables) {
            logger.error("Could not delete record", throwables);
        }
        return false;
    }

    private Annotation getFieldAnnotation(Field field) {
        Annotation[] annotations = field.getAnnotations();
        return annotations[0];
    }

    private Annotation getClassAnnotation(Class<?> clazz) {
        Annotation[] annotations = clazz.getAnnotations();
        return annotations[0];
    }

    private String getTableName(Class<?> clazz) {
        Annotation annotation = getClassAnnotation(clazz);
        if(annotation instanceof Entity) {
            Entity tName = (Entity) annotation;
            return tName.tableName();
        }
        return "";
    }

    private Field[] getClassFields(Class<?> clazz) {
        return clazz.getDeclaredFields();
    }

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

    public Class<?> getDaoClass() {
        return daoClass;
    }

    private List<Method> getSetters(Class<?> clazz) {
        List<Method> setters = new ArrayList<>();
        Method[] superMethods = clazz.getSuperclass().getDeclaredMethods();
        for(Method m:superMethods) {
            if(m.getName().contains("set")) {
                System.out.println(m.getName());
                setters.add(m);
            }
        }
        Field[] fields = clazz.getDeclaredFields();
        for(Field field:fields) {
            Method[] methods = clazz.getDeclaredMethods();
            for(Method method:methods) {
                if(method.getName().toLowerCase().contains("set" + field.getName().toLowerCase()) && method.getName().toLowerCase().contains(field.getName().toLowerCase())) {
                    System.out.println(method.getName());
                    setters.add(method);
                }
            }
        }
        return setters;
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getColumnNames() {
        return columnNames;
    }

    public List<String> getColumnTypes() {
        return columnTypes;
    }

    public List<String> getConstraints() {
        return constraints;
    }
}
