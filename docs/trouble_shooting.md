# Trouble Shooting
If you would like to see the Java code or byte code created use:
```java
System.setProperty("jpassport.build.home", [folder location]);
```

When using JPassport you will get a warning from the JVM like:

```
WARNING: A restricted method in java.lang.System has been called
WARNING: java.lang.System::load has been called by jpassport.PassportFactory in an unnamed module
WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
WARNING: Restricted methods will be blocked in a future release unless native access is enabled
```

In order to avoid this warning, start your java process with the command line argument:

```
--enable-native-access=ALL-UNNAMED

or 

--enable-native-access=jpassport
```

If you get an __java.lang.UnsatisfiedLinkError__ then you will need to provide the path to your library
either as a command line argument or in your PassportFactory.link call.

__-Djava.library.path=[path to lib]__
