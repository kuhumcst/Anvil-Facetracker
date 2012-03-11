rem Rem compile Anvil facetracker (32 bit)
javac -classpath "%CLASSPATH%";required/anvil.jar;required/extern/JMF/win32/jmf.jar;required/extern/OpenCV/javacpp.jar;required/extern/OpenCV/javacv.jar;required/extern/OpenCV/javacv-windows-x86.jar  Plugins\FaceTrackerAnvilPlugin.java
cd plugins
jar -cf FaceTrackerAnvilPlugin.jar *.class
cd ..
