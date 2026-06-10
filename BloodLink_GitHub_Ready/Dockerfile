# Use a lightweight Java 17 image (Debian-based instead of Alpine for better C-library compatibility with sqlite-jdbc)
FROM eclipse-temurin:17-jdk-jammy

# Set the working directory inside the container
WORKDIR /app

# Copy the entire project into the container
COPY . /app

# Create the bin directory for compiled files
RUN mkdir -p bin

# Find all Java files and compile them (This fixes the file not found error)
RUN find src -name "*.java" > sources.txt && \
    javac -cp "lib/*" -d bin @sources.txt

# Run the WebServer when the container starts
CMD ["java", "-cp", "bin:lib/*", "org.bloodbank.WebServer"]
