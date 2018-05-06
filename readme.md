Demonstration of BoofCV for Android devices.  BoofCV is an open source Java computer vision library.  The source code for this application is made freely available without restriction.  BoofCV has been released under an Apache 2.0 license.

Source Code: https://github.com/lessthanoptimal/BoofAndroidDemo

# Building

Just load into Android Studio and everything should compile and work just fine. No need to download jars yourself. Gradle will handle that all for you. I recommend ignoring requested by Android Studio to upgrade parts of the script. Historically those "upgrades" have broken things horribly and require additional modifications to fix it.

Build Variants
* debug Let's you see debug output and put breakpoints in the code bug is MUCH MUCH slower to run
* fast Turns off debugging and runs at full speed
* release Is for release and requires encryption keys

# Crash Reporting

ACRA is used to report crashes. To prevent me from getting spammed by weird errors when people
hack this code the destination address for ACRA isn't checked into the repository. To
add error reporting create a text file with the address here:

app/src/main/asserts/acra.txt

# Usage

See this old blog post
http://peterabeles.com/blog/?p=204

* Author: Peter Abeles
* Date: April 22, 2018

P.S. Holy sh** I first released this in 2013??
