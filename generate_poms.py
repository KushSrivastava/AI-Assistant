import os

files = {
    'knowledge-bot-common/pom.xml': '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>com.knowledgebot</groupId>
		<artifactId>knowledge-bot-parent</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</parent>
	<artifactId>knowledge-bot-common</artifactId>
	<name>knowledge-bot-common</name>
</project>
''',

    'knowledge-bot-core/pom.xml': '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>com.knowledgebot</groupId>
		<artifactId>knowledge-bot-parent</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</parent>
	<artifactId>knowledge-bot-core</artifactId>
	<name>knowledge-bot-core</name>
	<dependencies>
		<dependency>
			<groupId>com.knowledgebot</groupId>
			<artifactId>knowledge-bot-common</artifactId>
			<version>${project.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter</artifactId>
		</dependency>
		<dependency>
			<groupId>com.github.javaparser</groupId>
			<artifactId>javaparser-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.eclipse.jgit</groupId>
			<artifactId>org.eclipse.jgit</artifactId>
			<version>6.8.0.202311291450-r</version>
		</dependency>
	</dependencies>
</project>
''',

    'knowledge-bot-ai/pom.xml': '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>com.knowledgebot</groupId>
		<artifactId>knowledge-bot-parent</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</parent>
	<artifactId>knowledge-bot-ai</artifactId>
	<name>knowledge-bot-ai</name>
	<dependencies>
		<dependency>
			<groupId>com.knowledgebot</groupId>
			<artifactId>knowledge-bot-core</artifactId>
			<version>${project.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-ollama-spring-boot-starter</artifactId>
		</dependency>
	</dependencies>
</project>
''',

    'knowledge-bot-data/pom.xml': '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>com.knowledgebot</groupId>
		<artifactId>knowledge-bot-parent</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</parent>
	<artifactId>knowledge-bot-data</artifactId>
	<name>knowledge-bot-data</name>
	<dependencies>
		<dependency>
			<groupId>com.knowledgebot</groupId>
			<artifactId>knowledge-bot-ai</artifactId>
			<version>${project.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.ai</groupId>
			<artifactId>spring-ai-pgvector-store-spring-boot-starter</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-core</artifactId>
		</dependency>
		<dependency>
			<groupId>org.flywaydb</groupId>
			<artifactId>flyway-database-postgresql</artifactId>
		</dependency>
		<dependency>
			<groupId>org.postgresql</groupId>
			<artifactId>postgresql</artifactId>
			<scope>runtime</scope>
		</dependency>
	</dependencies>
</project>
''',

    'knowledge-bot-web/pom.xml': '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>com.knowledgebot</groupId>
		<artifactId>knowledge-bot-parent</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</parent>
	<artifactId>knowledge-bot-web</artifactId>
	<name>knowledge-bot-web</name>
	<dependencies>
		<dependency>
			<groupId>com.knowledgebot</groupId>
			<artifactId>knowledge-bot-data</artifactId>
			<version>${project.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>
	</dependencies>
	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>
</project>
''',

    'knowledge-bot-cli/pom.xml': '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>com.knowledgebot</groupId>
		<artifactId>knowledge-bot-parent</artifactId>
		<version>1.0.0-SNAPSHOT</version>
	</parent>
	<artifactId>knowledge-bot-cli</artifactId>
	<name>knowledge-bot-cli</name>
	<dependencies>
		<dependency>
			<groupId>com.knowledgebot</groupId>
			<artifactId>knowledge-bot-core</artifactId>
			<version>${project.version}</version>
		</dependency>
		<dependency>
			<groupId>org.springframework.shell</groupId>
			<artifactId>spring-shell-starter</artifactId>
			<version>3.4.0</version>
		</dependency>
	</dependencies>
</project>
'''
}

for path, content in files.items():
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)

print("Generated POM files")
