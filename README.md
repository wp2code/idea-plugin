# IntelliJ IDEA Plugin Project

A sample IntelliJ IDEA plugin built with Java and IntelliJ Platform Plugin SDK.

## Getting Started

### Prerequisites
- JDK 21+
- IntelliJ IDEA (Community or Ultimate)

### Build & Run

```bash
# Build
./gradlew build

# Run debug IDE instance
./gradlew runIde

# Verify plugin compatibility
./gradlew verifyPlugin
```

### Project Structure

```
src/
├── main/
│   ├── java/com/lsy/idea/   # Plugin source code
│   └── resources/META-INF/
│       └── plugin.xml             # Plugin descriptor
└── test/
    └── java/com/lsy/idea/   # Test code
```

## Configuration

Key properties are in `gradle.properties`:
- `pluginId` - Plugin unique ID
- `pluginName` - Plugin display name
- `pluginSinceBuild` - Minimum compatible version
- `pluginUntilBuild` - Maximum compatible version
- `platformVersion` - IntelliJ SDK version
