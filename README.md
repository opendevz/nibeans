[![Build Status](https://travis-ci.org/opendevz/nibeans.svg?branch=master)](https://travis-ci.org/opendevz/nibeans)

No Implementation Beans
================

No Implementation Beans (nibeans), is a simple yet powerful framework for sparing java developers from the tedious and error-prone task of defining and maintaining java bean classes.

We all believe that working with java bean classes should not cause any extra work than writing the thinnest definitions possible:
* Inteface name
* Getter signatures
* Setter signatures

and that's it. The rest should be a one-time fixed minimal effort that allows creating instances of these beans and working with them freely, without interrupting the main focus of the project being developed.

**The nibeans solution**
Writing an interface like this is all what the developer does, practically:
```
@NIBean
public interface Car {
  String getMake();
  void setMake(String make);
  String getModel();
  void setModel(String model);
}
```

nibeans is based on annotation processing for processing beans and generating full implementations, thus the processor should be available. In maven this is a very simple one-time task:
- Add dependencies to the nibeans artifacts:
```
<dependency>
    <groupId>org.nibeans</groupId>
    <artifactId>nibeans-api</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.nibeans</groupId>
    <artifactId>nibeans-processor</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <optional>true</optional>
</dependency>
```
-  Configure the processor where to look for bean interfaces:
```
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <compilerArgs>
                    <arg>-Anib.srcpackages=*your source package*</arg>
                </compilerArgs>
            </configuration>
        </plugin>
    </plugins>
</build>
```

During compilation nibeans will take care of generating the full implementations and making them transparently available during runtime through the factory service.

To instantiate beans in your source code, do this:
```
Car car = BeanFactory.getInstance().createBean(Car.class);
```
