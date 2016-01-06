rem Rem compile Anvil facetracker (64 bit)
javac -classpath "%CLASSPATH%";required/anvil.jar;required/extern/JMF/win32/jmf.jar;required/extern/OpenCV/opencv-310.jar  Plugins\FaceTrackerAnvilPlugin.java
cd plugins
jar -cf FaceTrackerAnvilPlugin.jar *.class
cd ..
