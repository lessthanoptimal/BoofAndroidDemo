Demonstration of BoofCV for Android devices.  BoofCV is an open source Java computer vision library.  The source code for this application is made freely available without restriction.  BoofCV has been released under an Apache 2.0 license.

The latest source code is available on GitHub:
https://github.com/lessthanoptimal/BoofAndroidDemo

Additional usage instructions can be found at:
http://peterabeles.com/blog/?p=204

Author: Peter Abeles
Date: February 18, 2013

-------------- Build Instructions ----------------

To build the source code you will need to check out source code for DDogleg and EJML.  There appears to be some
strange bug (maybe in Android) and if you include the DDogleg jar it will freeze while rectifying an image, but
if you include the source code everything works fine.  All the other included jars work just fine.