@echo off
rem Builds NetRescue with nothing but the JDK (javac + jar), runs the tests,
rem and produces netrescue.jar.   Usage:  build.bat
setlocal
cd /d "%~dp0"

if exist build rmdir /s /q build
mkdir build\classes build\test-classes

echo 1/4 Compiling sources...
dir /s /b src\main\java\*.java > build\sources.txt
javac -encoding UTF-8 -d build\classes @build\sources.txt || goto :fail

echo 2/4 Copying web page and sample networks...
xcopy /e /i /q /y src\main\resources build\classes > nul

echo 3/4 Compiling and running tests...
dir /s /b src\test\java\*.java > build\tests.txt
javac -encoding UTF-8 -cp build\classes -d build\test-classes @build\tests.txt || goto :fail
java -cp build\classes;build\test-classes netrescue.Tests || goto :fail

echo 4/4 Packaging netrescue.jar...
jar --create --file netrescue.jar --main-class netrescue.server.App -C build\classes . || goto :fail

echo Done. Start it with:  java -jar netrescue.jar
exit /b 0

:fail
echo BUILD FAILED
exit /b 1
