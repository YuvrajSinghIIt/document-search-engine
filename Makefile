JAVA ?= java
JAVAC ?= javac
JAVA_RELEASE ?= 17
MAIN_CLASS = com.yuvrajsingh.documentsearch.DocumentSearchApplication
TEST_CLASS = com.yuvrajsingh.documentsearch.TestSuite
MAIN_SOURCES = $(shell find src/main/java -type f -name '*.java')
TEST_SOURCES = $(shell find src/test/java -type f -name '*.java')

.PHONY: all run test clean

all:
	mkdir -p build/classes
	$(JAVAC) --release $(JAVA_RELEASE) -d build/classes $(MAIN_SOURCES)

run: all
	$(JAVA) -cp build/classes $(MAIN_CLASS)

test: all
	mkdir -p build/test-classes
	$(JAVAC) --release $(JAVA_RELEASE) -cp build/classes -d build/test-classes $(TEST_SOURCES)
	$(JAVA) -cp build/classes:build/test-classes $(TEST_CLASS)

clean:
	rm -rf build
