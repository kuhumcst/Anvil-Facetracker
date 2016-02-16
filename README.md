# Anvil Facetracker

Introduction
============

The Anvil Facetracker is a plugin that automatically creates annotations for
head movements. It has two modi operandi:

1. Create annotations when the head's velocity is above some preset threshold
2. Create annotations when the head's acceleration is above some preset
   threshold.
   
The acceleration of the head is a direct consequence of forces being exerted
on the head, which can be gravity, but more commonly are the due to the
muscles in the body, especially in the neck.

The software is based on face recognition algorithms implemented in 
OpenCV 3.1.0 (http://opencv.org).

Installing the face tracker.
============================

The installation has been tested for Windows 7 Enterprise, 32-bit and 64-bit,
and also Windows 8.1, 64-bit.

Java needs to be installed. If you have plans to recompile the face tracker's
source code, install the Java JDK. We have tested with versions 1.7.0_10 and
with version 1.8.0_65.

Download Anvil from http://www.anvil-software.de/ and install the software. 
The face tracker has been tested with Anvil version 5.1.15.

Copy the files in this distribution of the facetracker to the Anvil 
installation folder, taking care that the destination directory names match
with the source directory names. The Microsoft redistributables in the folders 
anvil-plugin-32-bit-specific and anvil-plugin-64-bit-specific may be necessary
if your windows computer has no Visual Studio installed. This has not been
tested. Try to run Anvil and the facetracker. If the program fails to start
successfully, install the appropriate Microsoft redistributable. The 
installation file is called vcredist_x86 or vcredist_x64, depending on the
architecture.

If you have both a 64-bit and a 32-bit Java, or if you have several
Javas with different version numbers, it may be necessary to specify 
the path to Java in the batch files for compiling and running. Open 
the files COMPILE_ANVIL-5.1.1_FACETRACKER_OpenCV-3.1.0.BAT and
ANVIL-5.1.1_FACETRACKER-OpenCV-3.1.0-64bit.BAT (or
ANVIL-5.1.1_FACETRACKER-OpenCV-3.1.0-32bit.BAT) in an editor.
(Right click | Edit). If you see a line starting with e.g. "jar", "javac"
or "java", the system's path information is used to find these programs.
You can specify the version of java by adding the full path, e.g.

    "C:\Program Files (x86)\Java\jdk1.7.0_10\bin\java"

Mac users
---------
Ignore the folder Anvil-Facetracker/required/extern/OpenCV, which is for 
Microsoft Windows. Instead, copy the folder 

    Anvil-Facetracker/required/extern/OpenCV-OSX
    
and rename it to

    Anvil-Facetracker/required/extern/OpenCV

Instead of the Windows batch files
COMPILE_ANVIL-5.1.1_FACETRACKER_OpenCV-3.1.0.BAT and
ANVIL-5.1.1_FACETRACKER-OpenCV-3.1.0-64bit.BAT,

use 

COMPILE_ANVIL-5.1.1_FACETRACKER_OpenCV-3.1.0 and
ANVIL-5.1.1_FACETRACKER-OpenCV-3.1.0-64bit


Necessary preparations from within the Anvil UI
===============================================

Start Anvil by running the ANVIL-5.1.1_FACETRACKER-OpenCV-3.1.0-64bit.BAT batch
file, or ANVIL-5.1.1_FACETRACKER-OpenCV-3.1.0-32bit.BAT if you are working in a
32-bit Windows. But see remark above if ANVIL is installed under OSX.

Editing the Anvil spec file
---------------------------

We now have to define tracks were the face tracker can dump its annotations.
There are two kinds of face tracker annotations: for velocity and for
acceleration. (Acceleration is the change of velocity with time and is caused
by some force, e.g. muscle forces or gravitation.)

Open the Specification Editor (Edit | Edit specification ...).
Velocity annotations are stored in a primary track with track attributes called
"velocity". Likewise acceleration annotations are stored in a primary track
with track attributes called "acceleration".

You can create as many velocity and acceleration tracks as needed in the
specification file.

Here's how:
1. Decide whether you want to create a new specification file 
        (File | Create new specification)
    or whether you want to edit an existing one.
        (File | Open specification | <browse and open>)

2. Decide where to put the new tracks. If you are creating a new specification
file, there is not much to choose. Click the word "Specification" in the track
pane. If you are editing an existing specification, you can choose
"Specification" or a group, the cursively spelled names in the track pane.

3. Press the Add Track/Group button and choose Add Track.

4. Choose a track name, for example "Velocity person 1". Primary track is
already OK.

5. Press the Add attribbute button.

6. Name must be "velocity" or "acceleration" (without the quotes). Type must
be Timestamped Point.

7. Save the specification file.

Add the face tracker plugin
---------------------------

Now you need to tell Anvil that it has a plug-in. Do the following:

1. Edit | Options ... | Plug-ins | add
2. Choose a name for the face tracker, e.g., "facetracker".
3. In the next windows you are asked for the full class name of the plug-in
class. Write
    FaceTrackerAnvilPlugin
4. press OK twice to return focus to Anvil's main window.
5. Choose Tools | <name you have given to the facetracker in step 2.>
A new window opens with face tracker controls.

Running the face tracker
========================

Start Anvil by running the ANVIL-5.1.1_FACETRACKER-OpenCV-3.1.0-<nn>bit.BAT
batch file. Next you need to open a video file and you need to activate the
plugin, in either order.

Activate the Facetracker plugin
-------------------------------

* From the Edit menu, choose Options... . 
* Press the OK button.
* From the Tools menu, choose the facetracker.

Opening a video file
--------------------
 
The next step is to open and prepare a movie for annotation of head movements:
1. File | Open ... | <browse and open movie>
2. In the Annotation properties windows, browse to the specification file
you made earlier, open it and press OK

Getting started with the facetracker
------------------------------------

1. In the annotation window you choose the velocity or accelation track by
clicking its name.
2. If you do not want to annotate the movie from the start, navigate to the
frame where you want the annotation to start, and put the cursor about one
second before the startpoint.
3. In the Main Video window, point with the mouse pointer at the person's
face who's head movements you want to annotate.

If there is only one person, you can let the software find the face. You may
need to watch the annotation process as the software may lose track of the
face you had pointed at and be attracted to another person's face.

Now you can start the annotation process. You get the best results by letting
the software analyse each and every frame. To ensure that no frames are
skipped, you should let the face tracker step through the movie rather than
play the movie. Stepping is started by pressing the 'Step' button in the Face 
Tracker window. As the program starts to step, the text on the button face
changes to 'Stop', so pressing the button once again stops the movie.

Controlling the Facetracker
---------------------------

In the Face Tracker window are a number of sliders and buttons. The sliders
are per default set at positions that work well, so you can leave them as they
are if you want.

With the upper slider you can tell the algorithm on how many frames it has to
base the calculation of velocity or acceleration. The higher the value is set,
the smoother will the movements seem to be. Also, high values will tend to
erase very quick, subtle movements, because they will be averaged away. The
highest value, 25, corresponds to a full second at normal speed. If the value
is set much lower than 10, the inevitable errors in the estimates of the
face's position in each frame will likely generate many spurious movements and
consequently many false positive annotation elements.

The other two sliders set the sensitivity for horizontal and vertical
movements. The lower the threshold, the more annotations for velocity or
accelerations are generated, and the longer the stretch in time will be over
which a movement is perceived to take place. The face tracker can also be used
to annotate the stretches in the movie where little happens, e.g. the precise
moment where a movement has come to a stand-still. In that case a low value of
a threshold means that relatively few annotations will be generated.

There are also four check boxes in the face tracker window:

1. Suspend face tracking - select if you temporarily want to disable face
tracking, for example for seeing the movie at normal speed.

2. Annotate when velocity/acceleration is LOW - select this if you are
interested for the stretches where velocity or acceleration is low. 
(The latter could be the case if a person walks through the scene at
a constant velocity, provided she or he doesn't make hade movements at the
same time.)

3. Left - only annotate head movements of person in left half of video

4. Right - only annotate head movements of person in right half of video

Having Left and Right both checked has the same effect as none of them being
checked.

There are five buttons in the face tracker window.

1. Step/Stop - press this button to start and stop a frame-by-frame analysis of
the movie. This is the prefered way to make automatic anotations, because it
is reproducible. Pressing the Play button plays the movie at normal speed,
which current hardware cannot keep up with: many frames will never be analysed.

2. Keep changes - press this button if you want to retain the settings for the
currently selected track. After pressing this button you can switch to another
track without Anvil forgetting the settings in the track you left, but
pressing this button does not save the settings to disk. When you later save
the annotation file to disk, the settings for each track individually will be
saved as well.

3. Cancel changes - press this button if you want to undo the settings you made
for the current track. Just switching to another track also cancels any such
changes.

4. Dismiss - close the face tracker

5. Haarcascade - The face tracker uses an algorithm based on Haar-like features
(http://en.wikipedia.org/wiki/Haar-like_features) to detect faces in a movie
frame. The algorithm uses a set of parameters which are obtained by machine
learning. For best detection results, different light conditions and different
body positures require different sets of parameters. The face tracker uses the
parameter sets that come with the OpenCV software. These parameter sets are
stored in files called haarcascade_<descriptor>.xml, where <descriptor> is any
of the following list:

    eye
    eye_tree_eyeglasses
    frontalcatface
    frontalcatface_extended
    frontalface_alt
    frontalface_alt2
    frontalface_alt_tree
    frontalface_default
    fullbody
    lefteye_2splits
    licence_plate_rus_16stages
    lowerbody
    profileface
    righteye_2splits
    russian_plate_number
    smile
    upperbody


As can be seen, in theory the face tracker can be used to track other things
than human faces as well. Not every Haar cascade is equally effective, though.
The default Haar cascade choice is haarcascade_frontalface_alt.xml, which also
works for faces that are turned slightly away from frontal. If the face tracker
cannot detect a face most of the time, it is advised to try several haar
cascades from the list. Press the Haarcascade button and choose one of the xml
files that are shown in the dialogue box.


Sources
=======

Java JDK
----------------
http://www.oracle.com/


Anvil 5.1.15
-------------------------
http://www.anvil-software.de/


OpenCV 3.1.0
------------
http://sourceforge.net/projects/opencvlibrary/files/opencv-win/3.1.0/opencv-3.1.0.exe/download
After installation, libraries were copied from opencv\build\java to the required\extern\OpenCV
folder under anvil. The haarcascades were copied from opencv\sources\data\haarcascades.


Microsoft Redistributables
--------------------------
http://www.microsoft.com/download/en/details.aspx?id=5555    (32 bit)
http://www.microsoft.com/download/en/details.aspx?id=14632   (64 bit)


Bart Jongejan 20160106