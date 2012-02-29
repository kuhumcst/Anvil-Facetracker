rem Rem compile Anvil facetracker (32 bit)
"C:\Program Files\Java\jdk1.7.0_02\bin\javac" -classpath "%CLASSPATH%";required/anvil.jar;required/extern/JMF/win32/jmf.jar;required/extern/OpenCV/javacpp.jar;required/extern/OpenCV/javacv.jar;required/extern/OpenCV/javacv-windows-x86.jar  Plugins\FaceTrackerAnvilPlugin.java
cd plugins
"C:\Program Files\Java\jdk1.7.0_02\bin\jar" -cf FaceTrackerAnvilPlugin.jar *.class
cd ..

