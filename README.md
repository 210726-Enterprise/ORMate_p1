# ORMate_p1

## Installation

First, clone the repo into a new project

`git clone https://github.com/210726-Enterprise/ORMate_p1.git`

Second, open the folder in a new Maven project.

Install the project into your local .m2

`mvn install`

Create a new Maven project and include ORMate as a dependency in your pom.xml file.

        <dependency>
            <groupId>com.revature</groupId>
            <artifactId>ORMateLite</artifactId>
            <version>1.2</version>
        </dependency>

## Getting Started

### Model Layer

All models should extend the `BaseClass` from ORMate.

```
public class myModel extends BaseClass
```

Give your Model classes the class Level annotation `@Entity` which accepts an argument tableName: 
`@Entity(tableName = "my_table")`

A primary surrogate key will be generated automatically for each model.

Annotate each field you want to persist as a column with `@Column` which accepts an argument columnName:
`@Column(columnName = "my_column")`

### Service Layer

The class that services your Model should be given a field of Type `Dao<myModel>`.

It should also contain a field with of type `ConnectionSource` which accepts your database credentials.

In the constructor, the Dao<> should be instantiated and added to ORMate's `DaoManager` which holds all the Dao classes for ease of access across service layers.

    private Dao<Task> dao;
    private ConnectionSource connection = new ConnectionSource(myUrl, myDbUsername, myDbPassword)
    
    public myModelService(Dao<myModel> dao) {
        this.dao = dao;
        DaoManager.addDao(dao);
    }
    
    
You can now call the Dao<> methods to interact with your database.
