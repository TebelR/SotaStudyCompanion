Run with:
java -Djava.library.path=/home/vstone/lib -cp "a4-behavior.jar:/home/vstone/lib/*:/home/vstone/vstonemagic/lib/*" a4.SotaFocusCompanion

on MobaXTerm from the /home/root/sotaprograms/jars directory

I ended up moving around the OpenCV libraries to /home/vstone/lib/ at some point, but I'm not sure if that's necessary for things to run.

Essentially, a part of the behavior tree will forever capture an image and send it over the othe python server. I used my phone's hotspot to setup a small network, so I just used my laptop's IP address
after allowing inbound and outbound traffic through the windows firewall on port 8080 TCP. With all things set up and running, I got about 2 requests per second on my laptop (2 fps) which was good
enough for the behavior to work as intended. My laptop is quite powerful however, so I am not sure how other hardware will handle the computer vision model.

Note that I also avoided calibration of the screen boundaries for the user as a part of my behavior - depending on where you place the sota, and how you sit while looking at a screen,
you may need to recalibrate the boundaries on the server in its gaze_interpreter class in the load_calibration_data() method. In order to calibrate things easily, I originally just ran the
archived Server.java on the Sota with my archived main.py file for the Python server. I then just looked into the 8 points around the screen as well as the center to map out approximate boundaries for
the gaze vector. For the archived variant of the Sota's program, you can run it with just:

java -jar a4-server.jar

and it should talk to the archived python server without issues ( I typically get 6-12 fps )

