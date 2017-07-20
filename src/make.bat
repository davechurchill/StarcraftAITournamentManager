@echo off
call clean.bat
@echo on

javac client/ClientMain.java
jar cfm ../client/client.jar config/Manifest_Client.txt *

javac server/ServerMain.java
jar cfm ../server/server.jar config/Manifest_Server.txt *

javac utility/ResultsParserMain.java
jar cfm ../server/results_parser.jar config/Manifest_Results.txt *