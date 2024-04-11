# makefile for use on Linux; may also work on other operating systems...
.SILENT:

integration:	
	cd src; javac MyProtocol.java -d ../bin

clean:
	cd bin; find -type f -name '*.class' -delete

run:
	cd bin; java MyProtocol
