import re
import sys

# Check if at least one argument is provided
if len(sys.argv) > 1:
    first_argument = sys.argv[1]
    print(f"First argument: {first_argument}")
else:
    print("No arguments provided.")

partVersion = '7.28.0';
finalVersion = f"{partVersion}.{first_argument}";

path = "src/chatty/Chatty.java";
chatty = open(path, 'r');
line = chatty.read();
chatty.close();

if not line:
	exit(1);

chatty = open(path, 'w');
line = re.sub(
	rf"public static final String VERSION = \"{partVersion}.[0-9]+",
	f"public static final String VERSION = \"{finalVersion}",
	line
);
#print(line)
chatty.write(line);
chatty.close();

open('final_version.txt', 'w').write(finalVersion);

#alias b='./gradlew build && ./gradlew packageAppArm'
#alias o='open ./build/jpackage-mac/Chatty-arm64.app'
