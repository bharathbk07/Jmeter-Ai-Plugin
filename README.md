# JMeter AI Plugin

## Overview

The JMeter AI Plugin is a custom JMeter plugin that integrates AI capabilities into JMeter. It allows users to interact with an AI assistant directly within the JMeter GUI to get help and suggestions for performance testing.

## Features

- **AI Chat Integration**: Chat with an AI assistant within the JMeter GUI.
- **Custom Sampler**: Includes a `DummySampler` for demonstration purposes.
- **API Client**: Communicates with the AI backend to fetch responses.

## Project Structure

```
.env
.gitignore
pom.xml
.idea/
src/
    main/
        java/
            org/
                example/
                    DummySampler.java
                    ApiClient.java
                    gui/
                        DummySamplerGui.java
        resources/
            icons/
            META-INF/
                services/
                    org.apache.jmeter.gui.JMeterToolbarPlugin
test/
    java/
target/
    classes/
    generated-sources/
    generated-test-sources/
    maven-archiver/
    maven-status/
    test-classes/
```

## Getting Started

### Prerequisites

- Java 17
- Maven

### Installation

1. Clone the repository:

   ```sh
   git clone <repository-url>
   cd jmeter-ai-plugin
   ```

2. Build the project using Maven:
   ```sh
   mvn clean install
   ```

### Configuration

1. Create a `.env` file in the root directory with your AI access token:
   ```env
   ACCESS_TOKEN=your_access_token_here
   ```

### Usage

1. Open JMeter and add the `DummySampler` to your test plan.
2. Configure the `DummySamplerGui` to interact with the AI assistant.

### Additional Setup

1. Download the `json-20210307.jar` from Maven Repository.
2. Place it in `lib/ext` of your JMeter installation.
3. Restart JMeter.

## Development

### Adding Dependencies

To add new dependencies, update the `pom.xml` file and run:

```sh
mvn clean install
```

### Running Tests

To run tests, use the following command:

```sh
mvn test
```

## Contributing

Contributions are welcome! Please open an issue or submit a pull request for any changes.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
