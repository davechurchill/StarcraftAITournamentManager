@echo off
call clean.bat
@echo on

:: Class path is required here so that any jar files in libs/ are used
javac -cp client/*.java;libs/*;objects/*.java;server/*.java;utility/*.java; client/ClientMain.java
javac -cp client/*.java;libs/*;objects/*.java;server/*.java;utility/*.java; server/ServerMain.java
javac -cp client/*.java;libs/*;objects/*.java;server/*.java;utility/*.java; utility/ResultsParserMain.java

:: Extract libraries from jar(s) before packaging the TM jars
jar xf libs/minimal-json-0.9.4.jar com/eclipsesource/json/

jar cfm ../client/client.jar config/Manifest_Client.txt client/*.class objects/*.class utility/*.class com/eclipsesource/json/*.class
jar cfm ../server/server.jar config/Manifest_Server.txt server/*.class objects/*.class utility/*.class com/eclipsesource/json/*.class
jar cfm ../server/results_parser.jar config/Manifest_Results.txt server/*.class objects/*.class utility/*.class com/eclipsesource/json/*.class

:: Remove extracted libraries
rmdir com /s /q